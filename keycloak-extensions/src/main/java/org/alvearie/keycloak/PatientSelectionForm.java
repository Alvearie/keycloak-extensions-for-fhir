/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.alvearie.keycloak.freemarker.PatientStruct;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.services.Urls;
import org.keycloak.services.util.DefaultClientSessionContext;
import org.keycloak.sessions.AuthenticationSessionModel;

import com.ibm.fhir.core.FHIRMediaType;
import com.ibm.fhir.model.config.FHIRModelConfig;
import com.ibm.fhir.model.resource.Bundle;
import com.ibm.fhir.model.resource.Bundle.Entry;
import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.type.Date;
import com.ibm.fhir.model.type.HumanName;
import com.ibm.fhir.model.type.Url;
import com.ibm.fhir.model.type.code.BundleType;
import com.ibm.fhir.model.type.code.HTTPVerb;
import com.ibm.fhir.provider.FHIRProvider;

/**
 * Present a patient context picker when the client requests the launch/patient scope and the
 * user record has multiple resourceId attributes. The selection is stored in a UserSessionNote
 * with name "patient_id".
 */
public class PatientSelectionForm implements Authenticator {

    private static final Logger LOG = Logger.getLogger(PatientSelectionForm.class);

    private static final String SMART_AUDIENCE_PARAM = "aud";
    private static final String SMART_SCOPE_PATIENT_READ = "patient/Patient.read";
    private static final String SMART_SCOPE_LAUNCH_PATIENT = "launch/patient";

    private static final String ATTRIBUTE_RESOURCE_ID = "resourceId";

    private Client fhirClient;

    public PatientSelectionForm() {
        FHIRModelConfig.setExtendedCodeableConceptValidation(false);
        fhirClient = ResteasyClientBuilder.newClient()
                .register(new FHIRProvider(RuntimeType.CLIENT));
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        ClientModel client = authSession.getClient();

        String requestedScopesString = authSession.getClientNote(OIDCLoginProtocol.SCOPE_PARAM);
        Stream<ClientScopeModel> clientScopes = TokenManager.getRequestedClientScopes(requestedScopesString, client);

        if (clientScopes.noneMatch(s -> SMART_SCOPE_LAUNCH_PATIENT.equals(s.getName()))) {
            // no launch/patient scope == no-op
            context.success();
            return;
        }

        if (context.getUser() == null) {
            fail(context, "Expected a user but found null");
            return;
        }

        List<String> resourceIds = context.getUser().getAttributeStream(ATTRIBUTE_RESOURCE_ID)
                .flatMap(a -> Arrays.stream(a.split(" ")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (resourceIds.size() == 0) {
            fail(context, "Expected user to have one or more resourceId attributes, but found none");
            return;
        }
        if (resourceIds.size() == 1) {
            succeed(context, resourceIds.get(0));
            return;
        }

        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null || !config.getConfig().containsKey(PatientSelectionFormFactory.INTERNAL_FHIR_URL_PROP_NAME)) {
            fail(context, "The Patient Selection Authenticator must be configured with a valid FHIR base URL");
            return;
        }

        String accessToken = buildInternalAccessToken(context, resourceIds);

        Bundle requestBundle = buildRequestBundle(resourceIds);
        try (Response fhirResponse = fhirClient
                .target(config.getConfig().get(PatientSelectionFormFactory.INTERNAL_FHIR_URL_PROP_NAME))
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken)
                .post(Entity.entity(requestBundle, FHIRMediaType.APPLICATION_FHIR_JSON_TYPE))) {

            if (fhirResponse.getStatus() != 200) {
                String msg = "Error while retrieving Patient resources for the selection form";
                LOG.warnf(msg);
                LOG.warnf("Response with code " + fhirResponse.getStatus() + "%n%s", fhirResponse.readEntity(String.class));
                context.failure(AuthenticationFlowError.INTERNAL_ERROR,
                        Response.status(302)
                        .header("Location", context.getAuthenticationSession().getRedirectUri() +
                                "?error=server_error" +
                                "&error_description=" + msg)
                        .build());
                return;
            }

            List<PatientStruct> patients = gatherPatientInfo(fhirResponse.readEntity(Bundle.class));
            if (patients.isEmpty()) {
                succeed(context, resourceIds.get(0));
                return;
            }

            if (patients.size() == 1) {
                succeed(context, patients.get(0).getId());
            } else {
                Response response = context.form()
                        .setAttribute("patients", patients)
                        .createForm("patient-select-form.ftl");

                context.challenge(response);
            }
        }
    }

    private String buildInternalAccessToken(AuthenticationFlowContext context, List<String> resourceIds) {
        KeycloakSession session = context.getSession();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        UserModel user = context.getUser();
        ClientModel client = authSession.getClient();

        UserSessionModel userSession = session.sessions().createUserSession(context.getRealm(), user, user.getUsername(),
                context.getConnection().getRemoteAddr(), null, false, null, null);

        AuthenticatedClientSessionModel authedClientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());
        if (authedClientSession == null) {
            authedClientSession = session.sessions().createClientSession(context.getRealm(), client, userSession);
        }
        authedClientSession.setNote(OIDCLoginProtocol.ISSUER,
                Urls.realmIssuer(session.getContext().getUri().getBaseUri(), context.getRealm().getName()));

        // Note: this depends on the corresponding string being registered as a valid scope for this client, otherwise it comes back empty
        Stream<ClientScopeModel> readPatient = TokenManager.getRequestedClientScopes(SMART_SCOPE_PATIENT_READ, client);
        ClientSessionContext clientSessionCtx = DefaultClientSessionContext.fromClientSessionAndClientScopes(authedClientSession,
                readPatient, session);

        String requestedAudience = context.getUriInfo().getQueryParameters().getFirst(SMART_AUDIENCE_PARAM);
        if (requestedAudience == null) {
            String internalFhirUrl = context.getAuthenticatorConfig().getConfig().get(PatientSelectionFormFactory.INTERNAL_FHIR_URL_PROP_NAME);
            LOG.info("Client request is missing the 'aud' parameter, using '" + internalFhirUrl + "' from config.");
            requestedAudience = internalFhirUrl;
        }

        // Explicit decision not to check the requested audience against the configured internal FHIR URL
        // Checking of the requested audience should be performed in a previous step by the AudienceValidator
        TokenManager tokenManager = new TokenManager();
        AccessToken accessToken = tokenManager.createClientAccessToken(session, context.getRealm(), authSession.getClient(),
                context.getUser(), userSession, clientSessionCtx);

        // Explicitly override the scope string with what we need (less brittle than depending on this to exist as a client scope)
        accessToken.setScope(SMART_SCOPE_PATIENT_READ);

        JsonWebToken jwt = accessToken.audience(requestedAudience);
        jwt.setOtherClaims("patient_id", resourceIds);
        return session.tokens().encode(jwt);
    }

    private Bundle buildRequestBundle(List<String> resourceIds) {
        Bundle.Builder requestBuilder = Bundle.builder()
                .type(BundleType.BATCH);
        resourceIds.stream()
                .map(id -> Entry.Request.builder()
                        .method(HTTPVerb.GET)
                        .url(Url.of("Patient/" + id))
                        .build())
                .map(request -> Entry.builder()
                        .request(request)
                        .build())
                .forEach(entry -> requestBuilder.entry(entry));
        return requestBuilder.build();
    }

    private void fail(AuthenticationFlowContext context, String msg) {
        LOG.warn(msg);
        context.failure(AuthenticationFlowError.INTERNAL_ERROR,
                Response.status(302)
                .header("Location", context.getAuthenticationSession().getRedirectUri() +
                        "?error=server_error" +
                        "&error_description=" + msg)
                .build());
    }

    private void succeed(AuthenticationFlowContext context, String patient) {
        // Add selected information to authentication session
        context.getAuthenticationSession().setUserSessionNote("patient_id", patient);
        context.success();
    }

    private List<PatientStruct> gatherPatientInfo(Bundle fhirResponse) {
        List<PatientStruct> patients = new ArrayList<>();

        for (Entry entry : fhirResponse.getEntry()) {
            if (entry.getResponse() == null || !entry.getResponse().getStatus().hasValue() ||
                    !entry.getResponse().getStatus().getValue().startsWith("200")) {
                continue;
            }

            Patient patient = entry.getResource().as(Patient.class);

            String patientId = patient.getId();

            String patientName = "Missing Name";
            if (patient.getName().isEmpty()) {
                LOG.warn("Patient[id=" + patient.getId() + "] has no name; using placeholder");
            } else {
                if (patient.getName().size() > 1) {
                    LOG.warn("Patient[id=" + patient.getId() + "] has multiple names; using the first one");
                }
                patientName = constructSimpleName(patient.getName().get(0));
            }

            String patientDOB = patient.getBirthDate() == null ? "missing"
                    : Date.PARSER_FORMATTER.format(patient.getBirthDate().getValue());

            patients.add(new PatientStruct(patientId, patientName, patientDOB));
        }

        return patients;
    }

    private String constructSimpleName(HumanName name) {
        if (name.getText() != null && name.getText().hasValue()) {
            return name.getText().getValue();
        }

        return Stream.concat(name.getGiven().stream(), Stream.of(name.getFamily()))
                .map(n -> n.getValue())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void action(AuthenticationFlowContext context) {

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String patient = formData.getFirst("patient");

        LOG.infof("Retrieved patient=%s", patient);

        if (patient == null || patient.trim().isEmpty()) {

            context.cancelLogin();

            // reauthenticate...
            authenticate(context);
            return;
        }

        succeed(context, patient);
    }

    @Override
    public void close() {
        // NOOP
    }
}
