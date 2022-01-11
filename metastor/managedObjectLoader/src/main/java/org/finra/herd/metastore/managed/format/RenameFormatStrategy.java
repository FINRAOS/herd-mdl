package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.jobProcessor.dao.PartitionsDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdl;
import org.finra.herd.sdk.model.BusinessObjectDataDdlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static org.finra.herd.metastore.managed.conf.HerdMetastoreConfig.ALTER_TABLE_MAX_PARTITIONS;


@Service
@Slf4j
@Qualifier("renameFormat")
@NoArgsConstructor
@Getter
@Setter
@ToString
public class RenameFormatStrategy implements FormatStrategy {

    @Autowired
    protected DataMgmtSvc dataMgmtSvc;

    @Autowired
    PartitionsDAO partitionsDAO;

    @Getter
    private boolean isComplete;

    private StringBuffer errMsg;

    @Autowired
    SubmitFormatProcess submitFormatProcess;

    @Override
    public void executeFormatChange(JobDefinition jobDefinition, FormatChange formatChange, boolean cascade){

        runProcess(jobDefinition);


    }

    @Override
    public boolean hasFormatCompleted(){

        return this.isComplete;
    }


    @Override
    public String getErr(){
        return  this.errMsg.toString();
    }


    private void runProcess(JobDefinition jobDefinition){

        List<List<String>> rootPartitionList= getSplitRootPartitionList(jobDefinition);

        try{
            List<CompletableFuture<Process>> processList = createTableAddPartitionDDL(jobDefinition,rootPartitionList);

            List<CompletableFuture<String>> processOutput = processList.stream().map(proc ->
                    proc.thenApplyAsync(



                            p -> {
                                String s = null;

                                try {
                                    s = CharStreams.toString(new InputStreamReader(
                                            p.getInputStream(), Charsets.UTF_8));
                                    log.info("Thread in collect"+Thread.currentThread().getName());

                                } catch (Exception ie) {
                                    log.info("processouput" +ie.getMessage());
                                }
                                return s;
                            }

                    )).collect(Collectors.toList());



            Consumer<String> printProcessOutput= (s)-> log.info(s);

            processOutput.forEach(c-> c.thenAcceptAsync(printProcessOutput));

            CompletableFuture.allOf(processList.toArray(new CompletableFuture[0]))
                    // avoid throwing an exception in the join() call
                    .exceptionally(ex -> null)
                    .join();
            Map<Boolean, List<CompletableFuture<Process>>> result =
                    processList.stream()
                            .collect(Collectors.partitioningBy(CompletableFuture::isCompletedExceptionally));

            log.info("RESULT =" + result);

            result.forEach((res,process)->{
                if(!res){
                    log.info("process failed"+process);
                    throw new RuntimeException("this process failed");
                }
            });


        }catch (Exception e){

        }



    }

    private List<CompletableFuture<Process>> createTableAddPartitionDDL(JobDefinition jobDefinition,List<List<String>> rootPartitionList) throws IOException {

        BusinessObjectDataDdlRequest request = new BusinessObjectDataDdlRequest();
        request.combineMultiplePartitionsInSingleAlterTable(true);
        request.combinedAlterTableMaxPartitions(JobProcessorConstants.ALTER_TABLE_ADD_MAX_PARTITIONS);
        request.setTableName(jobDefinition.getTableName() + "_LATEST");
        String tmpdir = Files.createTempDirectory("format").toFile().getAbsolutePath();

        log.info("The tmp dir for format is ==> {}", tmpdir);

        List<CompletableFuture<Process>> processList=new ArrayList<>();


        rootPartitionList.forEach(partitionList -> {
            processList.add(
                    submitFormatProcess.submitProcess(
                    submitFormatProcess.createHqlFile(
                            getableAddPartitionDDL(jobDefinition,partitionList,request),tmpdir)
                    )
            );

        });

        return processList;

    }


    private List<String> getableAddPartitionDDL(JobDefinition jobDefinition,List<String> partitionList,BusinessObjectDataDdlRequest businessObjectDataDdlRequest) {

                List<String> hiveDDL= new ArrayList<>();

                    try {
                        hiveDDL.add(dataMgmtSvc.getBusinessObjectDataDdl(jobDefinition, partitionList, businessObjectDataDdlRequest).getDdl());
                    }catch(ApiException api){
                        //Add error

                    }
        return hiveDDL;

    }

    private List<List<String>> getSplitRootPartitionList(JobDefinition jd)  {

        String objectName=jd.getObjectDefinition().getObjectName();
        String nameSpace=jd.getObjectDefinition().getNameSpace();
        List<String> rootPartitions = partitionsDAO.getDistinctRootPartition(objectName,nameSpace);

        // Highest Partition Count in all the partitions for a given Object
        int getMaxCount = partitionsDAO.getMaxCount(objectName,nameSpace);

        // To determine how to split for DM calls. Goal is to make sure we do not exceed the
        // ALTER_TABLE_ADD_MAX_PARTITIONS Limit for any given DM call
        int splitSize = JobProcessorConstants.ALTER_TABLE_ADD_MAX_PARTITIONS/getMaxCount;

        return Lists.partition(rootPartitions,splitSize);




    }

    private void grantPermissions(JobDefinition jobDefinition) {

        log.info("Granting Permissions");
    }

    private void renameExisitingTable(JobDefinition jobDefinition) {

        log.info("Renaming exisiting Table");
    }




}

