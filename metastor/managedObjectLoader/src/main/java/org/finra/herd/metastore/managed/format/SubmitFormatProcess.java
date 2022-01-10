package org.finra.herd.metastore.managed.format;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

                log.info("Thread in submitProcess {}",Thread.currentThread().getName());

                ProcessBuilder pb = new ProcessBuilder("/bin/sh", files.getAbsolutePath());
                pb.redirectErrorStream(true);
                process = pb.start();

            } catch (IOException ie) {
                log.error("Exceptiopn in submitProcess {}" , ie.getMessage());
                throw new RuntimeException("Unable to execute  hive process ==>"+files.getAbsolutePath());
            }
            return process;
        };


        CompletableFuture<Process> processCompletableFuture = CompletableFuture.supplyAsync(processSupplier);
        return processCompletableFuture;

    }

    public File createHqlFile(List<String> stringList, String tmpdir){

        File hqlFilePath =null;
        try{
            log.info("Thread in writetoFile {}" ,Thread.currentThread().getName());

            hqlFilePath = File.createTempFile("str", ".sh", new File(tmpdir));
            Path path = Paths.get(hqlFilePath.getAbsolutePath());

            String str=stringList.stream().collect(Collectors.joining(" "));
            Files.write(path, str.getBytes(), APPEND);

        } catch (Exception ioe) {
            log.error("Exceptiopn in creatingHql file {}" , ioe.getMessage());
            throw new RuntimeException("Unable to create  hive file ==>"+hqlFilePath.getAbsolutePath());        }

        return hqlFilePath;

    }
}
