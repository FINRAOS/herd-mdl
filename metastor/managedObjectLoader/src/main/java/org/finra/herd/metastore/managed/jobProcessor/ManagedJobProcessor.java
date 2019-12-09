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

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.hive.HiveClient;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;


@Component
@Slf4j
public class ManagedJobProcessor extends JobProcessor {

	@Autowired
	HiveHqlGenerator hiveHqlGenerator;

	@Override
	public ProcessBuilder createProcessBuilder( JobDefinition od ) {
		Objects.requireNonNull( od.getPartitionKey(), "PartitionKey is required for DDL call but found it NULL or EMPTY....EXITING!!!" );

		List<String> partitionVal = Lists.newArrayList();

		String partitionValue = od.getPartitionValue();
		if ( partitionValue.contains( JobProcessorConstants.DOUBLE_UNDERSCORE ) ) {
			try {
				String[] parts = partitionValue.split( JobProcessorConstants.DOUBLE_UNDERSCORE );

				String startDate = parts[1];
				String endDate = parts[0];

				SimpleDateFormat format = new SimpleDateFormat( "yyyy-MM-dd" );
				Date start = format.parse( startDate );
				Date end = format.parse( endDate );
				Date d = start;

				while ( d.before( end ) ) {
					partitionVal.add( format.format( d ) );
					d.setTime( d.getTime() + 24 * 3600 * 1000 );
				}

				partitionVal.add( endDate );
			} catch ( ParseException e ) {
				logger.severe( e.getMessage() );
				return null;
			}

		} else if ( partitionValue.contains( JobProcessorConstants.COMMA ) ) {
			partitionVal.addAll( Lists.newArrayList( partitionValue.split( JobProcessorConstants.COMMA ) ) );
		} else {
			partitionVal.add( partitionValue );
		}

		try {
			String path = hiveHqlGenerator.buildHql( od, partitionVal );
			ProcessBuilder pb = new ProcessBuilder( "hive", "-v", "-f", path );
			return pb;
		} catch ( Throwable ex ) {
			errorBuffer.append( ex.getMessage() );
			ex.printStackTrace();

		}

		return null;

	}

}
