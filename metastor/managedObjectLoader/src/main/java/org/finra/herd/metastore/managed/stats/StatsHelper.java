package org.finra.herd.metastore.managed.stats;

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.ObjectProcessor;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.jobProcessor.dao.DMNotification;
import org.finra.herd.metastore.managed.jobProcessor.dao.JobProcessorDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class StatsHelper {

    public static final String SUBMITTED_BY_JOB_PROCESSOR = "SUBMITTED_BY_JOB_PROCESSOR";

    @Autowired
    protected DataMgmtSvc dataMgmtSvc;

    @Autowired
    JobProcessorDAO jobProcessorDAO;


    public void addAnalyzeStats(JobDefinition jd, List<String> partitions) {

        log.info("Adding gather Stats job");
        try {

            if (partitions.size() == 1) {
                submitStatsJob(jd, jd.partitionValuesForStats(partitions.get(0)));
            } else {
                //Filter not available Partitions
                dataMgmtSvc.filterPartitionsAsPerAvailability(jd, partitions);

                partitions.stream()
                    .forEach(s -> submitStatsJob(jd, s));
            }

            // Start Stats cluster is not running
            dataMgmtSvc.createCluster(true, JobProcessorConstants.METASTOR_STATS_CLUSTER_NAME);
        } catch (Exception e) {
            log.error("Problem encountered in addAnalyzeStats: {}", e.getMessage(), e);
        }
    }

    private void submitStatsJob(JobDefinition jd, String partitionValue) {
        DMNotification dmNotification = buildDMNotification(jd);

        dmNotification.setWorkflowType(ObjectProcessor.WF_TYPE_MANAGED_STATS);
        dmNotification.setExecutionId(SUBMITTED_BY_JOB_PROCESSOR);

        dmNotification.setPartitionKey(jd.partitionKeysForStats());
        dmNotification.setPartitionValue(partitionValue);

        log.info("Herd Stats Notification DB request: \n{}", dmNotification);
        jobProcessorDAO.addDMNotification(dmNotification);
    }


    protected DMNotification buildDMNotification(JobDefinition jd) {
        return DMNotification.builder()
            .namespace(jd.getObjectDefinition().getNameSpace())
            .objDefName(jd.getObjectDefinition().getObjectName())
            .formatUsage(jd.getObjectDefinition().getUsageCode())
            .fileType(jd.getObjectDefinition().getFileType())
            .clusterName(jd.getClusterName())
            .correlationData(jd.getCorrelation())
            .build();
    }


}
