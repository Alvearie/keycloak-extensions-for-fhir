/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.jsmart.zerocode.core.httpclient.BasicHttpClient;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SeleniumOauthInteraction {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumOauthInteraction.class);

    @Inject(optional = true)
    @Named("keycloak.username")
    private String keycloakUser;

    @Inject(optional = true)
    @Named("keycloak.password")
    private String keycloakPwd;

    @Inject(optional = true)
    @Named("app.clientid")
    private String appClientId;

    @Inject(optional = true)
    @Named("app.redirecturi")
    private String appRedirectUri;

    @Inject(optional = true)
    @Named("oauth.auth.url")
    private String oauthAuthUrl;

    @Inject(optional = true)
    @Named("oauth.token.url")
    private String oauthTokenUrl;

    private WebDriver driver;

    private Map<String, String> tokenResponse = new HashMap<String,String>();

    public SeleniumOauthInteraction(String user, String password, String appclient_id, String appredirect_uri,
            String oauth_auth_url, String oauth_token_url){
        this();
        keycloakUser = user;
        keycloakPwd = password;
        appClientId = appclient_id;
        appRedirectUri = appredirect_uri;
        oauthAuthUrl = oauth_auth_url;
        oauthTokenUrl = oauth_token_url;
    }

    public SeleniumOauthInteraction() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.setHeadless(true);

        driver = new ChromeDriver(options);
    }

    /**
     * Returns a previously obtained token response or runs the entire implicit auth flow (fetchCode followed by fetchToken)
     * to obtain a new one.
     *
     * @return
     * @throws Exception
     */
    public Map<String, String> getToken() throws Exception {
        // if oauthToken is already generated in the same session, then use the existing one.
        if (tokenResponse != null && !tokenResponse.isEmpty()) {
            return tokenResponse;
        }

        Map<String,String> loginResponseParams = fetchCode("fhirUser", "launch/patient");
        return fetchToken(loginResponseParams.get("code"));
    }

    /**
     * @param url
     * @return
     * @throws Exception
     */
    public Map<String, String> fetchCode(String... scope) throws Exception {
        Map<String, String> response = new HashMap<String, String>();

        try {
            BasicNameValuePair[] params = new BasicNameValuePair[] {
                    new BasicNameValuePair("response_type", "code"),
                    new BasicNameValuePair("state", UUID.randomUUID().toString()),
                    new BasicNameValuePair("client_id", appClientId),
                    new BasicNameValuePair("scope", String.join(" ", scope)),
                    new BasicNameValuePair("redirect_uri", appRedirectUri)
            };
            String queryString = URLEncodedUtils.format(Arrays.asList(params), UTF_8);

            // launch Firefox and direct it to the Base URL
            driver.get(oauthAuthUrl + "?" + queryString);

            WebElement dynamicElement = (new WebDriverWait(driver, 10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.id("username")));
            dynamicElement.sendKeys(keycloakUser);

            driver.findElement(By.id("password")).sendKeys(keycloakPwd);
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

                // simulate choosing the patient that has an id of "example"
                driver.findElement(By.id("example")).click();
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
        BasicHttpClient bh = new BasicHttpClient();
        Map<String, Object> headers = new HashMap<String, Object>();
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
            BasicHttpClient bh = new BasicHttpClient();
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            String jsonbody = "{"
                    + quote("grant_type")   + ": " + quote(requestParams.get("grant_type")) + ", "
                    + quote("code")         + ": " + quote(requestParams.get("code")) + ", "
                    + quote("client_id")    + ": " + quote(requestParams.get("client_id")) + ", "
                    + quote("redirect_uri") + ": " + quote(requestParams.get("redirect_uri"))
                    + "}";
            try {
                Response r = bh.execute(oauthTokenUrl, "POST", headers, null, jsonbody);

                return new ObjectMapper().readValue(r.getEntity().toString(), HashMap.class);
            } catch (Exception e) {
                LOGGER.info("Caught exception running POST to " + oauthTokenUrl, e);
                throw e;
            }
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
    public static void main(String[] args) throws Exception {
        String realm = "test";
        String baseUrl = "https://localhost:8080/auth/realms/" + realm + "/protocol/openid-connect/";

        SeleniumOauthInteraction s = new SeleniumOauthInteraction();
        s.keycloakUser = "a";
        s.keycloakPwd = "a";
        s.appClientId = "test";
        s.appRedirectUri = "https://localhost";
        s.oauthAuthUrl = baseUrl + "/auth";
        s.oauthTokenUrl = baseUrl + "/token";

        Map<String, String> rc = s.getToken();

        System.out.println(rc);
    }

    private String quote(String in) {
        return "\"" + in + "\"";
    }
}