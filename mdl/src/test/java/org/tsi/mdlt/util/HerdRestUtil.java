package org.tsi.mdlt.util;

import static io.restassured.RestAssured.given;

import java.lang.invoke.MethodHandles;
import java.util.Collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.authentication.PreemptiveBasicAuthScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.pojos.User;

import org.finra.herd.model.api.xml.BusinessObjectData;
import org.finra.herd.model.api.xml.BusinessObjectDefinitionCreateRequest;
import org.finra.herd.model.api.xml.NamespaceCreateRequest;
import org.finra.herd.model.api.xml.NamespacePermissionEnum;
import org.finra.herd.model.api.xml.UserNamespaceAuthorizationCreateRequest;
import org.finra.herd.model.api.xml.UserNamespaceAuthorizationKey;

public class HerdRestUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String HERD_BASE_URL = TestProperties.getPropertyMap().get("HerdHostname") + "/herd-app/rest";

    private static final String GET_BUILD_INFO_URL = "/buildInfo";
    private static final String GET_DATA_PROVIDER_URL = "/dataProviders/{dataProviderName}";
    private static final String POST_DATA_PROVIDER_URL = "/dataProviders";
    private static final String POST_NAMESPACE_URL = "/namespaces";
    private static final String GET_NAMESPACE_URL = "/namespaces/{namespaceCode}";
    private static final String POST_BUSINESS_OBJECT_DEFINITION_URL = "/businessObjectDefinitions";
    private static final String GET_BUSINESS_OBJECT_DEFINITION_URL = "/businessObjectDefinitions/namespaces/{namespace}/businessObjectDefinitionNames/{businessObjectDefinitionName}";
    private static final String DELETE_BUSINESS_OBJECT_NOTIFICATION_URL = "/notificationRegistrations/businessObjectDataNotificationRegistrations/namespaces/{namespace}/notificationNames/{notificationName}";

    private static final String GET_BUSINESS_OBJECT_DATA_URL = "/businessObjectData/namespaces/{namespace}/businessObjectDefinitionNames/{businessObjectDefinitionName}/businessObjectFormatUsages"
        + "/{businessObjectFormatUsage}/businessObjectFormatFileTypes/{businessObjectFormatFileType}/?partitionValue={partitionValue}";
    private static final String DELETE_BUSINESS_OBJECT_DATA_URL = "/businessObjectData/namespaces/{namespace}/businessObjectDefinitionNames/{businessObjectDefinitionName}/businessObjectFormatUsages"
        + "/{businessObjectFormatUsage}/businessObjectFormatFileTypes/{businessObjectFormatFileType}/businessObjectFormatVersions/{businessObjectFormatVersion}"
        + "/partitionValues/{partitionValue}/businessObjectDataVersions/{businessObjectDataVersion}?deleteFiles=false";

    private static final String GET_BUSINESS_OBJECT_FORMAT_URL = "/businessObjectData/namespaces/{namespace}/businessObjectDefinitionNames/{businessObjectDefinitionName}/businessObjectFormatUsages"
        + "/{businessObjectFormatUsage}/businessObjectFormatFileTypes/{businessObjectFormatFileType}/businessObjectFormatVersions/{businessObjectFormatVersion}";
    private static final String DELETE_BUSINESS_OBJECT_FORMAT_URL = "/businessObjectFormats/namespaces/{namespace}/businessObjectDefinitionNames/{businessObjectDefinitionName}/businessObjectFormatUsages"
        + "/{businessObjectFormatUsage}/businessObjectFormatFileTypes/{businessObjectFormatFileType}/businessObjectFormatVersions/{businessObjectFormatVersion}";
    private static final String POST_NAMESPACE_AUTHORIZATION_URL = "/userNamespaceAuthorizations";
    private static final String DELETE_NAMESPACE_AUTHORIZATION_URL = "/userNamespaceAuthorizations/userIds/{userId}/namespaces/{namespace}";

    private static final String POST_EMR_CLUSTER_DEFINITION_URL = "/emrClusterDefinitions";
    private static final String DELETE_EMR_CLUSTER_DEFINITION_URL = "/emrClusterDefinitions/namespaces/{namespace}/emrClusterDefinitionNames/{emrClusterDefinitionName}";

    private static final String POST_EMR_CLUSTER_URL = "/emrClusters";
    private static final String DELETE_EMR_CLUSTER_URL = "/emrClusters/namespaces/{namespace}/emrClusterDefinitionNames/{emrClusterDefinitionName}/emrClusterNames/{emrClusterName}?overrideTerminationProtection=true";

    /**
     * Get herd build info
     *
     * @param user user
     * @return
     */
    public static Response getBuildInfo(User user) {
        return given().spec(given().spec(getHerdBaseRequestSpecification(user))).get(GET_BUILD_INFO_URL);
    }

    /**
     * Create data provider
     *
     * @param user             user to perform rest call
     * @param dataProviderName data provider name
     * @return
     */
    public static Response postDataProvider(User user, String dataProviderName) {
        String createDataProviderBody = String.format("<dataProviderCreateRequest><dataProviderName>%s</dataProviderName></dataProviderCreateRequest>", dataProviderName);
        Response response = given().spec(given().spec(getHerdBaseRequestSpecification(user)))
            .body(createDataProviderBody)
            .post(POST_DATA_PROVIDER_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get data provider
     *
     * @param user             user to perform rest call
     * @param dataProviderName data provider name
     * @return
     */
    public static Response getDataProvider(User user, String dataProviderName) {
        Response response = given().spec(given().spec(getHerdBaseRequestSpecification(user)))
            .pathParam("dataProviderName", dataProviderName)
            .get(GET_DATA_PROVIDER_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Delete data provider
     *
     * @param user             user to perform rest call
     * @param dataProviderName data provider name
     * @return
     */
    public static Response deleteDataProvider(User user, String dataProviderName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("dataProviderName", dataProviderName)
            .delete(GET_DATA_PROVIDER_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Create new namespace
     *
     * @param user      user to perform rest call
     * @param namespace namespace name to create
     * @return
     */
    public static Response postNamespace(User user, String namespace) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .body(new NamespaceCreateRequest(namespace))
            .post(POST_NAMESPACE_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get namespace
     *
     * @param user          user to perform rest call
     * @param namespaceCode namespace name to get info
     * @return
     */
    public static Response getNamespace(User user, String namespaceCode) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespaceCode", namespaceCode)
            .get(GET_NAMESPACE_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Delete new namespace
     *
     * @param user          user to perform rest call
     * @param namespaceCode namespace name to delete
     * @return
     */
    public static Response deleteNamespace(User user, String namespaceCode) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespaceCode", namespaceCode)
            .delete(GET_NAMESPACE_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Create business object definition
     *
     * @param user                         user to perform rest call
     * @param namespace                    namespace name
     * @param businessObjectDefinitionName business object definition name
     * @param dataProviderName             data provider name
     * @return
     */
    public static Response postBusinessObjectDefinition(User user, String namespace, String businessObjectDefinitionName, String dataProviderName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .body(getPostBusinessObjectDefinitionBody(namespace, businessObjectDefinitionName, dataProviderName))
            .post(POST_BUSINESS_OBJECT_DEFINITION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get business object definition
     *
     * @param user                         user to perform rest call
     * @param namespace                    namespace name
     * @param businessObjectDefinitionName business object definition name
     * @return Response
     */
    public static Response getBusinessObjectDefinition(User user, String namespace, String businessObjectDefinitionName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", namespace)
            .pathParam("businessObjectDefinitionName", businessObjectDefinitionName)
            .get(GET_BUSINESS_OBJECT_DEFINITION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get business object data info
     *
     * @param user               user to perform rest call
     * @param businessObjectData business data object, requires namespace, businessObjectDefinitionName, businessObjectFormatUsage
     *                           businessObjectFormatFileType, partitionValue
     * @return Response
     */
    public static Response getBusinessObjectData(User user, BusinessObjectData businessObjectData) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", businessObjectData.getNamespace())
            .pathParam("businessObjectDefinitionName", businessObjectData.getBusinessObjectDefinitionName())
            .pathParam("businessObjectFormatUsage", businessObjectData.getBusinessObjectFormatUsage())
            .pathParam("businessObjectFormatFileType", businessObjectData.getBusinessObjectFormatFileType())
            .pathParam("partitionValue", businessObjectData.getPartitionValue())
            .get(GET_BUSINESS_OBJECT_DATA_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Delete business object data
     *
     * @param user               user to perform rest call
     * @param businessObjectData business data object, requires namespace, businessObjectDefinitionName, businessObjectFormatUsage
     *                           businessObjectFormatFileType, businessObjectFormatVersion, partitionValue, businessObjectDataVersion
     * @return Response
     */
    public static Response deleteBusinessObjectData(User user, BusinessObjectData businessObjectData) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", businessObjectData.getNamespace())
            .pathParam("businessObjectDefinitionName", businessObjectData.getBusinessObjectDefinitionName())
            .pathParam("businessObjectFormatUsage", businessObjectData.getBusinessObjectFormatUsage())
            .pathParam("businessObjectFormatFileType", businessObjectData.getBusinessObjectFormatFileType())
            .pathParam("businessObjectFormatVersion", businessObjectData.getBusinessObjectFormatVersion())
            .pathParam("partitionValue", businessObjectData.getPartitionValue())
            .pathParam("businessObjectDataVersion", businessObjectData.getVersion())
            .delete(DELETE_BUSINESS_OBJECT_DATA_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get business object data format
     *
     * @param user               user to perform rest call
     * @param businessObjectData business data object, requires namespace, businessObjectDefinitionName, businessObjectFormatUsage
     *                           businessObjectFormatFileType, businessObjectFormatVersion
     * @return Response
     */
    public static Response getBusinessObjectFormat(User user, BusinessObjectData businessObjectData) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", businessObjectData.getNamespace())
            .pathParam("businessObjectDefinitionName", businessObjectData.getBusinessObjectDefinitionName())
            .pathParam("businessObjectFormatUsage", businessObjectData.getBusinessObjectFormatUsage())
            .pathParam("businessObjectFormatFileType", businessObjectData.getBusinessObjectFormatFileType())
            .pathParam("businessObjectFormatVersion", businessObjectData.getBusinessObjectFormatVersion())
            .get(GET_BUSINESS_OBJECT_FORMAT_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get business object notification
     *
     * @param user             user to perform rest call
     * @param namespace        namespace
     * @param notificationName notification name
     * @return Response
     */
    public static Response deleteBusinessObjectNotification(User user, String namespace, String notificationName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", namespace)
            .pathParam("notificationName", notificationName)
            .delete(DELETE_BUSINESS_OBJECT_NOTIFICATION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Delete business object notification
     *
     * @param user                user to perform rest call
     * @param businessObjectData, requires namespace, businessObjectDefinitionName, businessObjectFormatUsage, businessObjectFormatFileType, businessObjectFormatVersion
     * @return Response
     */
    public static Response deleteBusinessObjectFormat(User user, BusinessObjectData businessObjectData) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", businessObjectData.getNamespace())
            .pathParam("businessObjectDefinitionName", businessObjectData.getBusinessObjectDefinitionName())
            .pathParam("businessObjectFormatUsage", businessObjectData.getBusinessObjectFormatUsage())
            .pathParam("businessObjectFormatFileType", businessObjectData.getBusinessObjectFormatFileType())
            .pathParam("businessObjectFormatVersion", businessObjectData.getBusinessObjectFormatVersion())
            .delete(DELETE_BUSINESS_OBJECT_FORMAT_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * delete business object definition
     *
     * @param user                         user to perform rest call
     * @param namespace                    namespace
     * @param businessObjectDefinitionName businesse object definition name
     * @return
     */
    public static Response deleteBusinessObjectDefinition(User user, String namespace, String businessObjectDefinitionName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", namespace)
            .pathParam("businessObjectDefinitionName", businessObjectDefinitionName)
            .delete(GET_BUSINESS_OBJECT_DEFINITION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Grant namespace permission to user
     *
     * @param grantor        user to perform rest call
     * @param userId         user id to grant permission
     * @param namespace      namespace to grant permission
     * @param permissionEnum namespace permission type to grant
     * @return
     */
    public static Response grantNamespacePermission(User grantor, String userId, String namespace, NamespacePermissionEnum permissionEnum) {
        Response response = given().spec(getHerdBaseRequestSpecification(grantor))
            .body(getGrantBody(userId, namespace, permissionEnum))
            .post(POST_NAMESPACE_AUTHORIZATION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Delete namespace permission from user
     *
     * @param grantor   user to perform rest call
     * @param userId    user id to remove permission
     * @param namespace namespace permission to remove
     * @return
     */
    public static Response deleteNamespacePermission(User grantor, String userId, String namespace) {
        Response response = given().spec(getHerdBaseRequestSpecification(grantor))
            .pathParam("userId", userId)
            .pathParam("namespace", namespace)
            .delete(DELETE_NAMESPACE_AUTHORIZATION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Create EMR Cluster definition
     *
     * @param user user to perform rest call
     * @param body emr cluster definition body
     * @return Response
     */
    public static Response createClusterDefinition(User user, String body) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .body(body)
            .post(POST_EMR_CLUSTER_DEFINITION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Delete EMR Cluster definition
     *
     * @param user      user to perform rest call
     * @param namespace namespace of the emr cluster definition
     * @return Response
     */
    public static Response deleteClusterDefinition(User user, String namespace, String emrClusterDefinitionName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", namespace)
            .pathParam("emrClusterDefinitionName", emrClusterDefinitionName)
            .delete(DELETE_EMR_CLUSTER_DEFINITION_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Create EMR Cluster
     *
     * @param user user to perform rest call
     * @param body Create EMR cluster request body
     * @return Response
     */
    public static Response createCluster(User user, String body) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .body(body)
            .post(POST_EMR_CLUSTER_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get EMR Cluster information
     *
     * @param user                     user to use for rest call
     * @param namespace                namesapce of the EMR Cluster
     * @param emrClusterDefinitionName EMR Cluster definition name of the EMR Cluster
     * @param emrClusterName           EMR Cluster name
     * @return
     */
    public static Response getCluster(User user, String namespace, String emrClusterDefinitionName, String emrClusterName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", namespace)
            .pathParam("emrClusterDefinitionName", emrClusterDefinitionName)
            .pathParam("emrClusterName", emrClusterName)
            .get(DELETE_EMR_CLUSTER_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Delete EMR Cluster
     *
     * @param user                     user to use for rest call
     * @param namespace                name of the EMR Cluster
     * @param emrClusterDefinitionName EMR Cluster definition name of the EMR Cluster
     * @param emrClusterName           EMR Cluster name
     * @return
     */
    public static Response deleteCluster(User user, String namespace, String emrClusterDefinitionName, String emrClusterName) {
        Response response = given().spec(getHerdBaseRequestSpecification(user))
            .pathParam("namespace", namespace)
            .pathParam("emrClusterDefinitionName", emrClusterDefinitionName)
            .pathParam("emrClusterName", emrClusterName)
            .delete(DELETE_EMR_CLUSTER_URL);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    /**
     * Get deep link url
     *
     * @param user user to perform rest call
     * @param url  depp lin url
     * @return
     */
    public static Response getDeepLink(User user, String url) {
        Response response = given()
            .spec(getBaseRequestSpecification(user))
            .get(url);
        LOGGER.debug(prettyJsonResponse(response));
        return response;
    }

    private static RequestSpecification getBaseRequestSpecification(User user) {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder()
            .log(LogDetail.ALL)
            .setRelaxedHTTPSValidation()
            .setContentType("application/xml");

        //Only set authentication when auth is enabled
        if (BooleanUtils.toBoolean(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH))) {
            LOGGER.info("Setting Basic Auth for user: " + user.getUsername());
            PreemptiveBasicAuthScheme authenticationScheme = new PreemptiveBasicAuthScheme();
            authenticationScheme.setUserName(user.getUsername());
            authenticationScheme.setPassword(user.getPassword());
            requestSpecBuilder.setAuth(authenticationScheme)
                .setAuth(authenticationScheme);
        }
        return requestSpecBuilder.build().redirects().follow(true);
    }

    private static RequestSpecification getHerdBaseRequestSpecification(User user) {
        return getBaseRequestSpecification(user).baseUri(HERD_BASE_URL);
    }

    private static UserNamespaceAuthorizationCreateRequest getGrantBody(String userId, String namespace, NamespacePermissionEnum namespacePermissionEnum) {
        UserNamespaceAuthorizationKey userNamespaceAuthorizationKey = new UserNamespaceAuthorizationKey(userId, namespace);
        return new UserNamespaceAuthorizationCreateRequest(userNamespaceAuthorizationKey, Collections.singletonList(namespacePermissionEnum));
    }

    private static BusinessObjectDefinitionCreateRequest getPostBusinessObjectDefinitionBody(String namespace, String businessObjectDefinitionName, String dataProviderName) {
        BusinessObjectDefinitionCreateRequest businessObjectDefinitionCreateRequest = new BusinessObjectDefinitionCreateRequest();
        businessObjectDefinitionCreateRequest.setNamespace(namespace);
        businessObjectDefinitionCreateRequest.setBusinessObjectDefinitionName(businessObjectDefinitionName);
        businessObjectDefinitionCreateRequest.setDataProviderName(dataProviderName);
        return businessObjectDefinitionCreateRequest;
    }

    private static String prettyJsonResponse(Response response){
        String s = response.asString();
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(s);
        }
        catch (JsonProcessingException e) {
            LOGGER.warn(e.getMessage());
            return s;
        }
    }
}
