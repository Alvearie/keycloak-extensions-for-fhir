# Keycloak extensions for FHIR
<!-- Build Status, is a great thing to have at the top of your repository, it shows that you take your CI/CD as first class citizens -->
<!-- [![Build Status](https://travis-ci.org/jjasghar/ibm-cloud-cli.svg?branch=master)](https://travis-ci.org/jjasghar/ibm-cloud-cli) -->

[Keycloak](https://www.keycloak.org) is an open source identity and access management solution.

[HL7 FHIR](https://www.hl7.org/fhir) is a specification for sharing health-related data across processes, jurisdictions, and contexts.

[SMART on FHIR](https://docs.smarthealthit.org) is a collection of specifications that focus on authentication and authorization on top of HL7 FHIR.

The purpose of this project is to document and extend Keycloak in support of SMART on FHIR and related use cases.

## Patterns
The initial scope for this repository is to collect patterns for using Keycloak in support of SMART on FHIR and to provide the community with a "center of gravity" for comparing notes and discussing tradeoffs.

### Standalone app launch with "simple" `launch/patient` support via an external Identity Provider
In this pattern, Keycloak is used to broker users from an external OpenID Connect (OIDC) Identity Provider (IdP) and IdPs are required to scope a user session to a single FHIR Patient.

The IdP passes the corresponding Patient resource id (or something that can be mapped to the Patient resource id) to Keycloak via a pre-coordinated field on the IdP-issued id token or its corresponding userinfo/introspection response.

This field is mapped to a user attribute via a Keycloak *Attribute Importer* and then to
1. the issued access token via a Keycloak *User Attribute* mapper; and
2. the token response via the *Token Response Mapper* Keycloak extension from this repository (requires Keycloak 12.x or above).

Note: the TokenResponseMapper extension will be deprecated and removed from this repository once the built-in Keycloak *User Attribute* mapper has been updated to support this pattern.

#### Bill of materials
| Component | Description |
|-----------|-------------|
| keycloak-config | A Keycloak client for configuring realms via a JSON property file. |
| keycloak-extensions/AudienceValidator | A Keycloak Authenticator for validating the `aud` parameter passed as part of the SMART App Launch request to the authorization endpoint (see [SMART best practices](http://docs.smarthealthit.org/authorization/best-practices/#25-access-token-phishing-by-counterfeit-resource-servers) for more information). |
| keycloak-extensions/PatientprefixUserAttributeMapper | A Keycloak OIDCProtocolMapper for adding the `Patient/` prefix to a user attribute; used to map the Patient resource id attribute into a valid `fhirUser` claim on the id_token when the `fhirUser` claim is requested. |

### Standalone app launch with context picker and an external Identity Provider
In this pattern, Keycloak is used to broker users from one or more external OpenID Connect (OIDC) Identity Provider (IdP) and IdPs are required to pass the set of Patient resource ids (or something that can be mapped to the Patient resource ids) for which the user has access.

The IdP passes the corresponding Patient resource id (or something that can be mapped to the Patient resource id) to Keycloak via a pre-coordinated field on the IdP-issued id token or its corresponding userinfo/introspection response.

If the client application has requested the `launch/patient` scope, Keycloak will present a Patient context picker and the end user must select which patient to use for the scope of the current session.

Note that, with or without the patient context, it is possible to support both `user/<ResourceType>.<mode>` and `patient/<ResourceType>.<mode>` scope patterns with this approach because Keycloak will have access to all of the patient contexts for which the end user has access.

#### Bill of materials
TBD

## Contributing
Are you using Keycloak for SMART on FHIR or other health APIs? If so, we'd love to hear from you.

Connect with us on [chat.fhir.org](https://chat.fhir.org/#narrow/stream/179170-smart/topic/Keycloak.20for.20SMART.20authz)
or open an [issue](https://github.com/Alvearie/keycloak-extensions-for-fhir/issues).
