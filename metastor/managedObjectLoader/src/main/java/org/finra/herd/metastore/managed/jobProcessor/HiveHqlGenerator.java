/*
 * Copyright 2018 herd-mdl contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package org.finra.herd.metastore.managed.jobProcessor;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.ObjectProcessor;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.format.DetectSchemaChanges;
import org.finra.herd.metastore.managed.format.FormatChange;
import org.finra.herd.metastore.managed.hive.*;
import org.finra.herd.metastore.managed.jobProcessor.dao.JobProcessorDAO;
import org.finra.herd.metastore.managed.stats.StatsHelper;
import org.finra.herd.metastore.managed.util.MetastoreUtil;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdl;
import org.finra.herd.sdk.model.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.finra.herd.metastore.managed.jobProcessor.dao.DMNotification;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

@Component
@Slf4j
public class HiveHqlGenerator {

    public static final String SUBMITTED_BY_JOB_PROCESSOR = "SUBMITTED_BY_JOB_PROCESSOR";

    @Autowired
    protected DataMgmtSvc dataMgmtSvc;

    @Autowired
    protected HiveClient hiveClient;

    @Autowired
    protected NotificationSender notificationSender;

    @Autowired
    protected JobProcessorDAO jobProcessorDAO;

    @Autowired
    protected DetectSchemaChanges detectSchemaChanges;

    @Autowired
    protected StatsHelper statsHelper;

    protected FormatChange formatChange;


    public List<String> schemaSql(boolean schemaExists, JobDefinition jd) throws ApiException, SQLException {

        String tableName = jd.getTableName();

        List<String> schema = Lists.newArrayList();

        if (schemaExists) {
            this.formatChange = detectSchemaChanges.getFormatChange(jd);

            if (jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON && jd.getPartitionKey().equalsIgnoreCase("partition")) {
                schema.add(dataMgmtSvc.getTableSchema(jd, true));
            } else {
                try {
                    this.formatChange = detectSchemaChanges.getFormatChange(jd);
                    if (this.formatChange.hasChange()) {
                        List<DMNotification> formatNotification = jobProcessorDAO.getFormatNotification(jd);
                        log.info("formatNotification:{}", formatNotification);
                        if (formatNotification != null && formatNotification.size() <= 0) {
                            submitFormatJob(jd);
                        } else {
                            log.info("A format notification:  {} is being currently processed for this job definition:  {}", formatNotification, jd);
                        }

                    }
                } catch (Exception ex) {
                    log.warn("Error comparing formats", ex);
                    notificationSender.sendNotificationEmail(ex.getMessage(), "Error comparing formats", jd);
                    if (!jd.getObjectDefinition().getFileType().equalsIgnoreCase("ORC")) {
                        //Can only do replace column in this case
                        String sql = dataMgmtSvc.getTableSchema(jd, true);
                        schema.add(sql);
                    }
                }
            }

        } else {

            log.info("Table does not exist, create new " + jd.toString());
        }
        return schema;
    }


    public String buildHql(JobDefinition jd, List<String> partitions) throws IOException, ApiException, SQLException {

        boolean tableExists = hiveClient.tableExist(jd.getObjectDefinition().getDbName(), jd.getTableName());

        BusinessObjectDataDdl dataDdl = dataMgmtSvc.getBusinessObjectDataDdl(jd, partitions, true);

        //Check for Format changes
        List<String> schemaHql = schemaSql(tableExists, jd);
        if (!tableExists) {

            // Add DDL's from data DDL
            return getHqlFilePath(jd, partitions, tableExists, dataDdl, schemaHql);

        } else {

            //Singelton we do not create format object
            if (this.formatChange!=null) {
                log.info("Are there any Format Changes ==>{}", this.formatChange.hasChange());
                //Execute only when no format change
                if (!this.formatChange.hasChange()) {
                    // Add database Statements
                    return getHqlFilePath(jd, partitions, tableExists, dataDdl, schemaHql);

                }
            }

            return null;

        }


    }

    private String getHqlFilePath(JobDefinition jd, List<String> partitions, boolean tableExists, BusinessObjectDataDdl dataDdl, List<String> schemaHql) throws IOException {

        selectDatabase(jd, schemaHql);

        // Add DDL's from data DDL
        addPartitionChanges(tableExists, jd, dataDdl, schemaHql);

        //Stats
        statsHelper.addAnalyzeStats(jd, partitions);

        // Create file
        Path hqlFilePath = createHqlFile(jd);
        Files.write(hqlFilePath, schemaHql, CREATE, APPEND);

        return hqlFilePath.toString();
    }

    protected void selectDatabase(JobDefinition jd, List<String> schemaHql) {
        String dbName = jd.getObjectDefinition().getDbName();
        schemaHql.add(0, "use " + dbName + ";");
        schemaHql.add(0, "CREATE DATABASE IF NOT EXISTS " + dbName + ";");
    }

    protected void addPartitionChanges(boolean tableExists, JobDefinition jd, BusinessObjectDataDdl dataDdl, List<String> schemaHql) {
        if (tableExists && MetastoreUtil.isSingletonWF(jd.getWfType())) {
            // Handling partiton=none scenario
            if (jd.getPartitionKey().equalsIgnoreCase("partition")) {
                String ddl = dataDdl.getDdl();
                String location = ddl.substring(ddl.indexOf("LOCATION") + 8);
                schemaHql.add(String.format("alter table %s SET LOCATION %s", jd.getTableName(), location));
            } else {
                //Singleton, add drop statement when table exists
                schemaHql.add(String.format("alter table %s drop if exists partition (%s >'1970-01-01');", jd.getTableName(), jd.getPartitionKey()));
                schemaHql.add(dataDdl.getDdl());
            }
        } else {
            schemaHql.add(dataDdl.getDdl());
        }
    }


    private void submitFormatJob(JobDefinition jd) {
        DMNotification dmNotification = buildDMNotification(jd);

        dmNotification.setWorkflowType(ObjectProcessor.WF_TYPE_FORMAT);
        dmNotification.setExecutionId(SUBMITTED_BY_JOB_PROCESSOR);

        dmNotification.setPartitionKey(jd.partitionKeysForStats());
        dmNotification.setPartitionValue(jd.getPartitionValue());


        log.info("Herd Format Notification DB request: \n{}", dmNotification);
        jobProcessorDAO.addDMNotification(dmNotification);
    }

    protected String partition(Set<String> partitionKeys) {
        return partitionKeys.stream().collect(Collectors.joining("`,`", "`", "`"));
    }

    protected String quotedPartitionKeys(Schema schema) {
        return schema.getPartitions().stream().map(p -> p.getName()).collect(Collectors.joining("`,`", "`", "`"));
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

    // This method is used in the child class to override some objects for which we do not want cascade operation.

    protected boolean cascade(JobDefinition jd) {
        return true;
    }

    /**
     * Method to create the HQL file for adding partitions
     *
     * @param jd
     * @return
     * @throws IOException
     */
    protected Path createHqlFile(JobDefinition jd) throws IOException {
        Path hqlFilePath = Paths.get(hqlFileName(jd));
        Files.deleteIfExists(hqlFilePath);
        Files.createFile(hqlFilePath);
        return hqlFilePath;
    }

    protected String hqlFileName(JobDefinition jd) {
        return new StringJoiner("_", "/tmp/", ".hql")
                .add(String.valueOf(jd.getWfType()))
                .add(String.valueOf(jd.getExecutionID()))
                .add(String.valueOf(jd.getNumOfRetry()))
                .toString();
    }


}
