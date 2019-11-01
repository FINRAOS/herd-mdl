package org.finra.herd.metastore.managed.hive;

import com.google.common.base.Strings;
import lombok.*;

import java.util.Arrays;
import java.util.StringJoiner;

import static org.finra.herd.metastore.managed.util.JobProcessorConstants.*;

@Builder
@Data
@EqualsAndHashCode( of = {"partName"} )
@ToString
public class HivePartition {
	String location;
	String partition; // Hive Partition information from DDL
	String metastorePartName; // Hive Metastore PART_NAME column value from Metastore's PARTITIONS Table
	String partName;

	boolean exists;

	public String addPartition( String tableName ) {
		return String.format( "ALTER TABLE `%s` ADD IF NOT EXISTS PARTITION %s LOCATION '%s';", tableName, partition, location );
	}

	public String setPartitionLocation( String tableName ) {
		return String.format( "ALTER TABLE `%s` PARTITION %s SET LOCATION '%s';", tableName, partition, location );
	}

	public static class HivePartitionBuilder {
		String metastorePartName;
		String partition;
		String partName;

		public HivePartitionBuilder partition( String partition ) {
			this.partition = partition;
			constructPartName();
			return this;
		}

		public HivePartitionBuilder metastorePartName( String metastorePartName ) {
			this.metastorePartName = metastorePartName;
			constructPartName();
			return this;
		}

		public HivePartitionBuilder constructPartName() {
			if ( !Strings.isNullOrEmpty( partition ) ) {
				StringJoiner partNameJoiner = new StringJoiner( FORWARD_SLASH );

				Arrays.stream( partition.replaceAll( "[^a-zA-Z0-9,-=_]", "" ).split( COMMA ) ).forEach( s -> {
					String[] partitionKeyValue = s.split( EQUALS );
					if(partitionKeyValue.length == 2) {
						partNameJoiner.add( String.format( "%s=%s", partitionKeyValue[0].toLowerCase(), partitionKeyValue[1] ) );
					}else{
						partNameJoiner.add( s );
					}
				} );

				this.partName = partNameJoiner.toString();

			} else if ( !Strings.isNullOrEmpty( metastorePartName ) ) {
				this.partName = metastorePartName.toLowerCase();
			}
			return this;
		}
	}


}
