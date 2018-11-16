package org.tsi.mdlt.aws;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.codedeploy.AmazonCodeDeploy;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClientBuilder;
import com.amazonaws.services.codedeploy.model.GetDeploymentRequest;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterParameters;
import com.amazonaws.waiters.WaiterTimedOutException;
import com.amazonaws.waiters.WaiterUnrecoverableException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CodeDeployUtils {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(MethodHandles.lookup().lookupClass());

  private CodeDeployUtils() {
    // utility class
  }

  /**
   * Waits for a given deployment to succeed (or fail).
   *
   * @param deploymentId: String. The specified deployment's id.
   */
  public static void waitForMostRecentDeployment(String deploymentId) {

    LOGGER.debug("Waiting on deployment with id: \'{}\'", deploymentId);

    AmazonCodeDeploy codeDeployClient = AmazonCodeDeployClientBuilder.standard()
        .withRegion(Regions.US_EAST_1).build();

    GetDeploymentRequest getDeploymentRequest = new GetDeploymentRequest();
    getDeploymentRequest.setDeploymentId(deploymentId);

    Waiter<GetDeploymentRequest> waiter = codeDeployClient.waiters().deploymentSuccessful();
    try {

      waiter.run(new WaiterParameters<>(getDeploymentRequest));
      LOGGER.info("Deployment was successful.");

    } catch (WaiterUnrecoverableException | WaiterTimedOutException e) {
      LOGGER.error("Deployment with id: {} was unsuccessful.", deploymentId);
    }

  }

}
