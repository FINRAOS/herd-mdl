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
package org.finra.herd.metastore.managed.jobProcessor.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.model.Schema;

@Setter
@Getter
@Builder
public class JobSubmitterInfo {
	int workflowType = -1;

	String clusterName = JobProcessorConstants.METASTOR_CLUSTER_NAME;
	String namespace;
	String objectName;
	String usage;
	String fileType;
	String partitionKey;
	String partitionValues;
	String correlation;

	// variable for generating execution id
	long herdNotificationId;

	boolean manualLoadPartitions = false;
	String partitionsSubmitted;

	Schema tableSchema;

	public String getEmailContent() {
		return String.format( "Herd max records limit reached, please manually load partitions excluding following partitions: \n%s", partitionsSubmitted );
	}

	public String getExecutionId() {
		return String.valueOf( herdNotificationId );
	}

	public boolean isPartitionDateType() {
	    try {
            return this.getTableSchema().getPartitions().stream().filter( p -> p.getName().equalsIgnoreCase( this.getPartitionKey() ) ).anyMatch( p -> "DATE".equalsIgnoreCase( p.getType() ) );
        }catch ( Exception ex ) {
	        throw new RuntimeException( "Could not identify if partition key is date type due to: " + ex.getMessage(), ex );
        }
	}
}
