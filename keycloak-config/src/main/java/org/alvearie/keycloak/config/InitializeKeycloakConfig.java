/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.keycloak.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.admin.client.resource.ClientScopesResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.admin.client.resource.IdentityProvidersResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import org.alvearie.keycloak.config.util.KeycloakConfig;
import org.alvearie.keycloak.config.util.PropertyGroup;
import org.alvearie.keycloak.config.util.PropertyGroup.PropertyEntry;

/**
 * Initializes the Keycloak configuration.
 *
 * Command line arguments:
 *   configFile: the file path to a JSON file containing Keycloak configuration properties
 */
public class InitializeKeycloakConfig {

    private static final String CONFIG_FILE_PATH_OPTION = "configFile";
    private static final String CLASS_NAME = "InitializeKeycloakConfig";
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
        initializeKeycloakConfig(config);
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
     * Initializes the Keycloak configuration.
     * @param config the Keycloak configuration to initialize
     * @throws Exception an exception
     */
    private static void initializeKeycloakConfig(KeycloakConfig config) throws Exception {

        // Create Keycloak client
        Keycloak client = createKeycloakClient(config);

        // Initialize realms
        PropertyGroup realmsPg = config.getPropertyGroup(KeycloakConfig.PROP_KEYCLOAK_REALMS);
        if (realmsPg != null) {
            RealmsResource realms = client.realms();
            for (PropertyEntry realmPe: realmsPg.getProperties()) {
                String realmName = realmPe.getName();
                PropertyGroup realmPg = realmsPg.getPropertyGroup(realmName);
                initializeRealm(realms, realmName, realmPg);
            }
        }
    }

    /**
     * Creates a Keycloak client.
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

    /**
     * Initializes the realm.
     * @param realms the realms resource
     * @param realmName the realm name
     * @param realmPg the realm property group
     * @throws Exception an Exception
     */
    private static void initializeRealm(RealmsResource realms, String realmName, PropertyGroup realmPg) throws Exception {
        System.out.println("initializing realm: " + realmName);
        // Create realm if it does not exist
        RealmRepresentation realm = getRealmByName(realms, realmName);
        if (realm == null) {
            realm = new RealmRepresentation();
            realm.setRealm(realmName);
            realms.create(realm);
            realm = getRealmByName(realms, realmName);
            if (realm == null) {
                throw new RuntimeException("Unable to create realm");
            }
        }

        // Initialize client scopes
        PropertyGroup clientScopesPg = realmPg.getPropertyGroup(KeycloakConfig.PROP_CLIENT_SCOPES);
        if (clientScopesPg != null) {
            for (PropertyEntry clientScopePe: clientScopesPg.getProperties()) {
                String clientScopeName = clientScopePe.getName();
                PropertyGroup clientScopePg = clientScopesPg.getPropertyGroup(clientScopeName);
                initializeClientScope(realms.realm(realmName).clientScopes(), clientScopeName, clientScopePg);
            }
        }

        // Update "default" default assigned client scopes
        List<String> defaultClientScopeNames = realmPg.getStringListProperty(KeycloakConfig.PROP_DEFAULT_DEFAULT_CLIENT_SCOPES);
        if (defaultClientScopeNames != null) {
            List<String> defaultClientScopeIds = getClientScopeIds(realms.realm(realmName).clientScopes(), defaultClientScopeNames);
            if (defaultClientScopeIds != null) {
                List<ClientScopeRepresentation> existingDefaultClientScopes = realms.realm(realmName).getDefaultDefaultClientScopes();
                for (ClientScopeRepresentation existingDefaultClientScope : existingDefaultClientScopes) {
                    if (!defaultClientScopeIds.contains(existingDefaultClientScope.getId())) {
                        realms.realm(realmName).removeDefaultDefaultClientScope(existingDefaultClientScope.getId());
                    }
                    else {
                        defaultClientScopeIds.remove(existingDefaultClientScope.getId());
                    }
                }
                for (String defaultClientScopeId : defaultClientScopeIds) {
                    realms.realm(realmName).addDefaultDefaultClientScope(defaultClientScopeId);
                }
            }
        }

        // Update "default" optional assigned client scopes
        List<String> optionalClientScopeNames = realmPg.getStringListProperty(KeycloakConfig.PROP_DEFAULT_OPTIONAL_CLIENT_SCOPES);
        if (optionalClientScopeNames != null) {
            List<String> optionalClientScopeIds = getClientScopeIds(realms.realm(realmName).clientScopes(), optionalClientScopeNames);
            if (optionalClientScopeIds != null) {
                List<ClientScopeRepresentation> existingOptionalClientScopes = realms.realm(realmName).getDefaultOptionalClientScopes();
                for (ClientScopeRepresentation existingOptionalClientScope : existingOptionalClientScopes) {
                    if (!optionalClientScopeIds.contains(existingOptionalClientScope.getId())) {
                        realms.realm(realmName).removeDefaultOptionalClientScope(existingOptionalClientScope.getId());
                    }
                    else {
                        optionalClientScopeIds.remove(existingOptionalClientScope.getId());
                    }
                }
                for (String defaultClientScopeId : optionalClientScopeIds) {
                    realms.realm(realmName).addDefaultOptionalClientScope(defaultClientScopeId);
                }
            }
        }

        // Initialize clients
        PropertyGroup clientsPg = realmPg.getPropertyGroup(KeycloakConfig.PROP_CLIENTS);
        if (clientsPg != null) {
            for (PropertyEntry clientPe: clientsPg.getProperties()) {
                String clientName = clientPe.getName();
                PropertyGroup clientPg = clientsPg.getPropertyGroup(clientName);
                initializeClient(realms.realm(realmName).clients(), realms.realm(realmName).clientScopes(), clientName, clientPg);
            }
        }

        // Initialize identity providers
        PropertyGroup identityProvidersPg = realmPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDERS);
        if (identityProvidersPg != null) {
            for (PropertyEntry identityProviderPe: identityProvidersPg.getProperties()) {
                String identityProviderAlias = identityProviderPe.getName();
                PropertyGroup identityProviderPg = identityProvidersPg.getPropertyGroup(identityProviderAlias);
                initializeIdentityProvider(realms.realm(realmName).identityProviders(), identityProviderAlias, identityProviderPg);
            }
        }

        // Initialize authentication flows
        PropertyGroup authenticationFlowsPg = realmPg.getPropertyGroup(KeycloakConfig.PROP_AUTHENTICATION_FLOWS);
        if (authenticationFlowsPg != null) {
            for (PropertyEntry authenticationFlowPe: authenticationFlowsPg.getProperties()) {
                String authenticationFlowAlias = authenticationFlowPe.getName();
                PropertyGroup authenticationFlowPg = authenticationFlowsPg.getPropertyGroup(authenticationFlowAlias);
                initializeAuthenticationFlow(realms.realm(realmName).flows(), authenticationFlowAlias, authenticationFlowPg);
            }
        }

        // Initialize groups
        PropertyGroup groupsPg = realmPg.getPropertyGroup(KeycloakConfig.PROP_GROUPS);
        if (groupsPg != null) {
            for (PropertyEntry groupPe: groupsPg.getProperties()) {
                String groupName = groupPe.getName();
                PropertyGroup groupPg = groupsPg.getPropertyGroup(groupName);
                initializeGroup(realms.realm(realmName).groups(), groupName, groupPg);
            }
        }

        // Update "default" groups
        List<String> defaultGroups = realmPg.getStringListProperty(KeycloakConfig.PROP_DEFAULT_GROUPS);
        if (defaultGroups != null) {
            List<String> defaultGroupIds = getGroupIds(realms.realm(realmName).groups(), defaultGroups);
            if (defaultGroupIds != null) {
                List<GroupRepresentation> existingDefaultGroups = realms.realm(realmName).getDefaultGroups();
                for (GroupRepresentation existingDefaultGroup : existingDefaultGroups) {
                    if (!defaultGroupIds.contains(existingDefaultGroup.getId())) {
                        realms.realm(realmName).removeDefaultGroup(existingDefaultGroup.getId());
                    }
                    else {
                        defaultGroupIds.remove(existingDefaultGroup.getId());
                    }
                }
                for (String defaultGroupId : defaultGroupIds) {
                    realms.realm(realmName).addDefaultGroup(defaultGroupId);
                }
            }
        }

        // Initialize users
        PropertyGroup usersPg = realmPg.getPropertyGroup(KeycloakConfig.PROP_USERS);
        if (usersPg != null) {
            for (PropertyEntry userPe: usersPg.getProperties()) {
                String userName = userPe.getName();
                PropertyGroup userPg = usersPg.getPropertyGroup(userName);
                initializeUser(realms.realm(realmName).users(), realms.realm(realmName).groups(), userName, userPg);
            }
        }

        // Initialize events config
        PropertyGroup eventsPg = realmPg.getPropertyGroup(KeycloakConfig.PROP_EVENTS_CONFIG);
        if (eventsPg != null) {
            initializeEventsConfig(realm, eventsPg);
        }

        // Update realm settings
        realm.setEnabled(realmPg.getBooleanProperty(KeycloakConfig.PROP_REALM_ENABLED));
        realms.realm(realmName).update(realm);
    }

    /**
     * @param realm
     * @param eventsPg
     */
    private static void initializeEventsConfig(RealmRepresentation realm, PropertyGroup eventsPg) {
        System.out.println("initializing events config");

        // Login events
        Boolean eventsEnabled = eventsPg.getBooleanProperty(KeycloakConfig.PROP_EVENTS_CONFIG_SAVE_LOGIN_EVENTS);
        if (eventsEnabled != null) {
            realm.setEventsEnabled(eventsEnabled);
        }

        Integer eventsExpiration = eventsPg.getIntProperty(KeycloakConfig.PROP_EVENTS_CONFIG_EXPIRATION);
        if (eventsExpiration != null) {
            realm.setEventsExpiration(Long.valueOf(eventsExpiration));
        }

        List<String> saveTypes = null;
        try {
            saveTypes = eventsPg.getStringListProperty(KeycloakConfig.PROP_EVENTS_CONFIG_SAVE_TYPES);
        } catch (Exception e) {
            System.err.println("Error while reading event save types from the config file:");
            e.printStackTrace();
        }
        if (saveTypes != null) {
            realm.setEnabledEventTypes(saveTypes);
        }

        // Admin events
        Boolean adminEventsEnabled = eventsPg.getBooleanProperty(KeycloakConfig.PROP_EVENTS_CONFIG_SAVE_ADMIN_EVENTS);
        if (adminEventsEnabled != null) {
            realm.setAdminEventsEnabled(adminEventsEnabled);
        }
    }

    /**
     * Initializes the client scopes.
     * @param clientScopes the client scopes resource
     * @param clientScopeName the client scope name
     * @param clientScopePg the client scope property group
     * @throws Exception an Exception
     */
    private static void initializeClientScope(ClientScopesResource clientScopes, String clientScopeName, PropertyGroup clientScopePg) throws Exception {
        System.out.println("initializing client scope: " + clientScopeName);
        // Create client scope if it does not exist
        ClientScopeRepresentation clientScope = getClientScopeByName(clientScopes, clientScopeName);
        if (clientScope == null) {
            clientScope = new ClientScopeRepresentation();
            clientScope.setName(clientScopeName);
            clientScopes.create(clientScope);
            clientScope = getClientScopeByName(clientScopes, clientScopeName);
            if (clientScope == null) {
                throw new RuntimeException("Unable to create client scope");
            }
        }

        // Update client scope settings
        clientScope.setDescription(clientScopePg.getStringProperty(KeycloakConfig.PROP_CLIENT_SCOPE_DESCRIPTION));
        clientScope.setProtocol(clientScopePg.getStringProperty(KeycloakConfig.PROP_CLIENT_SCOPE_PROTOCOL));
        PropertyGroup attributesPg = clientScopePg.getPropertyGroup(KeycloakConfig.PROP_CLIENT_SCOPE_ATTRIBUTES);
        if (attributesPg != null) {
            Map<String, String> attributes = clientScope.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            for (PropertyEntry attributePe: attributesPg.getProperties()) {
                String attributeKey = attributePe.getName();
                attributes.put(attributeKey, attributePe.getValue() != null ? attributePe.getValue().toString() : null);
            }
            clientScope.setAttributes(attributes);
        }
        clientScopes.get(clientScope.getId()).update(clientScope);

        // Initialize protocol mappers
        PropertyGroup mappersPg = clientScopePg.getPropertyGroup(KeycloakConfig.PROP_CLIENT_SCOPE_MAPPERS);
        if (mappersPg != null) {
            for (PropertyEntry mapperPe: mappersPg.getProperties()) {
                String mapperName = mapperPe.getName();
                PropertyGroup mapperPg = mappersPg.getPropertyGroup(mapperName);
                initializeProtocolMapper(clientScopes.get(clientScope.getId()).getProtocolMappers(), mapperName, mapperPg);
            }
        }
    }

    /**
     * Initializes the protocol mappers of the client scope.
     * @param protocolMappers the protocol mappers
     * @param mapperName the protocol mapper name
     * @param mapperPg the protocol mapper property group
     * @throws Exception an Exception
     */
    private static void initializeProtocolMapper(ProtocolMappersResource protocolMappers, String mapperName, PropertyGroup mapperPg) throws Exception {
        System.out.println("initializing protocol mapper: " + mapperName);
        // Create protocol mapper if it does not exist
        ProtocolMapperRepresentation protocolMapper = getProtocolMapperByName(protocolMappers, mapperName);
        if (protocolMapper == null) {
            protocolMapper = new ProtocolMapperRepresentation();
            protocolMapper.setName(mapperName);
            protocolMapper.setProtocol(mapperPg.getStringProperty(KeycloakConfig.PROP_CLIENT_SCOPE_MAPPER_PROTOCOL));
            protocolMapper.setProtocolMapper(mapperPg.getStringProperty(KeycloakConfig.PROP_CLIENT_SCOPE_MAPPER_PROTOCOL_MAPPER));
            protocolMappers.createMapper(protocolMapper);
            protocolMapper = getProtocolMapperByName(protocolMappers, mapperName);
            if (protocolMapper == null) {
                throw new RuntimeException("Unable to create protocol mapper");
            }
        }

        // Update protocol mapper settings
        protocolMapper.setProtocol(mapperPg.getStringProperty(KeycloakConfig.PROP_CLIENT_SCOPE_MAPPER_PROTOCOL));
        protocolMapper.setProtocolMapper(mapperPg.getStringProperty(KeycloakConfig.PROP_CLIENT_SCOPE_MAPPER_PROTOCOL_MAPPER));
        PropertyGroup configPg = mapperPg.getPropertyGroup(KeycloakConfig.PROP_CLIENT_SCOPE_MAPPER_PROTOCOL_MAPPER_CONFIG);
        if (configPg != null) {
            Map<String, String> config = protocolMapper.getConfig();
            if (config == null) {
                config = new HashMap<>();
            }
            for (PropertyEntry configPe: configPg.getProperties()) {
                String configKey = configPe.getName();
                config.put(configKey, configPe.getValue() != null ? configPe.getValue().toString() : null);
            }
            protocolMapper.setConfig(config);
        }
        protocolMappers.update(protocolMapper.getId(), protocolMapper);
    }

    /**
     * Initializes the client.
     * @param clients the clients resource
     * @param clientScopes the client scopes resource
     * @param clientId the client id
     * @param clientPg the client property group
     * @throws Exception an Exception
     */
    private static void initializeClient(ClientsResource clients, ClientScopesResource clientScopes, String clientId, PropertyGroup clientPg) throws Exception {
        System.out.println("initializing client: " + clientId);
        // Create client if it does not exist
        ClientRepresentation client = getClientByClientId(clients, clientId);
        if (client == null) {
            client = new ClientRepresentation();
            client.setClientId(clientId);
            clients.create(client);
            client = getClientByClientId(clients, clientId);
            if (client == null) {
                throw new RuntimeException("Unable to create client");
            }
        }

        // Update client settings
        client.setName(clientPg.getStringProperty(KeycloakConfig.PROP_CLIENT_NAME));
        client.setDescription(clientPg.getStringProperty(KeycloakConfig.PROP_CLIENT_DESCRIPTION));
        client.setConsentRequired(clientPg.getBooleanProperty(KeycloakConfig.PROP_CLIENT_CONSENT_REQUIRED));
        client.setPublicClient(clientPg.getBooleanProperty(KeycloakConfig.PROP_CLIENT_PUBLIC_CLIENT));
        client.setBearerOnly(clientPg.getBooleanProperty(KeycloakConfig.PROP_CLIENT_BEARER_ONLY));
        client.setRootUrl(clientPg.getStringProperty(KeycloakConfig.PROP_CLIENT_ROOT_URL));
        client.setRedirectUris(clientPg.getStringListProperty(KeycloakConfig.PROP_CLIENT_REDIRECT_URIS));
        client.setAdminUrl(clientPg.getStringProperty(KeycloakConfig.PROP_CLIENT_ADMIN_URL));
        client.setWebOrigins(clientPg.getStringListProperty(KeycloakConfig.PROP_CLIENT_WEB_ORIGINS));
        clients.get(client.getId()).update(client);

        // Update default assigned client scopes
        List<String> defaultClientScopeNameStrings = clientPg.getStringListProperty(KeycloakConfig.PROP_CLIENT_DEFAULT_CLIENT_SCOPES);
        if (defaultClientScopeNameStrings != null) {
            List<String> defaultClientScopeIds = getClientScopeIds(clientScopes, defaultClientScopeNameStrings);
            if (defaultClientScopeIds != null) {
                List<ClientScopeRepresentation> existingDefaultClientScopes = clients.get(client.getId()).getDefaultClientScopes();
                for (ClientScopeRepresentation existingDefaultClientScope : existingDefaultClientScopes) {
                    if (!defaultClientScopeIds.contains(existingDefaultClientScope.getId())) {
                        clients.get(client.getId()).removeDefaultClientScope(existingDefaultClientScope.getId());
                    }
                    else {
                        defaultClientScopeIds.remove(existingDefaultClientScope.getId());
                    }
                }
                for (String defaultClientScopeId : defaultClientScopeIds) {
                    clients.get(client.getId()).addDefaultClientScope(defaultClientScopeId);
                }
            }
        }

        // Update optional assigned client scopes
        List<String> optionalClientScopeNameStrings = clientPg.getStringListProperty(KeycloakConfig.PROP_CLIENT_OPTIONAL_CLIENT_SCOPES);
        if (optionalClientScopeNameStrings != null) {
            List<String> optionalClientScopeIds = getClientScopeIds(clientScopes, optionalClientScopeNameStrings);
            if (optionalClientScopeIds != null) {
                List<ClientScopeRepresentation> existingOptionalClientScopes = clients.get(client.getId()).getOptionalClientScopes();
                for (ClientScopeRepresentation existingOptionalClientScope : existingOptionalClientScopes) {
                    if (!optionalClientScopeIds.contains(existingOptionalClientScope.getId())) {
                        clients.get(client.getId()).removeDefaultClientScope(existingOptionalClientScope.getId());
                    }
                    else {
                        optionalClientScopeIds.remove(existingOptionalClientScope.getId());
                    }
                }
                for (String defaultClientScopeId : optionalClientScopeIds) {
                    clients.get(client.getId()).addOptionalClientScope(defaultClientScopeId);

                }
            }
        }
    }

    /**
     * Initializes the identity provider.
     * @param identityProviders the identity providers resource
     * @param identityProviderAlias the identity provider alias
     * @param identityProviderPg the identity provider property group
     * @throws Exception an Exception
     */
    private static void initializeIdentityProvider(IdentityProvidersResource identityProviders, String identityProviderAlias, PropertyGroup identityProviderPg) throws Exception {
        System.out.println("initializing identity provider: " + identityProviderAlias);
        // Create identity provider if it does not exist
        IdentityProviderRepresentation identityProvider = getIdentityProviderByAlias(identityProviders, identityProviderAlias);
        if (identityProvider == null) {
            identityProvider = new IdentityProviderRepresentation();
            identityProvider.setAlias(identityProviderAlias);
            identityProvider.setProviderId(identityProviderPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_PROVIDER_ID));
            PropertyGroup configPg = identityProviderPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDER_CONFIG);
            if (configPg != null) {
                Map<String, String> config = identityProvider.getConfig();
                if (config == null) {
                    config = new HashMap<>();
                }
                config.remove(KeycloakConfig.KEYCLOAK_IDENTITY_PROVIDER_CLIENT_SECRET);
                for (PropertyEntry configPe: configPg.getProperties()) {
                    String configKey = configPe.getName();
                    config.put(configKey, configPe.getValue() != null ? configPe.getValue().toString() : null);
                }
                identityProvider.setConfig(config);
            }
            identityProviders.create(identityProvider);
            identityProvider = getIdentityProviderByAlias(identityProviders, identityProviderAlias);
            if (identityProvider == null) {
                throw new RuntimeException("Unable to create identity provider");
            }
        }

        // Update identity provider settings
        identityProvider.setProviderId(identityProviderPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_PROVIDER_ID));
        identityProvider.setDisplayName(identityProviderPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_DISPLAY_NAME));
        identityProvider.setEnabled(identityProviderPg.getBooleanProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_ENABLED));
        identityProvider.setFirstBrokerLoginFlowAlias(identityProviderPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_FIRST_BROKER_LOGIN_FLOW_ALIAS));
        PropertyGroup configPg = identityProviderPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDER_CONFIG);
        if (configPg != null) {
            Map<String, String> config = identityProvider.getConfig();
            if (config == null) {
                config = new HashMap<>();
            }
            config.remove(KeycloakConfig.KEYCLOAK_IDENTITY_PROVIDER_CLIENT_SECRET);
            for (PropertyEntry configPe: configPg.getProperties()) {
                String configKey = configPe.getName();
                config.put(configKey, configPe.getValue() != null ? configPe.getValue().toString() : null);
            }
            identityProvider.setConfig(config);
        }
        identityProviders.get(identityProvider.getAlias()).update(identityProvider);

        // Initialize identity provider mappers
        PropertyGroup mappersPg = identityProviderPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDER_MAPPERS);
        if (mappersPg != null) {
            for (PropertyEntry mapperPe: mappersPg.getProperties()) {
                String mapperName = mapperPe.getName();
                PropertyGroup mapperPg = mappersPg.getPropertyGroup(mapperName);
                initializeIdentityProviderMapper(identityProviders.get(identityProvider.getAlias()), identityProviderAlias, mapperName, mapperPg);
            }
        }
    }

    /**
     * Initializes the mappers of the identity provider.
     * @param identityProvider the identity provider
     * @param identityProviderAlias the identity provider alias
     * @param mapperName the identity provider mapper name
     * @param mapperPg the identity provider mapper property group
     * @throws Exception an Exception
     */
    private static void initializeIdentityProviderMapper(IdentityProviderResource identityProvider, String identityProviderAlias, String mapperName, PropertyGroup mapperPg) throws Exception {
        System.out.println("initializing identity provider mapper: " + mapperName);
        // Create protocol mapper if it does not exist
        IdentityProviderMapperRepresentation identityProviderMapper = getIdentityProvideMapperByName(identityProvider, mapperName);
        if (identityProviderMapper == null) {
            identityProviderMapper = new IdentityProviderMapperRepresentation();
            identityProviderMapper.setName(mapperName);
            identityProviderMapper.setIdentityProviderAlias(identityProviderAlias);
            identityProviderMapper.setIdentityProviderMapper(mapperPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_MAPPER_IDENTITY_PROVIDER_MAPPER));
            PropertyGroup configPg = mapperPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDER_MAPPER_CONFIG);
            if (configPg != null) {
                Map<String, String> config = identityProviderMapper.getConfig();
                if (config == null) {
                    config = new HashMap<>();
                }
                for (PropertyEntry configPe: configPg.getProperties()) {
                    String configKey = configPe.getName();
                    config.put(configKey, configPe.getValue() != null ? configPe.getValue().toString() : null);
                }
                identityProviderMapper.setConfig(config);
            }
            identityProvider.addMapper(identityProviderMapper);
            identityProviderMapper = getIdentityProvideMapperByName(identityProvider, mapperName);
            if (identityProviderMapper == null) {
                throw new RuntimeException("Unable to create identity provider mapper");
            }
        }

        // Update identity provider mapper settings
        identityProviderMapper.setIdentityProviderAlias(identityProviderAlias);
        identityProviderMapper.setIdentityProviderMapper(mapperPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_MAPPER_IDENTITY_PROVIDER_MAPPER));
        PropertyGroup configPg = mapperPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDER_MAPPER_CONFIG);
        if (configPg != null) {
            Map<String, String> config = identityProviderMapper.getConfig();
            if (config == null) {
                config = new HashMap<>();
            }
            for (PropertyEntry configPe: configPg.getProperties()) {
                String configKey = configPe.getName();
                config.put(configKey, configPe.getValue() != null ? configPe.getValue().toString() : null);
            }
            identityProviderMapper.setConfig(config);
        }
        identityProvider.update(identityProviderMapper.getId(), identityProviderMapper);
    }

    /**
     * Initializes the authentication flow.
     * @param authMgmt the authorization management
     * @param authenticationFlowAlias the authentication flow alias
     * @param authenticationFlowPg the authentication flow property group
     * @throws Exception an Exception
     */
    private static void initializeAuthenticationFlow(AuthenticationManagementResource authMgmt, String authenticationFlowAlias, PropertyGroup authenticationFlowPg) throws Exception {
        System.out.println("initializing authentication flow: " + authenticationFlowAlias);
        // Get authentication flow
        AuthenticationFlowRepresentation authenticationFlow = getAuthenticationFlowByAlias(authMgmt, authenticationFlowAlias);
        if (authenticationFlow == null) {
            throw new RuntimeException("Authentication flow does not exist");
        }

        // Update identity provider redirector
        for (PropertyEntry authExecutionPe: authenticationFlowPg.getProperties()) {
            String authExecutionType = authExecutionPe.getName();
            if (KeycloakConfig.PROP_IDENTITY_REDIRECTOR.equals(authExecutionType)) {
                PropertyGroup identityProviderRedirectorPg = authenticationFlowPg.getPropertyGroup(authExecutionType);
                String identityProviderRedirectorAlias = identityProviderRedirectorPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_REDIRECTOR_ALIAS);
                initializeIdentityProviderRedirector(authMgmt, authenticationFlowAlias, identityProviderRedirectorAlias, identityProviderRedirectorPg);
            }
        }
    }

    /**
     * Initializes the identity provider redirector.
     * @param authMgmt the authorization management
     * @param authenticationFlowAlias the authentication flow alias
     * @param identityProviderRedirectorAlias the identity provider redirector alias
     * @param identityProviderRedirectorPg the identity provider redirector property group
     * @throws Exception an Exception
     */
    private static void initializeIdentityProviderRedirector(AuthenticationManagementResource authMgmt, String authenticationFlowAlias, String identityProviderRedirectorAlias, PropertyGroup identityProviderRedirectorPg) throws Exception {
        System.out.println("initializing identity provider redirector: " + identityProviderRedirectorAlias);
        // Get identity provider redirector
        AuthenticationExecutionInfoRepresentation identityProviderRedirector = getIdentityProviderRedirector(authMgmt, authenticationFlowAlias);
        if (identityProviderRedirector == null) {
            throw new RuntimeException("Identity provider redirector does not exist");
        }

        // Update identity provider redirector
        identityProviderRedirector.setRequirement(identityProviderRedirectorPg.getStringProperty(KeycloakConfig.PROP_IDENTITY_PROVIDER_REDIRECTOR_REQUIREMENT));
        authMgmt.updateExecutions(authenticationFlowAlias, identityProviderRedirector);
        identityProviderRedirector = getIdentityProviderRedirector(authMgmt, authenticationFlowAlias);
        if (identityProviderRedirector == null) {
            throw new RuntimeException("Identity provider redirector does not exist");
        }

        // Create config representation if it does not exist
        AuthenticatorConfigRepresentation configRepresentation = identityProviderRedirector.getAuthenticationConfig() != null ? authMgmt.getAuthenticatorConfig(identityProviderRedirector.getAuthenticationConfig()) : null;
        if (configRepresentation == null) {
            configRepresentation = new AuthenticatorConfigRepresentation();
            configRepresentation.setAlias(identityProviderRedirectorAlias);
            PropertyGroup configPg = identityProviderRedirectorPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDER_MAPPER_CONFIG);
            if (configPg != null) {
                Map<String, String> config = configRepresentation.getConfig();
                if (config == null) {
                    config = new HashMap<>();
                }
                for (PropertyEntry configPe: configPg.getProperties()) {
                    String configKey = configPe.getName();
                    config.put(configKey, configPe.getValue() != null ? configPe.getValue().toString() : null);
                }
                configRepresentation.setConfig(config);
            }
            authMgmt.newExecutionConfig(identityProviderRedirector.getId(), configRepresentation);
            identityProviderRedirector = getIdentityProviderRedirector(authMgmt, authenticationFlowAlias);
            if (identityProviderRedirector == null) {
                throw new RuntimeException("Identity provider redirector does not exist");
            }
            configRepresentation = identityProviderRedirector.getAuthenticationConfig() != null ? authMgmt.getAuthenticatorConfig(identityProviderRedirector.getAuthenticationConfig()) : null;
            if (configRepresentation == null) {
                throw new RuntimeException("Unable to create identity provider redirector");
            }
        }

        // Update config representation
        configRepresentation.setAlias(identityProviderRedirectorAlias);
        PropertyGroup configPg = identityProviderRedirectorPg.getPropertyGroup(KeycloakConfig.PROP_IDENTITY_PROVIDER_MAPPER_CONFIG);
        if (configPg != null) {
            Map<String, String> config = configRepresentation.getConfig();
            if (config == null) {
                config = new HashMap<>();
            }
            for (PropertyEntry configPe: configPg.getProperties()) {
                String configKey = configPe.getName();
                config.put(configKey, configPe.getValue() != null ? configPe.getValue().toString() : null);
            }
            configRepresentation.setConfig(config);
        }
        authMgmt.updateAuthenticatorConfig(configRepresentation.getId(), configRepresentation);
    }

    /**
     * Initializes the group.
     * @param groups the groups resource
     * @param groupName the group name
     * @param groupPg the group property group
     * @throws Exception an Exception
     */
    private static void initializeGroup(GroupsResource groups, String groupName, PropertyGroup groupPg) throws Exception {
        System.out.println("initializing group: " + groupName);
        // Create group if it does not exist
        GroupRepresentation group = getGroupByName(groups, groupName);
        if (group == null) {
            group = new GroupRepresentation();
            group.setName(groupName);
            groups.add(group);
            group = getGroupByName(groups, groupName);
            if (group == null) {
                throw new RuntimeException("Unable to create group");
            }
        }

        // Update group settings
        PropertyGroup attributesPg = groupPg.getPropertyGroup(KeycloakConfig.PROP_GROUP_ATTRIBUTES);
        if (attributesPg != null) {
            Map<String, List<String>> attributes = group.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            for (PropertyEntry attributePe: attributesPg.getProperties()) {
                String attributeKey = attributePe.getName();
                List<String> attributeValue = PropertyGroup.convertToStringList(attributePe.getValue());
                attributes.put(attributeKey, attributeValue);
            }
            group.setAttributes(attributes);
        }
        groups.group(group.getId()).update(group);
    }

    /**
     * Initializes the user.
     * @param users the users resource
     * @param groups the groups resource
     * @param userName the user name
     * @param userPg the user property group
     * @throws Exception an Exception
     */
    private static void initializeUser(UsersResource users, GroupsResource groups, String userName, PropertyGroup userPg) throws Exception {
        System.out.println("initializing user: " + userName);
        // Create user if it does not exist
        UserRepresentation user = getUserByName(users, userName);
        if (user == null) {
            user = new UserRepresentation();
            user.setUsername(userName);
            users.create(user);
            user = getUserByName(users, userName);
            if (user == null) {
                throw new RuntimeException("Unable to create user");
            }
        }

        // Update user settings
        user.setEnabled(userPg.getBooleanProperty(KeycloakConfig.PROP_USER_ENABLED));
        PropertyGroup attributesPg = userPg.getPropertyGroup(KeycloakConfig.PROP_USER_ATTRIBUTES);
        if (attributesPg != null) {
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            for (PropertyEntry attributePe: attributesPg.getProperties()) {
                String attributeKey = attributePe.getName();
                List<String> attributeValue = PropertyGroup.convertToStringList(attributePe.getValue());
                attributes.put(attributeKey, attributeValue);
            }
            user.setAttributes(attributes);
        }
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(KeycloakConfig.KEYCLOAK_USER_PASSWORD_TYPE);
        credential.setTemporary(userPg.getBooleanProperty(KeycloakConfig.PROP_USER_PASSWORD_TEMPORARY));
        credential.setValue(userPg.getStringProperty(KeycloakConfig.PROP_USER_PASSWORD));
        user.setCredentials(Arrays.asList(credential));
        users.get(user.getId()).update(user);

        // Update user group memberships
        List<String> groupIds = getGroupIds(groups, userPg.getStringListProperty(KeycloakConfig.PROP_USER_GROUPS));
        if (groupIds != null) {
            List<String> existingGroupIds = getGroupIds(groups, user.getGroups());
            for (String existingGroupId : existingGroupIds) {
                if (!groupIds.contains(existingGroupId)) {
                    users.get(user.getId()).leaveGroup(existingGroupId);
                }
                else {
                    groupIds.remove(existingGroupId);
                }
            }
            for (String groupId : groupIds) {
                users.get(user.getId()).joinGroup(groupId);
            }
        }
    }

    /**
     * Gets the realm by name.
     * @param realmsResource the realms resource
     * @param realmName the realm name
     * @return the realm, or null if not found
     */
    private static RealmRepresentation getRealmByName(RealmsResource realmsResource, String realmName) {
        for (RealmRepresentation realm : realmsResource.findAll()) {
            if (realmName.equals(realm.getRealm())) {
                return realm;
            }
        }
        return null;
    }

    /**
     * Gets the client scope by name.
     * @param clientScopes the client scopes
     * @param clientScopeName the client scope name
     * @return the client scope, or null if not found
     */
    private static ClientScopeRepresentation getClientScopeByName(ClientScopesResource clientScopes, String clientScopeName) {
        for (ClientScopeRepresentation clientScope : clientScopes.findAll()) {
            if (clientScopeName.equals(clientScope.getName())) {
                return clientScope;
            }
        }
        return null;
    }

    /**
     * Gets the client scope IDs by name.
     * @param clientScopes the client scopes
     * @param clientScopeNames the client scope names
     * @return the client scope IDs
     */
    private static List<String> getClientScopeIds(ClientScopesResource clientScopes, List<String> clientScopeNames) {
        List<String> clientScopeIds = new ArrayList<>();
        Map<String, String> nameToIdMap = clientScopes.findAll().stream().collect(Collectors.toMap(c -> c.getName(), c -> c.getId()));

        for (String clientScopeName : clientScopeNames) {
            if (nameToIdMap.containsKey(clientScopeName)) {
                clientScopeIds.add(nameToIdMap.get(clientScopeName));
            } else {
                System.err.println("Skipping client scope '" + clientScopeName + "'; unable to find id for client scope with this name");
            }
        }
        return clientScopeIds;
    }

    /**
     * Gets the client by client ID.
     * @param client the clients
     * @param clientName the client name
     * @return the client, or null if not found
     */
    private static ClientRepresentation getClientByClientId(ClientsResource clients, String clientId) {
        for (ClientRepresentation client : clients.findAll()) {
            if (clientId.equals(client.getClientId())) {
                return client;
            }
        }
        return null;
    }

    /**
     * Gets the protocol mapper by name.
     * @param protocolMappers the protocol mappers
     * @param mapperName the mapper name
     * @return the protocol mapper, or null if not found
     */
    private static ProtocolMapperRepresentation getProtocolMapperByName(ProtocolMappersResource protocolMappers, String mapperName) {
        for (ProtocolMapperRepresentation protocolMapper : protocolMappers.getMappers()) {
            if (mapperName.equals(protocolMapper.getName())) {
                return protocolMapper;
            }
        }
        return null;
    }

    /**
     * Gets the identity provider by provider alias.
     * @param identityProviders the identity providers
     * @param identityProviderAlias the identity provider alias
     * @return the identity provider, or null if not found
     */
    private static IdentityProviderRepresentation getIdentityProviderByAlias(IdentityProvidersResource identityProviders, String identityProviderAlias) {
        for (IdentityProviderRepresentation identityProvider : identityProviders.findAll()) {
            if (identityProviderAlias.equals(identityProvider.getAlias())) {
                return identityProvider;
            }
        }
        return null;
    }

    /**
     * Gets the identity provider mapper by name.
     * @param identity provider the identity provider
     * @param mapperName the mapper name
     * @return the identity provider mapper, or null if not found
     */
    private static IdentityProviderMapperRepresentation getIdentityProvideMapperByName(IdentityProviderResource identityProvider, String mapperName) {
        for (IdentityProviderMapperRepresentation identityProviderMapper : identityProvider.getMappers()) {
            if (mapperName.equals(identityProviderMapper.getName())) {
                return identityProviderMapper;
            }
        }
        return null;
    }


    /**
     * Gets the authenication flow by alias.
     * @param authMgmt the authorization management
     * @param authenticationFlowAlias the authentication flow alias
     * @return the authorization flow, or null if not found
     */
    private static AuthenticationFlowRepresentation getAuthenticationFlowByAlias(AuthenticationManagementResource authMgmt, String authenticationFlowAlias) {
        for (AuthenticationFlowRepresentation flow : authMgmt.getFlows()) {
            if (authenticationFlowAlias.equals(flow.getAlias())) {
                return flow;
            }
        }
        return null;
    }

    /**
     * Gets the identity provider redirector by alias.
     * @param authMgmt the authorization management
     * @param authenticationFlowAlias the authentication flow alias
     * @return the authorization flow, or null if not found
     */
    private static AuthenticationExecutionInfoRepresentation getIdentityProviderRedirector(AuthenticationManagementResource authMgmt, String authenticationFlowAlias) {
        for (AuthenticationExecutionInfoRepresentation execution : authMgmt.getExecutions(authenticationFlowAlias)) {
            if (KeycloakConfig.KEYCLOAK_IDENTITY_PROVIDER_REDIRECTOR.equals(execution.getProviderId())) {
                return execution;
            }
        }
        return null;
    }


    /**
     * Gets the group by name.
     * @param groups the groups
     * @param groupName the group name
     * @return the group, or null if not found
     */
    private static GroupRepresentation getGroupByName(GroupsResource groups, String groupName) {
        for (GroupRepresentation group : groups.groups()) {
            if (groupName.equals(group.getName())) {
                return group;
            }
        }
        return null;
    }

    /**
     * Gets the group IDs by name.
     * @param groups the groups
     * @param groupNames the group names
     * @return the group IDs
     */
    private static List<String> getGroupIds(GroupsResource groups, List<String> groupNames) {
        List<String> groupIds = new ArrayList<>();
        for (GroupRepresentation group : groups.groups()) {
            if (groupNames != null && groupNames.contains(group.getName())) {
                groupIds.add(group.getId());
            }
        }
        return groupIds;
    }

    /**
     * Gets the user by name.
     * @param users the users
     * @param userName the user name
     * @return the user, or null if not found
     */
    private static UserRepresentation getUserByName(UsersResource users, String userName) {
        for (UserRepresentation user : users.list()) {
            if (userName.equals(user.getUsername())) {
                return user;
            }
        }
        return null;
    }
}
