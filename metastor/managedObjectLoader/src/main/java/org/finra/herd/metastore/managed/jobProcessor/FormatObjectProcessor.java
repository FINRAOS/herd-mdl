package org.finra.herd.metastore.managed.jobProcessor;

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.format.*;
import org.finra.herd.metastore.managed.hive.HiveFormatAlterTable;
import org.finra.herd.metastore.managed.jobProcessor.dao.PartitionsDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class FormatObjectProcessor extends JobProcessor {

    @Autowired
    private DetectSchemaChanges detectSchemaChanges;


    @Autowired
    PartitionsDAO partitionsDAO;

    @Autowired
    HiveHqlGenerator hiveHqlGenerator;

    @Autowired
    FormatStrategy regularFormatStrategy;

    @Autowired
    FormatStrategy renameFormatStrategy;

    final int concurrency = Runtime.getRuntime().availableProcessors();


    @Override
    public boolean process(JobDefinition od, String clusterID, String workerID) {

        try {

            log.info("Thread Name in process: {}",Thread.currentThread().getName());
            FormatStrategy formatStrategy= doFormat(od);
            log.info("Format Strategy:{}",formatStrategy);
            log.info("Format strategy completed? :{}",formatStrategy.hasFormatCompleted());

            Optional <String> errMsg = Optional.ofNullable(formatStrategy.getErr());
            log.info("err msg {}",errMsg);
            errMsg.ifPresent(err-> errorBuffer.append(err));
            log.info("Thread Name second in process: {}",Thread.currentThread().getName());

            // We will update Format Status here.
            return formatStrategy.hasFormatCompleted();
        } catch (Exception ex) {
            logger.severe(ex.getMessage());
            errorBuffer.append(ex.getMessage());
            // Seeing Error here
            return false;
        }

    }


    private FormatStrategy doFormat(JobDefinition jd) throws ApiException, SQLException {

        FormatChange change = detectSchemaChanges.getFormatChange(jd);


        if (partitionsDAO.getTotalPartitionCount(jd.getTableName(), jd.getObjectDefinition().getDbName()) < JobProcessorConstants.MAX_PARTITION_FORMAT_LIMIT)
        {
            regularFormatStrategy.executeFormatChange(jd, change, hiveHqlGenerator.cascade(jd));
            return regularFormatStrategy;

        } else {

            /*
              We are interested only in root Level partition Key for making DM call
              We do not want to include sub partition details in the DM Call.
             */
            super.setPartitionKeyRegardless(jd);
            jd.setSubPartitionLevelProcessing(false);

            renameFormatStrategy.executeFormatChange(jd, change, hiveHqlGenerator.cascade(jd));
            return renameFormatStrategy;
        }

    }


    @Override
    protected ProcessBuilder createProcessBuilder(JobDefinition od) {

        //Will never be called.
        return null;
    }


}
