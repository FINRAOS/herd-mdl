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
import org.finra.herd.metastore.managed.util.MetastoreUtil;
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


		String partitionValue = od.getPartitionValue();

		List<String> partitionVal = MetastoreUtil.formattedPartitionValues(partitionValue);

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
