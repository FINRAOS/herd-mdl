package org.finra.herd.metastore.managed.format;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.time.StopWatch;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;

@Slf4j
@Service
public class SubmitFormatProcess {


    public DefaultExecuteResultHandler submitProcess(CommandLine cmdline) {

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        try {
            DefaultExecutor executor = new DefaultExecutor();
            executor.setExitValue(0);
            long timeout = JobProcessorConstants.MAX_JOB_WAIT_TIME * 2;
            ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
            executor.setWatchdog(watchdog);
            log.info("File submitProcess {} in thread {}", cmdline.toString(), Thread.currentThread().getName());
            executor.setWorkingDirectory(new File("/tmp"));
            executor.execute(cmdline, resultHandler);

        } catch (Exception ie) {
            log.error("Exceptiopn in submitProcess for Regular format {}", ie.getMessage());
            throw new RuntimeException("Unable to execute  hive process for Regular format ==>" + ie.getMessage());
        }
        return resultHandler;


    }

    @Async("formatExecutor")
    public CompletableFuture<FormatProcessObject> submitProcess(CommandLine cmdline, FormatProcessObject formatProcessObject) {


        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(JobProcessorConstants.MAX_JOB_WAIT_TIME);
        executor.setWatchdog(watchdog);
        Supplier<FormatProcessObject> processSupplier = () -> {


            try {

                log.info("File submitProcess {} in thread {}", cmdline.toString(), Thread.currentThread().getName());
                executor.setWorkingDirectory(new File("/tmp"));
                int exitvalue = executor.execute(cmdline);
                formatProcessObject.setExitValue(exitvalue);
                formatProcessObject.setDefaultExecutor(executor);


            } catch (IOException ie
            ) {
                log.error("IO Exception", ie.getMessage());
                throw new RuntimeException("Unable to execute  hive process for Rename format ==>" + ie.getMessage());

            }

            return formatProcessObject;
        };


        CompletableFuture<FormatProcessObject> processCompletableFuture = CompletableFuture.supplyAsync(processSupplier);
        return processCompletableFuture;

    }


    public File createHqlFile(List<String> stringList) {

        File hqlFilePath = null;
        try {
            log.info("Thread in writetoFile {}", Thread.currentThread().getName());


            hqlFilePath = File.createTempFile("str-format", ".hql", new File("/home/hadoop"));
            Path path = Paths.get(hqlFilePath.getAbsolutePath());

            String str = stringList.stream().collect(Collectors.joining(" "));
            Files.write(path, str.getBytes(), APPEND);

        } catch (Exception ioe) {
            log.error("Exceptiopn in creatingHql file {}", ioe.getMessage());
            throw new RuntimeException("Unable to create  hive file ==>" + hqlFilePath.getAbsolutePath());
        }

        return hqlFilePath;

    }


    public CommandLine getCommandLine(File file) {


        String line = "hive -v -f " + file.getAbsolutePath();
        return CommandLine.parse(line);

    }


}
