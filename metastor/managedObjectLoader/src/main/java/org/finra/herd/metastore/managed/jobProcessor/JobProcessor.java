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
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.JobPicker;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectFormat;
import org.finra.herd.sdk.model.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class JobProcessor {

	@Autowired
	JobPicker jobPicker;

	@Autowired
	DataMgmtSvc dataMgmtSvc;

	void setJobPicker( JobPicker jobPicker ) {
		this.jobPicker = jobPicker;
	}

	public boolean process( JobDefinition od, String clusterID, String workerID ) {
		errorBuffer.setLength( 0 );
		setPartitionKeyIfNotPresent( od );
		try {
			return runProcess( od, clusterID, workerID );
		} catch ( Exception ex ) {
			logger.severe( ex.getMessage() );
			errorBuffer.append( ex.getMessage() );
			return false;
		}

	}

	protected void setPartitionKeyIfNotPresent( JobDefinition od ) {
		if ( Strings.isNullOrEmpty( od.getPartitionKey() ) ) {
			try {
				logger.info( "Partition Key NULL or EMPTY, calling Herd to get Partition Key" );
				BusinessObjectFormat dmFormat = dataMgmtSvc.getDMFormat( od );
				od.setPartitionKey( dmFormat.getPartitionKey() );
			} catch ( ApiException e ) {
				e.printStackTrace();
			}
		}
		logger.info( "Partition Key: " + od.getPartitionKey() );
	}

	protected void setPartitionKeyRegardless( JobDefinition od ) {

			try {
				logger.info( "Partition Key NULL or EMPTY, calling Herd to get Partition Key" );
				BusinessObjectFormat dmFormat = dataMgmtSvc.getDMFormat( od );
				od.setPartitionKey( dmFormat.getPartitionKey() );
			} catch ( ApiException e ) {
				e.printStackTrace();
			}

		logger.info( "Partition Key: " + od.getPartitionKey() );
	}

	protected abstract ProcessBuilder createProcessBuilder( JobDefinition od );

	protected final StringBuffer errorBuffer = new StringBuffer();

	Logger logger = Logger.getLogger( getClass().getName() );

	String getStorageNames( JobDefinition od ) {
		String correlation = od.getCorrelation();
		String storageNames = "?";

		if ( !StringUtils.isEmpty( correlation ) && !correlation.equals( "null" ) ) {
			JsonReader reader = Json.createReader( new StringReader( correlation ) );
			JsonObject object = reader.readObject();
			if ( object.containsKey( "businessObject" ) ) {
				JsonObject ob = object.getJsonObject( "businessObject" );
				if ( ob.containsKey( "storageNames" ) ) {
					JsonArray a = ob.getJsonArray( "storageNames" );
					storageNames = "";
					for ( int i = 0; i < a.size(); i++ ) {
						storageNames += a.getString( i );
						if ( i != a.size() - 1 ) {
							storageNames += ",";
						}
					}
				}
			}
		}
		return storageNames;
	}

	boolean collectStats( JobDefinition od ) {
		boolean collectStats = true; //default to true
		String correlation = od.getCorrelation();
		if ( !StringUtils.isEmpty( correlation ) && !correlation.equals( "null" ) ) {
			JsonReader reader = Json.createReader( new StringReader( correlation ) );
			JsonObject object = reader.readObject();
			if ( object.containsKey( "processing" ) ) {
				JsonObject ob = object.getJsonObject( "processing" );
				if ( ob.containsKey( "collectStatistics" ) ) {
					String s = ob.getString( "collectStatistics" );
					if ( "NONE".equalsIgnoreCase( s ) ) {
						collectStats = false;
					}
				}
			}
		}
		return collectStats;
	}

	boolean runProcess( JobDefinition od, String clusterID, String workerID ) {

		ExecutorService es = Executors.newFixedThreadPool( 2 );
		try {
			Future<Process> future = es.submit( () -> {
				ProcessBuilder pb = createProcessBuilder( od );
				if ( pb != null ) {
					logger.info( "Start Process " + pb.command() );
					pb.redirectErrorStream( true );
					return pb.start();
				}
				return null;
			} );

			Process p = null;

			while ( !future.isDone() ) {
				try {
					p = future.get( 60, TimeUnit.SECONDS );
					break;

				} catch ( TimeoutException ex ) {
					logger.info( "Extending lock for object 1" + od.getObjectDefinition() );
					jobPicker.extendLock( od, clusterID, workerID );
				} catch ( Exception ex ) {
					errorBuffer.append( ex.getMessage() );
					return false;
				}
			}
			final Process process = p;
			if ( process == null ) return false;
			es.submit( (Runnable) () -> {
				BufferedReader in = new BufferedReader( new InputStreamReader( process.getInputStream() ) );

				try {
					String line = in.readLine();
					while ( line != null ) {
						errorBuffer.append( line + "\n" );
						line = in.readLine();
					}
					in.close();
				} catch ( Exception ex ) {
					logger.severe( ex.getMessage() );
				} finally {
					try {
						in.close();
					} catch ( IOException e ) {
						e.printStackTrace();
					}
				}
			} );

			while ( process.isAlive() ) {
				try {
					process.waitFor( 1, TimeUnit.MINUTES );
				} catch ( InterruptedException ex ) {

				}
				if ( process.isAlive() ) {
					logger.info( "Extending lock for object " + od.getObjectDefinition() );
					jobPicker.extendLock( od, clusterID, workerID );
				}
			}
			int exit = process.exitValue();
			logger.info( "Complete with exit code " + exit );
			if ( exit != 0 )
				System.err.print( errorBuffer.toString() );
			return (exit == 0);
		} finally {
			es.shutdown();
		}
	}

	public String getLastErrorString() {
		return errorBuffer.toString();
	}

	protected String quotedPartitionKeys( Schema schema ) {
		return schema.getPartitions().stream().map( p -> p.getName() ).collect( Collectors.joining( "`,`", "`", "`" ) );
	}
}

