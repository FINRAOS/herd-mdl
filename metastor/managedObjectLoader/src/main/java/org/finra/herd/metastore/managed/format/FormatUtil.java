package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.ExecuteResultHandler;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.JobPicker;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
@Scope("prototype")
public class FormatUtil {

    @Getter
    private StringBuilder err;

    @Autowired
    public FormatUtil() {
        this.err = new StringBuilder();
    }

    @Autowired
    private JobPicker jobPicker;


    public CompletableFuture<String> printProcessOutput(CompletableFuture<Process> formatProcess) {

        CompletableFuture<String> processOutput = formatProcess.thenApply(process -> {
            String res = null;
            try {
                res = CharStreams.toString(new InputStreamReader(process.getInputStream(), Charsets.UTF_8));
            } catch (Exception e) {
                log.error(
                    "not able to parse inputstream {}", e.getMessage()
                );
                log.error("not able to parse inputstream");
                log.error(e.getMessage());
                err.append("not able to parse inputstream");
                err.append(e.getMessage());
            }
            return res;
        });

        processOutput.thenAccept(s -> log.info("Hive execution output is ==>{}", s));

        return processOutput;
    }


    protected void handleProcess(CompletableFuture<Process> formatProcess) {
        formatProcess.thenAccept(proc -> {
            try {
                if (!proc.waitFor(JobProcessorConstants.MAX_JOB_WAIT_TIME, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                    formatProcess.completeExceptionally(new Exception("Process did not terminate, killing it"));
                }
            } catch (InterruptedException ie) {
                log.error("Unable to kill the process after timeout {}", ie.getMessage());
                this.err.append("Unable to kill the process after timeout");
                this.err.append(ie.getMessage());
            }

        });
    }


    protected boolean processComplete(String existingTableName, String dbName, Throwable err) {
        boolean isComplete = true;

        if (err != null) {
            log.error("Unable to finish processing of format for Object {} , Namespace {}", existingTableName, dbName);
            this.err.append("Unable to finish processing of format for Object ==>");
            this.err.append(existingTableName);
            this.err.append(dbName);
            this.err.append(err.getMessage());
            isComplete = false;
        } else {
            log.info("processing Complete for Object {} , Namespace {}", existingTableName, dbName);
        }
        return isComplete;
    }


    public String getAlterTableStatemts(  Optional<String> ddl) {

        String [] ddlArr=null;
        if(ddl.isPresent())
        {
             ddlArr =ddl.get().split(";");
            return  ddlArr[1]+";";
        }
        else{
            log.info("DDL is empty ==> No cookie for you!");
        }

        return null;
    }

    protected void checkFutureComplete(JobDefinition jobDefinition, CompletableFuture<DefaultExecuteResultHandler> formatProcess, String clusterId, String workerId
    ) throws InterruptedException, java.util.concurrent.ExecutionException {
        while(!formatProcess.get().hasResult()){

            try {
                formatProcess.get(1, TimeUnit.MINUTES);

            }catch ( TimeoutException te) {
                log.info( "Extending lock for object ==>" + jobDefinition.getObjectDefinition() );
                jobPicker.extendLock(jobDefinition, clusterId, workerId);
            }
        }
    }
}
