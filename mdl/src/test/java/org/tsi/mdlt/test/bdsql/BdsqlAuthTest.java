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
package org.tsi.mdlt.test.bdsql;

import static org.awaitility.Awaitility.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.util.StackOutputPropertyReader;
import org.tsi.mdlt.util.TestProperties;
import org.tsi.mdlt.util.jdbc.JDBCHelper;
import org.tsi.mdlt.util.jdbc.JDBCTestCase;
import org.tsi.mdlt.util.shell.ShellCommandProperty;

/**
 * Presto authentication & authorization testcases
 */
public class BdsqlAuthTest extends BdsqlBaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String COMPONENT = "Presto Authentication Authorization And SSL Test";
    private static Map<String, String> envVars = ShellCommandProperty.getPropertiesMap();
    private static final boolean IS_AUTH_ENABLED = Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH));
    private static final String JDBC_AUTH_TESTCASES = "/testCases/auth/prestoAuthTestCases.json";
    private static final User LDAP_APP_USER = User.getLdapMdlAppUser();
    private static final User NOAUTH_VALID_JDBC_USER = User.getNoAuthValidJdbcUser();

    @TestFactory
    //@DisableOnAuthenticationDisabled
    public Stream<DynamicTest> testPrestoAuthentication() {
        if (!IS_AUTH_ENABLED) {
            LOGGER.info("Testcases skipped as auth is disabled");
            return null;
        }
        return getJdbcTestCases(JDBC_AUTH_TESTCASES)
                .stream().map(
                        (JDBCTestCase command) -> DynamicTest.dynamicTest(COMPONENT + command.getName(), () -> {
                            if (!IS_AUTH_ENABLED) {
                                LogVerification("Verify jdbc query response pass with bad credential/permission when enableAuth is false");
                                List<String> result = JDBCHelper.executeJDBCTestCase(command, envVars);
                                assertThat(result, containsInAnyOrder(command.getAssertVal()));
                            }
                            else {
                                LogVerification("Verify jdbc query response failure with bad credential/permission when enableAuth is true");
                                SQLException authenticationException = assertThrows(SQLException.class, () -> {
                                    JDBCHelper.executeJDBCTestCase(command, envVars);
                                });
                                List<String> expectErrorMsg = Arrays.asList("Authentication failed", "Connection property 'user' value is empty");
                                String errorMsg = authenticationException.getMessage();
                                assertTrue(errorMsg.contains(expectErrorMsg.get(0)) || errorMsg.contains(expectErrorMsg.get(1)));
                            }
                        }));
    }

    @Test
    //@DisableOnAuthenticationDisabled
    //TO disable condition annotation is NOT working somehow
    public void testPrestoReadWriteToLdapUserSchema() throws SQLException, ClassNotFoundException {
        if (!IS_AUTH_ENABLED) {
            LOGGER.info("Testcases skipped as auth is disabled");
            return;
        }

        String ldapUser = LDAP_APP_USER.getUsername();
        String jdbcUrl = getValidPrestoJdbcUrl("user_" + ldapUser);
        String tableName = "write_test_table_1";

        LogStep("Create table if not exist");
        String createTableQuery = String.format("create table IF NOT EXISTS %s (my_id bigint, my_string varchar)", tableName);
        executePrestoUpdate(createTableQuery, jdbcUrl, LDAP_APP_USER);

        LogStep("Wait for table created");
        given().atMost(10, TimeUnit.MINUTES).pollInterval(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    String showTableQuery = "show tables from user_" + ldapUser;
                    assertTrue(executePrestoSelect(showTableQuery, jdbcUrl, LDAP_APP_USER).contains(tableName));
                });

        LOGGER.info("Before Insertion:");
        String selectQuery = "select * from " + tableName;
        int oldRecords = executePrestoSelect(selectQuery, jdbcUrl, LDAP_APP_USER).size();

        LogStep("Insert record");
        LogVerification("Verify jdbc insertQuery response pass with write permission when enableAuth is true/false");
        String insertQuery = String.format("insert into %s values (1,'abcd')", tableName);
        assertEquals(1, executePrestoUpdate(insertQuery, jdbcUrl, User.getLdapMdlAppUser()));

        LogVerification("Verify jdbc insertQuery response pass with write permission when enableAuth is true/false");
        assertEquals(oldRecords + 1, executePrestoSelect(selectQuery, jdbcUrl, LDAP_APP_USER).size());

        LogStep("Drop table");
        String dropTable = "DROP TABLE " + tableName;
        executePrestoUpdate(dropTable, jdbcUrl, User.getLdapMdlAppUser());
    }

    @Test
    public void testPrestoReadWithoutPermission() throws SQLException, ClassNotFoundException {
        String jdbcUrl = getValidPrestoJdbcUrl("sec_market_data");
        String selectQuery = "select * from securitydata_mdl_txt";
        if (!IS_AUTH_ENABLED) {
            LogVerification("Verify jdbc query response pass without write permission when enableAuth is false");
            assertTrue(executePrestoSelect(selectQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER).size() > 0);
        }
        else {
            LogVerification("Verify jdbc query response failure without write permission when enableAuth is true");
            SQLException authorizationException = assertThrows(SQLException.class, () -> {
                executePrestoSelect(selectQuery, jdbcUrl, User.getLdapSecAppUser());
            });
            assertTrue(authorizationException.getMessage().contains("Access Denied"), "expect message not correct:" + authorizationException.getMessage());
        }
    }

    @Test
    public void testPrestoWriteWithoutPermission() throws SQLException, ClassNotFoundException {
        String jdbcUrl = getValidPrestoJdbcUrl("mdl");
        String tableName = "test_write_table";
        String createTableQuery = String.format("create table IF NOT EXISTS %s (my_id bigint, my_string varchar)", tableName);
        if (!IS_AUTH_ENABLED) {
            LOGGER.info("Testcase testPrestoWriteWithoutPermission skipped when authentication is not enabled");
        }
        else {
            LogVerification("Verify jdbc query response failure without write permission when enableAuth is true");
            SQLException authorizationException = assertThrows(SQLException.class, () -> {
                executePrestoUpdate(createTableQuery, jdbcUrl, LDAP_APP_USER);
            });
            assertTrue(authorizationException.getMessage().contains("Access Denied"));
        }
    }

    @Test
    //TODO mdl issue for dropping table
    public void testPrestoCreateInsertSelectDropTableIfNoAuth() throws SQLException, ClassNotFoundException {
        if (!IS_AUTH_ENABLED) {
            LogStep("Create Schema");
            String jdbUrlWithoutSchema = StackOutputPropertyReader.get(StackOutputKeyEnum.BDSQL_URL);
            executePrestoUpdate("create schema IF NOT EXISTS mdlt_test_schema", jdbUrlWithoutSchema, NOAUTH_VALID_JDBC_USER);

            String jdbcUrl = getValidPrestoJdbcUrl("mdlt_test_schema");
            String tableName = "test_write_table";
            LogVerification("Verify jdbc query to create/insert/query/drop table pass when enableAuth is false");
            LogStep("Create table");
            String createTableQuery = String.format("create table IF NOT EXISTS %s (my_id bigint, my_string varchar)", tableName);
            executePrestoUpdate(createTableQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER);

            LogStep("Insert record");
            String selectQuery = String.format("select * from %s", tableName);
            int oldRecords = executePrestoSelect(selectQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER).size();
            String insertQuery = String.format("insert into %s values (1,'abcd')", tableName);
            assertEquals(1, executePrestoUpdate(insertQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER));

            LogStep("Select record");
            executePrestoSelect(selectQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER).forEach(LOGGER::info);
            assertEquals(oldRecords + 1, executePrestoSelect(selectQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER).size());

            LogStep("Delete record");
            String deleteQuery = String.format("delete from %s", tableName);
            executePrestoUpdate(deleteQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER);

            LogStep("Select record after deletion");
            executePrestoSelect(selectQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER).forEach(LOGGER::info);
            assertEquals(0, executePrestoSelect(selectQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER).size(), "expect size is 0");

            LogStep("Drop table");
            String dropQuery = String.format("drop table %s", tableName);
            assertEquals(1, executePrestoUpdate(dropQuery, jdbcUrl, NOAUTH_VALID_JDBC_USER));
        }
        else {
            LOGGER.info("Testcase testPrestoCreateInsertSelectDropTableIfNoAuth skipped when authentication is enabled");
        }
    }
}
