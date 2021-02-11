#!/usr/bin/env sh
###############################################################################
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
###############################################################################

set -ex

# Assumes the first argument is "-configFile" and the second one is a path to the config file
CONFIG="$2"

# Replace the placeholders with values from env variables
sed -i -e "s%\"<KEYCLOAK_REALM>\"%\"${KEYCLOAK_REALM}\"%g" \
       -e "s%\"<KEYCLOAK_USER>\"%\"${KEYCLOAK_USER}\"%g" \
       -e "s%\"<KEYCLOAK_PASSWORD>\"%\"${KEYCLOAK_PASSWORD}\"%g" \
       -e "s%\"<FHIR_BASE_URL>\"%\"${FHIR_BASE_URL}\"%g" \
       -e "s%\"<FHIR_DSID>\"%\"${FHIR_DSID}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_CLIENT_ID>\"%\"${IDENTITY_PROVIDER_CLIENT_ID}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_CLIENT_SECRET>\"%\"${IDENTITY_PROVIDER_CLIENT_SECRET}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_TOKEN_URL>\"%\"${IDENTITY_PROVIDER_TOKEN_URL}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_AUTH_URL>\"%\"${IDENTITY_PROVIDER_AUTH_URL}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_USERINFO_URL>\"%\"${IDENTITY_PROVIDER_USERINFO_URL}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_JWKS_URL>\"%\"${IDENTITY_PROVIDER_JWKS_URL}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_ISSUER>\"%\"${IDENTITY_PROVIDER_ISSUER}\"%g" \
       -e "s%\"<IDENTITY_PROVIDER_RESOURCEID_CLAIM>\"%\"${IDENTITY_PROVIDER_RESOURCEID_CLAIM}\"%g" \
       ${CONFIG}

# Execute the cli jar
#java -jar keycloak-config-*-cli.jar $@
java -cp 'jars/*' org.alvearie.keycloak.config.InitializeKeycloakConfig $@
