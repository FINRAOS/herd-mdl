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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.ClusterState;
import com.amazonaws.services.elasticmapreduce.model.DescribeClusterRequest;
import com.amazonaws.services.elasticmapreduce.model.DescribeClusterResult;
import org.finra.herd.sdk.api.EmrApi;
import org.finra.herd.sdk.invoker.ApiClient;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.EmrCluster;
import org.finra.herd.sdk.model.EmrClusterCreateRequest;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.PreDestroy;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Component
public class ClusterManager implements InitializingBean {

	public static final String CLUSTER_NM_DELIMITER = "_";
	public static final String REMOVE_CLUSTER = "DELETE FROM EMR_CLUSTER where CLUSTER_ID = ?";

	@Autowired
    JdbcTemplate template;

    @Autowired
    ApiClient dmRestClient;

    String clusterName;
    String clusterID;

    @Value("${JOBFLOW_JSON_PATH}")
    String jobFlowJson;

    @Value("${MAX_RETRY}")
    int maxRetry;

    @Value("${TOTAL_PARTITION_THRESHOLD}")
    int totalPartitionThreshold = 100;

    @Value("${SINGLE_OBJECT_PARTITION_THRESHOLD}")
    int singleObjPartitionThreshold = 50;

    @Value("${PARTITION_AGE_THRESHOLD_IN_HOURS}")
    int ageThreshold = 10; //Hours

    @Value("${MAX_CLUSTER}")
    int maxCluster = 10;

    @Value("${AUTO_SCALE_INTERVAL_IN_MINUTES}")
    int autoScaleIntervalInMin=10;

    @Value("${MAX_CLUSTER_TO_START}")
    int maxClusterToStart=2;

    @Value("${CLUSTER_DEF_NAME}")
    String clusterDef;

	@Value( "${CREATE_CLUSTER_RETRY_COUNT}" )
	int createClusterMaxRetryCount = 5;

    @Value("${AGS}")
    private String ags;

	@Autowired
	NotificationSender notificationSender;

	int createClusterRetryCounter = 0;

	Set<String> errors = new HashSet<String>( 5 );

    Logger logger = Logger.getLogger("ClusterManager");

    public static final String FIND_JOB_QUERY = "SELECT n.*, CASE WHEN l.count is null THEN 0 ELSE l.count END as c FROM (DM_NOTIFICATION n left outer join " +
            "(SELECT NOTIFICATION_ID, group_concat(success) as success, count(*) as count from METASTOR_PROCESSING_LOG " +
            "group by NOTIFICATION_ID) l on l.NOTIFICATION_ID=n.ID) " +
            "where WF_TYPE != 3 and ( l.success is null or (l.success like '%N' and l.count<? ))";

    public static final String JOB_GROUP_QUERY = "select NAMESPACE, OBJECT_DEF_NAME, USAGE_CODE, FILE_TYPE, count(*) as count, " +
            "min(DATE_CREATED) as oldest from (" + FIND_JOB_QUERY + ") as t GROUP BY NAMESPACE, OBJECT_DEF_NAME, USAGE_CODE, FILE_TYPE";

    public static final String AUTO_SCALE_QUERY = "select ID, TIMESTAMPDIFF(MINUTE, PROCESSED_DT, now()) as age from METASTOR_EMR_AUTOSCALE order by ID DESC limit 1;";

    public static final String SELECT_CLUSTER_ID_SQL = "select CLUSTER_ID from EMR_CLUSTER where CLUSTER_NAME = ?";


    private AmazonElasticMapReduce emrClient;

	private ScheduledExecutorService es = Executors.newScheduledThreadPool(1);


    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    void setTemplate(JdbcTemplate template) {
        this.template = template;
    }

    public String getClusterID() {
        return clusterID;
    }

    public void setClusterID(String clusterID) {
        this.clusterID = clusterID;
    }

    AmazonElasticMapReduce getEmrClient() {
        return emrClient;
    }

    void setEmrClient(AmazonElasticMapReduceClient emrClient) {
        this.emrClient = emrClient;
    }

	public void setDmRestClient( ApiClient dmRestClient ) {
		this.dmRestClient = dmRestClient;
	}

	private void incrementCreateClusterRetryCounter(){
		createClusterRetryCounter++;
	}

	private void resetCreateClusterRetryCounter(){
		createClusterRetryCounter = 0;
	}

	public boolean registerCluster() {
        String sql = "INSERT IGNORE INTO `EMR_CLUSTER` (`CLUSTER_NAME`,`CLUSTER_ID`, CREATE_TIME, STATUS) VALUES (?, ?, now(), 'R')";

        template.update(sql, clusterName, clusterID);

        String runningClusterID = template.queryForObject(SELECT_CLUSTER_ID_SQL, new Object[]{clusterName}, String.class);
        boolean continueProcess = false;

        if (!runningClusterID.equals(clusterID)) {
            //Verify the running cluster is still up

            DescribeClusterRequest req = new DescribeClusterRequest().withClusterId(runningClusterID);
            DescribeClusterResult result = emrClient.describeCluster(req);

            String clusterState = result.getCluster().getStatus().getState();

            if (clusterState.equals(ClusterState.TERMINATED.toString()) || clusterState.equals(ClusterState.TERMINATED_WITH_ERRORS.toString())) {
                continueProcess = prepareProcessing();
            } else if (clusterState.equals(ClusterState.TERMINATING.toString())) {
                //Wait till cluster is terminated
                logger.info(String.format("Running Cluster %s is in TERMINCATING state, waiting for it to be terminated", runningClusterID));

                do {
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ex) {

                    }
                    result = emrClient.describeCluster(req);
                    clusterState = result.getCluster().getStatus().getState();
                } while (clusterState.equals(ClusterState.TERMINATING.toString()));

                if (clusterState.equals(ClusterState.TERMINATED.toString()) || clusterState.equals(ClusterState.TERMINATED_WITH_ERRORS)) {
                    continueProcess = prepareProcessing();
                }

            } else {
                logger.info("Running cluster is " + runningClusterID + ", exiting");
            }

        } else {
            continueProcess = true;
        }

        return continueProcess;
    }

    boolean prepareProcessing() {

        template.update("update EMR_CLUSTER set CLUSTER_ID=?, CREATE_TIME=now() Where CLUSTER_NAME=?", clusterID, clusterName);

        //Double check if this is the cluster that win
        String runningClusterID = template.queryForObject(SELECT_CLUSTER_ID_SQL, new Object[]{clusterName}, String.class);

        if (!runningClusterID.equals(clusterID)) {
            logger.info("Running cluster is " + runningClusterID + ", exiting");
        }
        return runningClusterID.equals(clusterID);
    }

    /**
     * Clean up before terminates.
     */
    @PreDestroy
    public void deleteCluster()
    {
        template.update(REMOVE_CLUSTER, this.clusterID);
		logger.info( this.clusterID + " deleted from running cluster list before destroy" );

        shutdownAutoscaleService( 1 );
	}

	void shutdownAutoscaleService(int counter){
		if ( counter >= 5 ) {
			logger.info("Max Retries reached, might have to manually terminate cluster!");
			return;
		}

		es.shutdownNow();

		try {
			es.awaitTermination( 5, TimeUnit.SECONDS );
		} catch ( InterruptedException e ) {
			logger.info( "Autoscale thread shutting down, wait interrupted" );
		}finally {
			if(es.isTerminated()){
				logger.info( "Autoscale service shutdown successfully!" );
			}else{
				logger.info( "Autoscale service is still running, trying again to shutdown" );
				counter++;
				logger.info( "Retry #: " + counter );
				shutdownAutoscaleService( counter );
			}
		}
	}

    public void deleteCluster(String clusterIDToBeDeleted)
    {
        template.update(REMOVE_CLUSTER, clusterIDToBeDeleted);
        logger.info( clusterIDToBeDeleted + " deleted from running cluster list via autoscale" );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        createEmrClient();

        String emrInfo = new String(Files.readAllBytes(Paths.get(jobFlowJson)));
        JsonReader reader = Json.createReader(new StringReader(emrInfo));
        JsonObject object = reader.readObject();
        if(object.containsKey("jobFlowId"))
        {
            setClusterID(object.getString("jobFlowId"));
            logger.info("Cluster ID = "+ clusterID);
            DescribeClusterRequest req = new DescribeClusterRequest().withClusterId(clusterID);
            DescribeClusterResult result = emrClient.describeCluster(req);
            String name = result.getCluster().getName();
            logger.info("Cluster Name = "+ name);
            setClusterName(name.substring(name.lastIndexOf(".")+1));
        }

    }

    AmazonElasticMapReduce createEmrClient() {
        DefaultAWSCredentialsProviderChain defaultAWSCredentialsProviderChain = new DefaultAWSCredentialsProviderChain();
        AWSCredentials credentials = defaultAWSCredentialsProviderChain.getCredentials();
        emrClient =  AmazonElasticMapReduceClientBuilder.standard()
				.withCredentials(new AWSStaticCredentialsProvider(credentials))
				.build();
        return emrClient;
    }

    void setupClusterAutoscaleSchedule() {
		Runnable task = () -> clusterAutoScale();
		es.scheduleWithFixedDelay(task, 0, autoScaleIntervalInMin, TimeUnit.MINUTES);
    }

    int calculateNumberOfClustersNeeded() {
        List<Map<String, Object>> result = template.queryForList(JOB_GROUP_QUERY, maxRetry);
        int clusterNum = 0;
        if(result.size() > 1)
        {
            clusterNum = 1; //start from 1
        }

        int totalPartition = 0;
        long current = template.queryForObject("select UNIX_TIMESTAMP()", Long.class);
        boolean ageIncrement = false;

        for (Map<String, Object> record : result) {
            int partCount = ((Long) record.get("count")).intValue();
            Date timeCreated = (Date) record.get("oldest");

            long age = (current - timeCreated.getTime()/1000) / 3600;
            if (partCount > singleObjPartitionThreshold)
            {
                clusterNum++;
            } else if ((age > ageThreshold) && (!ageIncrement)) {
                clusterNum++;
                ageIncrement = true;
            }
            else {
                totalPartition += partCount;

                if (totalPartition > totalPartitionThreshold) {
                    clusterNum++;
                    totalPartition = 0;

                }
            }
        }

        if (clusterNum == 0) clusterNum = 1;
        if(clusterNum > maxCluster) clusterNum=maxCluster;
        return clusterNum;
    }


    public void clusterAutoScale()
    {
        logger.info("Auto Scale Job started");
        Map<String, Object> lastCheck = template.queryForMap(AUTO_SCALE_QUERY);
        long minutes = (Long) lastCheck.get("age");
        if(minutes >= autoScaleIntervalInMin) {
            long id = (Long)lastCheck.get("ID") + 1;
            int updated = template.update("INSERT IGNORE INTO METASTOR_EMR_AUTOSCALE (`ID`," +
                    "`CLUSTER_ID`) VALUES (?, ?)", id, clusterID);

            if (updated > 0) {

                int clusterNumber = calculateNumberOfClustersNeeded();
                template.update("UPDATE METASTOR_EMR_AUTOSCALE SET TOTAL_CLUSTER= ? WHERE ID=?", clusterNumber, id);
                if (clusterNumber <= 1) return;

                List<Map<String, Object>> clusters = template.queryForList("select * from EMR_CLUSTER");
                List<String> existingCluster = new ArrayList<>();

                createEmrClient();
                for (Map<String, Object> record : clusters) {
                    String cid = (String) record.get("CLUSTER_ID");
                    String cname = (String) record.get("CLUSTER_NAME");

                    boolean clusterIsAlive = true;

                    try {

                        DescribeClusterRequest req = new DescribeClusterRequest().withClusterId(cid);
                        DescribeClusterResult result = emrClient.describeCluster(req);

                        String clusterState = result.getCluster().getStatus().getState();
                        if (clusterState.equals(ClusterState.TERMINATED.toString()) || clusterState.equals(ClusterState.TERMINATED_WITH_ERRORS.toString())) {
                            deleteCluster(cid);
                            clusterIsAlive = false;
                        }
                    } catch (Exception ex) {
                        logger.warning("Error getting info for cluster " + cid + ex.getMessage());
                        clusterIsAlive = false;
                    }


                    if (clusterIsAlive) {
                        existingCluster.add(cname);
                    }

                }

                // Start Additional if required
				startAdditionalClusters( clusterNumber, existingCluster );
			}
        }
		logger.info("Auto Scale Job completed");

    }

	public void startAdditionalClusters( int clusterNumber, List<String> existingCluster ) {
		if (existingCluster.size() < clusterNumber) {
			int clusterToCreate = clusterNumber - existingCluster.size();

			if ( clusterToCreate > maxClusterToStart ) clusterToCreate = maxClusterToStart;
			logger.info("Creating clusters, total number to create = " + clusterToCreate);

			EmrApi emrApi = new EmrApi(dmRestClient);

			// Create new additional cluster
			String proposedName = "";
			for ( int i = 1, created = 0; created < clusterToCreate; i++ ) {
				proposedName = proposedName( i );
				if ( !existingCluster.contains( proposedName ) ) {
					logger.info( "Creating cluster: " + proposedName );
					createAdditionalCluster( proposedName, emrApi, existingCluster );
					created++;
				}
			}

			resetCreateClusterRetryCounter();
		}
	}

	private void createAdditionalCluster( String proposedName, EmrApi emrApi, List<String> existingCluster ) {

		try {
			if ( maxRetriesNotReached() ) {
				try {
					// Call to Herd to create cluster
					createCluster( emrApi, proposedName );
					existingCluster.add( proposedName );

					Thread.sleep(500);
				} catch ( Exception ex ) {
					logger.severe( "Error calling Herd rest: " + ex.getMessage() );
					try {
						errors.add( ex.getMessage() );
						// increment retry counter
						incrementCreateClusterRetryCounter();
						Thread.sleep( 30000 );
					} catch ( InterruptedException e ) {
						logger.warning( "Error while waiting for retry: " + e.getMessage() );
					}

					// Try again to create cluster
					createAdditionalCluster( proposedName, emrApi, existingCluster );
				}
			} else {
				String message = String.format( "Create Cluster Retries reached max limit of %d for proposed cluster name: %s"
						, createClusterMaxRetryCount
						, proposedName );
				resetCreateClusterRetryCounter();
				sendNotificationEmail(proposedName);
				throw new RuntimeException( message );
			}
		} catch ( Exception ex ) {
			logger.severe( ex.getMessage() );
		}
	}

	private void sendNotificationEmail( String proposedName ) {
		String subject = String.format( "Create Cluster Failed for Cluster Name - %s", proposedName );
		String msgBody = errors.toString().replace("[","").replace("]","");
		notificationSender.sendEmail( msgBody, subject );
		errors.clear();
	}

	private boolean maxRetriesNotReached() {
		return (createClusterRetryCounter < createClusterMaxRetryCount);
	}

	private void createCluster( EmrApi emrApi, String proposedName ) throws ApiException {
		EmrClusterCreateRequest request = new EmrClusterCreateRequest();
		request.setNamespace( ags );
		request.setDryRun(false);
		request.setEmrClusterDefinitionName(clusterDef);
		request.setEmrClusterName(proposedName);

		EmrCluster cluster = emrApi.eMRCreateEmrCluster(request);
		logger.info(cluster.toString());
	}

	private String proposedName( int created ) {
		return new StringBuilder( ags )
				.append( CLUSTER_NM_DELIMITER )
				.append( created )
				.toString();
	}



}
