package org.finra.herd.metastore.managed.format;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.hive.HiveFormatAlterTable;
import org.finra.herd.metastore.managed.util.ExecuteHive;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

@Service
@Slf4j
@Qualifier("regularFormat")
public class RegularFormatStrategy implements FormatStrategy {

    @Setter
    private boolean isComplete;

    @Autowired
    HiveFormatAlterTable hiveFormatAlterTable;

    @Autowired
    ExecuteHive executeHive;

    @Override
    public void executeFormatChange(JobDefinition jobDefinition,FormatChange formatChange,boolean cascade){

        List<String> hiveStatements = new ArrayList<>();

        hiveFormatAlterTable.executeFormatChange(formatChange, jobDefinition,hiveStatements, cascade);

        runProcess(hiveStatements);




    }

    @Override
    public boolean hasFormatCompleted(){

        return this.isComplete;
    }


    void runProcess(List<String> hiveStatements) throws RuntimeException {
        ProcessBuilder pb = createProcessBuilder(hiveStatements,"/tmp/regular-format-");
        if (Objects.nonNull(pb)) {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                log.info("Start Task " + pb.command());
                Process process = pb.start();
                Callable task = () -> new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines()
                    .collect(Collectors.toList());

                Future<List<String>> future = pool.submit(task);
                if (!future.isDone()) {
                    log.info("Waiting for task to be done");
                    future.get(JobProcessorConstants.HIVE_TIMEOUT, TimeUnit.SECONDS);
                }

                log.info("Task completed" + pb.command());
                this.setComplete(true);

            } catch (Exception e) {
                log.info("Exception in Regular Format Strategy runProcess" + e.getMessage());
                throw new RuntimeException("Regular Format Changes Failed");
            } finally {
                pool.shutdown();
            }
        }
    }


    public ProcessBuilder createProcessBuilder(List<String> schemaHql,String filePrefix) {
            try {
                //"/tmp/regular-format-"
                byte[] setHiveTimeout = (JobProcessorConstants.SET_HIVE_CLIENT_TIMEOUT + JobProcessorConstants.HIVE_TIMEOUT + ";").getBytes();
                File hqlFilePath = File.createTempFile(filePrefix, ".hql", new File("/tmp"));
                Path path = Paths.get(hqlFilePath.getAbsolutePath());
                Files.write(path, setHiveTimeout, CREATE, APPEND);
                Files.write(path, schemaHql, APPEND);
                log.info("Schema HQL: {} \t regular format HQL fileName :{}", schemaHql, hqlFilePath.getAbsolutePath());
                ProcessBuilder pb = new ProcessBuilder("hive", "-v", "-f", hqlFilePath.getAbsolutePath());
                return pb;
            } catch (IOException ie) {
                log.info("Problem encountered while regular format update with message: {}", ie.getMessage(), ie);
            }


        return null;
    }


}
