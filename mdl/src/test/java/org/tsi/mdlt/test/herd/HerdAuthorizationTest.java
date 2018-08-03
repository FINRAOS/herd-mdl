package org.tsi.mdlt.test.herd;


import static io.restassured.RestAssured.given;

import java.lang.invoke.MethodHandles;

import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.util.HerdRestUtil;
public class HerdAuthorizationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final User SEC_APP_USER = new User("ldap_sec_app_user", "N2Q2OTAxYWNj");



    @Test
    @Tag("authTest")
    public void testHerdAdminUser() {


    }


    @Test
    @Tag("authTest")
    public void testHerdReadOnlyUser() {

        String url = "https://google.com";
        //url = "https://demotestherd.poc.aws.cloudfjord.com/herd-app/rest/buildInfo";
        //url = "https://AWSALBdemotestHerdprod-778227999.us-east-1.elb.amazonaws.com/herd-app/rest/buildInfo";

        Response responsetest = given().log().all()
            .config(RestAssured.config().sslConfig(new SSLConfig().allowAllHostnames()))
            .auth().preemptive().basic(SEC_APP_USER.getUsername(), SEC_APP_USER.getPassword())
            .relaxedHTTPSValidation()
            .contentType("application/xml")
            .get(url);
        LOGGER.info(responsetest.asString());


    }

    @Test
    public void test(){
        Response response = HerdRestUtil.getBuildInfo(SEC_APP_USER);
        LOGGER.info(response.asString());
    }


    @Test
    @Tag("authTest")
    public void testHerdNamespaceWriteUser() {

    }

}
