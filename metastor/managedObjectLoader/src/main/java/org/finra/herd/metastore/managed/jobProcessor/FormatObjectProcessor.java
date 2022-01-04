package org.finra.herd.metastore.managed.jobProcessor;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.format.DetectSchemaChanges;
import org.finra.herd.metastore.managed.format.FormatChange;
import org.finra.herd.metastore.managed.hive.HiveFormatAlterTable;
import org.finra.herd.metastore.managed.hive.HiveTableSchema;
import org.finra.herd.metastore.managed.jobProcessor.dao.PartitionsDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static org.finra.herd.metastore.managed.conf.HerdMetastoreConfig.ALTER_TABLE_MAX_PARTITIONS;

@Component
@Slf4j
public class FormatObjectProcessor extends JobProcessor {

    @Autowired
    private DetectSchemaChanges detectSchemaChanges;

    private FormatChange change;

    @Autowired
    PartitionsDAO partitionsDAO;

    @Autowired
    HiveFormatAlterTable hiveFormatAlterTable;

    @Autowired
    HiveHqlGenerator hiveHqlGenerator;


    @Autowired
    DataMgmtSvc dataMgmtSvc;

    final int concurrency = Runtime.getRuntime().availableProcessors();


    public void writeToFile(String str, String tmpdir) {

        try {



            log.info("Consumer thread inside is : " + Thread.currentThread().getName());

            File hqlFilePath = File.createTempFile("str", ".hql", new File(tmpdir));
            Path path = Paths.get(hqlFilePath.getAbsolutePath());
            Files.write(path, str.getBytes(), APPEND);


        } catch (Exception ioe) {

        }
    }

    @Override
    protected ProcessBuilder createProcessBuilder(JobDefinition jd) {
        try {

            Executor executor = Executors.newFixedThreadPool(concurrency);


            log.info(" thread is : " + Thread.currentThread().getName());
            String tmpdir = Files.createTempDirectory("format").toFile().getAbsolutePath();

            log.info("Dir " + tmpdir);



            List<CompletableFuture> result = formatStrategy(jd).stream().map(str -> CompletableFuture.runAsync(() -> writeToFile(str, tmpdir), executor))
                .collect(Collectors.toList());

            CompletableFuture.allOf(result.toArray(new CompletableFuture[result.size()])).join();


        } catch (Exception e) {
        }
        return null;
    }


    private List<String> formatStrategy(JobDefinition jd) throws ApiException {

        List<String> hiveStatements;
        if (partitionsDAO.getTotalPartitionCount(jd.getObjectDefinition().getObjectName(), jd.getObjectDefinition().getDbName()) < JobProcessorConstants.MAX_PARTITION_FORMAT_LIMIT) {

             hiveStatements = Lists.newArrayList();

            hiveStatements = hiveFormatAlterTable.executeFormatChange(change, jd, hiveStatements, hiveHqlGenerator.cascade(jd));

            return hiveStatements;

        } else {

            log.info("Total Partition Count :{}", partitionsDAO.getTotalPartitionCount(jd.getObjectDefinition().getObjectName(), jd.getObjectDefinition().getDbName()));
            log.info("Name:{},DB:{}", jd.getObjectDefinition().getObjectName(), jd.getObjectDefinition().getDbName());


            hiveStatements = createNewTable(jd);
            grantPermissions(jd);
            renameExisitingTable(jd);


        }

        return hiveStatements;


    }



    private List<String> createNewTable(JobDefinition jd) throws ApiException {

        BusinessObjectDataDdlRequest request = new BusinessObjectDataDdlRequest();
        request.combineMultiplePartitionsInSingleAlterTable(true);
        request.combinedAlterTableMaxPartitions(ALTER_TABLE_MAX_PARTITIONS);
        request.setTableName(jd.getTableName() + "_LATEST");

        String objectName=jd.getObjectDefinition().getObjectName();
        String nameSpace=jd.getObjectDefinition().getNameSpace();
        List<String> rootPartitoions = partitionsDAO.getDistinctRootPartition(objectName,nameSpace);

        int getMaxCount = partitionsDAO.getMaxCount(objectName,nameSpace);

        int splitSize = JobProcessorConstants.MAX_PARTITION_FORMAT_LIMIT/getMaxCount;

        List<String> tmpSubList= new ArrayList<>();
        List<String> hiveDDL = new ArrayList<>();

        AtomicInteger count= new AtomicInteger();
        count.set(0);

        rootPartitoions.forEach(rp -> {

            count.getAndIncrement();
            tmpSubList.add(rp);

            if(count.get() >=splitSize ){
                try {
                    hiveDDL.add(dataMgmtSvc.getBusinessObjectDataDdl(jd, tmpSubList, request).getDdl());
                    tmpSubList.clear();
                    count.set(0);
                }catch(Exception e){
                    throw  new RuntimeException("Unable to process DM");
                }
            }
        });


        hiveDDL.add(dataMgmtSvc.getBusinessObjectDataDdl(jd, tmpSubList, request).getDdl());




        return hiveDDL;


    }

    private void grantPermissions(JobDefinition jobDefinition) {

        log.info("Granting Permissions");
    }

    private void renameExisitingTable(JobDefinition jobDefinition) {

        log.info("Renaming exisiting Table");
    }


}
