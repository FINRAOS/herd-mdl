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
package org.tsi.mdlt.enums;

import org.tsi.mdlt.util.TestProperties;

public enum SsmParameterKeyEnum {
    ADMIN_USER("ldapAppUser", "/app/MDL/{instanceName}/{environment}/LDAP/AdministratorName"),
    ADMIN_PASSWORD("ldapAppPassword", "/app/MDL/{instanceName}/{environment}/LDAP/AdministratorPassword"),

    MDL_APP_USER("ldapAppUser", "/app/MDL/{instanceName}/{environment}/LDAP/MdlAppUsername"),
    MDL_APP_PASSWORD("ldapAppPassword", "/app/MDL/{instanceName}/{environment}/LDAP/MDLAppPassword"),

    SEC_APP_USER("ldapSecUser", "/app/MDL/{instanceName}/{environment}/LDAP/SecAppUsername"),
    SEC_APP_PASSWORD("ldapSecUserPassword", "/app/MDL/{instanceName}/{environment}/LDAP/SecAppPassword"),

    LDAP_DN("ldap_dn", "/app/MDL/{instanceName}/{environment}/LDAP/BaseDN"),
    LDAP_HOSTNAME("ldap_hostname", "/app/MDL/{instanceName}/{environment}/LDAP/HostName"),
    AUTH_GROUP("authGroup", "/app/MDL/{instanceName}/{environment}/LDAP/AuthGroup"),

    VPC_ID("vpcId", "/global/{instanceName}/{environment}/VPC/ID"),
    PRIVATE_SUBNETS("privateSubnets", "/global/{instanceName}/{environment}/VPC/SubnetIDs/private");

    private String variableName;
    private String parameterKey;

    SsmParameterKeyEnum(String variableName, String parameterKey) {
        this.variableName = variableName;
        this.parameterKey = parameterKey;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getParameterKey() {
        return parameterKey.replace("{instanceName}", TestProperties.get(StackInputParameterKeyEnum.MDL_INSTANCE_NAME))
            .replace("{environment}", TestProperties.get(StackInputParameterKeyEnum.ENVIRONMENT));
    }

}
