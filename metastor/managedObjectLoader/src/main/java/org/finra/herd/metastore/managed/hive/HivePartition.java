package org.finra.herd.metastore.managed.hive;

import com.google.common.base.Strings;
import lombok.*;

@Builder
@Data
@EqualsAndHashCode ( of = {"partName"} )
@ToString
public class HivePartition {
	String location;
	String partition;
	String metastorePartName;
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
				this.partName = partition.replaceAll( "[^a-zA-Z0-9,-=_]", "" ).toLowerCase().replace( ",","/" ) ;
			}else if( !Strings.isNullOrEmpty( metastorePartName ) ) {
				this.partName = metastorePartName.toLowerCase();
			}
			return this;
		}
	}


}
