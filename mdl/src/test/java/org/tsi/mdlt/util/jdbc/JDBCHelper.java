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
package org.tsi.mdlt.util.jdbc;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.util.TestProperties;
import org.tsi.mdlt.util.jdbc.JDBCTestCase.JDBCConnProps;

public class JDBCHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static List<JDBCTestCase> parseJDBCTestCases(String testCasesFile) throws IOException {
        List<JDBCTestCase> jdbcTestCases = new ArrayList<>();
        // Parse the JSON and populate the test cases
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonTestCases = mapper.readTree(JDBCHelper.class.getResourceAsStream(testCasesFile));
        for (JsonNode jsonTestCase : jsonTestCases) {
            // Print the json test case we are parsing
            printJsonTestCase(jsonTestCase.toString());

            // Extract info
            String name = jsonTestCase.get("name").asText();
            String description = jsonTestCase.get("description").asText();
            if (jsonTestCase.hasNonNull("ignore") && jsonTestCase.get("ignore").asBoolean()) {
                System.out.println("Ignoring test case --> " + name);
                continue;
            }

            JsonNode jsonTestDetails = jsonTestCase.get("testCase");
            JsonNode jsonQuery = jsonTestDetails.get("query");
            List<String> query = new ArrayList<>();
            for (JsonNode jsonQueryStr : jsonQuery) {
                query.add(jsonQueryStr.asText());
            }

            JsonNode jsonConnProps = jsonTestDetails.get("connectionProperties");
            String jdbcDriver = jsonConnProps.get("jdbcDriver").asText();
            String jdbcURL = jsonConnProps.get("jdbcURL").asText();
            String user = jsonConnProps.get("user").asText();
            String password = jsonConnProps.get("password").asText();
            JDBCConnProps connProps = new JDBCConnProps(jdbcDriver, jdbcURL, user, password);

            JDBCTestCase jdbcTestCase = new JDBCTestCase(name, description,
                    query.toArray(new String[query.size()]), connProps);

            if (jsonTestDetails.hasNonNull("delimiter")) {
                jdbcTestCase.setDelimiter(jsonTestDetails.get("delimiter").asText());
            }

            if (jsonTestCase.hasNonNull("waitAfterCompletion")) {
                jdbcTestCase.setWaitAfterCompletion(jsonTestCase.get("waitAfterCompletion").asInt());
            }

            if (jsonTestCase.hasNonNull("assert")) {
                JsonNode jsonResults = jsonTestCase.get("assert");
                List<String> results = new ArrayList<>();
                for (JsonNode jsonResult : jsonResults) {
                    results.add(jsonResult.asText());
                }
                jdbcTestCase.setAssertVal(results.toArray(new String[results.size()]));
            }
            jdbcTestCases.add(jdbcTestCase);
        }
        return jdbcTestCases;
    }

    public static List<String> executeJDBCTestCase(JDBCTestCase jdbcTestCase, Map<String, String> env)
            throws SQLException {
        JDBCConnProps connProps = jdbcTestCase.getConnProps();
        // Register the JDBC driver
        try {
            Class.forName(connProps.getJdbcDriver());
        }
        catch (ClassNotFoundException e) {
            //TODO modify to have customized Exception
            throw new RuntimeException("Failed to load JDBC Driver, " + connProps.getJdbcDriver());
        }
        // Create connection properties
        Properties props = new Properties();
        props.put("user", replaceEnvVar(connProps.getUser(), env));
        props.put("password", replaceEnvVar(connProps.getPassword(), env));

        String jdbcURL = replaceEnvVar(connProps.getJdbcURL(), env);
        String delimiter = jdbcTestCase.getDelimiter();
        return executeJDBCQuery(generateJDBCQuery(jdbcTestCase), jdbcURL, props, delimiter);
    }

    public static String generateJDBCQuery(JDBCTestCase jdbcTestCase) {
        return String.join(" ", jdbcTestCase.getQuery());
    }

    public static List<String> executeJDBCQuery(String query, String jdbcURL,
            Properties connProps, String delimiter) throws SQLException {
        List<String> result = new ArrayList<>();

        addCertificateToProps(connProps);

        // Create a connection and execute the query
        try (Connection conn = DriverManager.getConnection(jdbcURL, connProps);
                Statement stmt = conn.createStatement()) {
            // Go through the result set and populate the results list
            try (ResultSet rs = stmt.executeQuery(query)) {
                ResultSetMetaData metaData = rs.getMetaData();
                while (rs.next()) {
                    StringBuilder row = new StringBuilder();
                    for (int column = 1; column <= metaData.getColumnCount(); column++) {
                        String columnValue = String.valueOf(rs.getObject(column));
                        row.append(columnValue);
                        if (column != metaData.getColumnCount()) {
                            row.append(delimiter);
                        }
                    }
                    result.add(row.toString());
                }
            }
        }
        return result;
    }

    public static int executeJDBCUpdateQuery(String query, String jdbcURL, Properties connProps) throws SQLException {
        addCertificateToProps(connProps);
        try (Connection conn = DriverManager.getConnection(jdbcURL, connProps);
                Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(query);
        }
    }

    private static void addCertificateToProps(Properties connProps) {
        if (Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH))) {
            connProps.setProperty("SSL", "true");
            connProps.setProperty("SSLTrustStorePath", "./certs.jks");
            connProps.setProperty("SSLTrustStorePassword", "changeit");
        }
    }

    private static void printJsonTestCase(String jsonTestCase) {
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
        System.out.println("Test Case");
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
        System.out.println(jsonTestCase);
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
    }

    // Replace environment variables
    private static String replaceEnvVar(String var, Map<String, String> env) {
        for (Entry<String, String> tmp : env.entrySet()) {
            String regex = "\\$\\{" + tmp.getKey() + "\\}";
            var = var.replaceAll(regex, tmp.getValue());
        }
        return var;
    }
}
