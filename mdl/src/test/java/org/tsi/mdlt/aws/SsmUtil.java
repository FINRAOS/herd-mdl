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
package org.tsi.mdlt.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.DeleteParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterRequest;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;

/**
 * Used to read/write SSM parameters
 */
public class SsmUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Get parameter using parameter key
     *
     * @param parameterKey parameter key
     * @return Parameter
     */
    public static Parameter getPlainParameter(SsmParameterKeyEnum parameterKey) {
        return getSsmParameter(parameterKey, false);
    }

    /**
     * Get parameter with decrypted value using parameter key
     *
     * @param parameterKey parameter key
     * @return Parameter
     */
    public static Parameter getDecryptedParameter(SsmParameterKeyEnum parameterKey) {
        return getSsmParameter(parameterKey, true);
    }

    public static Parameter getPlainTextParameter(String parameterKey) {
        return getParameter(parameterKey, false);
    }

    public static Parameter getSecureParameter(String parameterKey) {
        return getParameter(parameterKey, true);
    }

    /**
     * Get list of parameters with prefix
     * @param prefix parameter prefix
     * @return list of  parameters
     */
    public static List<Parameter> getParametersWithPrefix(String prefix){
        AWSCredentialsProvider credentials = InstanceProfileCredentialsProvider.getInstance();
        AWSSimpleSystemsManagement simpleSystemsManagementClient =
            AWSSimpleSystemsManagementClientBuilder.standard().withCredentials(credentials)
                .withRegion(Regions.getCurrentRegion().getName()).build();

        GetParametersByPathRequest getParametersByPathRequest = new GetParametersByPathRequest()
            .withPath(prefix)
            .withRecursive(true);

        GetParametersByPathResult parameterResult = simpleSystemsManagementClient.getParametersByPath(getParametersByPathRequest);
        return parameterResult.getParameters();
    }

    private static Parameter getSsmParameter(SsmParameterKeyEnum parameterKey, boolean isEncrypted) {
        return getParameter(parameterKey.getParameterKey(), isEncrypted);
    }

    private static Parameter getParameter(String parameterKey, boolean isEncrypted) {
        LOGGER.info("get ssm parameter key:" + parameterKey);
        AWSCredentialsProvider credentials = InstanceProfileCredentialsProvider.getInstance();
        AWSSimpleSystemsManagement simpleSystemsManagementClient =
            AWSSimpleSystemsManagementClientBuilder.standard().withCredentials(credentials)
                .withRegion(Regions.getCurrentRegion().getName()).build();
        GetParameterRequest parameterRequest = new GetParameterRequest();
        parameterRequest.withName(parameterKey).setWithDecryption(isEncrypted);
        GetParameterResult parameterResult = simpleSystemsManagementClient.getParameter(parameterRequest);
        return parameterResult.getParameter();
    }

    /**
     * Put string parameter to aws ssm
     * @param parameterKey ssm parameter key
     * @param parameterValue ssm parameter value
     */
    public static void putParameter(String parameterKey, String parameterValue) {
        LOGGER.info(String.format("put ssm parameter key %s; with value: %s ", parameterKey, parameterValue));
        AWSCredentialsProvider credentials = InstanceProfileCredentialsProvider.getInstance();
        AWSSimpleSystemsManagement simpleSystemsManagementClient =
            AWSSimpleSystemsManagementClientBuilder.standard().withCredentials(credentials)
                .withRegion(Regions.getCurrentRegion().getName()).build();
        PutParameterRequest parameterRequest = new PutParameterRequest().withName(parameterKey).withValue(parameterValue).withOverwrite(true).withType("String");

        simpleSystemsManagementClient.putParameter(parameterRequest);
    }

    /**
     * Delete parameter from aws ssm
     * @param parameterKey ssm parameter key
     */
    public static void deleteParameter(String parameterKey) {
        LOGGER.info(String.format("delete ssm parameter key %s", parameterKey));
        AWSCredentialsProvider credentials = InstanceProfileCredentialsProvider.getInstance();
        AWSSimpleSystemsManagement simpleSystemsManagementClient =
            AWSSimpleSystemsManagementClientBuilder.standard().withCredentials(credentials)
                .withRegion(Regions.getCurrentRegion().getName()).build();
        DeleteParameterRequest parameterRequest = new DeleteParameterRequest().withName(parameterKey);

        simpleSystemsManagementClient.deleteParameter(parameterRequest);
    }
}
