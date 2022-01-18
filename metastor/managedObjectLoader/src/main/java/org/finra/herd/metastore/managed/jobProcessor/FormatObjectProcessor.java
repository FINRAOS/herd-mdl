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
import org.springframework.beans.factory.annotation.Qualifier;
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
    @Qualifier("regularFormat")
    FormatStrategy regularFormatStrategy;

    @Autowired
    @Qualifier("renameFormat")
    FormatStrategy renameFormatStrategy;

    final int concurrency = Runtime.getRuntime().availableProcessors();


    @Override
    public boolean process(JobDefinition od, String clusterID, String workerID) {

        try {

            FormatStrategy formatStrategy= doFormat(od);
            log.info("Format strategy completed? :{}",formatStrategy.hasFormatCompleted());
            Optional <String> errMsg = Optional.ofNullable(formatStrategy.getErr());
            errMsg.ifPresent(err-> errorBuffer.append(err));
            return formatStrategy.hasFormatCompleted();
        } catch (Exception ex) {
            logger.severe(ex.getMessage());
            errorBuffer.append(ex.getMessage());
            return false;
        }

    }

    /*
       If the number of partitions for a object are less than 50k use alter table ..
       else
       create a new object and backload the partitons and swap.
     */

    private FormatStrategy doFormat(JobDefinition jd) throws ApiException, SQLException {

        FormatChange change = detectSchemaChanges.getFormatChange(jd);


        if (partitionsDAO.
                getTotalPartitionCount(jd.getTableName(), jd.getObjectDefinition().getDbName()) < JobProcessorConstants.MAX_PARTITION_FORMAT_LIMIT || !hiveHqlGenerator.
                cascade(jd))
        {
            regularFormatStrategy.executeFormatChange(jd, change, hiveHqlGenerator.cascade(jd));
            return regularFormatStrategy;

        } else {

            /*
              jd object contains what ever partition key is set as part of add partition work flow.
              We are interested only in root Level partition Key for making DM call
              We do not want to include sub partition details in the DM Call. Hence reset partition key
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
