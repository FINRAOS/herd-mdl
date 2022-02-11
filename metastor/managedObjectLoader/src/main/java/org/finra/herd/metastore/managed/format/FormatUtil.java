package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.ExecuteResultHandler;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.JobPicker;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.hive.HiveClientImpl;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.finra.herd.metastore.managed.util.JobProcessorConstants.LATEST;

@Service
@Slf4j
@Scope("prototype")
public class FormatUtil {


    private SubmitFormatProcess submitFormatProcess;
    private HiveClientImpl hiveClient;
    private JobPicker jobPicker;
    private NotificationSender notificationSender;


    @Autowired
    public FormatUtil(SubmitFormatProcess submitFormatProcess, HiveClientImpl hiveClient, JobPicker jobPicker, NotificationSender
            notificationSender) {
        this.submitFormatProcess = submitFormatProcess;
        this.hiveClient = hiveClient;
        this.jobPicker = jobPicker;
        this.notificationSender = notificationSender;
    }


    public String getAlterTableStatemts(Optional<String> ddl) {

        String[] ddlArr = null;
        if (ddl.isPresent()) {
            ddlArr = ddl.get().split(";");
            return ddlArr[1] + ";";
        } else {
            log.info("DDL is empty ==> No cookie for you!");
        }

        return null;
    }


    protected boolean renameExisitingTable(JobDefinition jobDefinition, String clusterId, String workerId, Optional tableName) {

        String existingTableName = jobDefinition.getTableName();
        String newTableName = tableName.isPresent() ? tableName.get().toString() : jobDefinition.getTableName().concat(LATEST);
        String renameTime = new SimpleDateFormat("yyyy_MM_dd_HmsS").format(new Date());
        String dbName = jobDefinition.getObjectDefinition().getDbName();
        boolean isComplete = false;
        try {

            List<String> hqlStatements = new ArrayList<>();
            hqlStatements.add("USE " + dbName + ";" + "CREATE DATABASE IF NOT EXISTS archive;");
            hqlStatements.add("set role admin;");
            List<HRoles> existingRoles = new ArrayList<>();
            List<String> grantRolesHql = grantPrestoRoles(jobDefinition, existingRoles);

            if (!grantRolesHql.isEmpty()) {
                grantRolesHql.add(0, "set role admin");
                hiveClient.executeQueries(dbName, grantRolesHql);
                Thread.sleep(5000);
                List<HRoles> grantedRoles = hiveClient.getRoles(dbName.toLowerCase(), newTableName.toLowerCase());
                log.info("Are the roles set properly? :{}", CollectionUtils.subtract(existingRoles, grantedRoles).size());
            }

            hqlStatements.add("ALTER TABLE  " + existingTableName + " RENAME TO archive." + existingTableName + renameTime + ";");
            hqlStatements.add("ALTER TABLE  " + newTableName + " RENAME TO " + existingTableName + ";");
            File tmpFile = submitFormatProcess.createHqlFile(hqlStatements);
            DefaultExecuteResultHandler renameProcess = submitFormatProcess.submitProcess(submitFormatProcess.getCommandLine(tmpFile));

            int count = 0;
            /*
             Every 2 minutes extend the lock and after an hour break
             */
            while (!renameProcess.hasResult() && count < 20) {
                jobPicker.extendLock(jobDefinition, clusterId, workerId);
                try {
                    Thread.sleep(120_000);
                    count++;
                } catch (InterruptedException ie) {
                    log.info("Not done yet keep retrying");
                }
            }

            isComplete = renameProcess.getExitValue() == 0;
            log.info("Rename of object completed? :{} ==> {}", jobDefinition, isComplete);

            if (!isComplete) {
                notificationSender.sendFailureEmail(jobDefinition, jobDefinition.getNumOfRetry(), "Error occurred during rename operation", clusterId);
            }


        } catch (Exception e) {
            log.error("Unable to rename the latest Object in hive" + e.getMessage());
            notificationSender.sendFailureEmail(jobDefinition, jobDefinition.getNumOfRetry(), "Unable to rename the object in Hive ", clusterId);


        }
        return isComplete;


    }

    private List<String> grantPrestoRoles(JobDefinition jobDefinition, List<HRoles> roles) throws SQLException {


        String dbName = jobDefinition.getObjectDefinition().getDbName();
        String tableName = jobDefinition.getTableName().concat(LATEST);
        roles = hiveClient.getRoles(dbName.toLowerCase(), jobDefinition.getTableName().toLowerCase());
        log.info("Roles are ==>{}", roles);

        String objName = dbName + "." + tableName;
        if (!roles.isEmpty()) {

            return roles.stream().map(role -> role.isGrantOption() ?
                    ("GRANT " + role.getPrivilege()  +" ON TABLE " + objName + " TO ROLE " + role.getPrincipalName() + " WITH GRANT OPTION") :
                    ("GRANT "+ role.getPrivilege() +" ON TABLE " + objName + " TO ROLE " + role.getPrincipalName())).collect(Collectors.toList());


        }


        return Collections.emptyList();

    }


}
