/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.alvearie.keycloak.freemarker.Patient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatedClientSessionModel;
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

/**
 * Present a patient context picker when the client requests the launch/patient scope and the
 * user record has multiple resourceId attributes. The selection is stored in a UserSessionNote
 * with name "patient_id".
 */
public class PatientSelectionForm implements Authenticator {

    private static final Logger LOG = Logger.getLogger(PatientSelectionForm.class);

    private static final String FHIR_BASE_URL = "http://localhost:9080/fhir-server/api/v4";

    private static final String SMART_AUDIENCE_PARAM = "aud";
    private static final String SMART_SCOPE_PATIENT_READ = "patient/Patient.read";
    private static final String SMART_SCOPE_LAUNCH_PATIENT = "launch/patient";

    private static final int requestedPageSize = 10;

    private WebTarget fhirClient;

    public PatientSelectionForm() {
        fhirClient = ResteasyClientBuilder.newClient()
                .target(URI.create(FHIR_BASE_URL));
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

        String accessToken = buildInternalAccessToken(context);
        // use RESTEasy to make the call to the resource server

        Response fhirResponse = fhirClient.path("Patient")
                .queryParam("_summary", "true")
                .queryParam("_count", "10")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken)
                .get();

        if (fhirResponse.getStatus() != 200) {
            String msg = "Error while retrieving Patient resources for the selection form";
            LOG.warnf(msg);
            LOG.warnf("Response with code " + fhirResponse.getStatus() + "%s%n", fhirResponse.readEntity(String.class));
            context.failure(AuthenticationFlowError.INTERNAL_ERROR,
                    Response.status(302)
                    .header("Location", context.getAuthenticationSession().getRedirectUri() +
                            "?error=server_error" +
                            "&error_description=" + msg)
                    .build());
        } else {
            String bundle = fhirResponse.readEntity(String.class);
            JsonReader reader = Json.createReader(new StringReader(bundle));
            JsonObject bundleObj = reader.readObject();
            JsonArray links = bundleObj.getJsonArray("link");
            JsonArray entries = bundleObj.getJsonArray("entry");

            if (hasNextLink(links)) {
                // TODO: support paging on the form?
                String total = bundleObj.containsKey("total") ? Integer.toString(bundleObj.getInt("total")) : "over " + requestedPageSize;
                LOG.warn("Patient search returned " + total + " matches; using the first " + requestedPageSize);
            }

            List<Patient> patients = new ArrayList<>();

            for (JsonValue jsonValue : entries) {
                JsonObject patient = jsonValue.asJsonObject().getJsonObject("resource");

                String patientId = patient.getString("id");

                String patientName = "Missing Name";
                JsonArray names = patient.getJsonArray("name");
                if (names == null || names.size() == 0) {
                    LOG.warn("Patient[id=" + patient.getString("id") + "] has no name; using placeholder");
                } else {
                    if (names.size() > 1) {
                        LOG.warn("Patient[id=" + patient.getString("id") + "] has multiple names; using the first one");
                    }
                    patientName = constructName(names.get(0));
                }

                String patientDOB = patient.containsKey("birthDate") ? patient.getString("birthDate") : "missing";

                patients.add(new Patient(patientId, patientName, patientDOB));
            }

            Response response = context.form()
                    .setAttribute("patients", patients)
                    .createForm("patient-select-form.ftl");

            context.challenge(response);
        }
    }

    private String constructName(JsonValue name) {
        String patientName;
        JsonObject nameObj = name.asJsonObject();
        if (nameObj.containsKey("text")) {
            patientName = nameObj.getJsonString("text").getString();
        } else {
            JsonArray given = nameObj.getJsonArray("given");
            JsonString family = name.asJsonObject().getJsonString("family");
            patientName = Stream.concat(given.stream(), Stream.of(family))
                    .filter(g -> g instanceof JsonString)
                    .map(s -> ((JsonString) s).getString())
                    .collect(Collectors.joining(" "));
        }
        return patientName;
    }

    private boolean hasNextLink(JsonArray links) {
        for (JsonValue link : links) {
            if (link instanceof JsonObject && "next".equals(link.asJsonObject().getString("relation"))) {
                return true;
            }
        }
        return false;
    }

    private String buildInternalAccessToken(AuthenticationFlowContext context) {
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

        Stream<ClientScopeModel> readPatient = TokenManager.getRequestedClientScopes(SMART_SCOPE_PATIENT_READ, client);
        ClientSessionContext clientSessionCtx = DefaultClientSessionContext.fromClientSessionAndClientScopes(authedClientSession,
                readPatient, session);

        String requestedAudience = context.getUriInfo().getQueryParameters().getFirst(SMART_AUDIENCE_PARAM);
        // Explicit decision not to check the requested audience against the internal URL of the FHIR Server
        // Checking of the requested audience should be performed in a previous step by the AudienceValidator
        TokenManager tokenManager = new TokenManager();
        AccessToken accessToken = tokenManager.createClientAccessToken(session, context.getRealm(), authSession.getClient(),
                context.getUser(), userSession, clientSessionCtx);

        JsonWebToken jwt = accessToken.audience(requestedAudience);
        return session.tokens().encode(jwt);
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

        // Add selected information to authentication session
        context.getAuthenticationSession().setUserSessionNote("patient_id", patient);

        context.success();
    }

    @Override
    public void close() {
        // NOOP
    }
}
