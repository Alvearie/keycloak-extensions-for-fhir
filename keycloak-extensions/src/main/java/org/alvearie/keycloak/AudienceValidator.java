/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.keycloak;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Validate an incoming "aud" query parameter against a configured list of acceptable audiences.
 */
public class AudienceValidator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(AudienceValidator.class);
    private static final String SMART_AUDIENCE_PARAM = "aud";

    public AudienceValidator(KeycloakSession session) {
        // NOOP
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {

        if (context.getAuthenticatorConfig() == null ||
                !context.getAuthenticatorConfig().getConfig().containsKey(AudienceValidatorFactory.AUDIENCES)) {
            String msg = "The Keycloak Audience Validation Extension must be configured with one or more allowed audiences";
            context.failure(AuthenticationFlowError.CLIENT_CREDENTIALS_SETUP_REQUIRED,
                    Response.status(302)
                        .header("Location", context.getAuthenticationSession().getRedirectUri() +
                                "?error=server_error" +
                                "&error_description=" + msg)
                        .build());
            return;  // early exit
        }

        String requestedAudience = context.getUriInfo().getQueryParameters().getFirst(SMART_AUDIENCE_PARAM);
        String audiencesString = context.getAuthenticatorConfig().getConfig().get(AudienceValidatorFactory.AUDIENCES);
        LOG.debugf("Requested audience: %s", requestedAudience);
        LOG.debugf("Allowed audiences: %s", audiencesString);

        List<String> audiences = Arrays.asList(audiencesString.split("##"));
        if (audiences.contains(requestedAudience)) {
            context.success();
        } else {
            String msg = "Requested audience '" + requestedAudience +
                    "' must match one of the configured Resource Server URLs: " + audiences;
            context.failure(AuthenticationFlowError.CLIENT_CREDENTIALS_SETUP_REQUIRED,
                    Response.status(302)
                        .header("Location", context.getAuthenticationSession().getRedirectUri() +
                                "?error=invalid_request" +
                                "&error_description=" + msg)
                        .build());
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // NOOP
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // NOOP
    }

    @Override
    public void close() {
        // NOOP
    }
}
