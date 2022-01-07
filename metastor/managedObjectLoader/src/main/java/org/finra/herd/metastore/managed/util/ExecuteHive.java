package org.finra.herd.metastore.managed.util;

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.jobProcessor.JobProcessor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;


@Component
@Slf4j
public class ExecuteHive {


    /**
     * Method to create the HQL file for adding partitions
     *
     * @param jd
     * @return
     * @throws IOException
     */
    public Path createHqlFile(JobDefinition jd) throws IOException {
        Path hqlFilePath = Paths.get(hqlFileName(jd));
        Files.deleteIfExists(hqlFilePath);
        Files.createFile(hqlFilePath);
        return hqlFilePath;
    }

    private String hqlFileName(JobDefinition jd) {
        return new StringJoiner("_", "/tmp/", ".hql")
            .add(String.valueOf(jd.getWfType()))
            .add(String.valueOf(jd.getExecutionID()))
            .add(String.valueOf(jd.getNumOfRetry()))
            .toString();
    }


    public void createHqlFile(String hqlStmt,String tmpdir) {

        try {


            File hqlFilePath = File.createTempFile("str", ".hql", new File(tmpdir));
            Path path = Paths.get(hqlFilePath.getAbsolutePath());
            Files.write(path, hqlStmt.getBytes(), APPEND);


        } catch (Exception ioe) {
        }
    }







    }
