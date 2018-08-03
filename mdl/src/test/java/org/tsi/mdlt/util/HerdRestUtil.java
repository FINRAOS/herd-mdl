package org.tsi.mdlt.util;

import static io.restassured.RestAssured.given;

import io.restassured.authentication.PreemptiveBasicAuthScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.tsi.mdlt.pojos.User;

public class HerdRestUtil {


    //private static final String HERD_BASE_URL = StackOutputPropertyReader.get(StackOutputKeyEnum.HERD_URL) + "/herd-app/rest";
    private static final String HERD_BASE_URL = "https://AWSALBdemotestHerdprod-778227999.us-east-1.elb.amazonaws.com/herd-app/rest/";
    //private static final String HERD_BASE_URL = "https://demotestherd.poc.aws.cloudfjord.com/herd-app/rest/";

    private static final String GET_BUILD_INFO_URL = "/buildInfo";
    private static final String GET_DATA_PROVIDER_URL = "/dataProviders/{dataProviderName}";
    private static final String POST_DATA_PROVIDER_URL = "/dataProviders";
    private static final String POST_NAMESPACE_URL = "/namespaces";
    private static final String GET_NAMESPACE_URL = "/namespaces/{namespaceCode}";
    private static final String POST_BUSINESS_OBJECT_DEFINITION_URL = "/businessObjectDefinitions";
    private static final String GET_BUSINESS_OBJECT_DEFINITION_URL = "/businessObjectDefinitions/namespaces/{namespace}/businessObjectDefinitionNames/{businessObjectDefinitionName}";

    public static Response getBuildInfo(User user) {
        return given().spec(getBaseRequestSpecification(user)).get(GET_BUILD_INFO_URL);
    }

    public static Response postDataProvider(User user, String dataProviderName) {
        String createDataProviderBody = String.format("<dataProviderCreateRequest><dataProviderName></dataProviderName></dataProviderCreateRequest>", dataProviderName);
        return getBaseRequestSpecification(user)
            .body(createDataProviderBody)
            .post(POST_DATA_PROVIDER_URL);
    }

    public static Response getDataProvider(User user, String dataProviderName) {
        return getBaseRequestSpecification(user)
            .pathParam("dataProviderName", dataProviderName)
            .get(GET_DATA_PROVIDER_URL);
    }

    public static Response deleteDataProvider(User user, String dataProviderName) {
        return getBaseRequestSpecification(user)
            .pathParam("dataProviderName", dataProviderName)
            .delete(GET_DATA_PROVIDER_URL);
    }

    public static Response postNamespace(User user, String namespace) {
        String createNamespaceBody = String.format("<namespaceCreateRequest><namespaceCode>%s</namespaceCode></namespaceCreateRequest>", namespace);
        return getBaseRequestSpecification(user)
            .body(createNamespaceBody)
            .post(POST_NAMESPACE_URL);
    }

    public static Response getNamespace(User user, String namespaceCode) {
        return getBaseRequestSpecification(user)
            .pathParam("namespaceCode", namespaceCode)
            .get(GET_NAMESPACE_URL);
    }

    public static Response deleteNamespace(User user, String namespaceCode) {
        return getBaseRequestSpecification(user)
            .pathParam("namespaceCode", namespaceCode)
            .delete(GET_NAMESPACE_URL);
    }

    public static Response postBusinessObjectDefinition(User user, String namespace, String businessObjectDefinitionName, String dataProviderName) {
        return getBaseRequestSpecification(user)
            .body(constructPostBusinessObjectDefinitionBody(namespace, businessObjectDefinitionName, dataProviderName))
            .post(POST_BUSINESS_OBJECT_DEFINITION_URL);
    }

    public static Response getBusinessObjectDefinition(User user, String namespace, String businessObjectDefinitionName) {
        return getBaseRequestSpecification(user)
            .pathParam("namespace", namespace)
            .pathParam("businessObjectDefinitionName", businessObjectDefinitionName)
            .get(GET_BUSINESS_OBJECT_DEFINITION_URL);
    }

    public static Response deleteBusinessObjectDefinition(User user, String namespace, String businessObjectDefinitionName) {
        return getBaseRequestSpecification(user)
            .pathParam("namespace", namespace)
            .pathParam("businessObjectDefinitionName", businessObjectDefinitionName)
            .delete(GET_BUSINESS_OBJECT_DEFINITION_URL);
    }

    private static RequestSpecification getBaseRequestSpecification(User user) {
        PreemptiveBasicAuthScheme authenticationScheme = new PreemptiveBasicAuthScheme();
        authenticationScheme.setUserName(user.getUsername());
        authenticationScheme.setPassword(user.getPassword());
        return new RequestSpecBuilder().setAuth(authenticationScheme)
            .log(LogDetail.ALL)
            .setBaseUri(HERD_BASE_URL)
            .setRelaxedHTTPSValidation()
            .setContentType("application/xml").build();
    }

    private static String constructPostBusinessObjectDefinitionBody(String namespace, String businessObjectDefinitionName, String dataProviderName) {
        String businessObjectDefinitionBody = "<businessObjectDefinitionCreateRequest>\n"
            + "    <namespace>{{namespace}}</namespace>\n"
            + "    <businessObjectDefinitionName>{{businessObjectDefinitionName}}</businessObjectDefinitionName>\n"
            + "    <dataProviderName>{{dataProviderName}}</dataProviderName>\n"
            + "</businessObjectDefinitionCreateRequest>";
        return businessObjectDefinitionBody
            .replace("{{namespace}}", namespace)
            .replace("{{businessObjectDefinitionName}}", businessObjectDefinitionName)
            .replace("{{dataProviderName}}", dataProviderName);

    }

}
