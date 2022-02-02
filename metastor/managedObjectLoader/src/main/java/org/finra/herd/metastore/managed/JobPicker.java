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

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.jobProcessor.dao.MetastorObjectLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

@Component
@Slf4j
public class JobPicker {
	Logger logger = Logger.getLogger( "JobPicker" );

	public static final String FIND_UNLOCKED_JOB_QUERY = "SELECT n.*, CASE WHEN l.count is null THEN 0 ELSE l.count END as c, PRIORITY" +
			" FROM (DM_NOTIFICATION n left outer join " +
			"(SELECT NOTIFICATION_ID, group_concat(success) as success, count(*) as count, max(DATE_PROCESSED) as last_process from METASTOR_PROCESSING_LOG " +
			"group by NOTIFICATION_ID) l on l.NOTIFICATION_ID=n.ID) left outer join METASTOR_WORKFLOW m on WF_TYPE=m.WORKFLOW_ID " +
			"where WF_TYPE NOT IN (3,5) and ( l.success is null or (l.success not like '%Y' and l.count<? and TIMESTAMPDIFF(SECOND, l.last_process, now())>? )) and  NOT EXISTS (select * from " +
			"METASTOR_OBJECT_LOCKS lc where lc.NAMESPACE=n.NAMESPACE and lc.OBJ_NAME=n.OBJECT_DEF_NAME and lc.USAGE_CODE=n.USAGE_CODE and" +
			" lc.FILE_TYPE=n.FILE_TYPE and CLUSTER_ID !=? and  lc.WF_TYPE NOT IN(3,5))  ORDER BY PRIORITY ASC, PARTITION_VALUES DESC";


    public static final String FIND_UNLOCKED_STATS_JOB_QUERY = "SELECT n.*, CASE WHEN l.count is null THEN 0 ELSE l.count END as c, PRIORITY" +
        " FROM (DM_NOTIFICATION n left outer join " +
        "(SELECT NOTIFICATION_ID, group_concat(success) as success, count(*) as count, max(DATE_PROCESSED) as last_process from METASTOR_PROCESSING_LOG " +
        "group by NOTIFICATION_ID) l on l.NOTIFICATION_ID=n.ID) left outer join METASTOR_WORKFLOW m on WF_TYPE=m.WORKFLOW_ID " +
        "where WF_TYPE = 5  and ( l.success is null or (l.success not like '%Y' and l.count<? and TIMESTAMPDIFF(SECOND, l.last_process, now())>? )) and  NOT EXISTS (select * from " +
        "METASTOR_OBJECT_LOCKS lc where lc.NAMESPACE=n.NAMESPACE and lc.OBJ_NAME=n.OBJECT_DEF_NAME and lc.USAGE_CODE=n.USAGE_CODE and" +
        " lc.FILE_TYPE=n.FILE_TYPE and CLUSTER_ID !=? and lc.WF_TYPE = 5 )  ORDER BY PRIORITY ASC, PARTITION_VALUES DESC";



    static final String DELETE_EXPIRED_LOCKS = "delete from METASTOR_OBJECT_LOCKS where EXPIRATION_DT < now()";
	static final String DISPLAY_EXPIRED_LOCKS = "select * from METASTOR_OBJECT_LOCKS where EXPIRATION_DT < now()";

	static final String LOCK_QUERY = "insert ignore into METASTOR_OBJECT_LOCKS (NAMESPACE,\n" +
			"OBJ_NAME, USAGE_CODE,\n" +
			"FILE_TYPE,\n" +
			"CLUSTER_ID,\n" +
			"WORKER_ID,\n" +
            "WF_TYPE,\n" +
			"EXPIRATION_DT) VALUES (?,?,?,?,?,?,?, TIMESTAMPADD(MINUTE, 5, now()));";

	static final String FIND_LOCK = "SELECT * FROM METASTOR_OBJECT_LOCKS WHERE NAMESPACE=? and OBJ_NAME=? and USAGE_CODE=? and " +
			"FILE_TYPE=? and CLUSTER_ID=? and WORKER_ID=? and WF_TYPE=?";

	static final String UPDATE_LOCK_EXPIRATION = "UPDATE METASTOR_OBJECT_LOCKS SET EXPIRATION_DT=TIMESTAMPADD(MINUTE, 5, now())  " +
			"WHERE NAMESPACE=? and OBJ_NAME=? and USAGE_CODE=? and FILE_TYPE=? and CLUSTER_ID=? and WORKER_ID=? and WF_TYPE=?";

	static final String UNLOCK = "delete from METASTOR_OBJECT_LOCKS where CLUSTER_ID=? and WORKER_ID=?";

	static final String DELETE_NOT_PROCESSING_NOTIFICATIONS = "DELETE FROM DM_NOTIFICATION WHERE WF_TYPE IN (3, 33)";


	@Autowired
	JdbcTemplate template;

	@Value( "${MAX_RETRY}" )
	int maxRetry;

	@Value( "${RETRY_INTERVAL}" )
	int jobRetryIntervalInSecs;


    @Autowired
    boolean analyzeStats;

	List<JobDefinition> findJob( String clusterID, String workerID ) {
		List<JobDefinition> jobs = new ArrayList<JobDefinition>();

		try {
			deleteNotProcessingNotifications();
			deleteExpiredLocks();
            List<JobDefinition> result ;
            logger.info("Get Stats: " + analyzeStats);
			if ( analyzeStats ) {
				logger.info( "Running for stats" );
				result = template.query(
						FIND_UNLOCKED_STATS_JOB_QUERY, new Object[]{maxRetry, jobRetryIntervalInSecs, clusterID},
						new JobDefinition.ObjectDefinitionMapper() );
			} else {
				result = template.query(
						FIND_UNLOCKED_JOB_QUERY, new Object[]{maxRetry, jobRetryIntervalInSecs, clusterID},
						new JobDefinition.ObjectDefinitionMapper() );
			}
			//Locking
			//1. Delete expired locks
			ObjectDefinition lockedJd = null;

			String threadId = workerID;

			for ( JobDefinition jd : result ) {

				if ( lockedJd == null ) {
					if ( lockObj( jd, clusterID, threadId ) ) {
						lockedJd = jd.getObjectDefinition();
						jobs.add( jd );
					}

				} else {
					if ( jd.objectDefinition.equals( lockedJd ) ) {
						jobs.add( jd );
					} else {
						break;
					}
				}
			}
		} catch ( Exception ex ) {
			logger.severe( "Exception occurred when looking for jobs" );
			ex.printStackTrace();
		}
		return jobs;
	}

	/**
	 * This deletes the notifications, which are marked as
	 * 3 - object notification disabled
	 * 33 - Business data object status marking notifications which will be excluded from Metastore processing due intermediate processing status
	 * */
	private void deleteNotProcessingNotifications() {
		int numberOfRowsDeleted = template.update( DELETE_NOT_PROCESSING_NOTIFICATIONS );
		logger.info( "Number of Not processing Notifications Deleted = " + numberOfRowsDeleted );
	}

	void deleteExpiredLocks() {
		int numberOfRowsDeleted = template.update( DELETE_EXPIRED_LOCKS );
		List<MetastorObjectLock> displayList=template.query(DISPLAY_EXPIRED_LOCKS, new MetastorObjectLock.MetastorObjectLockMapper());
		logger.info("LOCKS TO BE DELETED ==> "+displayList);
		logger.info( "Number of Locks Deleted = " + numberOfRowsDeleted );
	}

	public boolean lockObj( JobDefinition jd, String clusterID, String threadID ) {
		String objectName = jd.getActualObjectName();

		ObjectDefinition od = jd.getObjectDefinition();
		if ( template.queryForList( FIND_LOCK, od.getNameSpace(), objectName,
				od.getUsageCode(), od.getFileType(), clusterID, threadID,jd.getWfType() ).size() > 0 ) {
			return extendLock( jd, clusterID, threadID );
		} else {
			unlockWorker( clusterID, threadID );
			int updated = template.update( LOCK_QUERY, od.getNameSpace(), objectName,
					od.getUsageCode(), od.getFileType(), clusterID, threadID, jd.getWfType() );
			return updated == 1;
		}
	}

	public void unlockWorker( String clusterID, String threadID ) {
		template.update( UNLOCK, clusterID, threadID );
	}


	public boolean extendLock( JobDefinition jd, String clusterID, String workerID ) {
		ObjectDefinition od = jd.getObjectDefinition();
		String objectName = jd.getActualObjectName();
		log.info("Going to extend lock for Object {} , Cluster {}, Worker Id {}",jd,clusterID,workerID);

		int updated = template.update( UPDATE_LOCK_EXPIRATION, od.getNameSpace(), objectName, od.getUsageCode(),
				od.getFileType(), clusterID, workerID ,jd.getWfType());
		return updated > 0;
	}

}
