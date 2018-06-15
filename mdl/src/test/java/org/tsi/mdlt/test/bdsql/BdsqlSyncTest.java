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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;

import org.junit.Assume;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.util.LdapUtil;
import org.tsi.mdlt.util.TestProperties;

/**
 * Application existing ldap/bdsql permission mappings
 * 1. Ldap Groups: APP_MDL_Users
 * ldap users: mdl_app, mdl_test_1, mdl_test_2
 * bdsql permission schemas: read permission to all bdsql non-user schemas except sec_demo_data, default read permission to all newly created schema too.
 * <p>
 * 2. Ldap Groups: APP_MDL_ACL_RO_sec_market_data
 * ldap users: mdl_test_1(we should add mdl_app too)
 * bdsql permission schemas: read permission to sec_demo_data
 */
@BdsqlBaseTest.DisableOnAuthenticationDisabled
//Note: Conditional disable annotation doesn't work when running as java jar
public class BdsqlSyncTest extends BdsqlBaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String PRESTO_NEW_OBJECT_SH = "prestoObjSetup.sh";
    private static final String BDSQL_SYNC_SH = "prestoSyncJob.sh";

    private static final String MDL_USER_GROUP = "APP_MDL_Users";

    private static final String LDAP_USER_NAMESPACE = "ldap_namespace";
    private static final String LDAP_USER_NEWUSER = "ldap_newuser";
    private static final String LDAP_USER_DELETEUSER = "ldap_deleteuser";

    @BeforeAll
    public static void setup() throws NamingException {
        boolean isAuthEnabled = Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH));
        if (!isAuthEnabled) {
            LOGGER.info("Skip bdsql sync testcases as enableAuth is disabled");
            Assume.assumeTrue(isAuthEnabled);
        } else {
            LdapUtil.listEntries();
        }
    }

    @AfterAll
    public static void cleanupLdapUsers() {
        try {
            LOGGER.info("Ldap User list");
            LdapUtil.listEntries();
            LdapUtil.deleteEntry(LDAP_USER_NAMESPACE);
            LdapUtil.deleteEntry(LDAP_USER_NEWUSER);
            LdapUtil.deleteEntry(LDAP_USER_DELETEUSER);

            LdapUtil.removeUserFromGroup(LDAP_USER_NEWUSER, MDL_USER_GROUP);
            LdapUtil.removeUserFromGroup(LDAP_USER_DELETEUSER, MDL_USER_GROUP);

            LOGGER.info("Ldap User list after cleanup");
            LdapUtil.listEntries();
            syncBdsqlAuth();

        }
        catch (Exception e) {
            LOGGER.error(e.getMessage());
            LOGGER.warn("Ignore errors in deleting user or removing user from group, "
                    + "as cleanup is not needed if testcases pass, it only needed when testcase failed and testcase cleanup is not executed");
        }
    }

    @Test
    /**
     * verify for new herd object with existing namespace, existing ldap user authorization works correctly
     */
    public void existingNamespaceNewObjectTest() throws IOException, InterruptedException, SQLException, ClassNotFoundException {
        String schema = "sec_market_data";
        String objectName = "testdata";

        LogStep("Create new herd object formats, object definition and upload herd data");
        executeShellScript(PRESTO_NEW_OBJECT_SH, schema, objectName, "false");

        LogStep("Wait for hive data inserted");
        waitForSchemaTableCreated(5, schema, objectName);

        LogStep("Trigger bdsql auth sync job");
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(10);

        LogVerification("Existing ldap user with schema permission can access new table");
        String jdbcUrl = getValidPrestoJdbcUrl(schema);
        String selectQuery = getSelectQuery(schema, objectName);
        assertEquals(2, executePrestoSelect(selectQuery, jdbcUrl, User.getLdapTestUser1()).size());

        LogVerification("Existing ldap user without schema permission cannot access new table");
        SQLException authorizationException = assertThrows(SQLException.class, () -> {
            executePrestoSelect(selectQuery, jdbcUrl, User.getLdapTestUser2());
        });
        verifyErrorMsgIsAccessDenied(authorizationException);
    }

    @Test
    /**
     * verify existing ldap user access to new herd object with new namespace, existing ldap user authorization works correctly
     */
    public void newNamespaceNewObjectTest() throws IOException, InterruptedException, ClassNotFoundException, NamingException, SQLException {
        String schema = "mdlt_demo_data";
        String objectName = "testdata";

        LogStep("Add Ldap user without AD group and sync");
        String username = LDAP_USER_NAMESPACE;
        String password = "ldapPwd";
        User ldapUser = new User(username, password);
        LdapUtil.addEntry(ldapUser);
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(15);

        LogStep("Create new namespace, herd object formats, object definition and upload herd data");
        executeShellScript(PRESTO_NEW_OBJECT_SH, schema, objectName, "true");

        LogStep("Wait for hive data inserted");
        waitForSchemaTableCreated(10, schema, objectName);

        LogStep("Trigger bdsql auth sync job");
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(10);

        LogVerification("ldap user with AD group can access new schema");
        assertEquals(2, executePrestoSelect(getSelectQuery(schema, objectName), getValidPrestoJdbcUrl(schema), User.getLdapAppUser()).size());

        LogVerification("ldap user without AD group cannot access new schema");
        SQLException authorizationException = assertThrows(SQLException.class, () -> {
            executePrestoSelect(getSelectQuery(schema, objectName), getValidPrestoJdbcUrl(schema), ldapUser);
        });
        verifyErrorMsgIsAccessDenied(authorizationException);

        LogStep("Delete User and sync");
        LdapUtil.deleteEntry(username);
        syncBdsqlAuth();
    }

    @Test
    /**
     * verify creating new ldap user without/with AD group, bdsql authorization works correctly
     */
    public void newLdapUserTest() throws IOException, InterruptedException, SQLException, ClassNotFoundException, NamingException {
        String schema = "mdl";
        String objectName = "mdl_object";
        String jdbcUrl = getValidPrestoJdbcUrl(schema);
        String selectQuery = getSelectQuery(schema, objectName);

        LogStep("Create Ldap user and Sync");
        String username = LDAP_USER_NEWUSER;
        String password = "ldapPwd";
        User newLdapUser = new User(username, password);
        LdapUtil.addEntry(newLdapUser);
        //sleep 10 seconds before sync as new ldap user may not be picked if sync immediately
        TimeUnit.SECONDS.sleep(10);
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(10);

        LogVerification("Verify new ldap user without AD group doesn't have permission to any existing table");
        SQLException authorizationException = assertThrows(SQLException.class, () -> {
            executePrestoSelect(selectQuery, jdbcUrl, newLdapUser);
        });
        verifyErrorMsgIsAccessDenied(authorizationException);

        LogStep("Add ldap user to AD group and sync");
        LdapUtil.addUserToGroup(username, MDL_USER_GROUP);
        syncBdsqlAuth();

        LogVerification("Verify new ldap user with AD group have permission to table");
        given().atMost(1, TimeUnit.MINUTES).pollInterval(5, TimeUnit.SECONDS)
                .ignoreExceptionsMatching(e -> e.getMessage().contains("Access Denied")).await()
                .until(() -> {
                    LOGGER.info("Polling every 5 seconds with 1 minute timeout");
                    return executePrestoSelect(selectQuery, jdbcUrl, newLdapUser).size() > 0;
                });

        LogStep("remove new ldap user from AD group");
        LdapUtil.removeUserFromGroup(username, MDL_USER_GROUP);

        LogVerification("Verify ldap user without AD group, with Hive role, still can access schema");
        assertTrue(executePrestoSelect(selectQuery, jdbcUrl, newLdapUser).size() > 0);

        LogVerification("Sync to remove Hive role");
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(15);

        LogVerification("Verify ldap user without AD group, without Hive role, cannot access Hive schema");
        authorizationException = assertThrows(SQLException.class, () -> {
            executePrestoSelect(selectQuery, jdbcUrl, newLdapUser);
        });
        verifyErrorMsgIsAccessDenied(authorizationException);

        LdapUtil.deleteEntry(username);
    }

    @Test
    /**
     * verify removed ldap user cannot access Bdsql anymore even before bdsql sync
     */
    public void removedLdapUserWithHiveRoleTest() throws IOException, InterruptedException, SQLException, ClassNotFoundException, NamingException {
        String schema = "mdl";
        String objectName = "mdl_object";
        String jdbcUrl = getValidPrestoJdbcUrl(schema);
        String selectQuery = getSelectQuery(schema, objectName);

        LogStep("Create ldap user, add user to AD group and sync");
        String username = LDAP_USER_DELETEUSER;
        String password = "ldapPwd";
        User newLdapUser = new User(username, password);
        LdapUtil.addEntry(newLdapUser);
        LdapUtil.addUserToGroup(username, MDL_USER_GROUP);
        syncBdsqlAuth();

        LogVerification("Verify ldap user have permission to table");
        given().atMost(1, TimeUnit.MINUTES).pollInterval(5, TimeUnit.SECONDS)
                .ignoreExceptionsMatching(e -> e.getMessage().contains("Access Denied")).await()
                .until(() -> {
                    LOGGER.info("Polling every 5 seconds with 1 minute timeout");
                    return executePrestoSelect(selectQuery, jdbcUrl, newLdapUser).size() > 0;
                });

        LogStep("Delete user from Ldap");
        LdapUtil.deleteEntry(username);
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(15);

        LogVerification("Verify deleted ldapUser cannot access bdsql");
        executePrestoSelect(selectQuery, jdbcUrl, newLdapUser).forEach(LOGGER::info);
        SQLException exception = assertThrows(SQLException.class, () -> {
            executePrestoSelect(selectQuery, jdbcUrl, newLdapUser);
        });
        verifyErrorMsgIsAuthenticationFailed(exception);

        LdapUtil.removeUserFromGroup(username, MDL_USER_GROUP);
    }

    private void waitForSchemaTableCreated(int timeoutMinutes, String schema, String tablename) throws ClassNotFoundException, InterruptedException {
        String showQuery = String.format("show tables from %s", schema);
        given().atMost(timeoutMinutes, TimeUnit.MINUTES).pollInterval(10, TimeUnit.SECONDS)
                .ignoreException(SQLException.class)
                .until(() -> {
                    LOGGER.info("Polling every 10 seconds with timeout: " + timeoutMinutes + " minutes");
                    List<String> records = executePrestoSelect(showQuery, getValidPrestoJdbcUrl(schema), User.getLdapAppUser());
                    return records.size() > 0 && records.stream().anyMatch(table -> table.equals(tablename + "_mdl_txt"));
                });
    }

    private String getSelectQuery(String schema, String table) {
        return String.format("select * from %s.%s_mdl_txt", schema, table);
    }

    private static void syncBdsqlAuth() throws IOException, InterruptedException {
        executeShellScript(BDSQL_SYNC_SH);
    }

    private void verifyErrorMsgIsAuthenticationFailed(SQLException exception) {
        assertTrue(exception.getMessage().contains("Authentication failed"), "expect message not correct:" + exception.getMessage());
    }

    private void verifyErrorMsgIsAccessDenied(SQLException exception) {
        assertTrue(exception.getMessage().contains("Access Denied"), "expect message not correct:" + exception.getMessage());
    }
}
