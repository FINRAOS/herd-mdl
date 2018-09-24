package org.tsi.mdlt.test.herd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.response.Response;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.Test;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.test.BaseTest;
import org.tsi.mdlt.util.HerdRestUtil;
import org.tsi.mdlt.util.StackOutputPropertyReader;
import org.tsi.mdlt.util.TestProperties;

public class HerdDeepLinkTest extends BaseTest {

    private static final User HERD_ADMIN_USER = User.getHerdAdminUser();

    @Test
    public void testDeepLink() {
        String shepherdUrl = StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_URL);
        if(!BooleanUtils.toBoolean(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH))){
            shepherdUrl = StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_WEBSITE_URL) + ":80";
        }
        String data_entity_url = shepherdUrl + "/data-entities/SEC_MARKET_DATA/SecurityData";
        String data_object_format_url = shepherdUrl + "/formats/MDL/MDL_OBJECT/MDL/TXT/0";
        String data_object_url = shepherdUrl + "/data-objects/MDL/MDL_OBJECT/MDL/TXT/0";

        validDeepLinkUrl(data_entity_url);
        validDeepLinkUrl(data_object_format_url);
        validDeepLinkUrl(data_object_url);
    }

    private void validDeepLinkUrl(String url) {
        String redirectJs = "src=\"inline.bundle.js\"";
        Response response = HerdRestUtil.getDeepLink(HERD_ADMIN_USER, url);
        String errorMsg = String.format("Deep link response expects to contain string %s, but found: \n %s", redirectJs, response.body().asString());
        assertTrue(response.body().asString().contains(redirectJs), errorMsg);
    }
}
