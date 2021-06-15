/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.alvearie.keycloak.config.KeycloakConfigurator;
import org.alvearie.keycloak.config.util.KeycloakConfig;
import org.alvearie.utils.SeleniumOauthInteraction;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.mockito.Mockito;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class KeycloakContainerTest {
    private static final String MASTER_REALM = "master";
    private static final String ADMIN_CLIENT_ID = "admin-cli";
    private static final String USERNAME = "a";
    private static final String PASSWORD = "a";
    private static final String KC_CLIENT = "test";
    private static final String REDIRECT_URI = "http://localhost";
    private static final String AUTH_ENDPOINT = "/auth/realms/test/protocol/openid-connect/auth";
    private static final String TOKEN_ENDPOINT = "/auth/realms/test/protocol/openid-connect/token";
    private static final String AUDIENCE = "https://localhost:9443/fhir-server/api/v4";

    // per https://www.testcontainers.org/features/networking/#exposing-host-ports-to-the-container
    // the host's port should be known prior to starting the container
    private static MockWebServer mockFhirServer;
    static {
        mockFhirServer = new MockWebServer();
        setupFHIRPortEnvVar(mockFhirServer.getPort());
        Testcontainers.exposeHostPorts(mockFhirServer.getPort());
    }

    // per the testcontainers doc, the contain should be started in a static block before JUnit starts up
    private static KeycloakContainer keycloak;
    static {
        keycloak = new KeycloakContainer().withExtensionClassesFrom("target/classes");
        keycloak.addFileSystemBind("target/dependency", "/opt/jboss/keycloak/modules/system/layers/base/com/ibm/fhir/main", BindMode.READ_ONLY);
        // Shouldn't be needed, but sometimes is: https://github.com/dasniko/testcontainers-keycloak/issues/15
        keycloak.withEnv("DB_VENDOR", "H2");
        // Temporarily uncomment to keep the container running after the tests complete
        // or keep it uncommented for reduced cycle time after https://github.com/dasniko/testcontainers-keycloak/issues/33
//        keycloak.withReuse(true);
        keycloak.start();
    }

    private static Keycloak adminClient;

    private static void setupFHIRPortEnvVar(int port) {
        Map<String,String> env = new HashMap<>(KeycloakConfig.EnvironmentVariables.get());
        env.put("FHIR_PORT", Integer.toString(port));
        Mockito.mockStatic(KeycloakConfig.EnvironmentVariables.class);
        Mockito.when(KeycloakConfig.EnvironmentVariables.get()).thenReturn(env);
    }

    @BeforeClass
    public static void setUp() throws Exception {
        mockFhirServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/fhir+json")
                .setBody(IOUtils.resourceToString("/mock_fhir_response.json", Charset.forName("UTF-8")))
                );

        adminClient = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm(MASTER_REALM)
                .username(keycloak.getAdminUsername())
                .password(keycloak.getAdminPassword())
                .clientId(ADMIN_CLIENT_ID)
                .build();
        KeycloakConfigurator configurator = new KeycloakConfigurator(adminClient);

        KeycloakConfig config = new KeycloakConfig("keycloak-config.json");
        configurator.initializeRealm("test", config.getPropertyGroup("test"));
    }

    @AfterClass
    public static void tearDown() throws IOException {
        mockFhirServer.shutdown();

        System.out.println("\n\n" + "Dumping container logs:" + "\n");
        System.out.println(keycloak.getLogs());

        // no explicit container cleanup to enable re-use
        //keycloak.close();
    }

    @Test
    public void testLogin() throws Exception {
        String host = "http://" + keycloak.getHost();
        Integer port = keycloak.getHttpPort();
        SeleniumOauthInteraction s = new SeleniumOauthInteraction(KC_CLIENT, REDIRECT_URI,
                host + ":" + port + AUTH_ENDPOINT, host + ":" + port + TOKEN_ENDPOINT);

        Map<String, String> authResponse = s.fetchCode(USERNAME, PASSWORD, AUDIENCE, "fhirUser", "launch/patient");

        System.out.println("Auth response: " + authResponse);
        assertTrue(authResponse.containsKey("code"));

        // verify that the underlying request from Keycloak to the mock FHIR server looks like we want
        RecordedRequest recordedRequest = mockFhirServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/fhir-server/api/v4", recordedRequest.getPath());
        String auth = recordedRequest.getHeader("Authorization");
        assertTrue(auth.startsWith("Bearer "));
        verifyToken(auth.substring("Bearer ".length()));
        String request = recordedRequest.getBody().readUtf8();
        Map<?,?> json = new ObjectMapper().readValue(request, HashMap.class);
        assertEquals("Bundle", json.get("resourceType"));


        Map<String, String> tokenResponse = s.fetchToken(authResponse.get("code"));

        System.out.println("Token response: " + tokenResponse);
        assertTrue(tokenResponse.containsKey("access_token"));
        verifyToken(tokenResponse.get("access_token"));

        assertTrue(tokenResponse.containsKey("refresh_token"));
        String[] refreshTokenParts = tokenResponse.get("access_token").split("\\.");
        assertEquals(3, refreshTokenParts.length);

        assertTrue(tokenResponse.containsKey("patient"));
        assertEquals("PatientA", tokenResponse.get("patient"));
    }

    private void verifyToken(String accessToken)
            throws IOException, JsonParseException, JsonMappingException {
        String[] accessTokenParts = accessToken.split("\\.");
        assertEquals(3, accessTokenParts.length);
        Map<?,?> claims = new ObjectMapper().readValue(Base64.getDecoder().decode(accessTokenParts[1]), HashMap.class);
        assertTrue(claims.containsKey("patient_id"));
        System.out.println("patient_id claim: " + claims.get("patient_id"));
    }
}
