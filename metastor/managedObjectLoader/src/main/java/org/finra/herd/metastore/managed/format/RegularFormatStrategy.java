package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.hive.HiveFormatAlterTable;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatProcessorDAO;
import org.finra.herd.metastore.managed.jobProcessor.dao.FormatStatus;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/*

 If the number of partitions in metastore are less than 50k use this Strategy
 */
@Service
@Slf4j
@Qualifier("regularFormat")
@ToString
public class RegularFormatStrategy implements FormatStrategy {

    @Getter
    private boolean isComplete;
    private HiveFormatAlterTable hiveFormatAlterTable;
    private SubmitFormatProcess submitFormatProcess;
    private FormatProcessorDAO formatProcessorDAO;
    private StringBuffer errMsg;
    private FormatUtil formatUtil;

    @Autowired
    public RegularFormatStrategy(HiveFormatAlterTable hiveFormatAlterTable, SubmitFormatProcess submitFormatProcess,
                                 FormatProcessorDAO formatProcessorDAO, FormatUtil formatUtil) {

        this.hiveFormatAlterTable = hiveFormatAlterTable;
        this.submitFormatProcess = submitFormatProcess;
        this.formatProcessorDAO = formatProcessorDAO;
        this.errMsg = new StringBuffer();
        this.formatUtil = formatUtil;

    }

    @Override
    public void executeFormatChange(JobDefinition jobDefinition, FormatChange formatChange, boolean cascade) {


        List<String> hiveStatements = hiveFormatAlterTable.getFormatHiveStatements(formatChange, jobDefinition, cascade);

        log.info("Regular Format hiveStatements size:[]", hiveStatements.size());

        if (hiveStatements.size() > 0) {
            runProcess(hiveStatements, jobDefinition.getObjectDefinition().getObjectName(), jobDefinition.getObjectDefinition().getNameSpace());
        } else {
            log.info("No Regular format changes detected {} {}", jobDefinition.getObjectDefinition().getNameSpace(), jobDefinition.getObjectDefinition().getDbName());
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

    void runProcess(List<String> hiveStatements, String objectName, String dbName) {

        try {
            String tmpdir = Files.createTempDirectory("format").toFile().getAbsolutePath();

            log.info("The tmp dir for format is ==> {}", tmpdir);
            log.info("Thread Name in runProcess: {}", Thread.currentThread().getName());

            CompletableFuture<Process> formatProcess = submitFormatProcess.submitProcess(submitFormatProcess.createHqlFile(hiveStatements, tmpdir));

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


        }

    }


}
