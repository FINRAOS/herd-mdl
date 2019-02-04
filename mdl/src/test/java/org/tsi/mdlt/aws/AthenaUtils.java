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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.athena.AmazonAthena;
import com.amazonaws.services.athena.AmazonAthenaClientBuilder;
import com.amazonaws.services.athena.model.ColumnInfo;
import com.amazonaws.services.athena.model.Datum;
import com.amazonaws.services.athena.model.GetQueryExecutionRequest;
import com.amazonaws.services.athena.model.GetQueryResultsRequest;
import com.amazonaws.services.athena.model.GetQueryResultsResult;
import com.amazonaws.services.athena.model.QueryExecutionContext;
import com.amazonaws.services.athena.model.QueryExecutionState;
import com.amazonaws.services.athena.model.ResultConfiguration;
import com.amazonaws.services.athena.model.Row;
import com.amazonaws.services.athena.model.StartQueryExecutionRequest;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.tsi.mdlt.util.TestProperties;

public class AthenaUtils {

    private static final Logger LOGGER = org.slf4j.LoggerFactory
        .getLogger(MethodHandles.lookup().lookupClass());


    private static final String REGION_NAME = Regions.US_EAST_1.getName();

    private static AmazonAthena athenaClient;
    private static AtomicBoolean atomicInitialized = new AtomicBoolean(false);
    private static final String MDLT_BUCKET_NAME = TestProperties.getProperties()
        .getProperty("MdltBucketName");


    /**
     * Select Top 10 record from athena table
     *
     * @param databaseName athena database name
     * @param tableName table name under provided athena database
     */
    public static List<Map<String, String>> getTableTop10(String databaseName, String tableName) {
        LOGGER.info(String.format("Getting top 10 records from Athena table: %s", databaseName + "." + tableName));
        String query = String.format("SELECT * FROM %s.%s limit 10", databaseName, tableName);
        return executeQuery(databaseName, query);

    }

    /**
     * Query data from Athena
     *
     * @param databaseName athena database name
     * @param query athena query to execute
     */
    public static List<Map<String, String>> executeQuery(String databaseName, String query) {
        if (atomicInitialized.compareAndSet(false, true)) {
            initializeAthenaClient();
        }
        String executionId = submitAthenaQuery(databaseName, query);
        waitForQueryToComplete(executionId);
        return processResultRows(executionId);
    }

    /**
     * Submits a sample query to Athena and returns the execution ID of the query.
     */
    private static String submitAthenaQuery(String databaseName, String query) {
        LOGGER.info(String.format("Submit Athena Query: %s", query));
        QueryExecutionContext queryExecutionContext = new QueryExecutionContext()
            .withDatabase(databaseName);
        ResultConfiguration resultConfiguration = new ResultConfiguration()
            .withOutputLocation("s3://" + MDLT_BUCKET_NAME);
        StartQueryExecutionRequest queryExecutionRequest = new StartQueryExecutionRequest()
            .withQueryExecutionContext(queryExecutionContext)
            .withQueryString(query)
            .withResultConfiguration(resultConfiguration);

        return athenaClient.startQueryExecution(queryExecutionRequest).getQueryExecutionId();
    }


    /**
     * Wait for an Athena query to complete, fail or to be cancelled.Maximum wait time is 5 Minutes.
     * If a query fails or is cancelled, then it will throw an exception.
     */

    private static void waitForQueryToComplete(String queryExecutionId) {
        LOGGER.info("Wait for query to complete");
        GetQueryExecutionRequest queryRequest = new GetQueryExecutionRequest()
            .withQueryExecutionId(queryExecutionId);

        given().atMost(5, TimeUnit.MINUTES).pollInterval(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                String queryState = athenaClient.getQueryExecution(queryRequest)
                    .getQueryExecution().getStatus().getState();
                if (queryState.equals(QueryExecutionState.FAILED.toString()) ||
                    queryState.equals(QueryExecutionState.CANCELLED.toString())) {
                    throw new RuntimeException(String.format("Query was %s", queryState));
                }
                assertEquals(QueryExecutionState.SUCCEEDED.toString(), queryState);
            });
    }

    /**
     * This code calls Athena and retrieves the results of a query. The query must be in a completed
     * state before the results can be retrieved and paginated. The first row of results are the
     * column headers. max row counts are 1000
     */
    private static List<Map<String, String>> processResultRows(String queryExecutionId) {
        List<Map<String, String>> results = new ArrayList<>();

        GetQueryResultsRequest getQueryResultsRequest = new GetQueryResultsRequest()
            .withQueryExecutionId(queryExecutionId);

        GetQueryResultsResult getQueryResultsResult = athenaClient
            .getQueryResults(getQueryResultsRequest);
        List<ColumnInfo> columnInfoList = getQueryResultsResult.getResultSet()
            .getResultSetMetadata().getColumnInfo();

        List<Row> rows = getQueryResultsResult.getResultSet().getRows();
        //remove first column row
        rows.remove(0);
        while (true) {
            for (Row row : rows) {
                Map<String, String> rowMap = new HashMap<String, String>();
                List<Datum> data = row.getData();
                for (int i = 0; i < data.size(); i++) {
                    rowMap.put(columnInfoList.get(i).getName(), data.get(i).getVarCharValue());
                }
                results.add(rowMap);
            }

            if (getQueryResultsResult.getNextToken() == null) {
                break;
            }
            getQueryResultsResult = athenaClient.getQueryResults(
                getQueryResultsRequest.withNextToken(getQueryResultsResult.getNextToken()));
            rows = getQueryResultsResult.getResultSet().getRows();
        }
        return results;
    }

    private static void initializeAthenaClient() {
        athenaClient = AmazonAthenaClientBuilder.standard()
            .withRegion(REGION_NAME)
            .build();
    }
}
