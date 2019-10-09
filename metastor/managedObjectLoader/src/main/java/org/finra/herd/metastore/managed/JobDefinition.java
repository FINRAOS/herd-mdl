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
package org.finra.herd.metastore.managed;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import static org.finra.herd.metastore.managed.util.JobProcessorConstants.SUB_PARTITION_VAL_SEPARATOR;

@Slf4j
@ToString
@Getter
@Setter
public class JobDefinition {
    long id;
    int wfType;
    int numOfRetry;

    boolean subPartitionLevelProcessing = false;

    String correlation;
    String executionID;
    String clusterName;
    String actualObjectName;
    String tableName;
	String partitionKey;
    String partitionValue;
	List<String> partitionValues;
	Map<String, String> partitionsKeyValue;

    ObjectDefinition objectDefinition;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobDefinition that = (JobDefinition) o;
        return id == that.id &&
                wfType == that.wfType &&
                numOfRetry == that.numOfRetry &&
                Objects.equals(objectDefinition, that.objectDefinition) &&
                Objects.equals(partitionValue, that.partitionValue) &&
                Objects.equals(correlation, that.correlation) &&
                Objects.equals(executionID, that.executionID) &&
                Objects.equals(clusterName, that.clusterName) &&
                Objects.equals(partitionKey, that.partitionKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, objectDefinition, partitionValue, correlation, executionID, clusterName, wfType, numOfRetry, partitionKey);
    }


    public JobDefinition(long id, String nameSpace, String objectName, String usageCode,
                         String fileType, String partitionValue, String correlation,
                         String executionID, String partitionKey) {
        this.id = id;
        objectDefinition=new ObjectDefinition();
        objectDefinition.nameSpace = nameSpace;
        objectDefinition.objectName = objectName;
        objectDefinition.usageCode = usageCode;
        objectDefinition.fileType = fileType;
        this.partitionValue = partitionValue;
        this.correlation = correlation;
        this.executionID = executionID;
        this.partitionKey = partitionKey;
    }

    public JobDefinition() {
    }

    public String getPartitionsSpecForStats(){
    	StringJoiner partitionStats = new StringJoiner( "," , "(", ")");
    	partitionsKeyValue.forEach( (k, v) -> {
    		partitionStats.add( String.format( "`%s`='%s'", k, v ) );
		} );

    	return partitionStats.toString();
	}

    @Slf4j
    public static class ObjectDefinitionMapper implements RowMapper<JobDefinition> {

        @Override
        public JobDefinition mapRow(ResultSet resultSet, int i) throws SQLException {
            JobDefinition jobDefinition = new JobDefinition();

            jobDefinition.id = resultSet.getLong("ID");
            jobDefinition.objectDefinition=new ObjectDefinition();
            jobDefinition.objectDefinition.nameSpace=resultSet.getString("NAMESPACE");
            jobDefinition.objectDefinition.objectName=resultSet.getString(("OBJECT_DEF_NAME"));
            jobDefinition.objectDefinition.usageCode=resultSet.getString("USAGE_CODE");
            jobDefinition.objectDefinition.fileType=resultSet.getString("FILE_TYPE");

			jobDefinition.partitionValue=resultSet.getString("PARTITION_VALUES");
			if ( jobDefinition.getPartitionValue().contains( SUB_PARTITION_VAL_SEPARATOR ) ) {
				jobDefinition.partitionValues = Lists.newArrayList( jobDefinition.getPartitionValue().split( SUB_PARTITION_VAL_SEPARATOR ));
			}

            // Execution ID not being used, other than filenaming .hql files it just have to unique
            jobDefinition.executionID=resultSet.getString("ID");
            jobDefinition.partitionKey=resultSet.getString("PARTITION_KEY");
            jobDefinition.wfType=resultSet.getInt("WF_TYPE");
            jobDefinition.numOfRetry=resultSet.getInt("c");
            jobDefinition.clusterName=resultSet.getString("CLUSTER_NAME");

			jobDefinition.correlation 		= resultSet.getString( "CORRELATION_DATA" );
			jobDefinition.actualObjectName	= getActualObjectName( jobDefinition );
			jobDefinition.tableName 		= identifyTableName( jobDefinition );

            return jobDefinition;
        }

		private String identifyTableName(JobDefinition jobDef){
			return new StringJoiner( "_" )
					.add( identifyObjectName(jobDef))
					.add( jobDef.getObjectDefinition().usageCode )
					.add( jobDef.getObjectDefinition().fileType )
					.toString()
					.replaceAll("\\.", "_")
					.replaceAll(" ","_")
					.replaceAll("-","_");
		}

		private String identifyObjectName( JobDefinition jobDef ) {
			String originalObjectName = "original_object_name";

			if ( !StringUtils.isEmpty(jobDef.getCorrelation()) && jobDef.getCorrelation().contains( originalObjectName ) ) {
				return Json.createReader( new StringReader( jobDef.getCorrelation() ) )
						.readObject().getJsonObject( "businessObject" )
						.getString( originalObjectName );
			}

			return getActualObjectName(jobDef);
		}

		private String getActualObjectName( JobDefinition jobDef ) {
			try {
				if (!StringUtils.isEmpty(jobDef.getCorrelation()) && !jobDef.getCorrelation().equals("null")) {
					JsonReader reader = Json.createReader(new StringReader(jobDef.getCorrelation()));
					JsonObject object = reader.readObject();
					if (object.containsKey("businessObject")) {
						JsonObject bo = object.getJsonObject("businessObject");
						if (bo.containsKey("late_reporting_for")) {
							String objName = bo.getString("late_reporting_for");
							return objName;
						}
					}
				}
			} catch (Exception ex) {
				log.warn("Error parsing correlation:"+jobDef.getCorrelation());
			}
			return jobDef.getObjectDefinition().getObjectName();
		}
    }
}
