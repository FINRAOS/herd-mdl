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
package org.tsi.mdlt.pojos;

import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;

public class User {
    private String username;
    private String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public static User getLdapAdminUser() {
        return new User(SsmUtil.getPlainLdapParameter(SsmParameterKeyEnum.ADMIN_USER).getValue(),
                SsmUtil.getDecryptedLdapParameter(SsmParameterKeyEnum.ADMIN_PASSWORD).getValue());
    }

    public static User getLdapMdlAppUser() {
        return new User(SsmUtil.getPlainLdapParameter(SsmParameterKeyEnum.MDL_APP_USER).getValue(),
                SsmUtil.getDecryptedLdapParameter(SsmParameterKeyEnum.MDL_APP_PASSWORD).getValue());
    }

    public static User getLdapSecAppUser() {
        return new User(SsmUtil.getPlainLdapParameter(SsmParameterKeyEnum.SEC_APP_USER).getValue(),
                SsmUtil.getDecryptedLdapParameter(SsmParameterKeyEnum.SEC_APP_PASSWORD).getValue());
    }

    public static User getNoAuthValidJdbcUser(){
        return new User("randomuser", "");
    }
}
