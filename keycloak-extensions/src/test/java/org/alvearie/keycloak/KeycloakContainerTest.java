/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.alvearie.keycloak.config.KeycloakConfigurator;
import org.alvearie.keycloak.config.util.KeycloakConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.info.ServerInfoRepresentation;

import dasniko.testcontainers.keycloak.KeycloakContainer;

public class KeycloakContainerTest {
    private static final String MASTER_REALM = "master";
    private static final String CLIENT_ID = "admin-cli";

    private static KeycloakContainer keycloak;
    private static Keycloak adminClient;

    @SuppressWarnings("resource")
    @BeforeClass
    public static void start() throws Exception {
        keycloak = new KeycloakContainer().withExtensionClassesFrom("target/classes");
        // Shouldn't be needed, but is: https://github.com/dasniko/testcontainers-keycloak/issues/15
        keycloak.withEnv("DB_VENDOR", "H2");
        keycloak.start();

        adminClient = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm(MASTER_REALM)
                .username(keycloak.getAdminUsername())
                .password(keycloak.getAdminPassword())
                .clientId(CLIENT_ID)
                .build();
        KeycloakConfigurator configurator = new KeycloakConfigurator(adminClient);
        KeycloakConfig config = new KeycloakConfig("keycloak-config.json");
        configurator.initializeRealm("test", config.getPropertyGroup("test"));
    }

    @AfterClass
    public static void end() {
        keycloak.close();
    }

    @Test
    public void shouldReturnServerInfo() {
        ServerInfoRepresentation serverInfo = adminClient.serverInfo().getInfo();
        assertThat(serverInfo, notNullValue());
    }
}
