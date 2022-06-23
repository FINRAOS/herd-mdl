package org.finra.herd.metastore.managed.jobProcessor.dao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
@Slf4j
public class PartitionsDAO {

    @Autowired
    JdbcTemplate template;


    public int getTotalPartitionCount(String tableName, String dbName) {

        String sql = "select count(*) from metastor.PARTITIONS p \n" +
                "join  TBLS T on T.TBL_ID = p.TBL_ID \n" +
                "join DBS D on D.DB_ID = T.DB_ID \n" +
                "where T.TBL_NAME =? \n" +
                "and D.NAME = ?;";
        int totalPartitionCount = template.queryForObject(sql, new Object[]{tableName.toLowerCase(), dbName.toLowerCase()}, Integer.class);
        log.info("Total PartitionCount for Object:{} {} {}", dbName, tableName, totalPartitionCount);
        return totalPartitionCount;
    }


    public List<String> getDistinctRootPartition(String tableName, String dbName) {

        String sql = "select DISTINCT(SUBSTRING_INDEX((SUBSTRING_INDEX(PART_NAME,\"/\",1)),\"=\",-1)) as partitions from metastor.PARTITIONS p \n" +
                "join  TBLS T on T.TBL_ID = p.TBL_ID \n" +
                "join DBS D on D.DB_ID = T.DB_ID \n" +
                "where T.TBL_NAME = ?\n" +
                "and D.NAME = ?;\n";


        return template.query(sql, new Object[]{tableName.toLowerCase(), dbName.toLowerCase()}, (resultSet, rowNum) -> {
            return resultSet.getString("partitions");
        });
    }


    public int getMaxCount(String tableName, String dbName) {

        String sql = "SELECT MAX(CNT) \n" +
                "  FROM (\n" +
                "        SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(P.PART_NAME, CONCAT(LOWER(PK.PKEY_NAME), '='), -1), '/', 1) as ROOT_PARTITION,\n" +
                "               COUNT(*)  CNT       \n" +
                "          FROM PARTITIONS P\n" +
                "          JOIN TBLS T \n" +
                "            ON T.TBL_ID = P.TBL_ID\n" +
                "          JOIN DBS D \n" +
                "            ON D.DB_ID = T.DB_ID\n" +
                "          JOIN PARTITION_KEYS PK \n" +
                "            ON T.TBL_ID = PK.TBL_ID \n" +
                "           AND INTEGER_IDX = 0\n" +
                "         WHERE lower(D.NAME) = ? \n" +
                "           AND lower(T.TBL_NAME) = ?\n" +
                "         GROUP BY SUBSTRING_INDEX(SUBSTRING_INDEX(P.PART_NAME, CONCAT(LOWER(PK.PKEY_NAME), '='), -1), '/', 1)\n" +
                "       ) as sq;";

        int maxCount = template.queryForObject(sql, new Object[]{dbName.toLowerCase(), tableName.toLowerCase()}, Integer.class);
        return maxCount;
    }


}