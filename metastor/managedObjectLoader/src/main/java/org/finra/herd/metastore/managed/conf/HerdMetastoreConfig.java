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
package org.finra.herd.metastore.managed.conf;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.hive.jdbc.HiveDriver;
import org.finra.herd.metastore.managed.util.OAuthTokenSupplier;
import org.finra.herd.sdk.api.BusinessObjectDataApi;
import org.finra.herd.sdk.invoker.ApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.retry.annotation.EnableRetry;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.finra.herd.metastore.managed.util.JobProcessorConstants.*;

/**
 * Herd Metastore Configuration
 */

@Slf4j
@Configuration
@EnableRetry
public class HerdMetastoreConfig {
    public static final String homeDir = System.getenv( "HOME" );
    public static final String DM_PASS_FILE_PATH = String.format( "%s/dmCreds/dmPass.base64", homeDir );
    public static final String CRED_FILE_PATH = "cred.file.path";
    public static final String ANALYZE_STATS  = "analyze.stats";
    public static final int ALTER_TABLE_MAX_PARTITIONS = 6000; //Max partitions that can be dropped at a highest partition level.



    @Value( "${MYSQL_URL}" )
    protected String dburl;

    @Value( "${MYSQL_USR}" )
    protected String dbUser;

    @Value( "${MYSQL_PASS}" )
    protected String dbPass;

    @Value( "${DM_URL}" )
    protected String dmUrl;

    @Value( "${JDBC_VALIDATE_QUERY}" )
    protected String validationQuery;


    @Autowired
    protected Environment environment;

    @Autowired
    protected Path credentialFilePath;

    @Autowired
    OAuthTokenSupplier oAuthTokenSupplier;

    @Bean(destroyMethod = "")
    public DataSource getDataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(dburl);
        dataSource.setUsername(dbUser);
        dataSource.setPassword(dbPass);
        dataSource.setInitialSize(2);
        dataSource.setValidationQuery(validationQuery);

        return dataSource;
    }

    @Bean(name = "template")
    public JdbcTemplate getJdbcTemplate() {
        return new JdbcTemplate(getDataSource());
    }

    @Bean
    public Path credentialFilePath() {
        return Paths.get(DM_PASS_FILE_PATH);
    }

    /**
     * Return herd ApiClient used to make calls to Herd Api's
     *
     * @return the Herd ApiClient {@link ApiClient}
     *
     */
    @Bean
    public ApiClient getDMApiClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(dmUrl);

        apiClient.setAccessToken(oAuthTokenSupplier.getAccessToken(getCredentials()));
//        apiClient.addDefaultHeader( "Authorization", String.format( "Basic %s", getCredentials() ) );

        return apiClient;
    }

    /**
     * Reads Credentials from credential file
     *
     * @return
     */
    public String getCredentials() {
        Path path = credentialFilePath;
        try {

            String cmdParamCredFilePath = environment.getProperty( CRED_FILE_PATH );

            // If credential file passed as parameter to the object processor script, use that
            log.info( "Credential file Passed as parameter: {}", cmdParamCredFilePath );
            path = Paths.get( cmdParamCredFilePath );


            return Files.lines( path ).findFirst().get();
        } catch ( IOException e ) {
            throw new RuntimeException( "Could not read Herd Credentials from: " + path, e );
        }
    }

    /**
     * Returns Herd's Business Object Data Api
     *
     * @return BusinessObjectDataApi {@link BusinessObjectDataApi}
     */
    @Bean
    public BusinessObjectDataApi businessObjectDataApi() {
        return new BusinessObjectDataApi( getDMApiClient() );
    }

    @Bean
    public String homeDir(){
        return homeDir;
    }

    @Bean
    public boolean analyzeStats() {
        String stats = environment.getProperty(ANALYZE_STATS);
        log.info("Analyze Stats from CMD: {}", stats);
        return "true".equalsIgnoreCase(stats);
    }


    @Bean(name = "hiveJdbcTemplate")
    public JdbcTemplate hiveJdbcTemplate() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new HiveDriver()
                , HIVE_URL
                , HIVE_USER
                , HIVE_PASSWORD
        );
        return new JdbcTemplate(dataSource);
    }
}
