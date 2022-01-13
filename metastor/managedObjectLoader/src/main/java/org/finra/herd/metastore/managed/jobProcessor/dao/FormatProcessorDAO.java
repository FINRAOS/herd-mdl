package org.finra.herd.metastore.managed.jobProcessor.dao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FormatProcessorDAO {

    @Autowired
    JdbcTemplate template;

    public void addFormatStatus( FormatStatus formatStatus ) {
        log.info("update :{}",template.update(
            "INSERT INTO FORMAT_STATUS (NAMESPACE, OBJECT_DEF_NAME, USAGE_CODE, FILE_TYPE, WF_TYPE,  PARTITION_VALUES, PARTITION_KEY, ERR_MSG, NOTIFICATION_ID,STATUS) \n"
                + "VALUES (?,?,?,?,?,?,?,?,?,?)"
            , formatStatus.getNamespace(), formatStatus.getObjDefName(), formatStatus.getFormatUsage(), formatStatus.getFileType(), formatStatus.getWorkflowType()
            ,  formatStatus.getPartitionValues(), formatStatus.getPartitionKey(), formatStatus.getErrorMessage(), formatStatus.getNotificationId(),formatStatus.getFormatStatus() ));
    }
}
