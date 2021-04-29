# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

FROM adoptopenjdk/openjdk11-openj9:ubi


RUN groupadd -r keycloak-client -g 1001 && \
    useradd -u 1001 -r -g 1001 -m -d /home/keycloak-client -s /sbin/nologin keycloak-client

COPY --chown=1001:0 target/keycloak-config-*.jar /jars/
COPY --chown=1001:0 target/dependency/* /jars/
COPY --chown=1001:0 src/main/resources/config/keycloak-config.json /config/
COPY --chown=1001:0 run.sh /

ENV KEYCLOAK_BASE_URL=http://host.docker.internal:8080/auth \
    KEYCLOAK_REALM=test \
    KEYCLOAK_USER=admin \
    KEYCLOAK_PASSWORD=admin \
    FHIR_BASE_URL=https://localhost:9443/fhir-server/api/v4

USER 1001

ENTRYPOINT ["/run.sh"]

CMD ["-configFile", "config/keycloak-config.json"]
