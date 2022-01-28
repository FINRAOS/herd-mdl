package org.finra.herd.metastore.managed.jobProcessor.dao;

import lombok.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@AllArgsConstructor
@Builder
@ToString
@Getter
@Setter
public class MetastorObjectLock {

    String namespace;
    String objectName;
    String usagecode;
    String fileType;
    Timestamp createdDate;
    String clusterId;
    String workerId;
    String wfType;
    Timestamp expiryDT;


    public static class MetastorObjectLockMapper implements RowMapper<MetastorObjectLock> {

        @Override
        public MetastorObjectLock mapRow(ResultSet resultSet, int i) throws SQLException {

            return MetastorObjectLock.builder()
                    .namespace(resultSet.getString("NAMESPACE"))
                    .objectName(resultSet.getString("OBJ_NAME"))
                    .fileType(resultSet.getString("FILE_TYPE"))
                    .usagecode(resultSet.getString("USAGE_CODE"))
                    .clusterId(resultSet.getString("CLUSTER_ID"))
                    .workerId(resultSet.getString("WORKER_ID"))
                    .wfType(resultSet.getString("WF_TYPE"))
                    .expiryDT(resultSet.getTimestamp("EXPIRATION_DT"))
                    .createdDate(resultSet.getTimestamp("CREATED_DATE"))
                    .build();
        }
    }
}
