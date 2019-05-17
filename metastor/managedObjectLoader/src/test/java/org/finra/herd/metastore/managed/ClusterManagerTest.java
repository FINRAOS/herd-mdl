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

import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient;
import com.amazonaws.services.elasticmapreduce.model.*;
import com.sun.jersey.api.client.GenericType;
import org.finra.herd.sdk.invoker.ApiClient;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.EmrCluster;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = HerdMetastoreTest.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ClusterManagerTest {

    @Resource
    JdbcTemplate template;

    @Autowired
    ApiClient dmApiClient;

    @Test
    public void testNewRegisterCluster()
    {
        ClusterManager processor = new ClusterManager();

        processor.setClusterID("abcd");
        processor.setClusterName("test name");

        JdbcTemplate template = Mockito.mock(JdbcTemplate.class);

        when(template.queryForObject(ObjectProcessor.SELECT_CLUSTER_ID_SQL, new Object[]{"test name"}, String.class)).thenReturn("abcd");

        processor.setTemplate(template);
        assertEquals(processor.registerCluster(), true);
    }

    @Test
    public void testTerminatedRunningCluster()
    {
        ClusterManager processor = new ClusterManager();

        processor.setClusterID("abcd");
        processor.setClusterName("test name");

        JdbcTemplate template = Mockito.mock(JdbcTemplate.class);
        AmazonElasticMapReduceClient elasticMapReduceClient = Mockito.mock(AmazonElasticMapReduceClient.class);
        DescribeClusterResult describeClusterResult = Mockito.mock(DescribeClusterResult.class);
        Cluster cluster=Mockito.mock(Cluster.class);

        when(template.queryForObject(ObjectProcessor.SELECT_CLUSTER_ID_SQL, new Object[]{"test name"}, String.class)).thenReturn("running_cluster").thenReturn("abcd");
        when(elasticMapReduceClient.describeCluster(any(DescribeClusterRequest.class))).thenReturn(describeClusterResult);

        when(describeClusterResult.getCluster()).thenReturn(cluster);
        ClusterStatus clusterStatus = new ClusterStatus();
        clusterStatus.setState(ClusterState.TERMINATED_WITH_ERRORS);
        when(cluster.getStatus()).thenReturn(clusterStatus);

        processor.setTemplate(template);
        processor.setEmrClient(elasticMapReduceClient);
        assertEquals(true, processor.registerCluster());
    }

    @Test
    @Transactional
    public void testAutoScale() throws Exception
    {
        ClusterManager clusterManager = new ClusterManager();
        clusterManager.setTemplate(template);

        String sql = new String(Files.readAllBytes(Paths.get("src/test/resources/dbunit/cluster_dataset.sql")));


        template.update(sql);
        sql = "INSERT INTO EMR_CLUSTER (CLUSTER_NAME,CLUSTER_ID, CREATE_TIME, STATUS) VALUES ('A', 'B', now(), 'R');";
        template.update(sql);

        assertEquals(2, clusterManager.calculateNumberOfClustersNeeded());

        AmazonElasticMapReduceClient emrClient = Mockito.mock(AmazonElasticMapReduceClient.class);
        clusterManager.setEmrClient(emrClient);

        clusterManager.dmRestClient = dmApiClient;

//        clusterManager.clusterAutoScale();
//        verify(restClient).performXmlPost(any(String.class), any(String.class));
    }

    @Test
    public void testScheduler() throws Exception
    {
        ClusterManager clusterManager = new ClusterManager();
        clusterManager.setupClusterAutoscaleSchedule();
    }

    @Test
    @Transactional
    public void testMaxCluster() throws Exception
    {
        ClusterManager clusterManager = new ClusterManager();
        clusterManager.setTemplate(template);

        String sql = new String(Files.readAllBytes(Paths.get("src/test/resources/dbunit/cluster_dataset.sql")));


        template.update(sql);
        clusterManager.maxCluster=1;
        assertEquals(1, clusterManager.calculateNumberOfClustersNeeded());


    }

    @Test
    public void testStartAdditionalClusterCreated() throws ApiException {
		ClusterManager clusterManager = new ClusterManager();
		final String[] accepts = {"application/xml", "application/json"};

		String[] authNames = new String[] { "basicAuthentication" };



		ApiClient dmApiClient = Mockito.mock( ApiClient.class );
		clusterManager.setDmRestClient( dmApiClient );

		when( dmApiClient.selectHeaderAccept( accepts ) ).thenReturn( "application/json" );
		when( dmApiClient.selectHeaderContentType( accepts ) ).thenReturn( "application/json" );
		when( dmApiClient.invokeAPI( anyString(), anyString(), anyList(), anyList(), anyObject(), anyMap(), anyMap()
				, anyString(), anyString(), eq(authNames),any(GenericType.class) ) )
				.thenReturn( new EmrCluster() );

		List<String> existingCluster = new ArrayList<>();
		clusterManager.startAdditionalClusters( 1, existingCluster );

		assertEquals( 1, existingCluster.size());
		assertEquals( "METASTOR_1", existingCluster.get( 0 ) );
	}

	@Test
	public void testStartAdditionalClusterNotCreatedMaxRetryReached() throws ApiException {
		ClusterManager clusterManager = new ClusterManager();

		ArrayList<String> existingCluster = new ArrayList<>();
		clusterManager.startAdditionalClusters( 1, existingCluster );

		assertEquals( 0, existingCluster.size() );
	}

}
