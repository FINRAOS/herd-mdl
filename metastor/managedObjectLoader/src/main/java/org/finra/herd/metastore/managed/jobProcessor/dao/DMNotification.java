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

import lombok.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

@AllArgsConstructor
@Builder
@ToString
@Getter
@Setter
public class DMNotification{
	String executionId;

	String namespace;
	String objDefName;
	String formatUsage;
	String fileType;
	String partitionValue;
	String partitionKey;
	String clusterName;
	String correlationData;

	int workflowType;

	public static class DMNotificationMapper implements RowMapper<DMNotification>
	{

		@Override
		public DMNotification mapRow( ResultSet resultSet, int i) throws SQLException {

			return DMNotification.builder()
					.namespace(  resultSet.getString("NAMESPACE"))
					.objDefName( resultSet.getString("OBJECT_DEF_NAME") )
					.formatUsage( resultSet.getString("USAGE_CODE") )
					.fileType( resultSet.getString("FILE_TYPE") )
					.partitionKey( resultSet.getString("PARTITION_KEY") )
					.partitionValue( resultSet.getString("PARTITION_VALUES") )
					.executionId( resultSet.getString("EXECUTION_ID") )
					.workflowType(  resultSet.getInt("WF_TYPE") )
					.build();
		}
	}
}