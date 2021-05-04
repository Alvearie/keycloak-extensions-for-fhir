/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.alvearie.keycloak.config.KeycloakConfigurator;
import org.alvearie.keycloak.config.util.KeycloakConfig;
import org.apache.commons.io.IOUtils;
import org.jsmart.zerocode.core.httpclient.BasicHttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.mockito.Mockito;
import org.testcontainers.containers.BindMode;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class KeycloakContainerTest {
    private static final String MASTER_REALM = "master";
    private static final String CLIENT_ID = "admin-cli";
    private static final String USERNAME = "a";
    private static final String PASSWORD = "a";
    private static final String KC_CLIENT = "test";
    private static final String TOKEN_ENDPOINT = "/auth/realms/test/protocol/openid-connect/token";

    private static KeycloakContainer keycloak;
    static {
        keycloak = new KeycloakContainer().withExtensionClassesFrom("target/classes");
        keycloak.addFileSystemBind("target/dependency", "/opt/jboss/keycloak/modules/system/layers/base/com/ibm/fhir/main", BindMode.READ_ONLY);
        // Shouldn't be needed, but sometimes is: https://github.com/dasniko/testcontainers-keycloak/issues/15
        keycloak.withEnv("DB_VENDOR", "H2");
        keycloak.withReuse(true);
        keycloak.start();
    }

    private static Keycloak adminClient;
    private static MockWebServer mockFhirServer;

    @BeforeClass
    public static void setUp() throws Exception {
        adminClient = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm(MASTER_REALM)
                .username(keycloak.getAdminUsername())
                .password(keycloak.getAdminPassword())
                .clientId(CLIENT_ID)
                .build();
        KeycloakConfigurator configurator = new KeycloakConfigurator(adminClient);

        mockFhirServer = new MockWebServer();
        mockFhirServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/fhir+json")
                .setBody(IOUtils.resourceToString("/mock_fhir_response.json", Charset.forName("UTF-8")))
                );
        mockFhirServer.start();
        setupFHIRPortEnvVar(mockFhirServer.getPort());

        KeycloakConfig config = new KeycloakConfig("keycloak-config.json");
        configurator.initializeRealm("test", config.getPropertyGroup("test"));
    }

    private static void setupFHIRPortEnvVar(int port) {
        Map<String,String> env = new HashMap<>(KeycloakConfig.EnvironmentVariables.get());
        env.put("FHIR_PORT", Integer.toString(port));
        Mockito.mockStatic(KeycloakConfig.EnvironmentVariables.class);
        Mockito.when(KeycloakConfig.EnvironmentVariables.get()).thenReturn(env);
    }

    @AfterClass
    public static void tearDown() throws IOException {
        mockFhirServer.shutdown();

        // no container cleanup to enable re-use
        //keycloak.close();
    }

    @Test
    public void shouldReturnServerInfo() {
        ServerInfoRepresentation serverInfo = adminClient.serverInfo().getInfo();
        assertThat(serverInfo, notNullValue());
    }

    @Test
    public void testMockFhirServer() throws Exception {
        URL url = new URL("http://localhost:" + mockFhirServer.getPort() + "/auth");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        assertEquals(connection.getResponseCode(), 200);
        IOUtils.copy(connection.getInputStream(), System.out);
    }

    @Test
    public void getHomePage() throws Exception {
        Integer port = keycloak.getMappedPort(8080);
        URL url = new URL("http://localhost:" + port + "/auth");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        assertEquals(connection.getResponseCode(), 200);
    }

    /**
     * Example of running the same test that we exercised in KeycloakLoginTest class, zerocode style.
     * Here we are relying on BasicHttpClient module that is part of the zerocode dependency.
     *
     * In rare cases, where declarative style testing does not work out, we can use this model for
     * test automation instead.
     *
     * @throws Exception
     */
    @Test
    public void passwordGrantLoginTest() throws Exception {
        System.out.println("Host is: " + keycloak.getHost());
        String tokenURL = "http://localhost:" + keycloak.getHttpPort() + TOKEN_ENDPOINT;
        String jsonbody = "{\"grant_type\":\"password\",\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\",\"client_id\":\""
                + KC_CLIENT + "\"}";
        BasicHttpClient bh = new BasicHttpClient();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        Response r = bh.execute(tokenURL, "POST", headers, null, jsonbody);
        System.out.println(r.getEntity().toString());
    }
}
