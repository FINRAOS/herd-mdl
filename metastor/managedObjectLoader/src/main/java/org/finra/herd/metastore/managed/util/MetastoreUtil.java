package org.finra.herd.metastore.managed.util;

import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.ObjectProcessor;

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

	public static boolean isNonPartitionedSingleton( int wfType, String partitionKey ) {
		return isSingletonWF( wfType ) && NON_SINGLETON_PARTITION_KEY.equalsIgnoreCase( partitionKey );
	}


	public static boolean isNonPartitionedSingleton( String partitionKey ) {
		return NON_SINGLETON_PARTITION_KEY.equalsIgnoreCase( partitionKey );
	}

	public static boolean isPartitionedSingleton( int wfType, String partitionKey ) {
		return isSingletonWF( wfType ) && !(NON_SINGLETON_PARTITION_KEY.equalsIgnoreCase( partitionKey ));
	}

}
