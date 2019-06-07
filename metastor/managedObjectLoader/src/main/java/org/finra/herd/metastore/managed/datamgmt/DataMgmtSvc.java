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
package org.finra.herd.metastore.managed.datamgmt;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.ObjectProcessor;
import org.finra.herd.sdk.api.BusinessObjectDataApi;
import org.finra.herd.sdk.api.BusinessObjectDataNotificationRegistrationApi;
import org.finra.herd.sdk.api.BusinessObjectFormatApi;
import org.finra.herd.sdk.invoker.ApiClient;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

/**
 * Herd Client
 */
@Component
@Slf4j
public class DataMgmtSvc {

    @Value("${AGS}")
    private String ags;

	@Autowired
	ApiClient dmApiClient;

	@Autowired
	BusinessObjectDataApi businessObjectDataApi;

	static {
		javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
				( hostname, sslSession ) -> true );
	}

	public String getTableSchema( org.finra.herd.metastore.managed.JobDefinition jd, boolean replaceColumn ) throws ApiException {

		BusinessObjectFormatApi businessObjectFormatApi = new BusinessObjectFormatApi( dmApiClient );

		BusinessObjectFormatDdlRequest request = new BusinessObjectFormatDdlRequest();
		request.setBusinessObjectDefinitionName( jd.getActualObjectName() );
		request.setBusinessObjectFormatFileType( jd.getObjectDefinition().getFileType() );
		request.setBusinessObjectFormatUsage( jd.getObjectDefinition().getUsageCode() );

		request.setNamespace( jd.getObjectDefinition().getNameSpace() );
		request.setIncludeDropTableStatement( false );
		request.setIncludeIfNotExistsOption( !replaceColumn );
		request.setOutputFormat( BusinessObjectFormatDdlRequest.OutputFormatEnum.HIVE_13_DDL );
		request.setReplaceColumns( replaceColumn );

		request.setTableName( jd.getTableName() );

		BusinessObjectFormatDdl ddl = businessObjectFormatApi.businessObjectFormatGenerateBusinessObjectFormatDdl( request );

		return ddl.getDdl();
	}

	public BusinessObjectFormat getDMFormat( org.finra.herd.metastore.managed.JobDefinition jd ) throws ApiException {

		BusinessObjectFormatApi businessObjectFormatApi = new BusinessObjectFormatApi( dmApiClient );

		BusinessObjectFormat format = businessObjectFormatApi.businessObjectFormatGetBusinessObjectFormat( jd.getObjectDefinition().getNameSpace(),
				jd.getActualObjectName(), jd.getObjectDefinition().getUsageCode(), jd.getObjectDefinition().getFileType(), null );

		return format;
	}

	public BusinessObjectDataDdl getBusinessObjectDataDdl( org.finra.herd.metastore.managed.JobDefinition jd, List<String> partitions ) throws ApiException {
		BusinessObjectDataDdlRequest request = new BusinessObjectDataDdlRequest();

		request.setIncludeDropTableStatement( false );
		request.setOutputFormat( BusinessObjectDataDdlRequest.OutputFormatEnum.HIVE_13_DDL );
		request.setBusinessObjectFormatUsage( jd.getObjectDefinition().getUsageCode() );
		request.setBusinessObjectFormatFileType( jd.getObjectDefinition().getFileType() );
		request.setBusinessObjectDefinitionName( jd.getObjectDefinition().getObjectName() );
		request.setAllowMissingData( true );
		request.setIncludeDropPartitions( true );
		request.setIncludeIfNotExistsOption( true );
		request.setTableName( jd.getTableName() );

		PartitionValueFilter filter = new PartitionValueFilter();

		filter.setPartitionKey( jd.getPartitionKey() );

		if ( jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON && !jd.getPartitionKey().equalsIgnoreCase( "PARTITION" ) ) {
			Calendar c = Calendar.getInstance();
			c.add( Calendar.DATE, 1 );
			String date = new SimpleDateFormat( "YYYY-MM-dd" ).format( c.getTime() );
			LatestBeforePartitionValue value = new LatestBeforePartitionValue();
			value.setPartitionValue( date );
			filter.setLatestBeforePartitionValue( value );

			filter.setPartitionValues( null );
			filter.setLatestAfterPartitionValue( null );


		} else {

			if ( jd.getWfType() == ObjectProcessor.WF_TYPE_SINGLETON && jd.getPartitionKey().equalsIgnoreCase( "PARTITION" ) ) {
				filter.setPartitionValues( Lists.newArrayList( "none" ) );
			} else {
				filter.setPartitionValues( partitions );
			}
		}

		request.setPartitionValueFilter( filter );
		request.setPartitionValueFilters( null );
		request.setNamespace( jd.getObjectDefinition().getNameSpace() );

		return businessObjectDataApi.businessObjectDataGenerateBusinessObjectDataDdl( request );
	}

	public BusinessObjectFormatKeys getBOAllFormatVersions( org.finra.herd.metastore.managed.JobDefinition od, boolean latestBusinessObjectFormatVersion ) throws ApiException {

		return new BusinessObjectFormatApi( dmApiClient )
				.businessObjectFormatGetBusinessObjectFormats(
						od.getObjectDefinition().getNameSpace()
						, od.getObjectDefinition().getObjectName()
						, latestBusinessObjectFormatVersion );
	}

	public BusinessObjectDataNotificationRegistrationKeys getBORegisteredNotification( org.finra.herd.metastore.managed.JobDefinition od ) throws ApiException {
		return new BusinessObjectDataNotificationRegistrationApi( dmApiClient )
				.businessObjectDataNotificationRegistrationGetBusinessObjectDataNotificationRegistrationsByNotificationFilter(
						od.getObjectDefinition().getNameSpace()
						, od.getObjectDefinition().getObjectName()
						, od.getObjectDefinition().getUsageCode()
						, od.getObjectDefinition().getFileType()

				);
	}

	public BusinessObjectDataNotificationRegistration getBORegisteredNotificationDetails( String notificationName ) throws ApiException {
		return new BusinessObjectDataNotificationRegistrationApi( dmApiClient )
				.businessObjectDataNotificationRegistrationGetBusinessObjectDataNotificationRegistration( ags, notificationName );
	}

	public BusinessObjectDataSearchResult searchBOData( JobDefinition jd, int pageNum, int pageSize, Boolean filterOnValidLatestVersions ) throws ApiException{
		// Create Search Key
		BusinessObjectDataSearchKey boDataSearchKeyItem = new BusinessObjectDataSearchKey();
		boDataSearchKeyItem.setNamespace( jd.getObjectDefinition().getNameSpace() );
		boDataSearchKeyItem.setBusinessObjectDefinitionName( jd.getObjectDefinition().getObjectName());
		boDataSearchKeyItem.setBusinessObjectFormatUsage( jd.getObjectDefinition().getUsageCode() );
		boDataSearchKeyItem.setBusinessObjectFormatFileType( jd.getObjectDefinition().getFileType() );
		boDataSearchKeyItem.setFilterOnLatestValidVersion( filterOnValidLatestVersions );


		// Search BO Data
		return businessObjectDataApi.businessObjectDataSearchBusinessObjectData(
				new BusinessObjectDataSearchRequest()
					.addBusinessObjectDataSearchFiltersItem( new BusinessObjectDataSearchFilter()
																	.addBusinessObjectDataSearchKeysItem( boDataSearchKeyItem )
					)
				, pageNum
				, pageSize );
	}
}
