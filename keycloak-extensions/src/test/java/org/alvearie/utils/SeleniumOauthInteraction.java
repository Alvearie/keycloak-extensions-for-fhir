/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SeleniumOauthInteraction {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumOauthInteraction.class);

    private String appClientId;
    private String appRedirectUri;
    private String oauthAuthUrl;
    private String oauthTokenUrl;

    private WebDriver driver;

    public SeleniumOauthInteraction(String appclient_id, String appredirect_uri,
            String oauth_auth_url, String oauth_token_url){
        appClientId = appclient_id;
        appRedirectUri = appredirect_uri;
        oauthAuthUrl = oauth_auth_url;
        oauthTokenUrl = oauth_token_url;

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.setHeadless(true);
        driver = new ChromeDriver(options);
    }

    /**
     * Hits the configured auth url with the configured client_id and redirect_uri, as well as
     * the passed audience and scopes, then tests the login forms with the passed username and password via
     * Selenium WebDriver.
     *
     * @param user
     * @param pass
     * @param aud
     * @param scope one or more requested scopes
     * @return a map of key-value pairs from the query string of the redirect location on the auth response
     * @throws Exception
     */
    public Map<String, String> fetchCode(String user, String pass, String aud, String... scope) throws Exception {
        Map<String, String> response = new HashMap<String, String>();

        try {
            BasicNameValuePair[] params = new BasicNameValuePair[] {
                    new BasicNameValuePair("response_type", "code"),
                    new BasicNameValuePair("state", UUID.randomUUID().toString()),
                    new BasicNameValuePair("client_id", appClientId),
                    new BasicNameValuePair("redirect_uri", appRedirectUri),
                    new BasicNameValuePair("aud", aud),
                    new BasicNameValuePair("scope", String.join(" ", scope)),
            };
            String queryString = URLEncodedUtils.format(Arrays.asList(params), UTF_8);

            // launch Firefox and direct it to the Base URL
            driver.get(oauthAuthUrl + "?" + queryString);

            WebElement dynamicElement = (new WebDriverWait(driver, 10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.id("username")));
            dynamicElement.sendKeys(user);

            driver.findElement(By.id("password")).sendKeys(pass);
            driver.findElement(By.id("kc-login")).click();

            Boolean loginButtonDisappeared = (new WebDriverWait(driver, 5, 200))
                    .until(ExpectedConditions.invisibilityOfElementLocated(By.id("kc-login")));
            LOGGER.debug("Login button is visible?? " + !loginButtonDisappeared);

            // At this point we'll either find:
            // A) patient selection form
            // B) consent grant form
            // C) URL with 'code=' in the query string
            // D) 'Update Account Information' - user created with sign_up API.

            // Wait up to 3 seconds for screen showing 'Select patient'
            // Page contents:
            //   div class=login-pf-page
            //   div class=card-pf
            //   header.div kc-username
            //   div kc-content
            //   form id=patient-selection
            //   input id=<patient_id> (one per patient that the user has access to)
            //   input id=submit
            try {
                // wait up to 3 seconds - poll for element every 200 ms
                new WebDriverWait(driver, 3, 200)
                        .until(ExpectedConditions.presenceOfElementLocated(By.id("patient-selection")));

                // simulate choosing the patient that has an id of "PatientA"
                driver.findElement(By.id("PatientA")).click();
                driver.findElement(By.id("submit")).click();

            } catch ( TimeoutException e ) {
                LOGGER.error("Expected the patient selection form but didn't find it", e);
                fail("Expected the patient selection form but didn't find it");
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
            } catch ( TimeoutException e ) {
                // Didn't find YES button with id='kc-login'.
                // Ignore exception; probably not the first sign-on for this user.
                LOGGER.error("Didn't find YES button with id='kc-login' - ignore exception" + e.getMessage());
            }

            // poll at 500 ms interval until 'code' is present in URL query parameter list.
            // Usually this loop completes in  under 2.5 seconds.
            for ( int i =0; i< 100; i++) {
                Thread.sleep(500);
                response = getQueryMap( driver.getCurrentUrl() );
                if ( response.keySet().contains("code") )
                {
                    break;
                }
            }
        } catch (Exception e) {
            if (driver == null || driver.getCurrentUrl() == null) {
                throw e;
            }
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
                    // do nothing
                }
            }
            if (!driver.getCurrentUrl().contains("code=")) {
                LOGGER.error("Something went wrong during the oauth code retreival process", e);
            } else {
                response = getQueryMap(driver.getCurrentUrl());
            }
        } finally {
            if (driver != null) {
                // close Firefox
                driver.close();
            }
        }
        return response;
    }

    /**
     * Exchange the code for a token (with default params)
     *
     * @param code
     * @return
     * @throws Exception
     */
    public Map<String, String> fetchToken(String code) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        Map<String,String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("client_id", appClientId);
        params.put("redirect_uri", appRedirectUri);

        return fetchTokenWith(params);
    }

    /**
     * Invoke the token endpoint with the passed params.
     *
     * @param requestParams
     * @return
     * @throws Exception
     */
    public Map<String, String> fetchTokenWith(Map<String,String> requestParams) throws Exception {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(oauthTokenUrl);

        Response response = target.request().post(Entity.form(new MultivaluedHashMap<String, String>(requestParams)));

        @SuppressWarnings("unchecked")
        Map<String, String> result = response.readEntity(HashMap.class);
        return result;
    }

    private Map<String, String> getQueryMap(String url) throws Exception {
        Map<String, String> response = new HashMap<String, String>();
        String[] pairs = new URI(url).getQuery().split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            response.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return response;
    }

    // main method allows for easy standalone testing. This could be moved to a separate file.
    public static void main(String[] args) throws Exception {
        String realm = "test";
        String baseUrl = "http://localhost:8080/auth/realms/" + realm + "/protocol/openid-connect/";

        SeleniumOauthInteraction s = new SeleniumOauthInteraction("test", "https://localhost",
                baseUrl + "auth", baseUrl + "token");

        Map<String, String> authResponse = s.fetchCode("a", "a", "https://localhost:9443/fhir-server/api/v4",
                "openid", "launch/patient");
        Map<String, String> tokenResponse = s.fetchToken(authResponse.get("code"));

        System.out.println(tokenResponse);
    }
}
