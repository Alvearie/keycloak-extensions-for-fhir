# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

FROM quay.io/keycloak/keycloak:12.0.4

# This can be overridden, but without this I've found the db vendor-detection in Keycloak to be brittle
ENV DB_VENDOR=H2

COPY keycloak-extensions/target/keycloak-extensions-*.jar /opt/jboss/keycloak/standalone/deployments/
COPY jboss-fhir-provider/target/jboss-modules/ /opt/jboss/keycloak/modules/system/layers/base/
RUN rm -rf /opt/jboss/keycloak/docs
