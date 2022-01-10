package org.finra.herd.metastore.managed.jobProcessor.dao;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

@NoArgsConstructor
@Builder
@ToString
@Getter
@Setter
public class FormatStatus {

    String namespace;
    String objDefName;
    String formatUsage;
    String fileType;
    String partitionValues;
    String partitionKey;
    String clusterName;
    String formatStatus;
    String errorMessage;
    int notificationId;
    int workflowType;

    public static class FormatStatusMapper implements RowMapper<FormatStatus>
    {

        @Override
        public FormatStatus mapRow(ResultSet resultSet, int i) throws SQLException {

            return FormatStatus.builder()
                .namespace(  resultSet.getString("NAMESPACE"))
                .objDefName( resultSet.getString("OBJECT_DEF_NAME") )
                .formatUsage( resultSet.getString("USAGE_CODE") )
                .fileType( resultSet.getString("FILE_TYPE") )
                .partitionKey( resultSet.getString("PARTITION_KEY") )
                .partitionValues( resultSet.getString("PARTITION_VALUES") )
                .workflowType(  resultSet.getInt("WF_TYPE") )
                .notificationId(resultSet.getInt("NOTIFICATION_ID"))
                .formatStatus(resultSet.getString("STATUS"))
                .errorMessage(resultSet.getString("ERR_MSG"))
                .build();
        }
    }
}
