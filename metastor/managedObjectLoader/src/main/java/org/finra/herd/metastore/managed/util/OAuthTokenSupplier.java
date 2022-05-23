package org.finra.herd.metastore.managed.util;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class OAuthTokenSupplier {

 private RestTemplate restTemplate;

 private ConcurrentMap<String,AccessToken> accessTokenConcurrentMap= new ConcurrentHashMap<>();

 public static final String CRED_FILE_PATH = "cred.file.path";

 @Autowired
 protected Path credentialFilePath;


 @Autowired
 protected Environment environment;



 @Autowired
 public OAuthTokenSupplier(RestTemplate restTemplate){

  this.restTemplate=restTemplate;
 }

 public String getAccessToken(){



  if (!accessTokenConcurrentMap.containsKey("metastore_token") ||
          accessTokenConcurrentMap.get("metastore_token").getExpirationTime().isBefore(LocalDateTime.now().plusMinutes(1)))
  {
   // Create a new token if there is no token in the cache, or the cached token is expired or
   // almost expired
   log.info("The token is about to expire");
   generateToken();
  }

  log.info("Expiration time :{}",accessTokenConcurrentMap.get("metastore_token").getExpiresIn());

  return accessTokenConcurrentMap.get("metastore_token").getAccessToken();

 }


 private void generateToken(){

  try
  {
   HttpHeaders headers = new HttpHeaders();
   headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
   headers.setContentType(MediaType.APPLICATION_JSON);
   headers.add("Authorization", "Basic " + getCredentials());
   HttpEntity<String> entity = new HttpEntity<>(null, headers);
   ResponseEntity<AccessToken> response = restTemplate.exchange("https://isso-devint.fip.dev.catnms.com/fip//rest/isso/oauth2/access_token?grant_type=client_credentials", HttpMethod.POST, entity, AccessToken.class);
   AccessToken accessToken = response.getBody();
   // calculate the token expiration time
   accessToken.setExpirationTime(LocalDateTime.now().plusSeconds(Integer.parseInt(accessToken.getExpiresIn())));
   accessTokenConcurrentMap.put("metastore_token", accessToken);
  }
  catch (RestClientException restClientException)
  {
   throw new RuntimeException("Rest Exception:Unable to generate Token"+restClientException.getMessage());
  }
  catch (Exception exception)
  {
   throw new RuntimeException("Exception:Unable to generate token"+exception.getMessage());
  }

 }


 /**
  * Reads Credentials from credential file
  *
  * @return
  */
 private String getCredentials() {
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

}