/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak.config;

import org.alvearie.keycloak.config.util.KeycloakConfig;
import org.alvearie.keycloak.config.util.PropertyGroup;
import org.alvearie.keycloak.config.util.PropertyGroup.PropertyEntry;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmsResource;

/**
 * Reads the KeycloakConfig and applies it to the target Keycloak
 *
 * Command line arguments:
 *   configFile: the file path to a JSON file containing Keycloak configuration properties
 */
public class Main {

    private static final String CONFIG_FILE_PATH_OPTION = "configFile";
    private static final String CLASS_NAME = "Main";
    private static final String MASTER_REALM = "master";

    /**
     * Initializes the Keycloak configuration.
     * @param args the passed in arguments
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        Options options = null;
        CommandLine cmd;
        String configFilePath;

        try {
            // Process the options
            options = buildCmdOptions();
            cmd = new DefaultParser().parse(options, args);
            configFilePath = cmd.getOptionValue(CONFIG_FILE_PATH_OPTION);
        } catch (ParseException e) {
            String header = "Initialize keycloak config\n\n";
            String footer = "\n";
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(CLASS_NAME, header, options, footer, true);
            throw new RuntimeException("Invalid arguments", e);
        }

        // Perform the action
        KeycloakConfig config = new KeycloakConfig(configFilePath);
        applyConfig(config);
    }

    /**
     * Builds the command options.
     * @return the command options
     */
    private static Options buildCmdOptions() {
        Options options = new Options();

        Option configFilePathOption = new Option(CONFIG_FILE_PATH_OPTION, true, "The file path to a JSON file containing Keycloak configuration properties");
        configFilePathOption.setRequired(true);
        options.addOption(configFilePathOption);

        return options;
    }

    /**
     * Applies the Keycloak configuration.
     * @param config the Keycloak configuration to apply
     * @throws Exception an exception
     */
    private static void applyConfig(KeycloakConfig config) throws Exception {
        // Create a Keycloak client and use that to initialize the configurator
        Keycloak client = createKeycloakClient(config);
        KeycloakConfigurator configurator = new KeycloakConfigurator(client);

        // Initialize realms
        PropertyGroup realmsPg = config.getPropertyGroup(KeycloakConfig.PROP_KEYCLOAK_REALMS);
        if (realmsPg != null) {
            for (PropertyEntry realmPe: realmsPg.getProperties()) {
                String realmName = realmPe.getName();
                PropertyGroup realmPg = realmsPg.getPropertyGroup(realmName);
                configurator.initializeRealm(realmName, realmPg);
            }
        }
    }

    /**
     * Creates a Keycloak admin client.
     * @param config the Keycloak configuration to initialize
     * @return a Keycloak client
     */
    private static Keycloak createKeycloakClient(KeycloakConfig config) {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(config.getStringProperty(KeycloakConfig.PROP_KEYCLOAK_SERVER_URL))
                .realm(MASTER_REALM)
                .username(config.getStringProperty(KeycloakConfig.PROP_KEYCLOAK_ADMIN_USER))
                .password(config.getStringProperty(KeycloakConfig.PROP_KEYCLOAK_ADMIN_PW))
                .clientId(config.getStringProperty(KeycloakConfig.PROP_KEYCLOAK_ADMIN_CLIENT_ID))
                .build();
        return keycloak;
    }
}
