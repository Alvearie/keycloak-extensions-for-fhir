# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

FROM adoptopenjdk/openjdk11-openj9:ubi


RUN dnf update -y && \
    dnf install -y nc tzdata openssl curl ca-certificates fontconfig glibc-langpack-en gzip tar findutils shadow-utils && \
    groupadd -r fhirschemaadmin -g 1001 && \
    useradd -u 1001 -r -g 1001 -m -d /home/fhirschemaadmin -s /sbin/nologin fhirschemaadmin && \
    rm -rf /var/cache/yum && \
    rm -f /@System.solv && \
    dnf clean all && \
    rm -rf /var/tmp/*

COPY --chown=1001:0 target/keycloak-config-*.jar /jars/
COPY --chown=1001:0 target/dependency/* /jars/
COPY --chown=1001:0 src/main/resources/config/keycloak-config.json /config/
COPY --chown=1001:0 run.sh /

USER 1001

ENTRYPOINT ["/run.sh"]

CMD ["-configFile", "config/keycloak-config.json"]
