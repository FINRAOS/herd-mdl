package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.hive.HiveFormatAlterTable;
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

    @Override
    public void executeFormatChange(JobDefinition jobDefinition,FormatChange formatChange,boolean cascade){

        List<String> hiveStatements = new ArrayList<>();

        hiveFormatAlterTable.executeFormatChange(formatChange, jobDefinition,hiveStatements, cascade);

        runProcess(hiveStatements,jobDefinition.getObjectDefinition().getObjectName(),jobDefinition.getObjectDefinition().getNameSpace());


    }

    @Override
    public boolean hasFormatCompleted(){

        return this.isComplete;
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
                }
                return res;
            });


            processOutput.thenAcceptAsync(s -> log.info("Hive execution output is ==>{}", s));

            formatProcess.thenAcceptAsync(proc -> {
                try{
                    if(!proc.waitFor(JobProcessorConstants.MAX_JOB_WAIT_TIME,TimeUnit.SECONDS))
                    {
                        proc.destroyForcibly();
                        processOutput.completeExceptionally(new Exception("Process TimedOut"));

                    }
                }catch(InterruptedException ie){
                    log.error("Unable to kill the process");
                }

            });

            processOutput.handleAsync((proc, err) -> {

                if (err != null) {
                    log.error("Unable to finish processing of format for Object {} , Namespace {}", objectName, dbName);
                    throw new RuntimeException("Unable to finish processing of format");
                }else{
                    this.isComplete=true;
                }
                return null;
            });


        }catch(Exception e){

        }
    }


}
