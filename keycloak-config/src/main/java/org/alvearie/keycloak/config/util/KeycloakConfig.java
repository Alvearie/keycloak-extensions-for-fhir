/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak.config.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonValue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;

/**
 * This class contains the Keycloak configuration.
 */
public class KeycloakConfig {

    // Keycloak configuration property names (top-level)
    public static final String PROP_KEYCLOAK_SERVER_URL = "keycloak|serverUrl";
    public static final String PROP_KEYCLOAK_ADMIN_USER = "keycloak|adminUser";
    public static final String PROP_KEYCLOAK_ADMIN_PW = "keycloak|adminPassword";
    public static final String PROP_KEYCLOAK_ADMIN_CLIENT_ID = "keycloak|adminClientId";
    public static final String PROP_KEYCLOAK_REALMS = "keycloak|realms";

    // Keycloak configuration property names (relative)
    public static final String PROP_REALM_ENABLED = "enabled";
    public static final String PROP_CLIENT_SCOPES = "clientScopes";
    public static final String PROP_CLIENT_SCOPE_DESCRIPTION = "description";
    public static final String PROP_CLIENT_SCOPE_PROTOCOL = "protocol";
    public static final String PROP_CLIENT_SCOPE_ATTRIBUTES = "attributes";
    public static final String PROP_CLIENT_SCOPE_MAPPERS = "mappers";
    public static final String PROP_CLIENT_SCOPE_MAPPER_PROTOCOL = "protocol";
    public static final String PROP_CLIENT_SCOPE_MAPPER_PROTOCOL_MAPPER = "protocolmapper";
    public static final String PROP_CLIENT_SCOPE_MAPPER_PROTOCOL_MAPPER_CONFIG = "config";
    public static final String PROP_DEFAULT_DEFAULT_CLIENT_SCOPES = "defaultDefaultClientScopes";
    public static final String PROP_DEFAULT_OPTIONAL_CLIENT_SCOPES = "defaultOptionalClientScopes";
    public static final String PROP_IDENTITY_PROVIDERS = "identityProviders";
    public static final String PROP_IDENTITY_PROVIDER_DISPLAY_NAME = "displayName";
    public static final String PROP_IDENTITY_PROVIDER_ENABLED = "enabled";
    public static final String PROP_IDENTITY_PROVIDER_FIRST_BROKER_LOGIN_FLOW_ALIAS = "firstBrokerLoginFlowAlias";
    public static final String PROP_IDENTITY_PROVIDER_CONFIG = "config";
    public static final String PROP_IDENTITY_PROVIDER_PROVIDER_ID = "providerId";
    public static final String PROP_IDENTITY_PROVIDER_MAPPERS = "mappers";
    public static final String PROP_IDENTITY_PROVIDER_MAPPER_IDENTITY_PROVIDER_MAPPER = "identityProviderMapper";
    public static final String PROP_IDENTITY_PROVIDER_MAPPER_CONFIG = "config";
    public static final String PROP_CLIENTS = "clients";
    public static final String PROP_CLIENT_NAME = "name";
    public static final String PROP_CLIENT_DESCRIPTION = "description";
    public static final String PROP_CLIENT_CONSENT_REQUIRED = "consentRequired";
    public static final String PROP_CLIENT_PUBLIC_CLIENT = "publicClient";
    public static final String PROP_CLIENT_BEARER_ONLY = "bearerOnly";
    public static final String PROP_CLIENT_DIRECT_ACCESS_ENABLED = "enableDirectAccess";
    public static final String PROP_CLIENT_DEFAULT_CLIENT_SCOPES = "defaultClientScopes";
    public static final String PROP_CLIENT_OPTIONAL_CLIENT_SCOPES = "optionalClientScopes";
    public static final String PROP_CLIENT_ROOT_URL = "rootURL";
    public static final String PROP_CLIENT_REDIRECT_URIS = "redirectURIs";
    public static final String PROP_CLIENT_ADMIN_URL = "adminURL";
    public static final String PROP_CLIENT_WEB_ORIGINS = "webOrigins";
    public static final String PROP_AUTHENTICATION_FLOWS = "authenticationFlows";
    public static final String PROP_BROWSER_FLOW = "browserFlow";
    public static final String PROP_IDENTITY_REDIRECTOR = "identityProviderRedirector";
    public static final String PROP_IDENTITY_PROVIDER_REDIRECTOR_ALIAS = "alias";
    public static final String PROP_IDENTITY_PROVIDER_REDIRECTOR_REQUIREMENT = "requirement";
    public static final String PROP_IDENTITY_PROVIDER_REDIRECTOR_DEFAULT_PROVIDER = "defaultProvider";
    public static final String PROP_GROUPS = "groups";
    public static final String PROP_GROUP_ATTRIBUTES = "attributes";
    public static final String PROP_DEFAULT_GROUPS = "defaultGroups";
    public static final String PROP_USERS = "users";
    public static final String PROP_USER_ENABLED = "enabled";
    public static final String PROP_USER_PASSWORD = "password";
    public static final String PROP_USER_PASSWORD_TEMPORARY = "passwordTemporary";
    public static final String PROP_USER_ATTRIBUTES = "attributes";
    public static final String PROP_USER_GROUPS = "groups";
    public static final String KEYCLOAK_USER_PASSWORD_TYPE = "password";
    public static final String KEYCLOAK_FIRST_BROKER_LOGIN = "first broker login";
    public static final String KEYCLOAK_IDENTITY_PROVIDER_CLIENT_SECRET = "clientSecret";
    public static final String KEYCLOAK_IDENTITY_PROVIDER_REDIRECTOR = "identity-provider-redirector";
    public static final String PROP_EVENTS_CONFIG = "eventsConfig";
    public static final String PROP_EVENTS_CONFIG_SAVE_LOGIN_EVENTS = "saveLoginEvents";
    public static final String PROP_EVENTS_CONFIG_EXPIRATION = "expiration";
    public static final String PROP_EVENTS_CONFIG_SAVE_TYPES = "types";
    public static final String PROP_EVENTS_CONFIG_SAVE_ADMIN_EVENTS = "saveAdminEvents";

    private static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(null);

    private String fileName;
    private PropertyGroup config;

    /**
     * Instantiates configuration using the specified file name.
     *
     * @param fileName
     *            the JSON file containing keycloak configuration
     */
    public KeycloakConfig(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the property group.
     *
     * @param propertyName
     *            the property name
     * @return the property group
     */
    public PropertyGroup getPropertyGroup(String propertyName) {
        return getTypedProperty(PropertyGroup.class, propertyName, null);
    }

    /**
     * Gets the property value as a string.
     *
     * @param propertyName
     *            the property name
     * @return the property value
     */
    public String getStringProperty(String propertyName) {
        return getTypedProperty(String.class, propertyName, null);
    }

    /**
     * Gets the property value as a boolean.
     *
     * @param propertyName
     *            the property name
     * @return the property value
     */
    public Boolean getBooleanProperty(String propertyName) {
        return getTypedProperty(Boolean.class, propertyName, null);
    }

    /**
     * Gets the property value as an integer.
     *
     * @param propertyName
     *            the property name
     * @return the property value
     */
    public Integer getIntProperty(String propertyName) {
        return getTypedProperty(Integer.class, propertyName, null);
    }

    /**
     * Gets the property value as a double.
     *
     * @param propertyName
     *            the property name
     * @return the property value
     */
    public Double getDoubleProperty(String propertyName) {
        return getTypedProperty(Double.class, propertyName, null);
    }

    /**
     * Gets the property value as a list.
     *
     * @param propertyName
     *            the property name
     * @return the property value
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringListProperty(String propertyName) {
        return getTypedProperty(List.class, propertyName, null);
    }

    /**
     * This function retrieves the specified property as a generic JsonValue.
     *
     * @param propertyName
     *            the hierarchical name of the property to be retrieved (e.g. "level1/level2/prop1")
     * @return a JsonValue representing the property's value or null if it wasn't found
     */
    private JsonValue getProperty(String propertyName) {
        JsonValue result = null;
        PropertyGroup pg = loadConfiguration();
        if (pg != null) {
            result = pg.getJsonValue(propertyName);
        }
        return result;
    }

    /**
     * Loads the specified file as a JSON file and returns a PropertyGroup containing the contents of the JSON file as
     * the root property group.
     *
     * @param filename
     *            the name of the JSON file to be loaded
     */
    private PropertyGroup loadConfiguration() {
        if (config == null) {
            try (InputStream is = resolveFile(fileName)) {
                String templatedJson = IOUtils.toString(is, StandardCharsets.UTF_8);
                String resolvedJson = StringSubstitutor.replace(templatedJson, EnvironmentVariables.get());
                try (JsonReader reader = JSON_READER_FACTORY.createReader(new StringReader(resolvedJson))) {
                    JsonObject jsonObj = reader.readObject();
                    reader.close();
                    config = new PropertyGroup(jsonObj);
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to load configuration", e);
            }
        }
        return config;
    }

    /**
     * Returns an InputStream for the specified filename. This function will first try to open the file using the
     * filename as a relative or absolute filename. If that fails, then we'll try to find the file on the classpath.
     *
     * @param filename
     *            the name of the file to search for
     * @return an InputStream to the file or throws a FileNotFoundException if not found
     * @throws FileNotFoundException
     */
    private static InputStream resolveFile(String filename) throws FileNotFoundException {
        // First, try to use the filename as-is.
        File f = new File(filename);
        if (f.exists()) {
            return new FileInputStream(f);
        }

        // Next, look on the classpath.
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
        if (is != null) {
            return is;
        }

        throw new FileNotFoundException("File '" + filename + "' was not found.");
    }

    /**
     * This generic function will perform the work of retrieving a property and then converting the resulting value to
     * the appropriate type.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @param defaultValue
     *            the default value to return in the event that the property is not found
     * @return the typed property
     */
    @SuppressWarnings("unchecked")
    private <T> T getTypedProperty(Class<T> expectedDataType, String propertyName, T defaultValue) {
        T result = null;

        // Find the property as a generic JsonValue from either the current tenant's config or the default config.
        JsonValue jsonValue = getProperty(propertyName);

        // If found, then convert the value to the expected type.
        if (jsonValue != null) {
            Object obj = null;
            try {
                obj = PropertyGroup.convertJsonValue(jsonValue);
                if (obj != null) {
                    // If the property was of the expected type, then just do the assignment.
                    // Otherwise, we'll try to do some simple conversions (e.g. String --> Boolean).
                    if (expectedDataType.isAssignableFrom(obj.getClass())) {
                        result = (T) obj;
                    } else {
                        if (obj instanceof String) {
                            if (Boolean.class.equals(expectedDataType)) {
                                result = (T) Boolean.valueOf((String) obj);
                            } else if (Integer.class.equals(expectedDataType)) {
                                result = (T) Integer.valueOf((String) obj);
                            } else if (Double.class.equals(expectedDataType)) {
                                result = (T) Double.valueOf((String) obj);
                            } else {
                                throw new RuntimeException("Expected property " + propertyName + " to be of type " + expectedDataType.getName()
                                        + ", but was of type "
                                        + obj.getClass().getName());
                            }
                        } else if (obj instanceof Boolean) {
                            if (String.class.equals(expectedDataType)) {
                                result = (T) ((Boolean) obj).toString();
                            } else {
                                throw new RuntimeException("Expected property " + propertyName + " to be of type " + expectedDataType.getName()
                                        + ", but was of type "
                                        + obj.getClass().getName());
                            }
                        } else {
                            throw new RuntimeException("Expected property " + propertyName + " to be of type " + expectedDataType.getName()
                                    + ", but was of type "
                                    + obj.getClass().getName());
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Unexpected error converting property '" + propertyName + "' to native type.", e);
            }
        }

        return (result != null ? result : defaultValue);
    }

    /**
     * Utility class that allows mocking system environment variables retrieval in test classes (as Mockito disallows
     * mocking static methods of {@link System}).
     */
    public static class EnvironmentVariables {
        /**
         * Simple proxy method for {@link System#getenv()} that returns an unmodifiable string map view of the current
         * system environment.
         *
         * @return the environment as a map of variable names to values
         */
        public static Map<String,String> get() {
            return System.getenv();
        }
    }
}
