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
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;

/**
 * Used to read/write SSM parameters
 */
public class SsmUtil {

    /**
     * Get parameter using parameter key
     *
     * @param parameterKey parameter key
     * @return Parameter
     */
    public static Parameter getParameterByName(SsmParameterKeyEnum parameterKey) {
        return getParameterByName(parameterKey, false);
    }

    /**
     * Get parameter with decrypted value using parameter key
     *
     * @param parameterKey parameter key
     * @return Parameter
     */
    public static Parameter getDecryptedParameterByName(SsmParameterKeyEnum parameterKey) {
        return getParameterByName(parameterKey, true);
    }

    private static Parameter getParameterByName(SsmParameterKeyEnum parameterKey,
            boolean isEncrypted) {
        AWSCredentialsProvider credentials = InstanceProfileCredentialsProvider.getInstance();
        AWSSimpleSystemsManagement simpleSystemsManagementClient =
                AWSSimpleSystemsManagementClientBuilder.standard().withCredentials(credentials)
                        .withRegion(Regions.getCurrentRegion().getName()).build();
        GetParameterRequest parameterRequest = new GetParameterRequest();
        parameterRequest.withName(parameterKey.getParameterKey()).setWithDecryption(isEncrypted);
        GetParameterResult parameterResult = simpleSystemsManagementClient.getParameter(parameterRequest);
        return parameterResult.getParameter();
    }
}
