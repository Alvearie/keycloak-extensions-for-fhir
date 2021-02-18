# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

FROM jboss/keycloak:12.0.3

USER root
RUN microdnf update -y && microdnf clean all
USER 1000

COPY keycloak-extensions/target/keycloak-extensions-*.jar /opt/jboss/keycloak/standalone/deployments/
RUN rm -rf /opt/jboss/keycloak/docs
