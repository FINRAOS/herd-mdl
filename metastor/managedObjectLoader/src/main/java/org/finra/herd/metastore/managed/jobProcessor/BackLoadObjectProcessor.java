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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.ObjectProcessor;
import org.finra.herd.metastore.managed.jobProcessor.dao.DMNotification;
import org.finra.herd.metastore.managed.jobProcessor.dao.JobProcessorDAO;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectData;
import org.finra.herd.sdk.model.BusinessObjectFormat;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.jobProcessor.bean.JobSubmitterInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Component
public class BackLoadObjectProcessor extends JobProcessor {
	@Value( "${PAGE_SIZE}" )
	int pageSize;

	@Value( "${CHUNK_SIZE}" )
	int reqChunkSize;

	@Autowired
	DataMgmtSvc dataMgmtSvc;

	@Autowired
	JobProcessorDAO jobProcessorDAO;

	@Autowired
	NotificationSender notificationSender;

	Boolean filterOnValidLatestVersions = true;

	@Override
	public boolean process( JobDefinition od, String clusterID, String workerID ) {
		log.info( "Back Load Process called with: {}", od );
        errorBuffer.setLength( 0 );
		try {
			BusinessObjectFormat dmFormat = dataMgmtSvc.getDMFormat( od );
			JobSubmitterInfo jsi = JobSubmitterInfo.builder()
					.herdNotificationId( od.getId() )
					.partitionKey( dmFormat.getPartitionKey() )
					.namespace( od.getObjectDefinition().getNameSpace() )
					.objectName( od.getObjectDefinition().getObjectName() )
					.usage( od.getObjectDefinition().getUsageCode() )
					.fileType( od.getObjectDefinition().getFileType() )
					.workflowType( ObjectProcessor.WF_TYPE_MANAGED )
					.tableSchema( dmFormat.getSchema() )
					.correlation( od.getCorrelation() )
					.build();

			identifyPartitionsAndBackLoad( od, jsi );

//			addGatherStatsJob( jsi );

		} catch ( Exception e ) {
			log.error( "Problem encountered in Back loading processor: {}", e.getMessage(), e );
			errorBuffer.append( e.getMessage() );
			return false;
		}

		log.info( "Back loading jobs submitted successfully" );
		return true;
	}

	private void identifyPartitionsAndBackLoad( JobDefinition od, JobSubmitterInfo jsi ) throws ApiException {
		Map<String, Set<String>> partitions = partitionsAsMap( od, jsi );
		log.info( "Total Available Partitions to load: {}", partitions.size() );

		TreeSet<String> orderedPartitions = Sets.newTreeSet();
		List<TreeSet<String>> chunkedPartitions = Lists.newArrayList();
		AtomicInteger partitionCounter = new AtomicInteger( 0 );

		partitions.forEach( ( k, v ) -> {
			orderedPartitions.add( k );
			partitionCounter.getAndAdd( 1 ); // for partition Value
			partitionCounter.getAndAdd( v.size() ); // add # of subpartitions

			if ( isBiggerThanChunkSize( partitionCounter.get() ) ) {
				log.info( "PartitionCounter: {}", partitionCounter.get() );
				chunkedPartitions.add( Sets.newTreeSet( orderedPartitions ) );
				orderedPartitions.clear();
				partitionCounter.getAndSet( 0 );
			}
		} );

		if ( !orderedPartitions.isEmpty() ) {
			chunkedPartitions.add( Sets.newTreeSet( orderedPartitions ) );
		}

		// Add Chunked Partition for processing
		AtomicReference<String> startDate = new AtomicReference<>();
		chunkedPartitions.stream()
				.forEach( ts -> {
					filterAlreadyAddedPartitions( jsi, ts );
					if ( !ts.isEmpty() ) {

						if ( jsi.isPartitionDateType() ) {
							if ( Strings.isNullOrEmpty( startDate.get() ) ) {
								startDate.set( ts.first() );
							}

							// To include skipped partition dates - using end date of the previous chunk
							String endDate = ts.last();
							jsi.setPartitionValues( String.format( "%s%s%s", endDate, JobProcessorConstants.DOUBLE_UNDERSCORE, startDate ) );
							addPartitions( jsi );
							startDate.set( endDate );
						} else {
							jsi.setPartitionValues( delimitedPartitionValues( ts.descendingSet() ) );
							addPartitions( jsi );
						}
					}
				} );


		if ( jsi.isManualLoadPartitions() ) {
			jsi.setPartitionsSubmitted( delimitedPartitionValues( partitions.keySet() ) );
			log.info( jsi.getEmailContent() );
			//send email
			notificationSender.sendNotificationEmail( jsi.getEmailContent(), "Manually Back Load Partitions for: ", od );
		}
	}

	private void addGatherStatsJob( JobSubmitterInfo jsi ) {
		log.info( "Adding gather Stats job" );
		DMNotification dmNotification = buildDMNotification( jsi );
		dmNotification.setWorkflowType( ObjectProcessor.WF_TYPE_MANAGED_STATS );
		dmNotification.setExecutionId( "SUBMITTED_BY_BACKLOADING" );
		dmNotification.setPartitionKey( quotedPartitionKeys( jsi.getTableSchema() ) );
		dmNotification.setPartitionValue( "" ); // partition values not required for gather stats job as it runs for all partitions
		log.info( "Herd Notification DB request: \n{}", dmNotification );
		jobProcessorDAO.addDMNotification( dmNotification );
	}

	@Override
	protected ProcessBuilder createProcessBuilder( JobDefinition od ) {
		// Keeping it empty as no ProcessBuilder need to run from this processor.
		throw new RuntimeException( "Invalid call, as Back Load process do not create any sub process." );
	}

	public boolean isBiggerThanChunkSize( int collectionSize ) {
		return (collectionSize >= reqChunkSize);
	}

	public String delimitedPartitionValues( Set<String> partitions ) {
		return partitions
				.stream()
				.collect( Collectors.joining( JobProcessorConstants.COMMA ) );
	}

	/**
	 * Method to get all available partition data for a object
	 * Get all the format versions, and then get data for each format version
	 *
	 * @param jd
	 * @param jsi
	 * @return
	 * @throws ApiException
	 */
	Map<String, Set<String>> partitionsAsMap( final JobDefinition jd, JobSubmitterInfo jsi ) throws ApiException {
		Map<String, Set<String>> partitionsAsMap = Maps.newTreeMap();

		IntStream.iterate( 1, i -> i + 1 )
				.mapToObj( pageNum -> {
				    try{
				        return getBusinessObjectData( jd, partitionsAsMap, pageNum );
                    } catch ( ApiException apiex){
				        throw new RuntimeException( "Backload partition failed to get available partitions due to: " + apiex.getMessage(), apiex );
                    }
				})
				.anyMatch( l -> l == 0 );

		return partitionsAsMap;
	}

	private int getBusinessObjectData( final JobDefinition jd, Map<String, Set<String>> partitionsAsMap, int pageNum ) throws ApiException {
        List<BusinessObjectData> businessObjectDataElements = dataMgmtSvc.searchBOData( jd, pageNum, pageSize, filterOnValidLatestVersions ).getBusinessObjectDataElements();
        log.info( "BO Data Search Result: \n{}", businessObjectDataElements.size() );
        businessObjectDataElements.stream()
                .forEach( as ->
                    partitionsAsMap.merge( as.getPartitionValue()
                            , (Objects.isNull( as.getSubPartitionValues() )) ? Sets.newHashSet() : Sets.newHashSet( as.getSubPartitionValues() )
                            , ( s1, s2 ) -> Stream.of( s1, s2 ).flatMap( Collection::stream ).collect( Collectors.toSet() ) )
                );

        return businessObjectDataElements.size();
}

	/**
	 * This method will add request to add partition
	 */
	private void addPartitions( JobSubmitterInfo jsi ) {
		DMNotification dmn = buildDMNotification( jsi );
		log.info( "Herd Notification DB request: \n{}", dmn );
		jobProcessorDAO.addDMNotification( dmn );
	}

	private DMNotification buildDMNotification( JobSubmitterInfo jsi ) {
		return DMNotification.builder()
				.namespace( jsi.getNamespace() )
				.objDefName( jsi.getObjectName() )
				.formatUsage( jsi.getUsage() )
				.fileType( jsi.getFileType() )
				.workflowType( jsi.getWorkflowType() )
				.partitionValue( jsi.getPartitionValues() )
				.partitionKey( jsi.getPartitionKey() )
				.executionId( jsi.getExecutionId() )
				.clusterName( jsi.getClusterName() )
				.correlationData( jsi.getCorrelation() )
				.build();
	}

	/**
	 * Method checks if there are any already request for these partition values
	 *
	 * @param jsi
	 * @param partitions
	 * @return
	 */
	private void filterAlreadyAddedPartitions( JobSubmitterInfo jsi, TreeSet<String> partitions ) {
		Set<String> alreadyAddedPartitions = new HashSet<>();
		jobProcessorDAO.getSubmittedRequests( jsi.getExecutionId() )
				.stream()
				.map( herd -> herd.getPartitionValue() )
				.forEach( s -> {
					if ( Strings.nullToEmpty( s ).contains( JobProcessorConstants.COMMA ) ) {
						alreadyAddedPartitions.addAll( Sets.intersection( partitions, Sets.newHashSet( s.split( JobProcessorConstants.COMMA ) ) ) );
					} else if ( Strings.nullToEmpty( s ).contains( JobProcessorConstants.DOUBLE_UNDERSCORE ) ) {
						alreadyAddedPartitions.addAll( Sets.intersection( partitions, Sets.newHashSet( s.split( JobProcessorConstants.DOUBLE_UNDERSCORE ) ) ) );
					}
				} );

		partitions.removeAll( alreadyAddedPartitions );
	}

}
