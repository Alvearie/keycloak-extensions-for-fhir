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
 * Factory for creating PatientSelectionForm instances.
 */
public class PatientSelectionFormFactory implements AuthenticatorFactory {

    private static final String PROVIDER_ID = "auth-select-patient";

    static final String INTERNAL_FHIR_URL_PROP_NAME = "internalFhirUrl";
    private static final String INTERNAL_FHIR_URL_PROP_LABEL = "FHIR Base URL";
    private static final String INTERNAL_FHIR_URL_PROP_DESCRIPTION = "The internal base URL of the FHIR resource server"
            + " for retrieving Patient resources. This can differ from the external base URL used by the client in"
            + " the 'aud' parameter.";

    @Override
    public String getDisplayType() {
        return "Patient Selection Authenticator";
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
            AuthenticationExecutionModel.Requirement.REQUIRED, AuthenticationExecutionModel.Requirement.DISABLED
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
        return "A patient context picker for supporting the launch/patient scope.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.singletonList(new ProviderConfigProperty(INTERNAL_FHIR_URL_PROP_NAME, INTERNAL_FHIR_URL_PROP_LABEL,
                INTERNAL_FHIR_URL_PROP_DESCRIPTION, ProviderConfigProperty.STRING_TYPE, null));
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new PatientSelectionForm();
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
