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
package org.tsi.mdlt.test.herd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.test.BaseTest;
import org.tsi.mdlt.util.HerdRestUtil;

import org.finra.herd.model.api.xml.BusinessObjectData;
import org.finra.herd.model.api.xml.NamespacePermissionEnum;

@Tag("authTest")
public class HerdAuthorizationTest extends BaseTest {

    private static final User SEC_APP_USER = User.getLdapSecAppUser();
    private static final User MDL_APP_USER = User.getLdapMdlAppUser();
    private static final User HERD_RO_USER = User.getHerdRoUser();
    private static final User HERD_ADMIN_USER = User.getHerdAdminUser();

    private static final String NAMESPACE_MDL = "MDL";
    private static final String NAMESPACE_SEC = "SEC_MARKET_DATA";

    @Test
    public void readWriteNamespaceWithPermission() {
        String dataProvider = "MDLT_DP1";

        LogStep("Call Write and Read non-namespace restricted endpoints with write permission");
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.postDataProvider(SEC_APP_USER, dataProvider).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getDataProvider(SEC_APP_USER, dataProvider).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.deleteDataProvider(SEC_APP_USER, dataProvider).statusCode());

        LogStep("Read business object data with namespace permission");
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBusinessObjectData(SEC_APP_USER, getSecBusinessObjectData()).statusCode());

        LogStep("Read business object format with namespace permission");
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBusinessObjectFormat(SEC_APP_USER, getSecBusinessObjectData()).statusCode());

        LogStep("Write with namespace permission");
        BusinessObjectData secBusinessObjectData = getSecBusinessObjectData();
        secBusinessObjectData.setBusinessObjectDefinitionName("InvalidValue");
        assertTrue(HerdRestUtil.deleteBusinessObjectData(SEC_APP_USER, secBusinessObjectData).statusCode() != HttpStatus.SC_FORBIDDEN);
    }

    @Test
    public void readWriteToNamespaceWithoutPermission() {
        LogStep("Read business object data without namespace permission");
        assertEquals(HttpStatus.SC_FORBIDDEN, HerdRestUtil.getBusinessObjectData(MDL_APP_USER, getSecBusinessObjectData()).statusCode());

        LogStep("Read business object format without namespace permission");
        assertEquals(HttpStatus.SC_FORBIDDEN, HerdRestUtil.getBusinessObjectFormat(MDL_APP_USER, getSecBusinessObjectData()).statusCode());

        LogStep("Write without namespace permission");
        assertEquals(HttpStatus.SC_FORBIDDEN, HerdRestUtil.deleteBusinessObjectData(MDL_APP_USER, getSecBusinessObjectData()).statusCode());
    }

    @Test
    public void testHerdReadOnlyUser() {
        LogStep("Call Read endpoints using ReadOnly User");
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBuildInfo(HERD_RO_USER).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getNamespace(HERD_RO_USER, NAMESPACE_MDL).statusCode());

        LogStep("Call Read endpoints with various endpoints using ReadOnly User");
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBusinessObjectData(SEC_APP_USER, getSecBusinessObjectData()).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBusinessObjectData(HERD_RO_USER, getMdlBusinessObjectData()).statusCode());

        LogStep("Call Write endpoint using ReadOnly User");
        Response writeResponse = HerdRestUtil.postDataProvider(HERD_RO_USER, "unAuthorizedDataProvider");
        LogVerification("Verify Readonly user cannot write");
        assertEquals(HttpStatus.SC_FORBIDDEN, writeResponse.statusCode(), "ReadOnly user should not have permission to write");
    }

    @Test
    public void testHerdAdminUser() {
        LogStep("Call Read endpoints with Admin User");
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBuildInfo(HERD_ADMIN_USER).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getNamespace(HERD_ADMIN_USER, NAMESPACE_MDL).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBusinessObjectData(HERD_ADMIN_USER, getMdlBusinessObjectData()).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getBusinessObjectData(HERD_ADMIN_USER, getSecBusinessObjectData()).statusCode());

        LogStep("Call Write endpoints with Admin User");
        String namespace = "MDLT_ADMIN_NM";
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.postNamespace(HERD_ADMIN_USER, namespace).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.getNamespace(HERD_ADMIN_USER, namespace).statusCode());
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.deleteNamespace(HERD_ADMIN_USER, namespace).statusCode());

        LogStep("Write namespace restricted endpoint with Admin User");
        BusinessObjectData secBusinessObjectData = getSecBusinessObjectData();
        secBusinessObjectData.setBusinessObjectDefinitionName("InvalidValue");
        BusinessObjectData mdlBusinessObjectData = getSecBusinessObjectData();
        mdlBusinessObjectData.setBusinessObjectDefinitionName("InvalidValue");
        assertTrue(HerdRestUtil.deleteBusinessObjectData(HERD_ADMIN_USER, secBusinessObjectData).statusCode() != HttpStatus.SC_FORBIDDEN);
        assertTrue(HerdRestUtil.deleteBusinessObjectData(HERD_ADMIN_USER, mdlBusinessObjectData).statusCode() != HttpStatus.SC_FORBIDDEN);

        LogStep("verify user get access denied read without permission");
        assertEquals(HttpStatus.SC_FORBIDDEN, HerdRestUtil.getBusinessObjectData(MDL_APP_USER, getSecBusinessObjectData()).statusCode());

        LogStep("grant permission to user");
        HerdRestUtil.grantNamespacePermission(HERD_ADMIN_USER, MDL_APP_USER.getUsername(), NAMESPACE_SEC, NamespacePermissionEnum.READ);
        assertTrue(HerdRestUtil.getBusinessObjectData(MDL_APP_USER, getSecBusinessObjectData()).statusCode() != HttpStatus.SC_FORBIDDEN);

        LogStep("revoke permission from user");
        HerdRestUtil.deleteNamespacePermission(HERD_ADMIN_USER, MDL_APP_USER.getUsername(), NAMESPACE_SEC);
        assertEquals(HttpStatus.SC_FORBIDDEN, HerdRestUtil.getBusinessObjectData(MDL_APP_USER, getSecBusinessObjectData()).statusCode());
    }

    private BusinessObjectData getSecBusinessObjectData() {
        BusinessObjectData secBuzObjData = new BusinessObjectData();
        secBuzObjData.setNamespace(NAMESPACE_SEC);
        secBuzObjData.setBusinessObjectDefinitionName("TradeData");
        secBuzObjData.setBusinessObjectFormatUsage("MDL");
        secBuzObjData.setBusinessObjectFormatFileType("TXT");
        secBuzObjData.setPartitionKey("TDATE");
        secBuzObjData.setPartitionValue("2017-08-01");
        secBuzObjData.setBusinessObjectFormatVersion(0);
        secBuzObjData.setVersion(0);
        return secBuzObjData;
    }

    private BusinessObjectData getMdlBusinessObjectData() {
        BusinessObjectData secBuzObjData = new BusinessObjectData();
        secBuzObjData.setNamespace(NAMESPACE_MDL);
        secBuzObjData.setBusinessObjectDefinitionName("MDL_OBJECT");
        secBuzObjData.setBusinessObjectFormatUsage("MDL");
        secBuzObjData.setBusinessObjectFormatFileType("TXT");
        secBuzObjData.setPartitionKey("TDATE");
        secBuzObjData.setPartitionValue("2017-01-02");
        secBuzObjData.setBusinessObjectFormatVersion(0);
        secBuzObjData.setVersion(0);
        return secBuzObjData;
    }
}
