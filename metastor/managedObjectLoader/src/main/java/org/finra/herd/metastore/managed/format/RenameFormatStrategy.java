package org.finra.herd.metastore.managed.format;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.JobPicker;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.hive.HiveClientImpl;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatProcessorDAO;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatStatus;
import org.finra.herd.metastore.managed.jobProcessor.dao.PartitionsDAO;
import org.finra.herd.metastore.managed.stats.StatsHelper;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;


@Component("renameFormat")
@Slf4j
@ToString
@NoArgsConstructor
@Scope("prototype")
public class RenameFormatStrategy implements FormatStrategy {

    @Getter
    private boolean isComplete;
    private DataMgmtSvc dataMgmtSvc;
    private PartitionsDAO partitionsDAO;
    private StringBuffer errMsg;
    private SubmitFormatProcess submitFormatProcess;
    private FormatUtil formatUtil;
    private NotificationSender notificationSender;
    private HRoles hRoles;
    private FormatProcessorDAO formatProcessorDAO;
    private StatsHelper statsHelper;
    private JobProcessorConstants jobProcessorConstants;
    private String clusterId;
    private String workerId;
    private JobPicker jobPicker;

    private HiveClientImpl hiveClient;

    @Autowired
    public RenameFormatStrategy(DataMgmtSvc dataMgmtSvc, PartitionsDAO partitionsDAO, SubmitFormatProcess submitFormatProcess,
                                FormatProcessorDAO formatProcessorDAO, FormatUtil formatUtil, NotificationSender notificationSender,
                                StatsHelper statsHelper, HRoles hRoles, JobProcessorConstants jobProcessorConstants,
                                JobPicker jobPicker, HiveClientImpl hiveClient) {

        this.dataMgmtSvc = dataMgmtSvc;
        this.partitionsDAO = partitionsDAO;
        this.submitFormatProcess = submitFormatProcess;
        this.errMsg = new StringBuffer();
        this.formatProcessorDAO = formatProcessorDAO;
        this.formatUtil = formatUtil;
        this.notificationSender = notificationSender;
        this.statsHelper = statsHelper;
        this.hRoles = hRoles;
        this.jobProcessorConstants = jobProcessorConstants;
        this.jobPicker = jobPicker;
        this.hiveClient = hiveClient;
    }


    @Override
    public void executeFormatChange(JobDefinition jobDefinition, FormatChange formatChange, boolean cascade, String clusterId, String workerId) {


        this.workerId = workerId;
        this.clusterId = clusterId;
        List<List<String>> rootPartitionList = getSplitRootPartitionList(jobDefinition);

        if (runProcess(jobDefinition, rootPartitionList) && isPartitionCountCorrect(jobDefinition)) {
            renameExisitingTable(jobDefinition);

            if (this.isComplete) {
                rootPartitionList.forEach(
                        partitionList ->
                                statsHelper.addAnalyzeStats(jobDefinition, partitionList)
                );
            }

        }


    }


    private boolean runProcess(JobDefinition jobDefinition, List<List<String>> rootPartitionList) {

        boolean result = false;
        String dbName = jobDefinition.getObjectDefinition().getDbName();
        String objectName = jobDefinition.getObjectDefinition().getObjectName();

        log.info("List of rootPartitions for the object => {} {} {}", rootPartitionList, dbName, objectName);
        log.info("Batch Size ===>{}", rootPartitionList.size());
        List<CompletableFuture<FormatProcessObject>> formatProcessObjectList = null;
        try {

            StopWatch watch = new StopWatch();
            watch.start();
            formatProcessObjectList = createTableAddPartition(jobDefinition, rootPartitionList);

            log.info("size of formatProcessObjectList" + formatProcessObjectList.size());

            if (!formatProcessObjectList.isEmpty()) {

                CompletableFuture<?>[] formatProcessObjListarr = formatProcessObjectList.toArray(new CompletableFuture[formatProcessObjectList.size()]);
                CompletableFuture<Void> allfuture = CompletableFuture.allOf(formatProcessObjListarr);

                while (!allfuture.isDone()) {
                    try {
                        allfuture.get(1, TimeUnit.MINUTES);

                    } catch (TimeoutException te) {
                        log.info("Extending lock for object ==>" + jobDefinition.getObjectDefinition());
                        jobPicker.extendLock(jobDefinition, this.clusterId, this.workerId);
                    }

                }

//            long overallWait = rootPartitionList.size() * 40; // No. of batches * 40 minutes
//            allfuture.get(overallWait, TimeUnit.MINUTES); //Blocking call wait for all futures to be done

                watch.stop();

                allfuture.whenComplete((res, err) -> {

                    log.info("format Process for table :{} and all partitions ran for :{} ",
                            jobDefinition.getTableName(), watch.getTime(TimeUnit.SECONDS));
                    if (err != null) {
                        log.error("The error is {}", err.getMessage());
                    }
                /*
                  Do not handle error here since we need to track error at individual process level.
                 */
                });

                final List<CompletableFuture<FormatProcessObject>> failedProcessing = formatProcessObjectList.stream().filter(
                        fl -> {
                            boolean isProcessed = false;
                            try {
                                isProcessed = fl.thenApply(f -> f.getExitValue()).get() != 0;
                            } catch (Exception e) {
                            }
                            return isProcessed;
                        }
                ).collect(Collectors.toList());

                formatProcessObjectList.forEach(
                        fl -> {
                            fl.thenAccept(
                                    fop -> {

                                        if (fop.getExitValue() == 0) {
                                            updateFormatStatus(fop, "P");

                                        } else {
                                            updateFormatStatus(fop, "E");
                                        }
                                    }
                            );
                        }
                );


                result = failedProcessing.size() <= 0;
            } else {
                log.info("Format process list is empty");
            }


        } catch (Exception e) {

            log.error("Error in execute {}", e.getMessage());
            errMsg.append("Error in execute {}" + e.getMessage());
            notificationSender.sendFailureEmail(jobDefinition, 1, "Unbale to create  the new latest object (Rename) after format change" + this.getErr(), "");
        }

        return result;
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


    private List<CompletableFuture<FormatProcessObject>> createTableAddPartition(JobDefinition jobDefinition, List<List<String>> rootPartitionList) throws Exception {

        BusinessObjectDataDdlRequest request = new BusinessObjectDataDdlRequest();
        request.combineMultiplePartitionsInSingleAlterTable(true);
        request.setTableName(jobDefinition.getTableName() + "_LATEST");

        List<CompletableFuture<FormatProcessObject>> formatProcessObjectList = new ArrayList<>();

        List<String> hiveDdl = new ArrayList<>();


        int count = 0;
        boolean isTableCreated = false;
        try {

            isTableCreated = createTable(jobDefinition, rootPartitionList, request);
            while (!isTableCreated && count < 2) {
                Thread.sleep(5000);
                isTableCreated = hiveClient.tableExist(jobDefinition.getObjectDefinition().getDbName(), jobDefinition.getTableName() + "_LATEST");
                count++;
            }

        } catch (SQLException se) {
            new RuntimeException("unable to create table" + se.getMessage());
        }

        if (isTableCreated) {
            rootPartitionList.forEach(partitionList -> {
                String setHiveClientTimeout = "set hive.metastore.client.socket.timeout=3600;";
                String useDb = "use " + jobDefinition.getObjectDefinition().getDbName() + ";";
                hiveDdl.add(setHiveClientTimeout);
                hiveDdl.add(useDb);
                Optional<String> ddl = getPartitionDdlFromDM(jobDefinition, partitionList, request);
                addPartition(jobDefinition, formatProcessObjectList, hiveDdl, partitionList, ddl);
                hiveDdl.clear();
                if (ddl.isPresent()) {
                    log.info("No. of partitions from DM is :{}", StringUtils.countMatches(ddl.get(), "PARTITION"));
                }
                try {
                    Thread.sleep(5000); //Pause for some time before submitting the next job
                } catch (InterruptedException ie) {
                }
            });
        } else {
            log.info("" +
                    "Can not add partitions table not created");
        }

        return formatProcessObjectList;

    }

    private void addPartition(JobDefinition jobDefinition, List<CompletableFuture<FormatProcessObject>> formatProcessObjectList, List<String> hiveDdl, List<String> partitionList, Optional<String> ddl) {
        hiveDdl.add(formatUtil.getAlterTableStatemts(ddl));
        File tmpFile = submitFormatProcess.createHqlFile(hiveDdl);
        FormatProcessObject formatProcessObject = FormatProcessObject.builder()
                .partitionList(partitionList)
                .jobDefinition(jobDefinition)
                .build();
        formatProcessObjectList.add(submitFormatProcess.submitProcess(submitFormatProcess.getCommandLine(tmpFile), formatProcessObject));
        jobPicker.extendLock(jobDefinition, this.clusterId, this.workerId);
    }


    private boolean createTable(JobDefinition jobDefinition, List<List<String>> rootPartitionList, BusinessObjectDataDdlRequest request) throws SQLException {

        Optional<String> ddl = getPartitionDdlFromDM(jobDefinition, rootPartitionList.get(0), request);
        String dbName = jobDefinition.getObjectDefinition().getDbName();

        if (ddl.isPresent()) {
            String[] ddlArr;
            ddlArr = ddl.get().split(";");
            log.info("Create table DDL ==>{}", ddlArr[0]);
            return hiveClient.runHiveQuery(dbName, ddlArr[0]);
        } else {
            log.info("DDL is empty");
            return false;
        }
    }


    private Optional getPartitionDdlFromDM(JobDefinition jobDefinition, List<String> partitionList, BusinessObjectDataDdlRequest businessObjectDataDdlRequest) {


        Optional<String> ddl = null;

        try {
            ddl = Optional.of(dataMgmtSvc.getBusinessObjectDataDdl(jobDefinition, partitionList, businessObjectDataDdlRequest).getDdl());
        } catch (ApiException api) {
            log.info("Unable to query DM:{}", api.getResponseBody());
            errMsg.append("Unable to query DM");

        }
        return ddl;

    }

    private List<List<String>> getSplitRootPartitionList(JobDefinition jd) {

        String tableName = jd.getTableName();
        String dbName = jd.getObjectDefinition().getDbName();
        List<String> rootPartitions = partitionsDAO.getDistinctRootPartition(tableName, dbName);

        // Highest Partition Count in all the partitions for a given Object
        int getMaxCount = partitionsDAO.getMaxCount(tableName, dbName);

        log.info("getMaxCount :{}", getMaxCount);

        // To determine how to split for DM calls. Goal is to make sure we do not exceed the
        // ALTER_TABLE_ADD_MAX_PARTITIONS Limit for any given DM call
        int splitSize = jobProcessorConstants.getAlterTableAddMaxPartitions() / getMaxCount;


        log.info("splitSize :{}", splitSize);

        return Lists.partition(rootPartitions, splitSize);


    }

    //TODO check with corey on when this may or may not happen.

    private boolean isPartitionCountCorrect(JobDefinition jd) {

        String existingTableName = jd.getTableName();
        String newTableName = jd.getTableName().concat("_LATEST");
        boolean result = partitionsDAO.getTotalPartitionCount(existingTableName, jd.getObjectDefinition().getDbName()) <=
                partitionsDAO.getTotalPartitionCount(newTableName, jd.getObjectDefinition().getDbName());
        if (!result) {
            notificationSender.sendFailureEmail(jd, jd.getNumOfRetry(), "The latest object has lesser partitions count than curent object", jd.getClusterName());
        }

        return result;

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
        List<String> grantRolesHql = hRoles.grantPrestoRoles(jobDefinition);
        if (!grantRolesHql.isEmpty()) {
            hqlStatements.addAll(grantRolesHql);
        }


        try {

            File tmpFile = submitFormatProcess.createHqlFile(hqlStatements);

            DefaultExecuteResultHandler renameProcess = submitFormatProcess.submitProcess(submitFormatProcess.getCommandLine(tmpFile));

            int count = 0;
            /*
             Every 3 minutes extend the lock and after an hour break
             */
            while (!renameProcess.hasResult() && count < 20) {
                jobPicker.extendLock(jobDefinition, clusterId, workerId);
                try {
                    Thread.sleep(180_0000);
                    count++;
                } catch (InterruptedException ie) {
                    log.info("Not done yet keep retrying");
                }
            }

            this.isComplete = renameProcess.getExitValue() == 0;

            if (!this.isComplete) {
                notificationSender.sendFailureEmail(jobDefinition, jobDefinition.getNumOfRetry(), "Error occurred during rename operation", this.clusterId);
            }


        } catch (Exception e) {
            log.error("Unable to rename the latest Object in hive after format change" + e.getMessage());
            errMsg.append("Unable to rename the latest Object in hive after format change" + e.getMessage());
            notificationSender.sendFailureEmail(jobDefinition, jobDefinition.getNumOfRetry(), "Unable to rename the object in Hive " + this.getErr(), this.clusterId);


        }


    }


}

