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
package org.tsi.mdlt.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.util.StackOutputPropertyReader;

/**
 * Shepherd ui automation: happypath, http/https, authentication testcases
 */
@Disabled
public class UiTest extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    //private static final boolean IS_HTTPS_ENABLED = Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_HTTPS));
    private static final boolean IS_HTTPS_ENABLED = true;
    //private static final boolean IS_AUTH_ENABLED = Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH));
    private static final boolean IS_AUTH_ENABLED = true;

    private static final By HEADER = By.xpath("//h2");
    //private static final String LDAP_USER = SsmUtil.getParameterByName(SsmParameterKeyEnum.APP_USER).getValue();
    //private static final String LDAP_PASSWORD = SsmUtil.getDecryptedParameterByName(SsmParameterKeyEnum.APP_PASSWORD).getValue();
    private static final String LDAP_USER = "ldap_app";
    private static final String LDAP_PASSWORD = "ZDQ2ZmZkMGY3";

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeAll
    public static void beforeAll() {
        String osName = System.getProperty("os.name");
        //https://chromedriver.storage.googleapis.com/index.html?path=2.38/
        LOGGER.info("System name is:" + osName);
        if (osName.contains("Windows")) {
            System.setProperty("webdriver.chrome.driver", "src/test/resources/driver/chromedriver_windows.exe");
        }
        else if (osName.contains("Linux")) {
            System.setProperty("webdriver.chrome.driver", "src/test/resources/driver/chromedriver_linux");
        }
        else {
            System.setProperty("webdriver.chrome.driver", "src/test/resources/driver/chromedriver_linux");
        }
    }

    @Test
    public void shepherdTest() {
        LogStep("Open shepherd home page");
        //driver.get(StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_WEBSITE_URL));
        String url = "http://" + "{{account-number}}-mdlauthoverweek-shepherd-dev.s3-website-us-east-1.amazonaws.com";
        driver.get(url);
        LogStep("Login shepherd if enable auth is true");
        if (IS_AUTH_ENABLED) {
            loginShepHerd(LDAP_USER, LDAP_PASSWORD);
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//form//button")));
        }

        LogVerification("Verify header text is correct");
        wait.until(ExpectedConditions.presenceOfElementLocated(HEADER));
        WebElement element = driver.findElement(HEADER);
        assert element.getText().contains("MDL");
    }

    @Test
    public void testHerdHttpIsNotAllowedWhenHttpsEnabled() {
        if (IS_HTTPS_ENABLED) {
            LogVerification("Verify herd/shepherd call pass with http url when enableHttps is false");
            String url = StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_WEBSITE_URL);
            url.replace("https", "http");

            assertThrows(RuntimeException.class, () -> {
                driver.get(url);
            });
        }
        else {
            LogStep("Skip http shepherd test when https is not enabled");
        }
    }

    @Test
    public void testHerdHttpsIsNotAllowedWhenHttpEnabled() {
        if (!IS_HTTPS_ENABLED) {
            LogVerification("Verify herd/shepherd call pass with http url when enableHttps is false");
            String url = StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_WEBSITE_URL);
            String httpsUrl = url.replace("http", "https");

            assertThrows(RuntimeException.class, () -> {
                driver.get(httpsUrl);
            });
        }
        else {
            LogStep("Skip https shepherd test when https enabled");
        }
    }

    @Test
    public void testShepherdWithWrongCredential() throws IOException, InterruptedException {
        if (IS_AUTH_ENABLED) {
            LogStep("Open shepherd home page");
            driver.get(StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_WEBSITE_URL));

            LogStep("Login shepherd with wrong credential");
            if (IS_AUTH_ENABLED) {
                loginShepHerd(LDAP_USER, "wrongpwd");
            }

            LogVerification("Verify login is not successful, login pop is present");
            //TODO popup not able to be identified by selenium, so just wait and check text now
            Thread.sleep(30000);
            assert driver.findElement(HEADER).getText().equals("Login");
        }
    }

    @BeforeEach
    public void setUp() {
        DesiredCapabilities capabilities = DesiredCapabilities.chrome();
        capabilities.setCapability("chrome.switches", Arrays.asList("--ignore-certificate-errors"));
        driver = new ChromeDriver();
        //driver = new ChromeDriver();
        wait = new WebDriverWait(driver, 60);
    }

    //@BeforeEach
    public void setupSauceLab() throws MalformedURLException {
        String username = "maggie";
        String accessKey = "98111f43-d3f5-4c1b-8885-48ebcfd017b1";
        String url = "http://" + username + ":" + accessKey + "@ondemand.saucelabs.com:80/wd/hub";

        DesiredCapabilities caps = DesiredCapabilities.chrome();
        caps.setCapability("platform", "Windows 10");
        caps.setCapability("version", "66.0");

        driver = new RemoteWebDriver(new URL(url), caps);
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.close();
        }
    }

    private void loginShepHerd(String username, String password) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("username")));
        WebElement usernameInput = ((ChromeDriver) driver).findElementById("username");
        usernameInput.sendKeys(username);

        WebElement passwordInput = ((ChromeDriver) driver).findElementById("password");
        System.out.print(password);
        passwordInput.sendKeys(password);
        WebElement button = ((ChromeDriver) driver).findElementByXPath("//form//button");
        button.click();
    }
}
