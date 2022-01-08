package org.finra.herd.metastore.managed.format;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdlRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static org.finra.herd.metastore.managed.conf.HerdMetastoreConfig.ALTER_TABLE_MAX_PARTITIONS;


@Service
@Slf4j
@Qualifier("renameFormat")
public class RenameFormatStrategy implements FormatStrategy {


    private boolean isComplete;

    @Override
    public void executeFormatChange(JobDefinition jobDefinition, FormatChange formatChange, boolean cascade){


    }

    @Override
    public boolean hasFormatCompleted(){

        return this.isComplete;
    }




}

