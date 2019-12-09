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
package org.finra.herd.metastore.managed.jobProcessor;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Slf4j
public class ManagedObjStatsProcessor extends JobProcessor {

	@Override
	protected ProcessBuilder createProcessBuilder( JobDefinition od ) {
		String partitionSpecsForStats = od.partitionSpecForStats();

		if ( Strings.isNullOrEmpty( partitionSpecsForStats ) ) {
			log.error( "ERROR: CLUSTER_TYPE_STATS PARTITION Spec is empty: {}", od );
			return null;
		}

		List<String> command = Lists.newArrayList( "sh", JobProcessorConstants.GATHER_STATS_SCRIPT_PATH );
		command.add( od.getObjectDefinition().getDbName() );
		command.add( od.getTableName() );
		command.add( partitionSpecsForStats );

		log.info( "Calling analyze stats with: {}", command );
		return new ProcessBuilder( command );
	}
}
