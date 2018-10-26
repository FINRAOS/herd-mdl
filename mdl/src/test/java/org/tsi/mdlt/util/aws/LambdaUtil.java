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
package org.tsi.mdlt.util.aws;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LambdaUtil {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(MethodHandles.lookup().lookupClass());


  private LambdaUtil() {
    //utility class
  }

  /**
   * Async-invokes a given lambda with a specified payload.
   *
   * @param functionName the given function name
   * @param payload the given payload to supply to the function
   */
  public static void invokeLambdaWithPayload(String functionName, String payload) {

    LOGGER.debug("Attempting to invoke function: \'{}\' with payload \'{}\'", functionName, payload);

    AWSLambdaAsync lambda = AWSLambdaAsyncClientBuilder.standard()
        .withRegion(Regions.US_EAST_1).build();
    InvokeRequest req = new InvokeRequest()
        .withFunctionName(functionName)
        .withPayload(ByteBuffer.wrap(payload.getBytes()));

    Future<InvokeResult> result = lambda.invokeAsync(req);

    LOGGER.debug("Waiting for lambda function to execute and return.");
    while (!result.isDone()) {
      try {
        TimeUnit.SECONDS.sleep(10);
      } catch (InterruptedException e) {
        LOGGER.error("Thread.sleep() was interrupted!");
        throw new RuntimeException("Lambda execution was interrupted.", e);
      }
    }

    try {
      InvokeResult res = result.get();
      if (res.getStatusCode() == HttpStatus.SC_OK) {
        LOGGER.debug("Response received from lambda function.");
        ByteBuffer responsePayload = res.getPayload();
        LOGGER.debug("Response: {}", new String(responsePayload.array()));
      } else {
        LOGGER.error("Received a non-OK response from AWS: %d\n",
            res.getStatusCode());
      }
    } catch (InterruptedException | ExecutionException | SdkClientException e) {
      LOGGER.error("Lambda execution failed. ", e);
      throw new RuntimeException("Lambda execution failed.", e);
    }

  }
}
