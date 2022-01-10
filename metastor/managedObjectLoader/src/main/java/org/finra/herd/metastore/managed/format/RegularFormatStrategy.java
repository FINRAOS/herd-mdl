package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.Setter;
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

@Service
@Slf4j
@Qualifier("regularFormat")
public class RegularFormatStrategy implements FormatStrategy {

    @Setter
    private boolean isComplete;

    @Autowired
    HiveFormatAlterTable hiveFormatAlterTable;


    @Autowired
    SubmitFormatProcess submitFormatProcess;

    @Autowired
    FormatProcessorDAO formatProcessorDAO;


    private StringBuffer errMsg;

    @Override
    public void executeFormatChange(JobDefinition jobDefinition,FormatChange formatChange,boolean cascade){


        List<String> hiveStatements = hiveFormatAlterTable.getFormatHiveStatements(formatChange, jobDefinition, cascade);


        if(hiveStatements.size() > 0) {
            runProcess(hiveStatements, jobDefinition.getObjectDefinition().getObjectName(), jobDefinition.getObjectDefinition().getNameSpace());
        }
        else {
            log.info("No Regular format changes detected {} {}", jobDefinition.getObjectDefinition().getNameSpace(), jobDefinition.getObjectDefinition().getDbName());
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

    void runProcess(List<String> hiveStatements,String objectName,String dbName) {

        try {
            String tmpdir = Files.createTempDirectory("format").toFile().getAbsolutePath();

            log.info("The tmp dir for format is ==> {}", tmpdir);

            CompletableFuture<Process> formatProcess = submitFormatProcess.submitProcess(submitFormatProcess.createHqlFile(hiveStatements, tmpdir));

            CompletableFuture<String> processOutput = formatProcess.thenApplyAsync(process -> {
                String res = null;
                try {
                    res = CharStreams.toString(new InputStreamReader(process.getInputStream(), Charsets.UTF_8));
                } catch (Exception e) {
                    log.error(
                            "not able to parse inputstream {}",e.getMessage()
                    );
                    errMsg.append("not able to parse inputstream");
                    errMsg.append(e.getMessage());
                }
                return res;
            });


            processOutput.thenAcceptAsync(s -> log.info("Hive execution output is ==>{}", s));

            formatProcess.thenAcceptAsync(proc -> {
                try{
                    if(!proc.waitFor(JobProcessorConstants.MAX_JOB_WAIT_TIME,TimeUnit.SECONDS))
                    {
                        proc.destroyForcibly();
                        processOutput.completeExceptionally(new Exception("Process Timed Out"));

                    }
                }catch(InterruptedException ie){
                    log.error("Unable to kill the process after timeout {}",ie.getMessage());
                    errMsg.append("Unable to kill the process after timeout");
                    errMsg.append(ie.getMessage());
                }

            });

            processOutput.handleAsync((proc, err) -> {

                if (err != null) {
                    log.error("Unable to finish processing of format for Object {} , Namespace {}", objectName, dbName);
                    errMsg.append("Unable to finish processing of format for Object ==>");
                    errMsg.append(objectName);
                    errMsg.append(dbName);

                }else{
                    this.isComplete=true;
                    log.info("Format change processing Complete for Object {} , Namespace {}", objectName, dbName);

                }
                return null;
            });

            formatProcess.join();


        }catch(Exception e){

            log.error("Exception in regular format change {}",e.getMessage());
            errMsg.append("Exception in regular format change");
            errMsg.append(e.getMessage());


        }

    }


}
