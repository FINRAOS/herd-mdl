package org.finra.herd.metastore.managed.jobProcessor;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.format.DetectSchemaChanges;
import org.finra.herd.metastore.managed.format.FormatChange;
import org.finra.herd.metastore.managed.hive.HiveFormatAlterTable;
import org.finra.herd.metastore.managed.hive.HiveTableSchema;
import org.finra.herd.metastore.managed.jobProcessor.dao.PartitionsDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class FormatObjectProcessor extends JobProcessor {

    @Autowired
    private DetectSchemaChanges detectSchemaChanges;

    private FormatChange change;

    @Autowired
    PartitionsDAO partitionsDAO;

    @Autowired
    HiveFormatAlterTable hiveFormatAlterTable;

    @Autowired
    HiveHqlGenerator hiveHqlGenerator;


    @Override
    protected ProcessBuilder createProcessBuilder(JobDefinition od) {
        return null;
    }


    private void formatStrategy(JobDefinition jd){

        if(partitionsDAO.getTotalPartitionCount(jd.getObjectDefinition().getObjectName(),jd.getObjectDefinition().getDbName()) < JobProcessorConstants.MAX_PARTITION_FORMAT_LIMIT)
        {
            List<String> list = Lists.newArrayList();

            hiveFormatAlterTable.executeFormatChange(change,jd,list,jd.getTableName(),hiveHqlGenerator.cascade(jd));

        } else

            {
                        log.info("Total Partition Count :{}",partitionsDAO.getTotalPartitionCount(jd.getObjectDefinition().getObjectName(),jd.getObjectDefinition().getDbName()));
                        log.info("Name:{},DB:{}",jd.getObjectDefinition().getObjectName(),jd.getObjectDefinition().getDbName());

            }


    }



}
