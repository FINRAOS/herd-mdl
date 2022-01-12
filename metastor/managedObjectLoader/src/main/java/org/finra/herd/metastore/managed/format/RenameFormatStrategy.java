package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatProcessorDAO;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatStatus;
import org.finra.herd.metastore.managed.jobProcessor.dao.PartitionsDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.lang.Boolean;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Slf4j
@Qualifier("renameFormat")
@ToString
public class RenameFormatStrategy implements FormatStrategy {

    @Getter
    private boolean isComplete;
    private DataMgmtSvc dataMgmtSvc;
    private PartitionsDAO partitionsDAO;
    private StringBuffer errMsg;
    private SubmitFormatProcess submitFormatProcess;

    private FormatProcessorDAO formatProcessorDAO;

    @Autowired
    public RenameFormatStrategy(DataMgmtSvc dataMgmtSvc,PartitionsDAO partitionsDAO,SubmitFormatProcess submitFormatProcess,FormatProcessorDAO formatProcessorDAO){

        this.dataMgmtSvc= dataMgmtSvc;
        this.partitionsDAO=partitionsDAO;
        this.submitFormatProcess=submitFormatProcess;
        this.errMsg= new StringBuffer();
        this.formatProcessorDAO=formatProcessorDAO;
    }



    @Override
    public void executeFormatChange(JobDefinition jobDefinition, FormatChange formatChange, boolean cascade){

        List<List<String>> rootPartitionList= getSplitRootPartitionList(jobDefinition);
        String dbName=jobDefinition.getObjectDefinition().getDbName();
        String objectName=jobDefinition.getObjectDefinition().getObjectName();

        log.info("List of rootPartitions for the object => {} {} {}",rootPartitionList,dbName,objectName);
        try{

            List<CompletableFuture<FormatProcessObject>> formatProcessObjectList = createTableAddPartitionDDL(jobDefinition,rootPartitionList);

            printProcessOutput(formatProcessObjectList);

            CompletableFuture<?>[] formatProcessObjListarr = formatProcessObjectList.toArray(new CompletableFuture[formatProcessObjectList.size()]);

            CompletableFuture<Void> allfuture= CompletableFuture.allOf(formatProcessObjListarr);

//Wait for all Process to complete
            if(!allfuture.isDone())
            {
                log.info("all process not complete");
                allfuture.get();
                //Hopefully done bythis time?
                log.info("all process  complete");


            }


            Map<Boolean, List<CompletableFuture<FormatProcessObject>>> result =
                    formatProcessObjectList
                            .stream()
                            .collect(Collectors.partitioningBy(CompletableFuture::isCompletedExceptionally));


            setFormatStatus(result);





        }catch (Exception e){

            log.error("Error in execute {}",e.getMessage());
            errMsg.append("Error in execute {}"+e.getMessage());
        }

    }

    @Override
    public boolean hasFormatCompleted(){

        return this.isComplete;
    }


    @Override
    public String getErr(){
        return  this.errMsg.toString();
    }



    private void buildFormatStatus(List<CompletableFuture<FormatProcessObject>> futureFormatObjList, String status)
    {

        futureFormatObjList.forEach(future -> {

            future.thenAccept(formatProcessObject -> {

                FormatStatus formatStatus = FormatStatus.builder()
                        .partitionValues(StringUtils.join(formatProcessObject.partitionList, ","))
                        .formatUsage(formatProcessObject.jobDefinition.getObjectDefinition().getUsageCode())
                        .clusterName(formatProcessObject.jobDefinition.getClusterName())
                        .partitionKey(formatProcessObject.jobDefinition.getPartitionKey())
                        .notificationId(formatProcessObject.jobDefinition.getId())
                        .workflowType(formatProcessObject.jobDefinition.getWfType())
                        .objDefName(formatProcessObject.jobDefinition.getObjectDefinition().getObjectName())
                        .formatStatus(status)
                        .build();
                log.info("Value of formatProcessObject is {}", formatProcessObject);
                formatProcessorDAO.addFormatStatus(
                        formatStatus
                );


            });


        });


    }
    private  void setFormatStatus(Map<Boolean,List<CompletableFuture<FormatProcessObject>>> resultsMap){


        Objects.requireNonNull(resultsMap,"Results of format process in Rename can not be Null!");


         resultsMap.forEach((result,futureFormatObjList)-> {


             this.isComplete=true;

             if (!result) {

                 buildFormatStatus(futureFormatObjList,"E");
             }
             else {

                 buildFormatStatus(futureFormatObjList,"P");

         }
    });
    }






    private void printProcessOutput(List<CompletableFuture<FormatProcessObject>> formatProcessObjectList) {

        List<CompletableFuture<String>> processOutput = formatProcessObjectList.stream().map(proc ->
                proc.thenApplyAsync(

                        p -> {
                            String s = null;

                            try {
                                s = CharStreams.toString(new InputStreamReader(
                                        p.getProcess().getInputStream(), Charsets.UTF_8));
                                log.info("Thread in collect"+Thread.currentThread().getName());

                            } catch (Exception ie) {
                                log.info("processouput" +ie.getMessage());
                            }
                            return s;
                        }

                )).collect(Collectors.toList());


        processOutput.forEach(c-> c.thenAccept(s->log.info(s)));


    }
    private List<CompletableFuture<FormatProcessObject>> createTableAddPartitionDDL(JobDefinition jobDefinition,List<List<String>> rootPartitionList) throws IOException {

        BusinessObjectDataDdlRequest request = new BusinessObjectDataDdlRequest();
        request.combineMultiplePartitionsInSingleAlterTable(true);
        request.combinedAlterTableMaxPartitions(JobProcessorConstants.ALTER_TABLE_ADD_MAX_PARTITIONS);
        request.setTableName(jobDefinition.getTableName() + "_LATEST");
        String tmpdir = Files.createTempDirectory("format").toFile().getAbsolutePath();

        log.info("The tmp dir for format is ==> {}", tmpdir);

        List<CompletableFuture<FormatProcessObject>> formatProcessObjectList=new ArrayList<>();



        rootPartitionList.forEach(partitionList -> {

            List<String> hiveDdl = getableAddPartitionDDL(jobDefinition,partitionList,request);
            File tmpFile=submitFormatProcess.createHqlFile(hiveDdl,tmpdir);
            FormatProcessObject formatProcessObject=FormatProcessObject.builder()
                    .partitionList(partitionList)
            .jobDefinition(jobDefinition)
            .build();
            formatProcessObjectList.add(submitFormatProcess.submitProcess(tmpFile,formatProcessObject));

        });

        return formatProcessObjectList;

    }


    private List<String> getableAddPartitionDDL(JobDefinition jobDefinition,List<String> partitionList,BusinessObjectDataDdlRequest businessObjectDataDdlRequest) {

                List<String> hiveDDL= new ArrayList<>();
                String useDb = "use "+jobDefinition.getObjectDefinition().getDbName()+";";
                hiveDDL.add(useDb);


        try {
                        hiveDDL.add(dataMgmtSvc.getBusinessObjectDataDdl(jobDefinition, partitionList, businessObjectDataDdlRequest).getDdl());
                    }catch(ApiException api){
                        //Add error

                    }
                    log.info("HIVE DDL FOR RENAME :{}",hiveDDL);
        return hiveDDL;

    }

    private List<List<String>> getSplitRootPartitionList(JobDefinition jd)  {

        String tableName=jd.getTableName();
        String dbName=jd.getObjectDefinition().getDbName();
        List<String> rootPartitions = partitionsDAO.getDistinctRootPartition(tableName,dbName);

        // Highest Partition Count in all the partitions for a given Object
        int getMaxCount = partitionsDAO.getMaxCount(tableName,dbName);

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



    private static <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allDoneFuture =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        return allDoneFuture.thenApply(v ->
                futures.stream().
                        map(future -> future.join()).
                        collect(Collectors.toList())
        );
    }


}

