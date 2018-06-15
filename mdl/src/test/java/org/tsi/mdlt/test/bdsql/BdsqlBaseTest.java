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
package org.tsi.mdlt.test.bdsql;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.test.BaseTest;
import org.tsi.mdlt.test.conditions.DisableWithoutAuthenticationCondition;
import org.tsi.mdlt.util.StackOutputPropertyReader;
import org.tsi.mdlt.util.jdbc.JDBCHelper;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.util.shell.ShellCommandProperty;
import org.tsi.mdlt.util.shell.ShellHelper;

public abstract class BdsqlBaseTest extends BaseTest {

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(DisableWithoutAuthenticationCondition.class)
    public @interface DisableOnAuthenticationDisabled {
    }

    private static Map<String, String> envVars = ShellCommandProperty.getPropertiesMap();

    protected List<String> executePrestoSelect(String query, String jdbcUrl, User user)
            throws ClassNotFoundException, SQLException {
        Properties props = setupForPresto(user);
        return JDBCHelper.executeJDBCQuery(query, jdbcUrl, props, "");
    }

    protected int executePrestoUpdate(String query, String jdbcUrl, User user) throws ClassNotFoundException, SQLException {
        Properties props = setupForPresto(user);
        return JDBCHelper.executeJDBCUpdateQuery(query, jdbcUrl, props);
    }

    private Properties setupForPresto(User user) throws ClassNotFoundException {
        Class.forName("com.facebook.presto.jdbc.PrestoDriver");
        Properties props = new Properties();
        props.put("user", user.getUsername());
        props.put("password", user.getPassword());
        return props;
    }

    protected static String getValidPrestoJdbcUrl(String schema) {
        return StackOutputPropertyReader.get(StackOutputKeyEnum.BDSQL_URL) + "/" + schema;
    }

    protected static void executeShellScript(String shellFile, String... args) throws IOException, InterruptedException {
        Map<String, String> setupEnvVars = new HashMap<>(envVars);
        setupEnvVars.put("InputFilesDir", "./mdl/build");
        String shellCommand = "./mdlt/scripts/sh/shellExecutor.sh "
                + "-w /home/ec2-user "
                + "-c ./mdlt/scripts/sh/presto/"
                + shellFile;
        if (args != null && args.length > 0) {
            shellCommand = shellCommand + "  -a " + StringUtils.join(args, " -a ");
        }
        ShellHelper.executeShellCommand(shellCommand, setupEnvVars);
    }
}
