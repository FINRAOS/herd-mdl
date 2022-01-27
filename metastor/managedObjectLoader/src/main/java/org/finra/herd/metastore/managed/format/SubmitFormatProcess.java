package org.finra.herd.metastore.managed.format;

import lombok.extern.slf4j.Slf4j;
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


    @Async("formatExecutor")
    public CompletableFuture<Process> submitProcess(File files) {


        Supplier<Process> processSupplier = () -> {

            Process process = null;
            try {
                synchronized (this) {
                    log.info("File submitProcess {} in thread {}", files.getAbsolutePath(), Thread.currentThread().getName());
                    StopWatch watch = new StopWatch();
                    watch.start();
                ProcessBuilder pb = new ProcessBuilder("hive", "-v", "-f", files.getAbsolutePath());
                pb.redirectErrorStream(true);
                process = pb.start();
                 printProcessOutput(process); //Enable when you need to debug.

                    process.waitFor(JobProcessorConstants.MAX_JOB_WAIT_TIME, TimeUnit.SECONDS);
                    watch.stop();
                    log.info("format Process ran for {} in thread for:{}", watch.getTime(TimeUnit.SECONDS), Thread.currentThread().getName());
                }

            } catch (InterruptedException iex) {
                if (process != null && process.isAlive()) {
                    log.error("Process interrupted or timeout going to kill it");
                    process.destroyForcibly();
                }
                throw new RuntimeException("Unable to execute  hive process Regular Format ==>" + files.getAbsolutePath());

            } catch (Exception ie) {
                log.error("Exceptiopn in submitProcess for Regular format {}", ie.getMessage());
                throw new RuntimeException("Unable to execute  hive process for Regular format ==>" + files.getAbsolutePath());
            }
            return process;
        };


        CompletableFuture<Process> processCompletableFuture = CompletableFuture.supplyAsync(processSupplier);
        return processCompletableFuture;

    }

    @Async("formatExecutor")
    public CompletableFuture<FormatProcessObject> submitProcess(File files, FormatProcessObject formatProcessObject) {


        Supplier<FormatProcessObject> processSupplier = () -> {

            Process process = null;
            try {

                synchronized (this) {
                    log.info("File submitProcess {} in thread {}", files.getAbsolutePath(), Thread.currentThread().getName());
                    StopWatch watch = new StopWatch();
                    watch.start();

                ProcessBuilder pb = new ProcessBuilder("hive", "-v", "-f", files.getAbsolutePath());
                log.info("pb.command is ==>{}", pb.command());
                pb.redirectErrorStream(true);
                process = pb.start();

                printProcessOutput(process); //Enable when you need to debug.

                    process.waitFor(JobProcessorConstants.MAX_JOB_WAIT_TIME, TimeUnit.SECONDS);
                    watch.stop();
                    formatProcessObject.setProcess(process);

                    log.info("format Process for table :{} and these partitions :{} ran for:{} in the file: {} with exit Value:{} in Thread :{}",
                            formatProcessObject.getJobDefinition().getTableName(), formatProcessObject.getPartitionList(), watch.getTime(TimeUnit.SECONDS), files
                                    .getAbsolutePath(), process.exitValue(), Thread.currentThread().getName());

                }
            } catch (InterruptedException iex) {
                if (process != null && process.isAlive()) {
                    log.error("Process interrupted or timeout going to kill it");
                    process.destroyForcibly();
                }
                throw new RuntimeException("Unable to execute  hive process Rename Format ==>" + files.getAbsolutePath());


            } catch (Exception ie) {
                log.error("Exceptiopn in submitProcess {}", ie.getMessage());
                throw new RuntimeException("Unable to execute  hive process Rename Format ==>" + files.getAbsolutePath());
            }
            return formatProcessObject;
        };


        CompletableFuture<FormatProcessObject> processCompletableFuture = CompletableFuture.supplyAsync(processSupplier);
        return processCompletableFuture;

    }

    private synchronized void printProcessOutput(Process process) {
        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));

        try {
                String line = in.readLine();
                while (line != null) {
                log.info("====>{}", line);
                    line = in.readLine();
                }
                in.close();
        } catch (Exception ex) {
            log.error("ERROR {}", ex.getMessage());
        } finally {
            try {
                    in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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


}
