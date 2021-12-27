package org.finra.herd.metastore.managed.util;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.ObjectProcessor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Slf4j
public class MetastoreUtil {

	public static final String NON_SINGLETON_PARTITION_KEY = "partition";

	public static boolean isSingletonWF( int wfType ) {
		return (ObjectProcessor.WF_TYPE_SINGLETON == wfType);
	}

	public static boolean isManagedWF( int wfType ) {
		return (ObjectProcessor.WF_TYPE_MANAGED == wfType);
	}

	public static boolean isManagedStatsWF( int wfType ) {
		return (ObjectProcessor.WF_TYPE_MANAGED_STATS == wfType);
	}

	public static boolean isPartitionedSingleton( int wfType, String partitionKey ) {
		return isSingletonWF( wfType ) && !(NON_SINGLETON_PARTITION_KEY.equalsIgnoreCase( partitionKey ));
	}

	public static boolean isNonPartitionedSingleton( int wfType, String partitionKey ) {
		return isSingletonWF( wfType ) && NON_SINGLETON_PARTITION_KEY.equalsIgnoreCase( partitionKey );
	}

	/**
	 * Overridden method to identify if WF is Non Partitioned Singleton for STATS
	 * Can't use the other method as WF_TYPE for stats would be 5
	 *
	 * @param partitionKey
	 * @return
	 */
	public static boolean isNonPartitionedSingleton( String partitionKey ) {
		return NON_SINGLETON_PARTITION_KEY.equalsIgnoreCase( partitionKey );
	}

	public static List<String> formattedPartitionValues(String partitionValue) {

		List<String> partitionVal = Lists.newArrayList();

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
				log.error( e.getMessage() );
				return null;
			}

		} else if ( partitionValue.contains( JobProcessorConstants.COMMA ) ) {
			partitionVal.addAll( Lists.newArrayList( partitionValue.split( JobProcessorConstants.COMMA ) ) );
		} else {
			partitionVal.add( partitionValue );
		}

		return partitionVal;


	}

}
