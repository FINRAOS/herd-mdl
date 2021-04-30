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
package org.finra.herd.metastore.managed.hive;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.hive.jdbc.HiveDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.List;
import java.util.Set;

import static org.finra.herd.metastore.managed.util.JobProcessorConstants.*;

@Component
@Slf4j
public class HiveClientImpl implements HiveClient {

	@Autowired
	JdbcTemplate hiveJdbcTemplate;

	static {
		try {
			Class.forName( DRIVER_NAME );
		} catch ( ClassNotFoundException e ) {
			e.printStackTrace();
			System.exit( 1 );
		}
		DriverManager.setLoginTimeout( 45 );
	}

	protected Connection getDatabaseConnection( String databaseName ) throws SQLException {
		return DriverManager.getConnection( String.format( HIVE_URL + "%s", databaseName ), HIVE_USER, HIVE_PASSWORD );
	}

	public static List<ColumnDef> getDMSchemaColumns( String ddl ) {
		String s = ddl.substring( ddl.indexOf( "(" ) + 1, ddl.indexOf( "PARTITIONED BY" ) ).trim();
		s = s.substring( 0, s.lastIndexOf( ")" ) );

		return getColumnDefs( s, ",\n" );
	}

	@Override
	public HiveTableSchema getExistingDDL( String dbName, String tableName ) throws SQLException {

		try ( Connection con = getDatabaseConnection( dbName ) ) {
			Statement stmt = con.createStatement();

			stmt.execute( "SHOW CREATE TABLE " + tableName );

			ResultSet resultSet = stmt.getResultSet();

			StringBuffer sb = new StringBuffer();

			while ( resultSet.next() ) {
				sb.append( resultSet.getString( 1 ).trim() + "\n" );
			}
			String ddl = sb.toString();
			log.info( "Hive Schema: " + ddl );

			HiveTableSchema schema = getHiveTableSchema( ddl );

			return schema;
		}
	}

	public static HiveTableSchema getHiveTableSchema( String ddl ) {
		List<ColumnDef> columnList;
		columnList = getDMSchemaColumns( ddl );
		log.info("HiveTableSchema ddl:{}",ddl);
		HiveTableSchema schema = new HiveTableSchema().toBuilder().ddl( ddl ).columns( columnList ).build();
		if ( ddl.contains( "PARTITIONED BY" ) ) {
			List<ColumnDef> partitionColumns = getPartitionColumns( ddl );
			schema.setPartitionColumns( partitionColumns );
		}

		if ( ddl.contains( "FIELDS TERMINATED BY" ) ) {
			String delimStr = ddl.substring( ddl.indexOf( "FIELDS TERMINATED BY" ) );
			int beginIndex = delimStr.indexOf( "'" ) + 1;
			delimStr = delimStr.substring( beginIndex, delimStr.indexOf( "'", beginIndex ) );
			schema.setDelim( delimStr );

		} else if ( ddl.contains( "'field.delim'" ) ) //Hive 2
		{
			schema.setDelim( getFieldStr( ddl, "'field.delim'" ) );
		}

		if ( ddl.contains( "ESCAPED BY" ) ) {
			String escapeStr = ddl.substring( ddl.indexOf( "ESCAPED BY" ) );
			int beginIndex = escapeStr.indexOf( "'" ) + 1;
			escapeStr = escapeStr.substring( beginIndex, escapeStr.indexOf( "'", beginIndex ) );
			schema.setEscape( escapeStr );
		} else if ( ddl.contains( "'escape.delim'" ) ) {
			String escapeStr = getFieldStr( ddl, "'escape.delim'" );
			schema.setEscape( escapeStr );
		}

		if ( ddl.contains( "NULL DEFINED AS" ) ) {
			String nullStr = ddl.substring( ddl.indexOf( "NULL DEFINED AS" ) );
			int beginIndex = nullStr.indexOf( "'" ) + 1;
			nullStr = nullStr.substring( beginIndex, nullStr.indexOf( "'", beginIndex ) );
			schema.setNullChar( nullStr );
		} else if ( ddl.contains( "'serialization.null.format'" ) ) {
			schema.setNullChar( getFieldStr( ddl, "'serialization.null.format'" ) );
		}
		return schema;
	}

	public static String getFieldStr( String ddl, String fieldName ) {
		String escapeStr = ddl.substring( ddl.indexOf( fieldName ) );
		int beginIndex = escapeStr.indexOf( "='" ) + 2;
		escapeStr = escapeStr.substring( beginIndex, escapeStr.indexOf( "'", beginIndex ) );
		return escapeStr;
	}

	@Override
	public boolean tableExist( String dbName, String tableName ) throws SQLException {
		try ( Connection con = getDatabaseConnection( "default" ) ) {
			Statement stmt = con.createStatement();

			stmt.execute( "show databases like \'" + dbName + "\'" );
			if ( stmt.getResultSet().next() ) {
				stmt.execute( String.format( "Show tables in %s like \'%s\'", dbName, tableName ) );
				return stmt.getResultSet().next();
			}
			return false;
		}
	}


	static List<ColumnDef> getPartitionColumns( String ddl ) {
		String s = ddl.substring( ddl.indexOf( "PARTITIONED BY" ) + 14, ddl.indexOf( "ROW FORMAT" ) ).trim();
		s = s.substring( s.indexOf( "(" ) + 1, s.lastIndexOf( ")" ) );

		if ( s.contains( "\n" ) ) {
			return getColumnDefs( s, ",\n" );
		} else {
			return getColumnDefs( s, ", `" );

		}
	}

	private static List<ColumnDef> getColumnDefs( String s, String split ) {
		List<ColumnDef> entries = Lists.newArrayList();
		String[] columns = s.split( split );
		int i = 0;
		for ( String c : columns ) {
			String[] column = c.trim().replace( "`", "" ).split( " " );
			if ( column.length < 2 ) {
				log.info( "Ignore line: " + c );
				continue;
			}
			String name = column[0].trim();
			String type = column[1].trim();
			if ( column.length > 2 && !"COMMENT".equalsIgnoreCase( column[2] ) ) {
				type += column[2].trim();
			}

			entries.add( ColumnDef.builder().name( name ).type( type ).index( i ).build() );
			i++;
		}
		return entries;
	}

	@Override
	public List<HivePartition> getExistingPartitions( String database, String tableName, Set<String> partitionSpec ) {
		List<HivePartition> partName = Lists.newArrayList();

		partitionSpec.forEach( s -> {
			partName.addAll(
					hiveJdbcTemplate.query(
							String.format( "SHOW PARTITIONS %s.%s PARTITION (%s)", database, tableName, s )
							, new RowMapper<HivePartition>() {
								@Nullable
								@Override
								public HivePartition mapRow( ResultSet resultSet, int i ) throws SQLException {
									return HivePartition.builder().metastorePartName( resultSet.getString( 1 ) ).build();
								}
							}
					) );
		} );

		return partName;
	}

    @Override
	public List<HivePartition> getExistingPartitions(String database, String tableName) {
        List<HivePartition> partName = Lists.newArrayList();

        partName.addAll(
            hiveJdbcTemplate.query(
                String.format( "SHOW PARTITIONS %s.%s", database, tableName)
                , new RowMapper<HivePartition>() {

                    @Override
                    public HivePartition mapRow(ResultSet resultSet, int i ) throws SQLException {
                        return HivePartition.builder().metastorePartName( resultSet.getString( 1 ) ).build();
                    }
                }
            ) );
        return partName;
    }

	@Override
	public void executeQueries( String database, List<String> schemaSql ) {
		log.info( "Executing schemaSQL: {}", schemaSql );
		hiveJdbcTemplate.batchUpdate( schemaSql.stream().map( s -> s.replaceAll( ";","" ) ).toArray( String[]::new ) );
	}
}
