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

import org.finra.herd.metastore.managed.jobProcessor.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Component
public class ObjectProcessor {
	public static final String SELECT_CLUSTER_ID_SQL = "select CLUSTER_ID from EMR_CLUSTER where CLUSTER_NAME = ?";

	Logger logger = Logger.getLogger( "ObjectProcessor" );

	@Value( "${EMR_IDLE_TIME_OUT}" )
	int IDLE_TIME_OUT = 5;

	@Value( "${MAX_RETRY}" )
	int maxRetry;

	@Autowired
	private JdbcTemplate template;

	@Autowired
	JobPicker jobPicker;

	@Autowired
	private ManagedJobProcessor managedJobProcessor;

	@Autowired
	private ManagedObjStatsProcessor managedObjStatsProcessor;

	@Autowired
	private DropObjectProcessor dropObjectProcessor;

	@Autowired
	private BackLoadObjectProcessor backLoadObjectProcessor;

	@Autowired
	private FormatObjectProcessor formatObjectProcessor;

	@Autowired
	private RenameObjectProcessor renameObjectProcessor;

	@Autowired
	protected NotificationSender notificationSender;

	@Autowired
	ClusterManager clusterManager;


	long reEvaluateTimeOutinMsec = 600000;
	long retryInterval = 1;

	public static final int WF_TYPE_MANAGED = 0;
	public static final int WF_TYPE_SINGLETON = 1;
	public static final int WF_TYPE_MANAGED_STATS = 5;
	public static final int WF_TYPE_FORMAT = 17;
	public static final int WF_TYPE_DROP_TABLE = 7;
	public static final int WF_TYPE_BACK_LOAD_PARTITIONS = 8;
	public static final int WF_TYPE_RENAME_OBJECT = 11;

	private final long startTime = System.currentTimeMillis();


	public static final String INSERT_PROCESS_SQL = "INSERT INTO METASTOR_PROCESSING_LOG (NOTIFICATION_ID, CLUSTER_ID) VALUES (?,?)";

	public static final String UPDATE_PROCESS_SQL = "UPDATE METASTOR_PROCESSING_LOG SET END_TIME = now(), SUCCESS=? where ID=?";

    private static final String PURGE_PROCESSED_NOTIFICAITON = "DELETE FROM DM_NOTIFICATION WHERE ID=?";

	public void setTemplate( JdbcTemplate template ) {
		this.template = template;
	}

	public ObjectProcessor() {

	}

	private String workerID;

	public void runJobs() {
		try {
			if ( clusterManager.registerCluster() ) {
				logger.info( "Start processing" );

				clusterManager.setupClusterAutoscaleSchedule();
				workerID = String.valueOf( Thread.currentThread().getId() );
				int retry = 0;
				while ( true ) {
					List<JobDefinition> jobDefinitions = jobPicker.findJob( clusterManager.getClusterID(), workerID );

					if ( jobDefinitions.isEmpty() ) {
						long time = System.currentTimeMillis();

						if ( retry >= IDLE_TIME_OUT ) {
							break;
						} else {

							try {
								TimeUnit.MINUTES.sleep( retryInterval );
								retry++;
								logger.info( "Wait for more notifications times " + retry );

							} catch ( InterruptedException e ) {
								e.printStackTrace();
								retry++;
							}
						}
					} else {
						retry = 0;
					}

					long start = System.currentTimeMillis();
					for ( JobDefinition jobDefinition : jobDefinitions ) {

						if ( jobPicker.extendLock( jobDefinition, clusterManager.getClusterID(),
								workerID ) ) {
							processObject( jobDefinition );

							long currentTime = System.currentTimeMillis();
							if ( (currentTime - start) > reEvaluateTimeOutinMsec ) {
								logger.info( "Re-evaluate the job list" );
								break;
							}
						} else {
							logger.info( String.format( "Extend lock failed for %s, %s, %s abandon job list",
									jobDefinition.getObjectDefinition().toString(),
									clusterManager.clusterID, workerID ) );
							break;
						}
					}
				}
			}

		} catch ( Exception ex ) {
			logger.severe( "Exception while processing job: " + ex.getMessage() );
			ex.printStackTrace();
		} finally {
			jobPicker.unlockWorker( clusterManager.getClusterID(), workerID );
			clusterManager.deleteCluster();
		}
		logger.info( "ALL JOB FINISHED, CLUSTER CAN TERMINATE NOW!!!" );
	}

	void processObject( final JobDefinition jobDefinition ) {
		logger.info( "Processing " + jobDefinition );

		JobProcessor jp = getJobProcessor( jobDefinition );
		boolean success = false;
		String error;

		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

		if ( jp != null ) {
			try {

				template.update( connection -> {
					PreparedStatement ps = connection.prepareStatement( INSERT_PROCESS_SQL, Statement.RETURN_GENERATED_KEYS );
					ps.setLong( 1, jobDefinition.getId() );
					ps.setString( 2, clusterManager.clusterID );
					return ps;
				}, keyHolder );
				success = jp.process( jobDefinition, clusterManager.clusterID, workerID );
				error = jp.getLastErrorString();
				template.update( UPDATE_PROCESS_SQL, success ? "Y" : "N", keyHolder.getKey().longValue() );
			} catch ( Exception ex ) {
				logger.severe( "Exception when processing job, " );
				ex.printStackTrace();
				error = "Exception when processing job, " + ex.getMessage();
			}
		} else {
			error = "Cannot find job processor";
		}

        if ( success ) {
            deleteProcessedNotificaiton( jobDefinition );
        } else if ( !success && (jobDefinition.numOfRetry >= (maxRetry - 1)) ) {
            logger.warning( "Retry maxed out, send email" );
			int numRetry = jobDefinition.numOfRetry + 1;

			deleteProcessedNotificaiton( jobDefinition );
			sendFailureEmail( jobDefinition, error, numRetry, clusterManager.getClusterID());

		}
	}

	protected void deleteProcessedNotificaiton(final JobDefinition jd){
        try {
            template.update( PURGE_PROCESSED_NOTIFICAITON, jd.getId() );
        } catch ( Exception ex ) {
            logger.warning("Error encountered while purge processed Notification: "+ ex.getMessage());
            ex.printStackTrace();
        }
    }

	protected void sendFailureEmail( JobDefinition jobDefinition, String error, int numRetry, String clusterID ) {
		notificationSender.sendFailureEmail( jobDefinition, numRetry, error, clusterID );
	}

	protected JobProcessor getJobProcessor( JobDefinition jobDefinition ) {
		JobProcessor jp = null;
		switch ( jobDefinition.getWfType() ) {
			case WF_TYPE_MANAGED:
				jp = managedJobProcessor;
				break;
			case WF_TYPE_SINGLETON:
				jp = managedJobProcessor;
				break;
			case WF_TYPE_MANAGED_STATS:
				jp = managedObjStatsProcessor;
				break;
			case WF_TYPE_DROP_TABLE:
				jp = dropObjectProcessor;
				break;
			case WF_TYPE_BACK_LOAD_PARTITIONS:
				jp = backLoadObjectProcessor;
				break;
			case WF_TYPE_FORMAT:
				jp= formatObjectProcessor;
				break;
			case WF_TYPE_RENAME_OBJECT:
				jp = renameObjectProcessor;
				break;
			default:
				logger.warning( "Cannot find job processor" );
				break;
		}
		return jp;
	}

}
