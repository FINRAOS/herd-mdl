package org.finra.herd.metastore.managed.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.core.env.Environment;


@Slf4j
@Component
public class OAuthTokenSupplier {


    private ConcurrentMap<String, AccessToken> accessTokenConcurrentMap = new ConcurrentHashMap<>();

    public static final String CRED_FILE_PATH = "cred.file.path";

    private static final String TOKEN_NAME = "metastore_token";

    private static final ObjectMapper mapper = new ObjectMapper();


    @Autowired
    protected Path credentialFilePath;


    @Autowired
    protected Environment environment;


    public String getAccessToken() {


        if (!accessTokenConcurrentMap.containsKey(TOKEN_NAME) ||
                accessTokenConcurrentMap.get(TOKEN_NAME).getExpirationTime().isBefore(LocalDateTime.now().plusMinutes(1))) {

            log.info("<===Generating a new token as one is needed===>");
            try {
                generateToken();
            } catch (Exception e) {
                throw new RuntimeException("Unable to execute call for getting Token" + e.getMessage());
            }
        }

        log.info("Expiration time :{}", accessTokenConcurrentMap.get("metastore_token").getExpiresIn());

        return accessTokenConcurrentMap.get(TOKEN_NAME).getAccessToken();

    }


    private void generateToken() throws Exception {


        HttpPost httpPost = new HttpPost("https://isso-devint.fip.dev.catnms.com/fip//rest/isso/oauth2/access_token?grant_type=client_credentials");
        httpPost.addHeader("Basic", getCredentials());

        CloseableHttpClient client = HttpClients.createDefault();
        AccessToken accessToken = client.execute(httpPost, httpResponse ->
                mapper.readValue(httpResponse.getEntity().getContent(), AccessToken.class));
        accessToken.setExpirationTime(LocalDateTime.now().plusSeconds(Integer.parseInt(accessToken.getExpiresIn())));
        accessTokenConcurrentMap.put(TOKEN_NAME, accessToken);


    }


    /**
     * Reads Credentials from credential file
     *
     * @return
     */
    private String getCredentials() {
        Path path = credentialFilePath;
        try {

            String cmdParamCredFilePath = environment.getProperty(CRED_FILE_PATH);

            // If credential file passed as parameter to the object processor script, use that
            log.info("Credential file Passed as parameter: {}", cmdParamCredFilePath);
            path = Paths.get(cmdParamCredFilePath);


            return Files.lines(path).findFirst().get();
        } catch (IOException e) {
            throw new RuntimeException("Could not read Herd Credentials from: " + path, e);
        }
    }

}