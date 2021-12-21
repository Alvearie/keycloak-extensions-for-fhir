# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

# Note: this Dockerfile must be executed from the root of the project
# Example:
#   > docker build . -f keycloak-config/Dockerfile

# Build stage
FROM maven:3-jdk-8-slim AS build
COPY pom.xml ./
COPY keycloak-config ./keycloak-config

RUN mvn -B package -f keycloak-config -DskipTests


# Package stage
FROM eclipse-temurin:11

RUN groupadd -r keycloak-client -g 1001 && \
    useradd -u 1001 -r -g 1001 -m -d /home/keycloak-client -s /sbin/nologin keycloak-client

COPY --from=build --chown=1001:0 keycloak-config/target/keycloak-config-*.jar /jars/
COPY --from=build --chown=1001:0 keycloak-config/target/dependency/* /jars/
COPY --from=build --chown=1001:0 keycloak-config/src/main/resources/config/keycloak-config.json /config/
COPY --from=build --chown=1001:0 keycloak-config/run.sh /

ENV KEYCLOAK_BASE_URL=http://host.docker.internal:8080/auth \
    KEYCLOAK_REALM=test \
    KEYCLOAK_USER=admin \
    KEYCLOAK_PASSWORD=admin \
    FHIR_BASE_URL=https://localhost:9443/fhir-server/api/v4

USER 1001

ENTRYPOINT ["/run.sh"]

CMD ["-configFile", "config/keycloak-config.json"]
