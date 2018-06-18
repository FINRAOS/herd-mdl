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
package org.tsi.mdlt.util.jdbc;

public class JDBCTestCase {

    private final String name;
    private final String description;

    private final String[] query;
    private final JDBCConnProps connProps;
    private String delimiter;
    private int waitAfterCompletion;
    private String[] assertVal;

    public JDBCTestCase(String name, String description, String[] query,
            JDBCConnProps connProps) {
        this.name = name;
        this.description = description;
        this.query = query;
        this.connProps = connProps;
        this.delimiter = ",";
        this.waitAfterCompletion = 0;
        this.assertVal = null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getQuery() {
        return query;
    }

    public JDBCConnProps getConnProps() {
        return connProps;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public int getWaitAfterCompletion() {
        return waitAfterCompletion;
    }

    public String[] getAssertVal() {
        return assertVal;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public void setWaitAfterCompletion(int waitForCompletion) {
        if (waitForCompletion < 0) {
            waitForCompletion = 0;
        }
        this.waitAfterCompletion = waitForCompletion;
    }

    public void setAssertVal(String[] assertVal) {
        this.assertVal = assertVal;
    }

    public static class JDBCConnProps {

        private final String jdbcDriver;
        private final String jdbcURL;
        private final String user;
        private final String password;

        public JDBCConnProps(String jdbcDriver, String jdbcURL,
                String user, String password) {
            this.jdbcDriver = jdbcDriver;
            this.jdbcURL = jdbcURL;
            this.user = user;
            this.password = password;
        }

        public String getJdbcDriver() {
            return jdbcDriver;
        }

        public String getJdbcURL() {
            return jdbcURL;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }
    }
}
