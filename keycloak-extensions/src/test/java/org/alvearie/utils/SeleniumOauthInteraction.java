/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.utils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.bouncycastle.util.encoders.Base64;
import org.jsmart.zerocode.core.httpclient.BasicHttpClient;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class SeleniumOauthInteraction {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumOauthInteraction.class);

    @Inject(optional = true)
    @Named("keycloak.username")
    private String appidUser;

    @Inject(optional = true)
    @Named("keycloak.password")
    private String appidPwd;

    @Inject(optional = true)
    @Named("webdriver.binary.path")
    private String driverPath;

    @Inject(optional = true)
    @Named("browser.binary.path")
    private String browserPath;

    @Inject(optional = true)
    @Named("app.clientid")
    private String appClientId;

    @Inject(optional = true)
    @Named("app.redirecturi")
    private String appRedirectUri;

    @Inject(optional = true)
    @Named("oauth.token.service.url")
    private String oauthTokenUrl;

    public static Map<String, String> oauthToken = new HashMap<String,String>();


    public SeleniumOauthInteraction( String user, String password, String webdriver_path, String browser_path, String appclient_id, String appredirect_uri, String oauthtoken_url){
        appidUser=user;
        appidPwd=password;
        driverPath=webdriver_path;
        browserPath=browser_path;
        appClientId=appclient_id;
        appRedirectUri=appredirect_uri;
        oauthTokenUrl=oauthtoken_url;
    }

    public SeleniumOauthInteraction() {
    }

    public Map<String, String> fetchToken() throws UnsupportedEncodingException,
    URISyntaxException {

        // if oauthToken is already generated in the same session, then use the existing one.
        if (oauthToken.isEmpty() || oauthToken == null) {
            String URL = oauthTokenUrl
                    + "/openid-connect/auth?response_type=code&state=123&client_id=" + appClientId
                    + "&scope=patient/*.read+launch/patient+openid+fhirUser+offline_access"
                    + "&redirect_uri=" + appRedirectUri;
            /*String URL = oauthTokenUrl
                    + "/openid-connect/auth?response_type=code&state=123&client_id=" + appClientId
                    + "&scope=patient/*.read+launch/patient+openid+fhirUser+online_access"
                    + "&redirect_uri=" + appRedirectUri;*/

            // Get the authorization code from keycloak
            Map<String, String> keys = runUI(URL);
            String jsonbody = "{\"grant_type\":\"authorization_code\",\"code\":\"" + keys.get(
                    "code") + "\",\"client_id\":\"" + appClientId + "\",\"redirect_uri\":\""
                    + appRedirectUri + "\"}";

            // Now using the authorization code , retrieve token details.
            BasicHttpClient bh = new BasicHttpClient();
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            Map<String, String> tokenDetails = new HashMap<>();
            Response r;
            try {
                r = bh.execute(
                        oauthTokenUrl + "/openid-connect/token",
                        "POST", headers, null, jsonbody);

                tokenDetails = new ObjectMapper().readValue(r.getEntity().toString(),
                        HashMap.class);

            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.info("Caught exception running POST to " + oauthTokenUrl + "/openid-connect/token");
            }
            oauthToken = tokenDetails;
        }

        return oauthToken;
    }

    public Map<String, String> fetchTokenWith(Map<String,String> requestparamters)
            throws UnsupportedEncodingException, URISyntaxException {

        // if oauthToken is already generated in the same session, then use the existing one.
        if (oauthToken.isEmpty() || oauthToken == null) {
            String URL = oauthTokenUrl
                    + "/openid-connect/auth?response_type=code&state=123&client_id=" + appClientId
                    + "&scope="+ requestparamters.get("scope")
                    + "&redirect_uri=" + appRedirectUri;
            /*String URL = oauthTokenUrl
            + "/openid-connect/auth?response_type=code&state=123&client_id=" + appClientId
            + "&scope=patient/*.read+launch/patient+openid+fhirUser+online_access"
            + "&redirect_uri=" + appRedirectUri;*/

            String userNameIs = requestparamters.get("username");
            userNameIs = (userNameIs != null) ? userNameIs : "";
            if (!userNameIs.isEmpty())
                this.appidUser = userNameIs;
            // Get the authorization code from keycloak
            Map<String, String> keys = runUI(URL);
            String jsonbody = "{\"grant_type\":\"authorization_code\",\"code\":\"" + keys.get(
                    "code") + "\",\"client_id\":\"" + appClientId + "\",\"redirect_uri\":\""
                    + appRedirectUri + "\"}";

            // Now using the authorization code , retrieve token details.
            BasicHttpClient bh = new BasicHttpClient();
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            Map<String, String> tokenDetails = new HashMap<>();
            Response r;
            try {
                r = bh.execute(
                        oauthTokenUrl + "/openid-connect/token",
                        "POST", headers, null, jsonbody);

                tokenDetails = new ObjectMapper().readValue(r.getEntity().toString(),
                        HashMap.class);

            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.info("Caught exception running POST to " + oauthTokenUrl + "/openid-connect/token");
            }
            oauthToken = tokenDetails;
        }

        return oauthToken;
    }

    public Map<String, String> fetchTokenWithW3(Map<String,String> requestparamters)
            throws UnsupportedEncodingException, URISyntaxException {

           // if oauthToken is already generated in the same session, then use the existing one.
        if (oauthToken.isEmpty() || oauthToken == null) {
            fetchFreshTokenWith(requestparamters, "w3");
        }

        return oauthToken;
    }

    public Map<String, String> fetchFreshTokenWith(Map<String,String> requestparamters)
            throws UnsupportedEncodingException, URISyntaxException {
        return fetchFreshTokenWith(requestparamters, null);
    }

    public Map<String, String> fetchFreshTokenWithW3(Map<String,String> requestparamters)
            throws UnsupportedEncodingException, URISyntaxException {
        return fetchFreshTokenWith(requestparamters, "w3");
    }

    public Map<String, String> fetchFreshTokenWith(Map<String,String> requestparamters, String loginType)
            throws UnsupportedEncodingException, URISyntaxException {

        LOGGER.info("Getting new token");
        // If request parameters are set, use those.  If not use values in property file
        String authUrl = oauthTokenUrl + "/openid-connect/auth";
        String tokenUrl = oauthTokenUrl + "/openid-connect/token";
        if(requestparamters.containsKey("authUrl"))
            authUrl = requestparamters.get("authUrl");
        if(requestparamters.containsKey("tokenUrl"))
            tokenUrl = requestparamters.get("tokenUrl");
        if(requestparamters.containsKey("clientId"))
            appClientId = requestparamters.get("clientId");
        if(requestparamters.containsKey("username"))
            appidUser = requestparamters.get("username");
        if(requestparamters.containsKey("password"))
            appidPwd = requestparamters.get("password");

        String URL = authUrl
            + "?response_type=code&state=123&client_id=" + appClientId
            + "&scope="+ requestparamters.get("scope")
            + "&redirect_uri=" + appRedirectUri;
        /*String URL = oauthTokenUrl
        + "/openid-connect/auth?response_type=code&state=123&client_id=" + appClientId
        + "&scope=patient/*.read+launch/patient+openid+fhirUser+online_access"
        + "&redirect_uri=" + appRedirectUri;*/
        String userNameIs = requestparamters.get("username");
        userNameIs = (userNameIs != null) ? userNameIs : "";
        if (!userNameIs.isEmpty())
            this.appidUser = userNameIs;
        // Get the authorization code

        Map<String, String> keys = runUI(URL, loginType);
        String jsonbody = "{\"grant_type\":\"authorization_code\",\"code\":\"" + keys.get(
            "code") + "\",\"client_id\":\"" + appClientId + "\",\"redirect_uri\":\""
            + appRedirectUri + "\"}";

        // Now using the authorization code , retrieve token details.
        BasicHttpClient bh = new BasicHttpClient();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        // If clientId and secret are set, use these
        if(requestparamters.containsKey("clientId") && requestparamters.containsKey("secret")) {
            String auth = appClientId + ":" + requestparamters;
            headers.put("Authorization", "Basic " + Base64.encode(auth.getBytes("UTF8")));
        }

        Map<String, String> tokenDetails = new HashMap<>();
        Response r;
        try {
            r = bh.execute(
                    tokenUrl, "POST", headers, null, jsonbody);
                    tokenDetails = new ObjectMapper().readValue(r.getEntity().toString(),
                        HashMap.class);
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.info("Caught exception running POST to " + oauthTokenUrl + "/openid-connect/token");
            }
        oauthToken = tokenDetails;
        return oauthToken;
    }

    public Map<String, String> fetchCode(String URL) throws UnsupportedEncodingException, URISyntaxException {

        Map<String, String> code = runUI(URL);

        return code;
    }

    public Map<String, String> runUI(String url) throws URISyntaxException, UnsupportedEncodingException {

        return runUI(url, null);
    }

    public Map<String, String> runUI(String url, String loginType) throws URISyntaxException, UnsupportedEncodingException {

        Map<String, String> response = new HashMap<String, String>();
        System.setProperty("webdriver.gecko.driver", driverPath);
        System.setProperty("webdriver.firefox.bin",browserPath);

        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(true);

        WebDriver driver = new FirefoxDriver(options);

        // comment the above 2 lines and uncomment below 2 lines to use Chrome
        // System.setProperty("webdriver.chrome.driver","G:\\chromedriver.exe");
        // WebDriver driver = new ChromeDriver();
        try {
            // String baseUrl =
            // "https://us-south.wh-cmsiop.dev.watson-health.ibm.com/auth/realms/test-4196/protocol/openid-connect/auth?response_type=code&state=123&client_id=inferno&scope=patient/*.read+launch/patient+openid+fhirUser+offline_access&redirect_uri=http://localhost:4567/inferno/oauth2/static/redirect";
            String baseUrl = url;
            // launch Fire fox and direct it to the Base URL
            driver.get(baseUrl);


            if(loginType != null && loginType.equalsIgnoreCase("w3")) {

                WebElement signInButton = (new WebDriverWait(driver, 10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.id("credentialSignin")));
                signInButton.click();
                WebElement dynamicElement = (new WebDriverWait(driver, 10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.id("user-name-input")));
                dynamicElement.sendKeys(appidUser);
                WebElement dynamicElementPw = (new WebDriverWait(driver, 10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.id("password-input")));
                dynamicElementPw.sendKeys(appidPwd);
                //driver.findElement(By.id("password-input")).sendKeys(appidPwd);
                driver.findElement(By.id("login-button")).click();

                Boolean loginButtonDisappeared = (new WebDriverWait(driver, 5, 200))
                        .until(ExpectedConditions.invisibilityOfElementLocated(By.id("login-button")));

                // TODO do this smartly later... for now, give it time to get passed the verify screen
                Thread.sleep(5000);

                // Handling of 2FA if prompted
                //WebElement otpInput = (new WebDriverWait(driver, 10))
                //        .until(ExpectedConditions.presenceOfElementLocated(By.id("otp-input")));
                WebElement otpInput = null;
                try {
                    otpInput = driver.findElement(By.id("otp-input"));
                } catch (NoSuchElementException e) {
                    // Do nothing
                }


            }

            // Different logic depending on if login is keycloak or w3
            else {

                WebElement dynamicElement = (new WebDriverWait(driver, 10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.id("email")));
                dynamicElement.sendKeys(appidUser);

                driver.findElement(By.id("password")).sendKeys(appidPwd);
                driver.findElement(By.id("cd_login_button")).click();

                Boolean loginButtonDisappeared = (new WebDriverWait(driver, 5, 200))
                        .until(ExpectedConditions.invisibilityOfElementLocated(By.id("cd_login_button")));

                LOGGER.debug("Login button is visible?? " + !loginButtonDisappeared);
                // At this point we'll either find:
                // 1) 'Update Account Information' - user created with sign_up API.
                // 2) findElement(By.id("kc-page-title") value == "Grant Access...")
                // 3) URL with 'code=' in parameter list

                // Wait up to 2 seconds for screen showing 'Update Account Information'
                // kc-page-title=Update Account Information
                // input id=username
                // input id=email
                // input id=firstName
                // input id=lastName
                // div id=kc-form-buttons -> submit button
                try {
                    // wait up to 2 seconds - poll for element every 200 ms
                    WebElement lastNameInput = (new WebDriverWait(driver, 2, 200))
                            .until(ExpectedConditions.presenceOfElementLocated(By.id("lastName")));
                    lastNameInput.sendKeys("CMS-Test");

                    driver.findElement(By.id("kc-form-buttons")).click();
                } catch ( TimeoutException e )
                {
                    // Didn't find YES button with id='kc-login'.
                    // Ignore exception; probably not the first sign-on for this user.
                    LOGGER.error("Didn't find YES button with id='kc-forms-buttons' - ignore exception" + e.getMessage());
                }


                // wait up to 2 seconds for screen showing YES/NO page for first-time users.
                // Page contents:
                //   kc-page-title=Grant Access to inferno
                //   li - permissions granted
                //   input id=kc-cancel NO
                //   input kd=kc-login YES
                try {
                    // either the button is found or a TimeoutException is generated
                    WebElement grantAccessButton = (new WebDriverWait(driver, 1,200))
                            .until(ExpectedConditions.presenceOfElementLocated(By.id("kc-login")));

                    grantAccessButton.click();
                } catch ( TimeoutException e )
                {
                    // Didn't find YES button with id='kc-login'.
                    // Ignore exception; probably not the first sign-on for this user.
                    LOGGER.error("Didn't find YES button with id='kc-login' - ignore exception" + e.getMessage());
                }
            }

            // poll at 500 ms interval until 'code' is present in URL query parameter list.
            // Usually this loop completes in  under 2.5 seconds.
            for ( int i =0; i< 100; i++)
            {
                Thread.sleep(500);
                response = getQueryMap( driver.getCurrentUrl() );
                if ( response.keySet().contains("code") )
                {
                    break;
                }
            }

            // close Fire fox
            driver.close();

        } catch (Exception e) {
            // adding the while loop to avoid the timing issues and reliably
            // extract the grant code from next page after clicking the login
            // button
            // Note: the handling in exception is required because on some
            // operating systems the selenium webdriver exits browser when
            // the recipient host is unable to connect.
            int cnt = 0;
            while (!driver.getCurrentUrl().contains("code=")) {
                try {
                    Thread.sleep(500);
                    cnt++;
                    if (cnt > 10) {
                        LOGGER.debug("Waiting for page to retrieve grant code, Round ... " + cnt);
                        break;
                    }
                } catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
            if (!driver.getCurrentUrl().contains("code=")) {
                LOGGER.error("Something went wrong during the oauth code retreival process using browser" + e.getMessage());
            } else {
                response = getQueryMap(driver.getCurrentUrl());
            }
            driver.close();

        }
        return response;

    }


    private Map<String, String> getQueryMap(String url) {
        Map<String, String> response = new HashMap<String, String>();
        String[] pairs;
        try {
            pairs = new URI(url).getQuery().split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                response.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }

        } catch (URISyntaxException e) {
            // catch exception -- hopefully next URL is successful
            e.printStackTrace();
        } catch ( UnsupportedEncodingException e ) {
            // catch exception -- hopefully next URL is successful
            e.printStackTrace();
        }

        return response;
    }

    // main method allows for easy standalone testing. This could be moved to a separate file.
    public static void main(String[] args) throws UnsupportedEncodingException, URISyntaxException {
        SeleniumOauthInteraction s = new SeleniumOauthInteraction();
        s.appidUser = "a";
        s.appidPwd = "a";
        s.driverPath = "/usr/local/bin/geckodriver";
        s.browserPath = "/Applications/Firefox.app/Contents/MacOS/firefox";

        String realm = "test";
        String client = "test";

        String url = "https://localhost:8080/auth/realms/" + realm
                + "/protocol/openid-connect/auth?response_type=code&state=123&client_id=" + client
                + "&scope=launch/patient+openid+fhirUser+offline_access"
                + "&redirect_uri=http://localhost";
        Map<String, String> rc = s.fetchCode(url);

        System.out.println(rc);
    }
}