
/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.utils;

import java.util.HashMap;
import java.util.Map;

import org.alvearie.keycloak.config.KeycloakConfigurator;
import org.alvearie.keycloak.config.util.KeycloakConfig;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dasniko.testcontainers.keycloak.KeycloakContainer;

/**
 * Provides helper methods and utilities for the integration tests
 * primarily to assist with the zerocode tests
 * 
 * @author vreddy
 *
 */
public class TestHelper {

    // Following properties are injected from the kc_integration_test.properties file
    // without hard coding any in the classes.
    @Inject(optional = true)
    @Named("keycloak.master.realm")
    private String master_realm;

    @Inject(optional = true)
    @Named("keycloak.admin.client.id")
    private String client_id;

    @Inject(optional = true)
    @Named("keycloak.realm.name")
    private String realm_name;

    @Inject(optional = true)
    @Named("keycloak.realm.group")
    private String realm_group;

    @Inject(optional = true)
    @Named("keycloak.env.key")
    private String env_key;

    @Inject(optional = true)
    @Named("keycloak.env.value")
    private String env_value;

    private static KeycloakContainer keycloak;
    private static Keycloak adminClient;
    static Map<String, Object> keycloakprops = new HashMap<String, Object>();

    @SuppressWarnings("resource")
    public Map<String, Object> startKeycloakContainer(Map<String, String> parameters) throws Exception {

	// Object to store/return properties needed for the down the line tests
	// spin up and start the keycloak container
	keycloak = new KeycloakContainer().withExtensionClassesFrom("target/classes");
	// Shouldn't be needed, but is:
	// https://github.com/dasniko/testcontainers-keycloak/issues/15
	keycloak.withEnv(env_key, env_value);
	keycloak.start();
	adminClient = KeycloakBuilder.builder().serverUrl(keycloak.getAuthServerUrl()).realm(master_realm)
		.username(keycloak.getAdminUsername()).password(keycloak.getAdminPassword()).clientId(client_id)
		.build();
	KeycloakConfigurator configurator = new KeycloakConfigurator(adminClient);
	KeycloakConfig config = new KeycloakConfig("keycloak-config.json");

	configurator.initializeRealm(realm_name, config.getPropertyGroup(realm_group));
	// waiting an extra 3 secs for the container to initiate
	Thread.sleep(3000);

	// build the variables needed to return
	keycloakprops.put("runningStatus", Boolean.toString(keycloak.isRunning()));
	keycloakprops.put("httpPort", keycloak.getHttpPort());
	keycloakprops.put("httpsPort", keycloak.getHttpsPort());
	keycloakprops.put("host", keycloak.getHost());
	keycloakprops.put("adminUser", keycloak.getAdminUsername());
	keycloakprops.put("adminPassword", keycloak.getAdminPassword());

	return keycloakprops;
    }

    public Map<String, Object> stopKeycloakContainer() {

	// stopping the keycloak container
	keycloak.close();
	// build the variables needed to return
	keycloakprops.clear();
	keycloakprops.put("runningStatus", Boolean.toString(keycloak.isRunning()));
	return keycloakprops;
    }
}