/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;

/**
 * Factory for creating AudienceValidator instances.
 */
public class AudienceValidatorFactory implements AuthenticatorFactory {

    private static final String PROVIDER_ID = "audience-validator";

    static final String AUDIENCES_PROP_NAME = "audiences";
    private static final String AUDIENCES_PROP_LABEL = "Allowed Audiences";
    private static final String AUDIENCES_PROP_DESCRIPTION = "Valid audiences for clients to request.";

    @Override
    public String getDisplayType() {
        return "Audience Validation";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    public static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Verifies that the audience requested by the client (via the 'aud' parameter) "
                + "matches one of the configured audience values.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.singletonList(new ProviderConfigProperty(AUDIENCES_PROP_NAME, AUDIENCES_PROP_LABEL,
                AUDIENCES_PROP_DESCRIPTION, ProviderConfigProperty.MULTIVALUED_STRING_TYPE, null));
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new AudienceValidator(session);
    }

    @Override
    public void init(Config.Scope config) {
        // NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // NOOP
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
