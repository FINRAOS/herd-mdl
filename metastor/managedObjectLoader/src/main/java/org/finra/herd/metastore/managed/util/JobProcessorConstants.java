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
package org.finra.herd.metastore.managed.util;

import org.finra.herd.metastore.managed.conf.HerdMetastoreConfig;
import org.springframework.context.annotation.Bean;

public interface JobProcessorConstants {
	String METASTOR_CLUSTER_NAME 		= "metastore";
	String METASTOR_STATS_CLUSTER_NAME 	= "metastore_stats";

	String SVC_ACC_PREFIX = "svc";
	String UNDERSCORE = "_";
	String EQUALS = "=";
	String FORWARD_SLASH = "/";
	String COLON = ":";
	String NEW_LINE = "\n";

	// DM_NOTIFICATION table partition values delimiters
	String DOUBLE_UNDERSCORE = "__";
	String COMMA = ",";
	String SUB_PARTITION_VAL_SEPARATOR = ":";
	String NON_PARTITIONED_SINGLETON_VALUE = "none";

	int DM_RECORD_RETURN_MAX_LIMIT = 1000;

	// Stats jobs
	String GATHER_STATS_SCRIPT_PATH = HerdMetastoreConfig.homeDir + "/metastor/deploy/common/scripts/stats/emr_gather_stats.sh";
	String DROP_TABLE_SCRIPT_PATH = HerdMetastoreConfig.homeDir + "/metastor/deploy/common/scripts/dropObj/emr_drop_table.sh";

	// Hive JDBC properties
	String HIVE_URL = "jdbc:hive2://localhost:10000/";
	String DRIVER_NAME = "org.apache.hive.jdbc.HiveDriver";
	String HIVE_USER = "hadoop";
	String HIVE_PASSWORD = "";

	long MAX_JOB_WAIT_TIME=2100000;

	int getAlterTableAddMaxPartitions();
	int getMaxPartitionFormatLimit();
	int NO_OF_PARALLEL_EXECUTIONS = 1;

}
