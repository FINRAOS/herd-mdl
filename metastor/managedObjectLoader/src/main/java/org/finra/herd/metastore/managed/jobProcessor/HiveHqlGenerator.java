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
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdl;
import org.finra.herd.sdk.model.BusinessObjectFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

@Component
@Slf4j
public class HiveHqlGenerator {

    @Autowired
    DataMgmtSvc dataMgmtSvc;

    @Autowired
	HiveClient hiveClient;

    @Autowired
    NotificationSender notificationSender;

    public List<String> schemaSql(boolean schemaExists, JobDefinition jd) throws ApiException, SQLException {

        String tableName = jd.getTableName();

        List<String> list = Lists.newArrayList();

        if (schemaExists) {
            if(jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON && jd.getPartitionKey().equalsIgnoreCase("partition"))
            {
                list.add(dataMgmtSvc.getTableSchema(jd, true));
            }
            else {
                HiveTableSchema hiveTableSchema =
                        hiveClient.getExistingDDL(jd.getObjectDefinition().getDbName(), tableName);

                BusinessObjectFormat format = dataMgmtSvc.getDMFormat(jd);

                String ddl = dataMgmtSvc.getTableSchema(jd, false);

                try {

                    FormatChange change = detectSchemaChange(jd, hiveTableSchema, format, ddl);

                    if (change.hasColumnChanges()) {
                        boolean cascade = cascade(jd.getWfType());
                        String cascadeStr = "";
                        if(cascade)
                        {
                            cascadeStr = "CASCADE";
                        }
                        if (!jd.getObjectDefinition().getFileType().equalsIgnoreCase("ORC")) {
                            String sql = dataMgmtSvc.getTableSchema(jd, true);

                            if(cascade)
                            {
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
                        }
                    }
                } catch(Exception ex) {
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
        return list;
    }

    @VisibleForTesting
    FormatChange detectSchemaChange(JobDefinition jd,
                                    HiveTableSchema hiveTableSchema,
                                    BusinessObjectFormat format, String ddl) throws ApiException {

            List<Pair<ColumnDef, ColumnDef>> nameChanges = Lists.newArrayList();
            List<Pair<ColumnDef, ColumnDef>> typeChanges = Lists.newArrayList();
            List<ColumnDef> addedColumns = Lists.newArrayList();

            HiveTableSchema newSchema = HiveClientImpl.getHiveTableSchema(ddl);
            List<ColumnDef> existingColumns = hiveTableSchema.getColumns();
            List<ColumnDef> newColumns = newSchema.getColumns();

            log.info("Existing columns = " + existingColumns.size() + ", ddl from Herd has columns = " + newColumns.size());

            int minColumns = Math.min(existingColumns.size(), newColumns.size());

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

            FormatChange change = FormatChange.builder().nameChanges(nameChanges).newColumns(addedColumns)
                    .typeChanges(typeChanges).build();

            if(! HiveTableSchema.isSameChar(newSchema.getNullChar(), hiveTableSchema.getNullChar()))
            {
                change.setNullStrChanged(true);
            }

            if(! HiveTableSchema.isSameChar(newSchema.getDelim(),hiveTableSchema.getDelim()))
            {
                change.setDelimChanged(true);
            }

            if(! HiveTableSchema.isSameChar(newSchema.getEscape(),hiveTableSchema.getEscape()))
            {
                change.setEscapeStrChanged(true);
            }

            List<ColumnDef> existingPartition = hiveTableSchema.getPartitionColumns();
            List<ColumnDef> newPartition = newSchema.getPartitionColumns();

            if(existingPartition.size() != newPartition.size())
            {
                change.setPartitonColumnChanged(true);
            }
            else {
                for (int i = 0; i < existingPartition.size(); i++) {
                    if(!existingPartition.get(i).isSameColumn(newPartition.get(i)))
                    {
                        change.setPartitonColumnChanged(true);
                    }
                }
            }
            if (change.hasChange()) {
                notificationSender.sendFormatChangeEmail(change, format.getBusinessObjectFormatVersion(), jd,
                        hiveTableSchema, newSchema);
            }

        return change;
    }

    public String buildHql(JobDefinition jd, List<String> partitions) throws IOException, ApiException, SQLException {

        Path path = Paths.get("/tmp/" + jd.getWfType() + "_" + jd.getExecutionID() + "_" + jd.getNumOfRetry() + ".hql");
        Files.deleteIfExists(path);

        Files.createFile(path);
        String dbName = jd.getObjectDefinition().getDbName();
        String tableName = jd.getTableName();

        boolean tableExists = hiveClient.tableExist(jd.getObjectDefinition().getDbName(), tableName);

        List<String> schemaHql = schemaSql(tableExists, jd);

        schemaHql.add(0, "use " + dbName + ";");
        schemaHql.add(0, "CREATE DATABASE IF NOT EXISTS " + dbName + ";");

        BusinessObjectDataDdl dataDdl = dataMgmtSvc.getBusinessObjectDataDdl(jd, partitions);

        if (tableExists && jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON
                && jd.getPartitionKey().equalsIgnoreCase("partition")) {
            String ddl = dataDdl.getDdl();
            String location = ddl.substring(ddl.indexOf("LOCATION") + 8);
            schemaHql.add(String.format("alter table %s SET LOCATION %s", tableName, location));
        } else {
            if (tableExists && jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON) {
                //Singleton, add drop statement when table exists
                schemaHql.add(String.format("alter table %s drop if exists partition (%s >'1970-01-01');", tableName, jd.getPartitionKey()));
            }
            schemaHql.add(dataDdl.getDdl());
        }
        //Stats

        if (jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON) {
            if (jd.getPartitionKey().equalsIgnoreCase("partition")) {
                schemaHql.add(String.format("analyze table %s compute statistics noscan;",
                        tableName));
            } else {
                schemaHql.add(String.format("analyze table %s partition(`%s`) compute statistics noscan;",
                        tableName, jd.getPartitionKey()));
            }
        } else if (partitions.size() == 1) {
            String stats = String.format("analyze table %s partition(`%s`='%s') compute statistics noscan;",
                    tableName,
                    jd.getPartitionKey(),
                    partitions.get(0));
            schemaHql.add(stats);
        }

        Files.write(path, schemaHql, CREATE, APPEND);

        return path.toString();
    }

    protected boolean cascade( int wfType ){
    	return true;
	}

}
