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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.logging.Logger;

public class JobDefinition {
    long id;

    ObjectDefinition objectDefinition;
    String partitionValue;
    String correlation;
    String executionID;

    String clusterName;
    int wfType;


    public int getNumOfRetry() {
        return numOfRetry;
    }

    int numOfRetry;

    public String getPartitionKey() {
        return partitionKey;
    }

	public void setPartitionKey( String partitionKey ) {
		this.partitionKey = partitionKey;
	}

	String partitionKey;

    public String getClusterName() {
        return this.clusterName;
    }

    public ObjectDefinition getObjectDefinition() {
        return objectDefinition;
    }

    private Logger logger = Logger.getLogger("JobDefinition");


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

    @Override
    public String toString() {
        return "JobDefinition{" +
                "id=" + id +
                ", objectDefinition=" + objectDefinition +
                ", partitionValue='" + partitionValue + '\'' +
                ", correlation='" + correlation + '\'' +
                ", executionID='" + executionID + '\'' +
                ", clusterName='" + clusterName + '\'' +
                ", wfType=" + wfType +
                ", numOfRetry=" + numOfRetry +
                ", partitionKey='" + partitionKey + '\'' +
                '}';
    }

    public long getId() {
        return id;
    }

    public String getPartitionValue() {
        return partitionValue;
    }

    public String getCorrelation() {
        return correlation;
    }

    public String getExecutionID() {
        return executionID;
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

    public static class ObjectDefinitionMapper implements RowMapper<JobDefinition>
    {

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
            jobDefinition.correlation = resultSet.getString("CORRELATION_DATA");
            // Execution ID not being used, other than filenaming .hql files it just have to unique
            jobDefinition.executionID=resultSet.getString("ID");
            jobDefinition.partitionKey=resultSet.getString("PARTITION_KEY");
            jobDefinition.wfType=resultSet.getInt("WF_TYPE");
            jobDefinition.numOfRetry=resultSet.getInt("c");
            jobDefinition.clusterName=resultSet.getString("CLUSTER_NAME");
            return jobDefinition;
        }
    }

    public String getActualObjectName()
    {
        try {

            if (!StringUtils.isEmpty(correlation) && !correlation.equals("null")) {
                JsonReader reader = Json.createReader(new StringReader(correlation));
                JsonObject object = reader.readObject();
                if (object.containsKey("businessObject")) {
                    JsonObject bo = object.getJsonObject("businessObject");
                    if (bo.containsKey("late_reporting_for")) {
                        String objName = bo.getString("late_reporting_for");
                        return objName;
                    }
                }
            }
        } catch (Exception ex)
        {
            logger.warning("Error parsing correlation:"+correlation);
        }
        return objectDefinition.objectName;
    }

    public int getWfType() {
        return wfType;
    }

    public void setWfType(int wfType) {
        this.wfType = wfType;
    }

    public String getTableName()
    {
		return new StringJoiner( "_" )
				.add( identifyObjectName())
				.add( objectDefinition.usageCode )
				.add( objectDefinition.fileType )
				.toString()
					.replaceAll("\\.", "_")
					.replaceAll(" ","_")
					.replaceAll("-","_");
    }

	private String identifyObjectName() {
		String originalObjectName = "originalObjectName";

		if ( correlation.contains( originalObjectName ) ) {
				return Json.createReader( new StringReader( correlation ) )
						.readObject().getJsonObject( "businessObject" )
						.getString( originalObjectName );
		}

		return getActualObjectName();
	}

}
