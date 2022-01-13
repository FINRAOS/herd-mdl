package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    private FormatUtil formatUtil;

    private FormatProcessorDAO formatProcessorDAO;

    @Autowired
    public RenameFormatStrategy(DataMgmtSvc dataMgmtSvc, PartitionsDAO partitionsDAO, SubmitFormatProcess submitFormatProcess,
                                FormatProcessorDAO formatProcessorDAO, FormatUtil formatUtil) {

        this.dataMgmtSvc = dataMgmtSvc;
        this.partitionsDAO = partitionsDAO;
        this.submitFormatProcess = submitFormatProcess;
        this.errMsg = new StringBuffer();
        this.formatProcessorDAO = formatProcessorDAO;
        this.formatUtil = formatUtil;
    }


    @Override
    public void executeFormatChange(JobDefinition jobDefinition, FormatChange formatChange, boolean cascade) {


        if (runProcess(jobDefinition) && doCountsMatch(jobDefinition)) {
            renameExisitingTable(jobDefinition);

        }

    }


    private boolean runProcess(JobDefinition jobDefinition) {

        boolean result = false;
        List<List<String>> rootPartitionList = getSplitRootPartitionList(jobDefinition);
        String dbName = jobDefinition.getObjectDefinition().getDbName();
        String objectName = jobDefinition.getObjectDefinition().getObjectName();

        log.info("List of rootPartitions for the object => {} {} {}", rootPartitionList, dbName, objectName);
        log.info("Batch Size ===>{}", rootPartitionList.size());
        try {

            StopWatch watch = new StopWatch();
            watch.start();


            List<CompletableFuture<FormatProcessObject>> formatProcessObjectList = createTableAddPartitionDDL(jobDefinition, rootPartitionList);


            CompletableFuture<?>[] formatProcessObjListarr = formatProcessObjectList.toArray(new CompletableFuture[formatProcessObjectList.size()]);


            CompletableFuture<Void> allfuture = CompletableFuture.allOf(formatProcessObjListarr);

            long overallWait = rootPartitionList.size() * 40; // No. of batches * 40 minutes
            allfuture.get(overallWait, TimeUnit.MINUTES); //Blocking call wait for all futures to be done

            watch.stop();

            allfuture.whenComplete((res, err) -> {

                log.info("format Process for table :{} and all partitions ran for :{} ",
                        jobDefinition.getTableName(), watch.getTime());
            });

            List<CompletableFuture<String>> processOutput = formatUtil.printProcessOutput(formatProcessObjectList);


            final List<CompletableFuture<FormatProcessObject>> failedProcessing = formatProcessObjectList.stream().filter(
                    fl -> {
                        boolean isProcessed = false;
                        try {
                            isProcessed = fl.thenApply(f -> f.getProcess().exitValue()).get() != 0;
                        } catch (Exception e) {
                        }
                        return isProcessed;
                    }
            ).collect(Collectors.toList());


            formatProcessObjectList.forEach(
                    fl -> {
                        fl.thenAccept(
                                fop -> {

                                    if (fop.getProcess().exitValue() == 0) {
                                        updateFormatStatus(fop, "P");

                                    } else {
                                        updateFormatStatus(fop, "E");
                                    }
                                }
                        );
                    }
            );


            result = failedProcessing.size() <= 0;

            cleanUp(formatProcessObjectList);

        } catch (Exception e) {

            log.error("Error in execute {}", e.getMessage());
            errMsg.append("Error in execute {}" + e.getMessage());
        }

        return result;
    }

    private void cleanUp(List<CompletableFuture<FormatProcessObject>> futureList) {

        futureList.forEach(
                fl -> {
                    fl.thenAccept(

                            f -> {
                                if (f.getProcess().exitValue() != 0 || f.getProcess().isAlive()) {
                                    log.info("Going to kill process");

                                    try {
                                        f.getProcess().destroyForcibly();
                                    } catch (Exception e) {
                                        log.info("Unable to kill process");
                                    }
                                }
                            });
                    ;
                }
        );

    }

    @Override
    public boolean hasFormatCompleted() {

        return this.isComplete;
    }


    @Override
    public String getErr() {
        return this.errMsg.toString();
    }


    private void updateFormatStatus(FormatProcessObject formatProcessObject, String status) {

        FormatStatus formatStatus = FormatStatus.builder()
                .partitionValues(StringUtils.join(formatProcessObject.partitionList, ","))
                .formatUsage(formatProcessObject.jobDefinition.getObjectDefinition().getUsageCode())
                .partitionKey(formatProcessObject.jobDefinition.getPartitionKey())
                .notificationId(formatProcessObject.jobDefinition.getId())
                .workflowType(formatProcessObject.jobDefinition.getWfType())
                .objDefName(formatProcessObject.jobDefinition.getObjectDefinition().getObjectName())
                .formatStatus(status)
                .errorMessage(this.getErr())
                .namespace(formatProcessObject.jobDefinition.getObjectDefinition().getNameSpace())
                .fileType(formatProcessObject.jobDefinition.getObjectDefinition().getFileType())
                .build();
        log.info("Value of formatProcessObject is {}", formatProcessObject);
        log.info("Value of formatStatus is {}", formatStatus);
        formatProcessorDAO.addFormatStatus(formatStatus);


    }


    private List<CompletableFuture<FormatProcessObject>> createTableAddPartitionDDL(JobDefinition jobDefinition, List<List<String>> rootPartitionList) throws IOException {

        BusinessObjectDataDdlRequest request = new BusinessObjectDataDdlRequest();
        request.combineMultiplePartitionsInSingleAlterTable(true);
        request.combinedAlterTableMaxPartitions(JobProcessorConstants.ALTER_TABLE_ADD_MAX_PARTITIONS);
        request.setTableName(jobDefinition.getTableName() + "_LATEST");
        String tmpdir = Files.createTempDirectory("format").toFile().getAbsolutePath();

        log.info("The tmp dir for format is ==> {}", tmpdir);

        List<CompletableFuture<FormatProcessObject>> formatProcessObjectList = new ArrayList<>();


        rootPartitionList.forEach(partitionList -> {

            List<String> hiveDdl = getableAddPartitionDDL(jobDefinition, partitionList, request);
            File tmpFile = submitFormatProcess.createHqlFile(hiveDdl, tmpdir);
            FormatProcessObject formatProcessObject = FormatProcessObject.builder()
                    .partitionList(partitionList)
                    .jobDefinition(jobDefinition)
                    .build();
            formatProcessObjectList.add(submitFormatProcess.submitProcess(tmpFile, formatProcessObject));


        });

        return formatProcessObjectList;

    }


    private List<String> getableAddPartitionDDL(JobDefinition jobDefinition, List<String> partitionList, BusinessObjectDataDdlRequest businessObjectDataDdlRequest) {

        List<String> hiveDDL = new ArrayList<>();
        String useDb = "use " + jobDefinition.getObjectDefinition().getDbName() + ";";
        hiveDDL.add(useDb);


        try {
            hiveDDL.add(dataMgmtSvc.getBusinessObjectDataDdl(jobDefinition, partitionList, businessObjectDataDdlRequest).getDdl());
        } catch (ApiException api) {
            log.info("Unable to query DM:{}", api.getResponseBody());
            errMsg.append("Unable to query DM");
        }
        return hiveDDL;

    }

    private List<List<String>> getSplitRootPartitionList(JobDefinition jd) {

        String tableName = jd.getTableName();
        String dbName = jd.getObjectDefinition().getDbName();
        List<String> rootPartitions = partitionsDAO.getDistinctRootPartition(tableName, dbName);

        // Highest Partition Count in all the partitions for a given Object
        int getMaxCount = partitionsDAO.getMaxCount(tableName, dbName);

        // To determine how to split for DM calls. Goal is to make sure we do not exceed the
        // ALTER_TABLE_ADD_MAX_PARTITIONS Limit for any given DM call
        int splitSize = JobProcessorConstants.ALTER_TABLE_ADD_MAX_PARTITIONS / getMaxCount;

        return Lists.partition(rootPartitions, splitSize);


    }

    private boolean doCountsMatch(JobDefinition jd) {

        String existingTableName = jd.getTableName();
        String newTableName = jd.getTableName().concat("_LATEST");
        return partitionsDAO.getTotalPartitionCount(existingTableName, jd.getObjectDefinition().getDbName()) ==
                partitionsDAO.getTotalPartitionCount(newTableName, jd.getObjectDefinition().getDbName());

    }

    private void grantPermissions(JobDefinition jobDefinition) {

        log.info("Granting Permissions");
    }


    private void renameExisitingTable(JobDefinition jobDefinition) {

        String existingTableName = jobDefinition.getTableName();
        String newTableName = jobDefinition.getTableName().concat("_LATEST");
        String renameTime = new SimpleDateFormat("yyyy_MM_dd_HmsS").format(new Date());
        String dbName = jobDefinition.getObjectDefinition().getDbName();
        List<String> hqlStatements = new ArrayList<>();
        hqlStatements.add("USE " + dbName + ";" + "CREATE DATABASE IF NOT EXISTS archive;");
        hqlStatements.add("ALTER TABLE  " + existingTableName + " RENAME TO archive." + existingTableName + renameTime + ";");
        hqlStatements.add("ALTER TABLE  " + newTableName + " RENAME TO " + existingTableName + ";");


        try {
            String tmpdir = Files.createTempDirectory("rename").toFile().getAbsolutePath();

            log.info("The tmp dir for rename is ==> {}", tmpdir);

            CompletableFuture<Process> formatProcess = submitFormatProcess.submitProcess(submitFormatProcess.createHqlFile(hqlStatements, tmpdir));
            //TODO Comment after finishing testing
            CompletableFuture<String> processOutput = formatUtil.printProcessOutput(formatProcess);

            formatUtil.handleProcess(formatProcess);
            formatProcess.whenComplete((proc, err) -> {

                this.isComplete=formatUtil.processComplete(existingTableName, dbName, err);
            });


        } catch (Exception e) {
                log.error("Unable to rename the Object");

        }


    }


}

