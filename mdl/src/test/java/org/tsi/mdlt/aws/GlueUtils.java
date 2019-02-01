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
package org.tsi.mdlt.aws;

import static org.awaitility.Awaitility.given;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.glue.AWSGlueAsync;
import com.amazonaws.services.glue.AWSGlueAsyncClientBuilder;
import com.amazonaws.services.glue.model.Column;
import com.amazonaws.services.glue.model.EntityNotFoundException;
import com.amazonaws.services.glue.model.GetDatabaseRequest;
import com.amazonaws.services.glue.model.GetTableRequest;
import com.amazonaws.services.glue.model.Table;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlueUtils {

    private static final Logger LOGGER = LoggerFactory
        .getLogger(MethodHandles.lookup().lookupClass());
    private static final String REGION_NAME = Regions.US_EAST_1.getName();

    private static AWSGlueAsync awsGlue;
    private static AtomicBoolean atomicInitialized = new AtomicBoolean(false);


    /**
     * Check whether glue database exist with given databaseName
     * @param databaseName aws Glue database name
     * @return
     */
    public static boolean doesDatabaseExist(String databaseName) {
        GetDatabaseRequest getRequest = new GetDatabaseRequest();
        getRequest.setName(databaseName);
        try {
            getAwsGlueClient().getDatabase(getRequest);
        } catch (EntityNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Check whether glue table exit with given database & table name
     * @param databaseName glue database name
     * @param tableName glue table name of given database
     * @return
     */
    public static boolean doesTableExist(String databaseName, String tableName) {
        GetTableRequest getRequest = new GetTableRequest();
        getRequest.setName(tableName);
        getRequest.setDatabaseName(databaseName);
        try {
            getAwsGlueClient().getTable(getRequest);
        } catch (EntityNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Get glue partitions list for given glue table name
     * @param databaseName database name
     * @param tableName table name
     * @return
     */
    public static Optional<List<Column>> getGlueTablePartitionKeys(String databaseName, String tableName) {
        GetTableRequest getRequest = new GetTableRequest();
        getRequest.setName(tableName);
        getRequest.setDatabaseName(databaseName);
        List<Column> partitionKeys = new ArrayList<>();
        try {
            Table table = getAwsGlueClient().getTable(getRequest).getTable();
            partitionKeys.addAll(table.getPartitionKeys());
        } catch (EntityNotFoundException e) {
            return Optional.empty();
        }
        return Optional.ofNullable(partitionKeys);
    }


    /**
     * Get Glue Table info
     * @param databaseName glue database name
     * @param tableName glue table name
     * @return
     */
    public static Optional<Table> getGlueTable(String databaseName, String tableName) {
        GetTableRequest getRequest = new GetTableRequest();
        getRequest.setName(tableName);
        getRequest.setDatabaseName(databaseName);
        Table table;
        try {
            table = getAwsGlueClient().getTable(getRequest).getTable();
        } catch (EntityNotFoundException e) {
            return Optional.empty();
        }
        return Optional.ofNullable(table);
    }

    private static AWSGlueAsync getAwsGlueClient() {
        if (atomicInitialized.compareAndSet(false, true)) {
            awsGlue = AWSGlueAsyncClientBuilder.standard()
                .withRegion(REGION_NAME).build();
        }
        return awsGlue;
    }

}
