# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

# Build stage
FROM maven:3-jdk-8-slim AS build
COPY pom.xml ./
COPY keycloak-config ./keycloak-config
COPY jboss-fhir-provider ./jboss-fhir-provider
COPY keycloak-extensions ./keycloak-extensions

RUN mvn -B package -DskipTests


# Package stage
FROM quay.io/keycloak/keycloak:16.1.0

# This can be overridden, but without this I've found the db vendor-detection in Keycloak to be brittle
ENV DB_VENDOR=H2

COPY --from=build keycloak-extensions/target/keycloak-extensions-*.jar /opt/jboss/keycloak/standalone/deployments/
COPY --from=build jboss-fhir-provider/target/jboss-modules/ /opt/jboss/keycloak/modules/system/layers/base/
RUN rm -rf /opt/jboss/keycloak/docs
