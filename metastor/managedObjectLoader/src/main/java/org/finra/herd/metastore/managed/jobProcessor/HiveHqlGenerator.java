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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.ObjectProcessor;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.hive.*;
import org.finra.herd.metastore.managed.jobProcessor.dao.JobProcessorDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.metastore.managed.util.MetastoreUtil;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdl;
import org.finra.herd.sdk.model.BusinessObjectFormat;
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
    JobProcessorDAO jobProcessorDAO;


    public List<String> schemaSql(boolean schemaExists, JobDefinition jd) throws ApiException, SQLException {

        String tableName = jd.getTableName();

        List<String> list = Lists.newArrayList();

        if (schemaExists) {
            if (jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON && jd.getPartitionKey().equalsIgnoreCase("partition")) {
                list.add(dataMgmtSvc.getTableSchema(jd, true));
            } else {
                HiveTableSchema hiveTableSchema = hiveClient.getExistingDDL(jd.getObjectDefinition().getDbName(), tableName);

                BusinessObjectFormat format = dataMgmtSvc.getDMFormat(jd);

                String ddl = dataMgmtSvc.getTableSchema(jd, false);

                try {

                    FormatChange change = detectSchemaChange(jd, hiveTableSchema, format, ddl);
                    formatRegularColumn(change,jd,list,tableName);
                    formatPartitionColumn(change,jd,list,tableName);

                } catch (Exception ex) {
                    log.warn("Error comparing formats", ex);
                    notificationSender.sendNotificationEmail(ex.getMessage(), "Error comparing formats", jd);
                    if (!jd.getObjectDefinition().getFileType().equalsIgnoreCase("ORC")) {
                        //Can only do replace column in this case
                        String sql = dataMgmtSvc.getTableSchema(jd, true);
                        list.add(sql);
                    }
                }
            }

        } else {
            log.info("Table does not exist, create new " + jd.toString());
        }
        log.info("Table Schema: " + list);
        return list;
    }



    void formatRegularColumn(FormatChange change, JobDefinition jd, List<String> list, String tableName) throws org.finra.herd.sdk.invoker.ApiException
    {
        if (change.hasColumnChanges()) {
            boolean cascade = cascade(jd);
            String cascadeStr = "";
            if (cascade) {
                cascadeStr = "CASCADE";
            }
            if (!jd.getObjectDefinition().getFileType().equalsIgnoreCase("ORC")) {
                String sql = dataMgmtSvc.getTableSchema(jd, true);

                log.info("=======>sql:{}",sql);

                if (cascade) {
                    sql = sql.substring(0, sql.lastIndexOf(";")) + " CASCADE;";
                }
                list.add(sql);
            } else {
                for (Pair<ColumnDef, ColumnDef> pair : change.getNameChanges()) {
                    ColumnDef existing = pair.getFirst();
                    ColumnDef newColum = pair.getSecond();

                    list.add(String.format("Alter table %s change %s %s %s %s;", tableName,
                            existing.getName(), newColum.getName(), newColum.getType(), cascadeStr));
                }

                for (Pair<ColumnDef, ColumnDef> pair : change.getTypeChanges()) {
                    ColumnDef existing = pair.getFirst();
                    ColumnDef newColum = pair.getSecond();
                    list.add(String.format("Alter table %s change %s %s %s %s;", tableName,
                            existing.getName(), newColum.getName(), newColum.getType(), cascadeStr));
                }

                if (!change.getNewColumns().isEmpty()) {
                    StringBuffer sb = new StringBuffer();
                    for (ColumnDef c : change.getNewColumns()) {
                        sb.append(String.format("%s %s,", c.getName(), c.getType()));
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    list.add(String.format("Alter table %s add columns (%s) %s;", tableName, sb.toString(),
                            cascadeStr));
                }


                log.info("the formatRegularColumn list is :{}",list);
            }
        }


    }


    void formatPartitionColumn(FormatChange change, JobDefinition jd, List<String> list, String tableName) throws org.finra.herd.sdk.invoker.ApiException{

        if(change.hasPartitionColumnChanges())
        {


                for (Pair<ColumnDef, ColumnDef> pair : change.getPartitionColTypeChanges()) {
                    ColumnDef existing = pair.getFirst();
                    ColumnDef newColum = pair.getSecond();
                    list.add(String.format("Alter table %s partition column %s %s %s;", tableName,
                            existing.getName(), newColum.getName(), newColum.getType()));
                }


                log.info("the formatPartitionColumn list is :{}",list);

        }


    }




    @VisibleForTesting
    FormatChange detectSchemaChange(JobDefinition jd,
                                    HiveTableSchema hiveTableSchema,
                                    BusinessObjectFormat format, String ddl) throws ApiException {


        HiveTableSchema newSchema = HiveClientImpl.getHiveTableSchema(ddl);
        List<ColumnDef> existingColumns = hiveTableSchema.getColumns();
        List<ColumnDef> existingPartitionColumns = hiveTableSchema.getPartitionColumns();
        List<ColumnDef> newColumns = newSchema.getColumns();
        List<ColumnDef> newPartitionColumns = newSchema.getPartitionColumns();

        log.info("format:{} ", format);

        log.info("Existing Columns:{}, newColumns:{}",existingColumns,newColumns);

        log.info("Existing Partition columns = " + existingPartitionColumns.size() + ", ddl from Herd  Partitioncolumns = " + newPartitionColumns.size());

        log.info("Existing columns = " + existingColumns.size() + ", ddl from Herd has columns = " + newColumns.size());



        FormatChange change = FormatChange.builder().build();

        detectRegularColumnChanges(existingColumns,newColumns,change);
        detectPartitionColumnChanges(existingPartitionColumns,newPartitionColumns,change);


        //@Todo - Once the fix for delimiters is done

//            if(! HiveTableSchema.isSameChar(newSchema.getNullChar(), hiveTableSchema.getNullChar()))
//            {
//                change.setNullStrChanged(true);
//            }
//
//            if(! HiveTableSchema.isSameChar(newSchema.getDelim(),hiveTableSchema.getDelim()))
//            {
//                change.setDelimChanged(true);
//            }
//
//            if(! HiveTableSchema.isSameChar(newSchema.getEscape(),hiveTableSchema.getEscape()))
//            {
//                change.setEscapeStrChanged(true);
//            }

        log.info("Format Object change is :{}", change);


        if (change.hasChange()) {
            notificationSender.sendFormatChangeEmail(change, format.getBusinessObjectFormatVersion(), jd,
                    hiveTableSchema, newSchema);
        }

        return change;
    }

    @VisibleForTesting
    void detectRegularColumnChanges(List<ColumnDef> existingColumns,List<ColumnDef> newColumns,FormatChange change)
    {
        List<Pair<ColumnDef, ColumnDef>> nameChanges = Lists.newArrayList();
        List<Pair<ColumnDef, ColumnDef>> typeChanges = Lists.newArrayList();
        List<ColumnDef> addedColumns = Lists.newArrayList();



        int minColumns = Math.min(existingColumns.size(), newColumns.size());

        //@TODO - Refactor and Move all of these logic out of HqlGenerator when we implement Format Change api using sns
            /*
              Regular Column
             */
        for (int i = 0; i < minColumns; i++) {
            ColumnDef existing = existingColumns.get(i);
            ColumnDef newColum = newColumns.get(i);

            if (!newColum.getName().equalsIgnoreCase(existing.getName())) {
                nameChanges.add(new Pair<>(existing, newColum));
            } else if (!newColum.isSameType(existing)) {
                typeChanges.add(new Pair<>(existing, newColum));
            }

        }

        if (newColumns.size() > existingColumns.size()) {
            for (int i = existingColumns.size(); i < newColumns.size(); i++) {
                ColumnDef newColum = newColumns.get(i);
                addedColumns.add(newColum);
            }
        }

        log.info("Regular Column Changes nameChanges :{}, typeChanges:{},addedColumns :{}",nameChanges,typeChanges,addedColumns);
        change.setTypeChanges(typeChanges);
        change.setNameChanges(nameChanges);
        change.setNewColumns(addedColumns);



    }

    @VisibleForTesting
    void detectPartitionColumnChanges(List<ColumnDef> existingPartitionColumns,List<ColumnDef> newPartitionColumns,FormatChange change)
    {
        List<Pair<ColumnDef, ColumnDef>> partitionColTypeChanges = Lists.newArrayList();
        List<Pair<ColumnDef, ColumnDef>> partitionColNameChanges = Lists.newArrayList();


        int minColumns = Math.min(existingPartitionColumns.size(), newPartitionColumns.size());


        for (int i = 0; i < minColumns; i++) {
            ColumnDef existing = existingPartitionColumns.get(i);
            ColumnDef newColum = newPartitionColumns.get(i);

            if (!newColum.getName().equalsIgnoreCase(existing.getName())) {
                partitionColNameChanges.add(new Pair<>(existing, newColum));
                log.error("Hive does not support partition Column Name changes:{}",partitionColNameChanges);

            } else if (!newColum.isSameType(existing)) {
                partitionColTypeChanges.add(new Pair<>(existing, newColum));
            }

        }



        log.info("Partition Column Changes partitionNameChanges:{}, partitionTypeChanges:{}",partitionColNameChanges,partitionColTypeChanges);
        change.setPartitionColNameChanges(partitionColNameChanges);
        change.setPartitionColTypeChanges(partitionColTypeChanges);


    }




    public String buildHql(JobDefinition jd, List<String> partitions) throws IOException, ApiException, SQLException {
        boolean tableExists = hiveClient.tableExist(jd.getObjectDefinition().getDbName(), jd.getTableName());
        BusinessObjectDataDdl dataDdl = dataMgmtSvc.getBusinessObjectDataDdl(jd, partitions);

        List<String> schemaHql = schemaSql(tableExists, jd);

        // Add database Statements
        selectDatabase(jd, schemaHql);

        // Add DDL's from data DDL
        addPartitionChanges(tableExists, jd, dataDdl, schemaHql);

        //Stats
        addAnalyzeStats(jd, partitions);

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

    protected void addAnalyzeStats(JobDefinition jd, List<String> partitions) {

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

        log.info("Herd Notification DB request: \n{}", dmNotification);
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
