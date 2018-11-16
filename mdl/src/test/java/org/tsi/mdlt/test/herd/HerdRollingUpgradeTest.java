package org.tsi.mdlt.test.herd;

import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import io.restassured.response.Response;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
import org.apache.http.HttpStatus;
import org.finra.herd.model.api.xml.BuildInformation;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.CodeDeployUtils;
import org.tsi.mdlt.aws.LambdaUtils;
import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.test.BaseTest;
import org.tsi.mdlt.util.HerdRestUtil;
import org.tsi.mdlt.util.TestProperties;

public class HerdRollingUpgradeTest extends BaseTest {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(MethodHandles.lookup().lookupClass());

  private static final Properties TEST_PROPERTIES = TestProperties.getProperties();

  @Test
  /*
   * Tests Herd rolling deployments, the test procedure is as follows:
   *
   * 1. Get the current Herd version.
   * 2. Trigger the Herd upgrade Lambda function with the requested version.
   * 3. Wait until the CodeDeploy deployment is successful.
   * 4. Get buildInfo and verify that the new Herd version is what was requested.
   */
  public void testHerdRollingUpgrade() {

    final String instanceName = TEST_PROPERTIES
        .getProperty(StackInputParameterKeyEnum.MDL_INSTANCE_NAME.getKey());
    final String environment = TEST_PROPERTIES
        .getProperty(StackInputParameterKeyEnum.ENVIRONMENT.getKey());

    String lambdaPayload = "{\"RequestedVersion\": \"REQUESTED_VERSION\"}";

    String currentBuildNumber = getHerdBuildNumber();
    LOGGER.info("Current Herd version is: {}", currentBuildNumber);

    // Simulate a rolling upgrade for a build version: 1 greater than the current.
    String requestedVersion = generateRequestedBuildNumber(currentBuildNumber);
    LOGGER.info("Requested version is: {}", requestedVersion);

    lambdaPayload = lambdaPayload.replace("REQUESTED_VERSION", requestedVersion);

    // invoke the herd upgrade Lambda util
    invokeUpgradeUtilLambdaFunction(lambdaPayload);

    // get deployment id
    String deploymentId = getMostRecentDeploymentId(instanceName, environment);

    // wait for deployment
    CodeDeployUtils.waitForMostRecentDeployment(deploymentId);

    // validate Herd version
    String newHerdVersion = getHerdBuildNumber();
    LOGGER.info("New Herd version: {}", currentBuildNumber);

    Assert.assertEquals(requestedVersion, newHerdVersion);
  }

  /**
   * Invokes the upgrade-herd utility Lambda function.
   */
  private void invokeUpgradeUtilLambdaFunction(String lambdaPayload) {

    final String instanceName = TEST_PROPERTIES
        .getProperty(StackInputParameterKeyEnum.MDL_INSTANCE_NAME.getKey());
    final String environment = TEST_PROPERTIES
        .getProperty(StackInputParameterKeyEnum.ENVIRONMENT.getKey());

    String lambdaFunctionName = String
        .format("%s-Herd-%s-UPGRADE-HERD-LAMBDA-FUNCTION", instanceName, environment);

    LambdaUtils.invokeLambdaWithPayload(lambdaFunctionName, lambdaPayload);
  }

  /**
   * Fetches the current version of Herd running in the MDL stack
   *
   * @return String. Herd's build number
   */
  private String getHerdBuildNumber() {

    // get buildInfo
    Response buildInfoResponse = HerdRestUtil.getBuildInfo(HERD_ADMIN_USER);

    // verify success
    Assert.assertEquals(HttpStatus.SC_OK, buildInfoResponse.getStatusCode());

    // Deserialize response
    BuildInformation buildInformation = buildInfoResponse
        .as(BuildInformation.class);

    return buildInformation.getBuildNumber();
  }

  /**
   * Generates a Herd 'requested version' based on a given version. The generated Herd version is
   * simply the semver minor version incremented by one.
   *
   * @param currentBuildNumber the given build number
   * @return String. requested version
   */
  private String generateRequestedBuildNumber(String currentBuildNumber) {

    Integer minorVersion = Integer.parseInt(currentBuildNumber.split(".")[1]);
    Integer requestedMinorVersion = minorVersion + 1;

    return String
        .join(".", new String[]{"0", String.valueOf(requestedMinorVersion), "0"});
  }

  /**
   * Fetches the most recent deployment id from parameter-store, populated by the Herd upgrade utility.
   *
   * @param instanceName the specified MDL instance name
   * @param environment the specified environment
   * @return String. The most recent deployment id
   */
  private String getMostRecentDeploymentId(String instanceName, String environment) {

    final String mostRecentDeploymendIdFormat = "/app/MDL/%s/%s/HERD/MostRecentDeploymentId";

    Parameter parameter = SsmUtil
        .getPlainTextParameter(String.format(mostRecentDeploymendIdFormat, instanceName, environment));

    return parameter.getValue();
  }

}
