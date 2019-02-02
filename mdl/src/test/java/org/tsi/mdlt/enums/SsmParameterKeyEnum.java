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
    ADMIN_USER("ldapAdminUser", "/app/MDL/{instanceName}/{environment}/LDAP/User/AdministratorName"),
    ADMIN_PASSWORD("ldapAdminPassword", "/app/MDL/{instanceName}/{environment}/LDAP/Password/AdministratorPassword"),

    HERD_ADMIN_USER("herdAdminUser", "/app/MDL/{instanceName}/{environment}/LDAP/User/HerdAdminUsername"),
    Herd_ADMIN_PASSWORD("herdAdminPassword", "/app/MDL/{instanceName}/{environment}/LDAP/Password/HerdAdminPassword"),

    HERD_RO_USER("herdRoUser", "/app/MDL/{instanceName}/{environment}/LDAP/User/HerdRoUsername"),
    HERD_RO_PASSWORD("herdRoPassword", "/app/MDL/{instanceName}/{environment}/LDAP/Password/HerdRoPassword"),

    HERD_BASIC_USER("herdRoUser", "/app/MDL/{instanceName}/{environment}/LDAP/User/HerdBasicUsername"),
    HERD_BASIC_PASSWORD("herdRoPassword", "/app/MDL/{instanceName}/{environment}/LDAP/Password/HerdBasicUserPassword"),

    MDL_APP_USER("ldapAppUser", "/app/MDL/{instanceName}/{environment}/LDAP/User/HerdMdlUsername"),
    MDL_APP_PASSWORD("ldapAppPassword", "/app/MDL/{instanceName}/{environment}/LDAP/Password/HerdMdlPassword"),

    SEC_APP_USER("ldapSecUser", "/app/MDL/{instanceName}/{environment}/LDAP/User/HerdSecUsername"),
    SEC_APP_PASSWORD("ldapSecUserPassword", "/app/MDL/{instanceName}/{environment}/LDAP/Password/HerdSecPassword"),

    LDAP_DN("ldap_dn", "/app/MDL/{instanceName}/{environment}/LDAP/BaseDN"),
    LDAP_HOSTNAME("ldap_hostname", "/app/MDL/{instanceName}/{environment}/LDAP/HostName"),

    VPC_ID("vpcId", "/global/{instanceName}/{environment}/VPC/ID"),
    PRIVATE_SUBNETS("privateSubnets", "/global/{instanceName}/{environment}/VPC/SubnetIDs/private"),

    GLUE_SCHEMA_LAMBDA_NAME("glueFunction", "/app/MDL/{instanceName}/{environment}/Lambda/GlueSchemaLambdaArn");

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
