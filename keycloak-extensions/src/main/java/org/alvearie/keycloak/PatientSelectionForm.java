/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.keycloak;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.alvearie.keycloak.freemarker.PatientStruct;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.jboss.logging.Logger;
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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

/**
 * Present a patient context picker when the client requests the launch/patient scope and the
 * user record has multiple resourceId attributes. The selection is stored in a UserSessionNote
 * with name "patient_id".
 */
public class PatientSelectionForm implements Authenticator {

	private static final Logger LOG = Logger.getLogger(PatientSelectionForm.class);

	private static final String SMART_AUDIENCE_PARAM = "client_request_param_aud";

	private static final String SMART_SCOPE_PATIENT_READ = "patient/Patient.read";
	private static final String SMART_SCOPE_LAUNCH_PATIENT = "launch/patient";

	private static final String ATTRIBUTE_RESOURCE_ID = "resourceId";


	// creating the fhirContext is expensive, you only want to create it once
	private static final FhirContext fhirCtx = FhirContext.forR4();
	static {
		// turn off server validation (capability statement pre-checks)
		fhirCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
	}

	public PatientSelectionForm() {

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

		List<String> resourceIds = getResourceIdsForUser(context);
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

		String fhirBaseUrl = config.getConfig().get(PatientSelectionFormFactory.INTERNAL_FHIR_URL_PROP_NAME);
		IGenericClient hapiClient = fhirCtx.newRestfulGenericClient(fhirBaseUrl);

		try {
			Bundle returnBundle = hapiClient.transaction().withBundle(requestBundle)
					.withAdditionalHeader(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken)
					.execute();

			List<PatientStruct> patients = gatherPatientInfo(returnBundle);
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
		} catch (Exception ex) {
			String msg = "Error while retrieving Patient resources for the selection form";
			LOG.warn(msg);
			LOG.warn("Exception caught: " + ex);
			context.failure(AuthenticationFlowError.INTERNAL_ERROR,
					Response.status(302).header("Location", context.getAuthenticationSession().getRedirectUri()
							+ "?error=server_error" + "&error_description=" + msg).build());
			return;
		}
	}

	private List<String> getResourceIdsForUser(AuthenticationFlowContext context) {
		return context.getUser().getAttributeStream(ATTRIBUTE_RESOURCE_ID)
				.flatMap(a -> Arrays.stream(a.split(" ")))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
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

		// Note: this depends on the corresponding string being registered as a valid scope for this client
		ClientSessionContext clientSessionCtx = DefaultClientSessionContext.fromClientSessionAndScopeParameter(authedClientSession,
				SMART_SCOPE_PATIENT_READ, session);

		String requestedAudience = authSession.getClientNote(SMART_AUDIENCE_PARAM);
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

		Bundle searchBundle = new Bundle();
		searchBundle.setType(BundleType.BATCH);

		for (String id : resourceIds) {
			BundleEntryComponent bec = searchBundle.addEntry();
			BundleEntryRequestComponent request = new BundleEntryRequestComponent();
			request.setMethod(HTTPVerb.GET);
			request.setUrl("Patient/" + id);
			bec.setRequest(request);
		}
		return searchBundle;
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

		for (BundleEntryComponent entry : fhirResponse.getEntry()) {
			String status = entry.getResponse() == null ? null : entry.getResponse().getStatus();
			if (status == null || status.isBlank() || !status.startsWith("200")) {
				continue;
			}

			Resource resource = entry.getResource();
			if (!(resource instanceof Patient)) {
				continue;
			}

			Patient patient = (Patient) resource;
			String patientId = patient.getIdElement().getIdPart();

			String patientName = "Missing Name";
			if (patient.getName().isEmpty()) {
				LOG.warn("Patient[id=" + patient.getId() + "] has no name; using placeholder");
			} else {
				if (patient.getName().size() > 1) {
					LOG.warn("Patient[id=" + patient.getId() + "] has multiple names; using the first one");
				}
				patientName = constructSimpleName(patient.getName().get(0));
			}

			String patientDOB = "missing";
			if (patient.getBirthDate() != null) {
				LocalDate ld = patient.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				patientDOB = ld.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
			}

			LOG.debugf("Adding patient to return struct %s, %s", patientId, patientName);
			patients.add(new PatientStruct(patientId, patientName, patientDOB));
		}

		return patients;
	}

	private String constructSimpleName(HumanName name) {
		if (name == null) {
			return null;
		}
		String firstNames = name.getGivenAsSingleString();
		String lastName = name.getFamily();
		firstNames = firstNames == null ? "" : firstNames;
		lastName = lastName == null ? "" : lastName;
		return (firstNames + lastName).trim();
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

		LOG.debugf("The user selected patient '%s'", patient);

		if (patient == null || patient.trim().isEmpty() || !getResourceIdsForUser(context).contains(patient.trim())) {
			LOG.warnf("The patient selection '%s' is not valid for the authenticated user.", patient);
			context.cancelLogin();

			// reauthenticate...
			authenticate(context);
			return;
		}

		succeed(context, patient.trim());
	}

	@Override
	public void close() {
		// nothing to do
	}
}
