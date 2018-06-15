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

//TODO ldap password location will be changed after open ldap ssm renamed
public enum SsmParameterKeyEnum {
    ADMIN_USER("ldapAppUser", "/mdl/ldap/admin_user"),
    ADMIN_PASSWORD("ldapAppPassword", "/mdl/ldap/admin_pass"),

    APP_USER("ldapAppUser", "/mdl/ldap/app_user"),
    APP_PASSWORD("ldapAppPassword", "/mdl/ldap/app_pass"),

    TEST_USER_1("ldapTestUser1", "/mdl/ldap/mdl_test_1_user"),
    TEST_USER_1_PASSWORD("ldapTestUser1Password", "/mdl/ldap/mdl_test_1_pass"),

    TEST_USER_2("ldapTestUser2", "/mdl/ldap/mdl_test_2_user"),
    TEST_USER_2_PASSWORD("ldapTestUser2Password", "/mdl/ldap/mdl_test_2_pass"),

    LDAP_DN("ldap_dn", "/mdl/ldap/base_dn"),
    LDAP_HOSTNAME("ldap_dn", "/mdl/ldap/hostname");

    private String variableName;
    private String parameterKey;

    SsmParameterKeyEnum(String variableName, String parameterKey) {
        this.variableName = variableName;
        this.parameterKey = parameterKey;
    }

    public String getParameterKey() {
        return parameterKey;
    }

    public String getVariableName() {
        return variableName;
    }
}
