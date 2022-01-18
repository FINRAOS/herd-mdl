package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FormatUtil {

    @Getter
    private StringBuilder err;

    @Autowired
    public FormatUtil() {
        this.err = new StringBuilder();
    }


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

    public List<CompletableFuture<String>> printProcessOutput(List<CompletableFuture<FormatProcessObject>> formatProcessObjectList) {

        List<CompletableFuture<String>> processOutput = formatProcessObjectList.stream().map(proc ->
            proc.thenApplyAsync(

                p -> {
                    String s = null;

                    try {
                        s = CharStreams.toString(new InputStreamReader(
                            p.getProcess().getInputStream(), Charsets.UTF_8));
                        log.info("Thread in collect" + Thread.currentThread().getName());

                    } catch (Exception ie) {
                        log.info("processouput" + ie.getMessage());
                    }
                    return s;
                }

            )).collect(Collectors.toList());


        processOutput.forEach(c -> c.thenAccept(s -> log.info(s)));


        return processOutput;


    }

    protected void handleProcess(CompletableFuture<Process> formatProcess, CompletableFuture<String> processOutput) {
        formatProcess.thenAccept(proc -> {
            try {
                if (!proc.waitFor(JobProcessorConstants.MAX_JOB_WAIT_TIME, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                    processOutput.completeExceptionally(new Exception("Process Timed Out"));

                }
            } catch (InterruptedException ie) {
                log.error("Unable to kill the process after timeout {}", ie.getMessage());
                this.err.append("Unable to kill the process after timeout");
                this.err.append(ie.getMessage());
            }

        });
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
            log.info("Thread Name in handleAsync: {}", Thread.currentThread().getName());

        }
        return isComplete;
    }

}
