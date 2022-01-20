package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.JobPicker;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.hive.HiveFormatAlterTable;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatProcessorDAO;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatStatus;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/*

 If the number of partitions in metastore are less than 50k use this Strategy
 */
@Slf4j
@Component("regularFormat")
@ToString
@Scope("prototype")
public class RegularFormatStrategy implements FormatStrategy {

    @Getter
    private boolean isComplete;
    private HiveFormatAlterTable hiveFormatAlterTable;
    private SubmitFormatProcess submitFormatProcess;
    private FormatProcessorDAO formatProcessorDAO;
    private StringBuffer errMsg;
    private FormatUtil formatUtil;
    private NotificationSender notificationSender;
    private String clusterId;
    private String workerId;
    private JobPicker jobPicker;


    @Autowired
    public RegularFormatStrategy(HiveFormatAlterTable hiveFormatAlterTable, SubmitFormatProcess submitFormatProcess,
                                 FormatProcessorDAO formatProcessorDAO, FormatUtil formatUtil,NotificationSender notificationSender,JobPicker jobPicker) {

        this.hiveFormatAlterTable = hiveFormatAlterTable;
        this.submitFormatProcess = submitFormatProcess;
        this.formatProcessorDAO = formatProcessorDAO;
        this.errMsg = new StringBuffer();
        this.formatUtil = formatUtil;
        this.notificationSender=notificationSender;
        this.jobPicker=jobPicker;

    }

    @Override
    public void executeFormatChange(JobDefinition jobDefinition, FormatChange formatChange, boolean cascade,String clusterId,String workerId) {


        List<String> hiveStatements = hiveFormatAlterTable.getFormatHiveStatements(formatChange, jobDefinition, cascade);
        this.clusterId=clusterId;
        this.workerId=workerId;

        log.info("Regular Format hiveStatements size:[]", hiveStatements.size());

        if (hiveStatements.size() > 0) {
            runProcess(hiveStatements, jobDefinition);
        } else {
            log.info("No Regular format changes detected {} {}", jobDefinition.getObjectDefinition().getNameSpace(), jobDefinition.getObjectDefinition().getDbName());
        }

        if(errMsg!=null && errMsg.capacity()>0){
            notificationSender.sendFailureEmail(
                    jobDefinition,1,"Unbale to do Regular format change",""
            );
        }

    }

    @Override
    public boolean hasFormatCompleted() {

        return this.isComplete;
    }

    @Override
    public String getErr() {
        return this.errMsg.toString();
    }

    void runProcess(List<String> hiveStatements, JobDefinition jobDefinition) {

        String objectName = jobDefinition.getObjectDefinition().getObjectName();
        String dbName = jobDefinition.getObjectDefinition().getNameSpace();
        try {
            String tmpdir = Files.createTempDirectory("format").toFile().getAbsolutePath();

            log.info("The tmp dir for format is ==> {}", tmpdir);

            CompletableFuture<Process> formatProcess = submitFormatProcess.submitProcess(submitFormatProcess.createHqlFile(hiveStatements, tmpdir));

            while(!formatProcess.isDone()){

                try {
                    formatProcess.get(1,TimeUnit.MINUTES);

                }catch ( TimeoutException te) {
                    log.info( "Extending lock for object ==>" + jobDefinition.getObjectDefinition() );
                    jobPicker.extendLock(jobDefinition, this.clusterId, this.workerId);
                }
            }

            CompletableFuture<String> processOutput = formatUtil.printProcessOutput(formatProcess);

            formatUtil.handleProcess(formatProcess);
            formatProcess.whenComplete((proc, err) -> {

                this.isComplete = formatUtil.processComplete(objectName, dbName, err);
            });


            errMsg.append(formatUtil.getErr());


        } catch (Exception e) {

            log.error("Exception in regular format change {}", e.getMessage());
            errMsg.append("Exception in regular format change");
            errMsg.append(e.getMessage());
            notificationSender.sendEmail("Unbale to do Regular format change for "+dbName + objectName + " ==>" +this.getErr(),dbName+"."+objectName+"  format failed");



        }

    }


}
