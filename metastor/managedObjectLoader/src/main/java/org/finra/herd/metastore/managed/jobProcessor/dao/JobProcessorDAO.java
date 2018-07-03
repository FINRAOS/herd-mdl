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
package org.finra.herd.metastore.managed.jobProcessor.dao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class JobProcessorDAO {
	@Autowired
	JdbcTemplate template;

	public void addDMNotification( DMNotification dmn ) {
		template.update(
				"INSERT INTO DM_NOTIFICATION (NAMESPACE, OBJECT_DEF_NAME, USAGE_CODE, FILE_TYPE, WF_TYPE, EXECUTION_ID, PARTITION_VALUES, PARTITION_KEY, CLUSTER_NAME, CORRELATION_DATA) \n"
						+ "VALUES (?,?,?,?,?,?,?,?,?,?)"
				, dmn.getNamespace(), dmn.getObjDefName(), dmn.getFormatUsage(), dmn.getFileType(), dmn.getWorkflowType()
				, dmn.getExecutionId(), dmn.getPartitionValue(), dmn.getPartitionKey(), dmn.getClusterName(), dmn.getCorrelationData() );
	}

	public List<DMNotification> getSubmittedRequests( String executionId ) {
		return template.query( "" +
						"SELECT ID, NAMESPACE, OBJECT_DEF_NAME, USAGE_CODE, FILE_TYPE, WF_TYPE, EXECUTION_ID, PARTITION_VALUES, PARTITION_KEY\n" +
						"FROM DM_NOTIFICATION\n" +
						"WHERE EXECUTION_ID = ?\n" +
						"ORDER BY ID;"
				, new Object[]{ executionId }
				, new DMNotification.DMNotificationMapper() );
	}
}

