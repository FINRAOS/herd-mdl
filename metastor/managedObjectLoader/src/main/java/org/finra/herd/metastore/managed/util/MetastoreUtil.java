package org.finra.herd.metastore.managed.util;

import org.finra.herd.metastore.managed.ObjectProcessor;

public class MetastoreUtil {
	public static boolean isSingletonWF( int wfType ) {
		return (ObjectProcessor.WF_TYPE_SINGLETON == wfType);
	}

	public static boolean isManagedWF( int wfType ) {
		return (ObjectProcessor.WF_TYPE_MANAGED == wfType);
	}

	public static boolean isManagedStatsWF( int wfType ) {
		return (ObjectProcessor.WF_TYPE_MANAGED_STATS == wfType);
	}
}
