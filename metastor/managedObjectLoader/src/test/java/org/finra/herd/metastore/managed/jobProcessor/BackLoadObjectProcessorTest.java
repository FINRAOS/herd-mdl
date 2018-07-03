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
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.HerdMetastoreTest;
import org.finra.herd.metastore.managed.jobProcessor.dao.JobProcessorDAO;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.*;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@RunWith( SpringJUnit4ClassRunner.class )
@SpringBootTest( classes = HerdMetastoreTest.class )
@Slf4j
public class BackLoadObjectProcessorTest {
	public static final String PARTITION_KEY = "TRADE_DT";
	private DataMgmtSvc dataMgmtSvc;

	private JobProcessorDAO jobProcessorDAO;

	private BackLoadObjectProcessor backLoadObjectProcessor;

	private NotificationSender notificationSender;

	private JobDefinition jobDefinition;

	@Before
	public void setUp() throws Exception {
		dataMgmtSvc = Mockito.mock( DataMgmtSvc.class );
		jobProcessorDAO = Mockito.mock( JobProcessorDAO.class );
		notificationSender = Mockito.mock( NotificationSender.class );

		jobDefinition = new JobDefinition( 1L, "hub", "cqs_pbbo", "prc",
				"bz", "2016-08-12", "null", "111", PARTITION_KEY );

		when( dataMgmtSvc.getBOAllFormatVersions( eq( jobDefinition ), anyBoolean() ) ).thenReturn( businessObjectFormatVersions() );
		when( dataMgmtSvc.getBORegisteredNotification( eq(jobDefinition) ) ).thenReturn( businessObjectDataNotificationRegistrationKeys() );
		when( dataMgmtSvc.getBORegisteredNotificationDetails( any() ) ).thenReturn( notificationDetails() );

		BusinessObjectFormat businessObjectFormat = new BusinessObjectFormat();
		businessObjectFormat.setPartitionKey( PARTITION_KEY );
		Schema schema = new Schema();
		SchemaColumn schemaColumn = new SchemaColumn();
		schemaColumn.setName( PARTITION_KEY );
		schemaColumn.setType( "DATE" );
		schema.setPartitions( Lists.newArrayList(schemaColumn) );
		businessObjectFormat.setSchema( schema );
		when( dataMgmtSvc.getDMFormat( any() ) ).thenReturn( businessObjectFormat );

		doNothing().when( notificationSender ).sendNotificationEmail( any(),any(),eq(jobDefinition) );

		backLoadObjectProcessor = new BackLoadObjectProcessor();
		backLoadObjectProcessor.dataMgmtSvc = dataMgmtSvc;
		backLoadObjectProcessor.jobProcessorDAO = jobProcessorDAO;
		backLoadObjectProcessor.reqChunkSize = 100;
		backLoadObjectProcessor.notificationSender = notificationSender;

	}

	@After
	public void tearDown() throws Exception {
		dataMgmtSvc = null;
		jobProcessorDAO = null;
		backLoadObjectProcessor = null;
		jobDefinition = null;
	}

	@Test
	public void testProcessForSmallerDataSet() throws ApiException {
		BusinessObjectDataSearchResult businessObjectDataSearchResult = new BusinessObjectDataSearchResult();
		businessObjectDataSearchResult.setBusinessObjectDataElements( buildBOStatues() );
		when( dataMgmtSvc.searchBOData( any(), anyInt(), anyInt(), anyBoolean() ) ).thenReturn( businessObjectDataSearchResult );

		emptyResult();

		Assert.assertTrue( backLoadObjectProcessor.process( jobDefinition, "", "" ) );
	}

	private void emptyResult() throws ApiException {
		BusinessObjectDataSearchResult businessObjectDataSearchResult1 = new BusinessObjectDataSearchResult();
		businessObjectDataSearchResult1.setBusinessObjectDataElements( new ArrayList<BusinessObjectData>(  ) );
		when( dataMgmtSvc.searchBOData( any(), eq(2), anyInt(), anyBoolean() ) ).thenReturn( businessObjectDataSearchResult1 );
	}

	@Test
	public void testProcessForSubpartitions() throws ApiException {
		BusinessObjectDataSearchResult businessObjectDataSearchResult = new BusinessObjectDataSearchResult();
		businessObjectDataSearchResult.setBusinessObjectDataElements( buildBOStatues() );
		when( dataMgmtSvc.searchBOData( any(), anyInt(), anyInt(), anyBoolean() ) ).thenReturn( businessObjectDataSearchResult );

		emptyResult();

		Assert.assertTrue( backLoadObjectProcessor.process( jobDefinition, "", "" ) );
	}

	@Test
	public void testCreateProcessBiggerDataSet() throws ApiException {
		BusinessObjectDataSearchResult businessObjectDataSearchResult = new BusinessObjectDataSearchResult();
		businessObjectDataSearchResult.setBusinessObjectDataElements( businessObjectDataBiggerDataSet() );
		when( dataMgmtSvc.searchBOData( any(), anyInt(), anyInt(), anyBoolean() ) ).thenReturn( businessObjectDataSearchResult );

		emptyResult();

		Assert.assertTrue( backLoadObjectProcessor.process( jobDefinition, "", "" ) );
	}

	@Test
	public void testCreateProcessBiggerDataSetNonDates() throws ApiException {
		BusinessObjectDataSearchResult businessObjectDataSearchResult = new BusinessObjectDataSearchResult();
		businessObjectDataSearchResult.setBusinessObjectDataElements( buildBOBiggerDatSetNonDate() );
		when( dataMgmtSvc.searchBOData( any(), anyInt(), anyInt(), anyBoolean() ) ).thenReturn( businessObjectDataSearchResult );

		emptyResult();

		Assert.assertTrue( backLoadObjectProcessor.process( jobDefinition, "", "" ) );
	}

	private BusinessObjectDataNotificationRegistration notificationDetails() {
		JobAction jobAction = new JobAction();
		jobAction.setJobName( "addPartitionWorkflow" );

		BusinessObjectDataNotificationRegistration registration = new BusinessObjectDataNotificationRegistration();
		registration.setJobActions( Lists.newArrayList( jobAction ) );
		return registration;
	}

	private BusinessObjectDataNotificationRegistrationKeys businessObjectDataNotificationRegistrationKeys() {
		NotificationRegistrationKey notificationRegistrationKey = new NotificationRegistrationKey();
		notificationRegistrationKey.setNamespace( "METASTOR" );
		notificationRegistrationKey.setNotificationName( "METASTOR_TEST_OBJ_USAGE_TYPE" );

		BusinessObjectDataNotificationRegistrationKeys bodNotificationRegKeys = new BusinessObjectDataNotificationRegistrationKeys();
		bodNotificationRegKeys.setBusinessObjectDataNotificationRegistrationKeys( Lists.newArrayList( notificationRegistrationKey ) );
		return bodNotificationRegKeys;

	}

	private BusinessObjectFormatKeys businessObjectFormatVersions() {
		List<BusinessObjectFormatKey> formatKey = Lists.newArrayList();
		formatKey.add( getBOFormatKey( "BZ", 0 ) );
		formatKey.add( getBOFormatKey( "BZ", 1 ) );
		formatKey.add( getBOFormatKey( "BZ", 2 ) );
		formatKey.add( getBOFormatKey( "PRC", 0 ) );


		BusinessObjectFormatKeys formatKeys = new BusinessObjectFormatKeys();
		formatKeys.setBusinessObjectFormatKeys( formatKey );
		return formatKeys;
	}

	private List<BusinessObjectData> buildBOBiggerDatSetNonDate() {
		List<BusinessObjectData> data = Lists.newArrayList();

		IntStream.rangeClosed( 1, 2000 )
				.forEach( d -> data.add( getBOData( "BZ", String.format( "%d", d ), 0) ));

		return data;
	}

	private List<BusinessObjectData> businessObjectDataBiggerDataSet() {
		List<BusinessObjectData> data = Lists.newArrayList();
		IntStream.rangeClosed( 1, 6 )
				.forEach( m -> IntStream.rangeClosed( 1, 30 )
						.forEach( d -> data.add( getBOData( "BZ", String.format( "2016-%1$02d-%2$02d", m, d ), 0) )
						) );

		return data;
	}

	private BusinessObjectData getBOData( String fileType, String partitionValue, int formatVer ) {
		BusinessObjectData businessObjectData = new BusinessObjectData();
		businessObjectData.setPartitionValue( partitionValue );
		businessObjectData.setBusinessObjectFormatVersion(  formatVer);
		businessObjectData.setSubPartitionValues( Lists.newArrayList() );
		return businessObjectData;
	}

	private List<BusinessObjectData> buildBOStatues() {
		List<BusinessObjectData> dataKeys = Lists.newArrayList();

		List<String> subpartitions = IntStream.rangeClosed( 1, 50 )
				.mapToObj( Integer::toString )
				.collect( Collectors.toList() );

		BusinessObjectData data = getBOData( "BZ", "2016-08-27", 0 );
		data.setSubPartitionValues( subpartitions );
		dataKeys.add( data );

		data = getBOData( "BZ", "2016-08-28", 0 );
		data.setSubPartitionValues( subpartitions );
		dataKeys.add( data );

		data = getBOData( "BZ", "2016-08-29", 2 );
		data.setSubPartitionValues( subpartitions );
		dataKeys.add( data );

		return dataKeys;
	}

	private BusinessObjectFormatKey getBOFormatKey( String fileType, int formatVer ) {
		BusinessObjectFormatKey bofk = new BusinessObjectFormatKey();
		bofk.setNamespace( "HUB" );
		bofk.setBusinessObjectDefinitionName( "CQS_PBBO" );
		bofk.setBusinessObjectFormatUsage( "PRC" );
		bofk.setBusinessObjectFormatFileType( fileType );
		bofk.setBusinessObjectFormatVersion( formatVer );
		return bofk;
	}

}
