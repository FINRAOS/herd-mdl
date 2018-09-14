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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.util.HerdRestUtil;
import org.tsi.mdlt.util.LdapUtil;
import org.tsi.mdlt.util.shell.ShellHelper;

import org.finra.herd.model.api.xml.BusinessObjectData;

@Tag("authTest")
public class BdsqlSyncTest extends BdsqlBaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String PRESTO_NEW_OBJECT_SH = "prestoObjSetup.sh";
    private static final String BDSQL_SYNC_SH = "prestoSyncJob.sh";

    private static final String MDL_USER_GROUP = "APP_MDL_ACL_RO_mdl_rw";

    private static final String LDAP_NEWPUBLICNAMESPACE_NEWUSER = "ldap_publicnamespace";
    private static final String LDAP_MDL_NEWUSER = "ldap_newuser";
    private static final String LDAP_MDL_DELETEUSER = "ldap_deleteuser";

    private static final User HERD_ADMIN_USER = User.getHerdAdminUser();

    @BeforeAll
    public static void setup() throws NamingException, IOException, InterruptedException {
        cleanupLdapUsers();
        LdapUtil.listEntries();
    }

    @AfterAll
    public static void teardown() throws InterruptedException, NamingException, IOException {
        cleanupLdapUsers();
        syncBdsqlAuth();
    }

    public static void cleanupLdapUsers() throws NamingException, IOException, InterruptedException {
        LOGGER.info("Ldap User list before cleanup");
        LdapUtil.listEntries();
        deleteEntryIgnoringError(LDAP_NEWPUBLICNAMESPACE_NEWUSER);
        deleteEntryIgnoringError(LDAP_MDL_NEWUSER);
        deleteEntryIgnoringError(LDAP_MDL_DELETEUSER);

        removeUserFromGroupIgnoringError(LDAP_MDL_NEWUSER, MDL_USER_GROUP);
        removeUserFromGroupIgnoringError(LDAP_MDL_DELETEUSER, MDL_USER_GROUP);
        LOGGER.info("Ldap User list after cleanup");
        LdapUtil.listEntries();
    }

    private static void deleteEntryIgnoringError(String username) {
        try {
            LdapUtil.deleteEntry(username);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    private static void removeUserFromGroupIgnoringError(String username, String groupName) {
        try {
            LdapUtil.removeUserFromGroup(username, groupName);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    private static void deleteGroupIgnoringError(String groupName) {
        try {
            LdapUtil.deleteAdGroup(groupName);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage());
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
        assertEquals(2, executePrestoSelect(selectQuery, jdbcUrl, User.getLdapSecAppUser()).size());

        LogVerification("Existing ldap user without schema permission cannot access new table");
        SQLException authorizationException = assertThrows(SQLException.class, () -> {
            executePrestoSelect(selectQuery, jdbcUrl, User.getLdapMdlAppUser());
        });
        verifyErrorMsgIsAccessDenied(authorizationException);

        LogStep("Clean up test data");
        cleanupBusinessData(HERD_ADMIN_USER, getBusinessObjectData(schema, objectName), false);
    }

    @Test
    /**
     * verify existing ldap user access to new herd object with new public namespace, existing ldap user authorization works correctly
     */
    public void newPublicNamespaceNewObjectTest() throws IOException, InterruptedException, ClassNotFoundException, NamingException, SQLException {
        String schema = "mdlt_demo_data";
        String objectName = "testdata";

        String username = LDAP_NEWPUBLICNAMESPACE_NEWUSER;
        String password = "ldapPwd";
        User ldapUser = new User(username, password);

        LogStep("Cleanup before test");
        deleteEntryIgnoringError(username);

        LogStep("Add Ldap user without AD group and sync");
        LdapUtil.addEntry(ldapUser);
        TimeUnit.SECONDS.sleep(15);

        LogStep("Create new namespace, herd object formats, object definition and upload herd data");
        executeShellScript(PRESTO_NEW_OBJECT_SH, schema, objectName, Boolean.toString(true));

        LogStep("Wait for hive data inserted");
        waitForSchemaTableCreated(10, schema, objectName);

        LogStep("Trigger bdsql auth sync job");
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(10);

        LogVerification("ldap user with public AD group can access new public schema");
        assertEquals(2, executePrestoSelect(getSelectQuery(schema, objectName), getValidPrestoJdbcUrl(schema), User.getLdapMdlAppUser()).size());

        LogVerification("ldap user without AD group cannot access new schema");
        SQLException authorizationException = assertThrows(SQLException.class, () -> {
            executePrestoSelect(getSelectQuery(schema, objectName), getValidPrestoJdbcUrl(schema), ldapUser);
        });
        verifyErrorMsgIsAccessDenied(authorizationException);

        LogStep("Delete User");
        LdapUtil.deleteEntry(username);

        LogStep("Clean up test data");
        cleanupBusinessData(HERD_ADMIN_USER, getBusinessObjectData(schema, objectName), true);
    }

    @Test
    /**
     * verify existing ldap user access to new herd object with new restricted namespace(namespace has corresponding ldap AD group),
     * existing ldap user authorization works correctly
     */
    public void newPrivateNamespaceNewObjectTest() throws IOException, InterruptedException, ClassNotFoundException, NamingException, SQLException {
        String schema = "mdlt_sec";
        String adGroupName = "APP_MDL_ACL_RO_" + schema.toLowerCase();
        String objectName = "testdata";
        User secUser = User.getLdapSecAppUser();

        LogStep("Cleanup ldap before test");
        deleteGroupIgnoringError(adGroupName);

        LogStep("Create new private namespace, herd object formats, object definition and upload herd data");
        executeShellScript(PRESTO_NEW_OBJECT_SH, schema, objectName, "true");

        LogStep("Wait for hive data inserted");
        waitForSchemaTableCreated(10, schema, objectName);

        LogStep("Create AD group for new namespace and add sec user to new ad group and sync");
        LdapUtil.createAdGroup(adGroupName, secUser.getUsername());
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(15);

        LogVerification("ldap user with AD group can access new schema");
        assertEquals(2, executePrestoSelect(getSelectQuery(schema, objectName), getValidPrestoJdbcUrl(schema), secUser).size());

        LogVerification("ldap user without AD group cannot access new schema");
        SQLException authorizationException = assertThrows(SQLException.class, () -> {
            executePrestoSelect(getSelectQuery(schema, objectName), getValidPrestoJdbcUrl(schema), User.getLdapMdlAppUser());
        });
        verifyErrorMsgIsAccessDenied(authorizationException);

        LogStep("Delete AD group and sync");
        LdapUtil.deleteAdGroup(adGroupName);

        LogStep("Clean up test data");
        cleanupBusinessData(HERD_ADMIN_USER, getBusinessObjectData(schema, objectName), true);
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
        String username = LDAP_MDL_NEWUSER;
        String password = "ldapPwd";
        User newLdapUser = new User(username, password);

        LogStep("Cleanup ldap before test");
        deleteEntryIgnoringError(username);

        LogStep("Create Ldap user and Sync");
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
        String username = LDAP_MDL_DELETEUSER;
        String password = "ldapPwd";
        User newLdapUser = new User(username, password);

        LogStep("Cleanup ldap before test");
        removeUserFromGroupIgnoringError(username, MDL_USER_GROUP);
        deleteEntryIgnoringError(username);

        LogStep("Create ldap user, add user to AD group and sync");
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

        LogStep("Remove user from Ldap");
        LdapUtil.deleteEntry(username);
        syncBdsqlAuth();
        TimeUnit.SECONDS.sleep(15);

        LogVerification("Verify deleted ldapUser cannot access bdsql");
        SQLException exception = assertThrows(SQLException.class, () -> {
            executePrestoSelect(selectQuery, jdbcUrl, newLdapUser);
        });
        verifyErrorMsgIsAuthenticationFailed(exception);
    }

    private void waitForSchemaTableCreated(int timeoutMinutes, String schema, String tablename) throws ClassNotFoundException, InterruptedException {
        String showQuery = String.format("show tables from %s", schema);
        given().atMost(timeoutMinutes, TimeUnit.MINUTES).pollInterval(10, TimeUnit.SECONDS)
            .ignoreException(SQLException.class)
            .until(() -> {
                LOGGER.info("Polling every 10 seconds with timeout: " + timeoutMinutes + " minutes");
                List<String> records = executePrestoSelect(showQuery, getValidPrestoJdbcUrl(schema), User.getLdapMdlAppUser());
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

    private void cleanupBusinessData(User user, BusinessObjectData businessObjectData, boolean deleteNamespace) {
        String namespace = businessObjectData.getNamespace();
        String objectName = businessObjectData.getBusinessObjectDefinitionName();

        try {
            LogStep("Delete Business object data");
            Response response = HerdRestUtil.deleteBusinessObjectData(user, businessObjectData);
            BusinessObjectData responseBusinessObject = response.as(BusinessObjectData.class);

            LogStep("Delete business data from s3 bucket");
            String bucketName = responseBusinessObject.getStorageUnits().get(0).getStorage().getAttributes().stream().filter(attribute -> attribute.getName().equals("bucket.name")).findFirst().get().getValue();
            String directoryPath = responseBusinessObject.getStorageUnits().get(0).getStorageDirectory().getDirectoryPath();
            ShellHelper.executeShellCommand(String.format("aws s3 rm s3://%s/%s/ --recursive", bucketName, directoryPath), new HashMap<>());

            LogStep("Delete business object notification registration");
            HerdRestUtil.deleteBusinessObjectNotification(user, namespace, namespace + "_" + objectName + "_OBJECT_MDL_USAGE_TXT");

            LogStep("Delete business object format registration");
            HerdRestUtil.deleteBusinessObjectFormat(user, businessObjectData);

            LogStep("Delete business object definition");
            HerdRestUtil.deleteBusinessObjectDefinition(user, namespace, objectName);

            if (deleteNamespace) {
                LogStep("Delete namespace");
                HerdRestUtil.deleteNamespace(user, namespace);
            }
        } catch (Exception e){
            LOGGER.info("Failed on data cleanup, doesn't fail the testcase");
            LOGGER.warn(e.getMessage());
        }
    }

    private BusinessObjectData getBusinessObjectData(String namespace, String objectName) {
        BusinessObjectData businessObjectData = new BusinessObjectData();
        businessObjectData.setNamespace(namespace);
        businessObjectData.setBusinessObjectDefinitionName(objectName);
        businessObjectData.setBusinessObjectFormatFileType("TXT");
        businessObjectData.setBusinessObjectFormatUsage("MDL");
        businessObjectData.setBusinessObjectFormatVersion(0);
        businessObjectData.setPartitionValue("2017-08-01");
        businessObjectData.setVersion(0);
        return businessObjectData;
    }
}
