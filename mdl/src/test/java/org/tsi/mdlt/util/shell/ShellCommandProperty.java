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
package org.tsi.mdlt.util.shell;

import java.util.Map;

import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.util.StackOutputPropertyReader;
import org.tsi.mdlt.util.TestProperties;

public class ShellCommandProperty {

    private static Map<String, String> propertiesMap;

    /**
     * Get properties to be used in shell command, properties include: StackInput, StackOutput, and SSM parameters
     *
     * @return properties for shell command as  map
     */
    public static Map<String, String> getPropertiesMap() {
        if (propertiesMap != null) {
            return propertiesMap;
        }
        propertiesMap = StackOutputPropertyReader.getTestProperties();
        addAllRequiredSsmToMap(propertiesMap);
        propertiesMap.putAll(TestProperties.getPropertyMap());
        return propertiesMap;
    }

    private static void addAllRequiredSsmToMap(Map<String, String> propertiesMap) {
        addSsmParameterToMap(SsmParameterKeyEnum.MDL_APP_USER, propertiesMap);
        addDecryptedSsmParameterToMap(SsmParameterKeyEnum.MDL_APP_PASSWORD, propertiesMap);
        addSsmParameterToMap(SsmParameterKeyEnum.HERD_ADMIN_USER, propertiesMap);
        addDecryptedSsmParameterToMap(SsmParameterKeyEnum.Herd_ADMIN_PASSWORD, propertiesMap);
        addSsmParameterToMap(SsmParameterKeyEnum.SEC_APP_USER, propertiesMap);
        addDecryptedSsmParameterToMap(SsmParameterKeyEnum.SEC_APP_PASSWORD, propertiesMap);
    }

    private static void addSsmParameterToMap(SsmParameterKeyEnum ssmParameterKeyEnum,
            Map<String, String> propertiesMap) {
        boolean isAuthEnabled = Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH));
        String value = isAuthEnabled ? SsmUtil.getDecryptedParameter(ssmParameterKeyEnum).getValue() : "randomusername";
        propertiesMap.put(ssmParameterKeyEnum.getVariableName(), value);
    }

    //when auth is disable, jdbc password cannot be provided, tweak this way because of current jdbc json framework
    private static void addDecryptedSsmParameterToMap(SsmParameterKeyEnum ssmParameterKeyEnum,
            Map<String, String> propertiesMap) {
        boolean isAuthEnabled = Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH));
        String value = isAuthEnabled ? SsmUtil.getDecryptedParameter(ssmParameterKeyEnum).getValue() : "";
        propertiesMap.put(ssmParameterKeyEnum.getVariableName(), value);
    }
}
