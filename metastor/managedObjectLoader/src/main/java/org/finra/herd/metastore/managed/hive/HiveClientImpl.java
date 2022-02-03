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
package org.finra.herd.metastore.managed.hive;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.format.ClusteredDef;
import org.finra.herd.metastore.managed.format.ColumnDef;
import org.finra.herd.metastore.managed.format.HRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.finra.herd.metastore.managed.util.JobProcessorConstants.*;

@Component
@Slf4j
public class HiveClientImpl implements HiveClient {

    @Autowired
    JdbcTemplate hiveJdbcTemplate;


    static {
        try {
            Class.forName(DRIVER_NAME);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        DriverManager.setLoginTimeout(45);
    }

    protected Connection getDatabaseConnection(String databaseName) throws SQLException {
        return DriverManager.getConnection(String.format(HIVE_URL + "%s", databaseName), HIVE_USER, HIVE_PASSWORD);
    }

    public static List<ColumnDef> getDMSchemaColumns(String ddl) {


        String s = ddl.substring(ddl.indexOf("(") + 1, ddl.indexOf("PARTITIONED BY")).trim();
        s = s.substring(0, s.lastIndexOf(")"));

        return getColumnDefs(s, ",\n");

    }

    public static ClusteredDef getClusterByClause(String ddl, List<ColumnDef> columnDefs) {

        ClusteredDef clusteredDef = null;

        List<String> clusteredSortedColumns = Lists.newArrayList();


        if (ddl.contains("CLUSTERED") || ddl.contains("clustered")) {

            String clusterclause = ddl.substring(ddl.indexOf("CLUSTERED BY"), ddl.indexOf("ROW"));

            log.info("ClusterSortedclauseSQL:{}", clusterclause);

            List<String> clusterColumns = getClusteredColumns(clusterclause);
            List<String> sortedColumns = getSortedColumns(clusterclause);

            Iterable<String> unionClusterSortedCols = CollectionUtils.union(
                    clusterColumns, sortedColumns);

            clusteredSortedColumns = StreamSupport.stream(unionClusterSortedCols.spliterator(), false)
                    .collect(Collectors.toList());

            clusteredDef = clusteredDef.builder().
                    clusterCols(clusterColumns).
                    clusterSql(clusterclause).
                    sortedCols(sortedColumns).
                    clusteredSortedColDefs(getClusteredSortedColDefs(clusteredSortedColumns, columnDefs)).build();


        } else {
            clusteredDef = clusteredDef.builder().build();
        }


        return clusteredDef;

    }

    static List<String> getClusteredColumns(String ddl) {


        List<String> clusterColumns = Lists.newArrayList();


        String cc = StringUtils.substringBetween(ddl, "(", ")");
        final String[] array = cc.split(",");
        Arrays.stream(array).map(String::trim).toArray(arr -> array);
        clusterColumns = Arrays.asList(array);
        log.info("clusterColumns:{}", clusterColumns);

        return clusterColumns;


    }


    static List<String> getSortedColumns(String ddl) {
        List<String> sortedColumns = Lists.newArrayList();


        if (ddl.contains("SORTED BY") || ddl.contains("sorted by")) {

            String sb = StringUtils.substringBetween(ddl, "SORTED BY ", ")");
            log.info("SORTED BY COLUMNS :{}", sb);

            sb = StringUtils.remove(sb, "(");
            if (sb.contains("ASC") || sb.contains("asc")) {
                sb = StringUtils.removeIgnoreCase(sb, "ASC");
            }
            if (sb.contains("DESC") || sb.contains("desc")) {
                sb = StringUtils.removeIgnoreCase(sb, "DESC");
            }
            final String[] array1 = sb.split(",");
            Arrays.stream(array1).map(String::trim).toArray(arr -> array1);
            sortedColumns = Arrays.asList(array1);
            log.info("sortedColumns:{}", sortedColumns);

        }

        return sortedColumns;

    }


    static List<ColumnDef> getClusteredSortedColDefs(List<String> clusteredSortedColumns, List<ColumnDef> columnDefs) {

        log.info("clusteredSortedColumns:{}", clusteredSortedColumns);

        List<ColumnDef> clusterSortedColDefs = columnDefs.stream().filter(
                cols -> clusteredSortedColumns.stream().anyMatch(
                        clusCol -> clusCol.trim().equalsIgnoreCase(cols.getName().trim())
                )).collect(Collectors.toList());


        log.info("clusterSortedColDefs:{}", clusterSortedColDefs);
        return clusterSortedColDefs;

    }

    @Override
    public HiveTableSchema getExistingDDL(String dbName, String tableName) throws SQLException {

        try (Connection con = getDatabaseConnection(dbName)) {
            Statement stmt = con.createStatement();

            stmt.execute("SHOW CREATE TABLE " + tableName);

            ResultSet resultSet = stmt.getResultSet();

            StringBuffer sb = new StringBuffer();

            while (resultSet.next()) {
                sb.append(resultSet.getString(1).trim() + "\n");
            }
            String ddl = sb.toString();
            log.info("Existing Hive Schema: " + ddl);

            HiveTableSchema schema = getHiveTableSchema(ddl);

            return schema;
        }
    }

    public static HiveTableSchema getHiveTableSchema(String ddl) {
        List<ColumnDef> columnList;


        columnList = getDMSchemaColumns(ddl);

        ClusteredDef clusteredDef = getClusterByClause(ddl, columnList);


        HiveTableSchema schema = new HiveTableSchema().toBuilder().ddl(ddl).columns(columnList).clusteredDef(clusteredDef).build();
        if (ddl.contains("PARTITIONED BY")) {
            List<ColumnDef> partitionColumns = getPartitionColumns(ddl);
            schema.setPartitionColumns(partitionColumns);
            log.info("Partition columns :{}", partitionColumns);
        }

        if (ddl.contains("FIELDS TERMINATED BY")) {
            String delimStr = ddl.substring(ddl.indexOf("FIELDS TERMINATED BY"));
            int beginIndex = delimStr.indexOf("'") + 1;
            delimStr = delimStr.substring(beginIndex, delimStr.indexOf("'", beginIndex));
            schema.setDelim(delimStr);

        } else if (ddl.contains("'field.delim'")) //Hive 2
        {
            schema.setDelim(getFieldStr(ddl, "'field.delim'"));
        }

        if (ddl.contains("ESCAPED BY")) {
            String escapeStr = ddl.substring(ddl.indexOf("ESCAPED BY"));
            int beginIndex = escapeStr.indexOf("'") + 1;
            escapeStr = escapeStr.substring(beginIndex, escapeStr.indexOf("'", beginIndex));
            schema.setEscape(escapeStr);
        } else if (ddl.contains("'escape.delim'")) {
            String escapeStr = getFieldStr(ddl, "'escape.delim'");
            schema.setEscape(escapeStr);
        }

        if (ddl.contains("NULL DEFINED AS")) {
            String nullStr = ddl.substring(ddl.indexOf("NULL DEFINED AS"));
            int beginIndex = nullStr.indexOf("'") + 1;
            nullStr = nullStr.substring(beginIndex, nullStr.indexOf("'", beginIndex));
            schema.setNullChar(nullStr);
        } else if (ddl.contains("'serialization.null.format'")) {
            schema.setNullChar(getFieldStr(ddl, "'serialization.null.format'"));
        }
        return schema;
    }

    public static String getFieldStr(String ddl, String fieldName) {
        String escapeStr = ddl.substring(ddl.indexOf(fieldName));
        int beginIndex = escapeStr.indexOf("='") + 2;
        escapeStr = escapeStr.substring(beginIndex, escapeStr.indexOf("'", beginIndex));
        return escapeStr;
    }

    @Override
    public boolean tableExist(String dbName, String tableName) throws SQLException {
        try (Connection con = getDatabaseConnection("default")) {
            Statement stmt = con.createStatement();
            log.info("tableExist? {},{}",dbName,tableName);
            stmt.execute("show databases like \'" + dbName + "\'");
            if (stmt.getResultSet().next()) {
                stmt.execute(String.format("Show tables in %s like \'%s\'", dbName, tableName));
                return stmt.getResultSet().next();
            }
            return false;
        }
    }


    static List<ColumnDef> getPartitionColumns(String ddl) {
        String s = ddl.substring(ddl.indexOf("PARTITIONED BY") + 14, ddl.indexOf("ROW FORMAT")).trim();

        if (s.contains("CLUSTERED")) {

            int indexofClustered = s.indexOf("CLUSTERED");
            s = s.substring(s.indexOf("(") + 1, s.lastIndexOf(")", indexofClustered));

        } else {
            s = s.substring(s.indexOf("(") + 1, s.lastIndexOf(")"));
        }

        log.info("The partition columns are :{}", s);


        if (s.contains("\n")) {
            return getColumnDefs(s, ",\n");
        } else {
            return getColumnDefs(s, ", `");

        }
    }

    private static List<ColumnDef> getColumnDefs(String s, String split) {
        List<ColumnDef> entries = Lists.newArrayList();
        String[] columns = s.split(split);
        int i = 0;
        for (String c : columns) {
            String[] column = c.trim().replace("`", "").split(" ");
            if (column.length < 2) {
                log.info("Ignore line: " + c);
                continue;
            }
            String name = column[0].trim();
            String type = column[1].trim();
            if (column.length > 2 && !"COMMENT".equalsIgnoreCase(column[2])) {
                type += column[2].trim();
            }

            entries.add(ColumnDef.builder().name(name).type(type).index(i).build());
            i++;
        }
        return entries;
    }

    @Override
    public List<HivePartition> getExistingPartitions(String database, String tableName, Set<String> partitionSpec) {
        List<HivePartition> partName = Lists.newArrayList();

        partitionSpec.forEach(s -> {
            partName.addAll(
                    hiveJdbcTemplate.query(
                            String.format("SHOW PARTITIONS %s.%s PARTITION (%s)", database, tableName, s)
                            , new RowMapper<HivePartition>() {
                                @Nullable
                                @Override
                                public HivePartition mapRow(ResultSet resultSet, int i) throws SQLException {
                                    return HivePartition.builder().metastorePartName(resultSet.getString(1)).build();
                                }
                            }
                    ));
        });

        return partName;
    }

    @Override
    public List<HivePartition> getExistingPartitions(String database, String tableName) {
        List<HivePartition> partName = Lists.newArrayList();

        partName.addAll(
                hiveJdbcTemplate.query(
                        String.format("SHOW PARTITIONS %s.%s", database, tableName)
                        , new RowMapper<HivePartition>() {

                            @Override
                            public HivePartition mapRow(ResultSet resultSet, int i) throws SQLException {
                                return HivePartition.builder().metastorePartName(resultSet.getString(1)).build();
                            }
                        }
                ));
        return partName;
    }

    @Override
    public   void executeQueries(String database, List<String> hqlStatement) {
        log.info("Executing schemaSQL: {}", hqlStatement);

        try (Connection con = getDatabaseConnection(database)) {

            Statement stmt = con.createStatement();
            hqlStatement.forEach(hql->{
                try {
                    stmt.execute(hql);
                }catch (SQLException sqe){}
            });


        }catch (SQLException sqe){

        }
    }

    public List<HRoles> getRoles(JobDefinition jobDefinition){

        List<HRoles> hRoles=Lists.newArrayList();

        hRoles.addAll(hiveJdbcTemplate.query(
                String.format("set role admin;SHOW grant on table %s.%s", jobDefinition.getObjectDefinition().getDbName(), jobDefinition.getTableName())
                , new RowMapper<HRoles>() {

                    @Override
                    public HRoles mapRow(ResultSet resultSet, int i) throws SQLException {
                        return HRoles.builder().dbName(resultSet.getString(1))
                                .tableName(resultSet.getString(2))
                                .principalName(resultSet.getString(5))
                                .principalType(resultSet.getString(6))
                                .privilege(resultSet.getString(7))
                                .grantOption(resultSet.getBoolean(8))
                                .build();
                    }
                }
        ));

        return  hRoles;

    }




    public boolean runHiveQuery(String dbName, String hqlStatement) throws SQLException {

        try (Connection con = getDatabaseConnection(dbName)) {

            Statement stmt = con.createStatement();
            return stmt.execute(hqlStatement);

        }

    }



}
