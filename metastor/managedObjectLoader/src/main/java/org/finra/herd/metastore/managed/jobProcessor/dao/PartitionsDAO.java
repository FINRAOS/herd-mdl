package org.finra.herd.metastore.managed.jobProcessor.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
public class PartitionsDAO {

    @Autowired
    JdbcTemplate jdbcTemplate;



    public int getTotalPartitionCount(String objectName, String dbName){

        String sql="select count(*) from metastor.PARTITIONS p \n" +
            "join  TBLS T on T.TBL_ID = p.TBL_ID \n" +
            "join DBS D on D.DB_ID = T.DB_ID \n" +
            "where T.TBL_NAME =?\n" +
            "and D.NAME = ?;";
        int totalPartitionCount= jdbcTemplate.queryForObject(sql,new Object[]{objectName.toLowerCase(),dbName.toLowerCase()},Integer.class);
        return  totalPartitionCount;
    }


    public List<String> getDistinctRootPartition(String objectName, String dbName){

       String sql="select DISTINCT(SUBSTRING_INDEX((SUBSTRING_INDEX(PART_NAME,\"/\",1)),\"=\",-1)) as partitions from metastor.PARTITIONS p \n" +
           "join  TBLS T on T.TBL_ID = p.TBL_ID \n" +
           "join DBS D on D.DB_ID = T.DB_ID \n" +
           "where T.TBL_NAME = ?\n" +
           "and D.NAME = ?;\n";


       return jdbcTemplate.query(sql, new Object[]{objectName.toLowerCase(), dbName.toLowerCase()}, (resultSet,rowNum) -> {
           return resultSet.getString("partitions");
       });
    }


}
