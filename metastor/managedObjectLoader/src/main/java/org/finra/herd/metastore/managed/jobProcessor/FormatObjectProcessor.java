package org.finra.herd.metastore.managed.jobProcessor;

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.format.DetectSchemaChanges;
import org.finra.herd.metastore.managed.format.FormatChange;
import org.finra.herd.metastore.managed.format.FormatStrategy;
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

            FormatStrategy formatStrategy= doFormat(od);
            Optional <String> errMsg = Optional.of(formatStrategy.getErr());
            errMsg.ifPresent(err-> errorBuffer.append(err));
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

        if (partitionsDAO.getTotalPartitionCount(jd.getObjectDefinition().getObjectName(), jd.getObjectDefinition().getDbName()) < JobProcessorConstants.MAX_PARTITION_FORMAT_LIMIT)
        {
            regularFormatStrategy.executeFormatChange(jd, change, hiveHqlGenerator.cascade(jd));
            return regularFormatStrategy;

        } else {

            log.info("Total Partition Count :{}", partitionsDAO.getTotalPartitionCount(jd.getObjectDefinition().getObjectName(), jd.getObjectDefinition().getDbName()));
            log.info("Name:{},DB:{}", jd.getObjectDefinition().getObjectName(), jd.getObjectDefinition().getDbName());
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
