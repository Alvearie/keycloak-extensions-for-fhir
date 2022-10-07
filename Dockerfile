# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

# Build stage
FROM maven:3-openjdk-18-slim AS build
COPY pom.xml ./
COPY keycloak-config ./keycloak-config
COPY keycloak-extensions ./keycloak-extensions

RUN mvn -B clean package -DskipTests


# Package stage
FROM quay.io/keycloak/keycloak:18.0.2

# This can be overridden, but without this I've found the db vendor-detection in Keycloak to be brittle
ENV KC_HEALTH_ENABLED=true

# Install custom providers
#RUN curl -sL https://github.com/aerogear/keycloak-metrics-spi/releases/download/2.5.3/keycloak-metrics-spi-2.5.3.jar -o /opt/keycloak/providers/keycloak-metrics-spi-2.5.3.jar

COPY --from=build keycloak-extensions/target/keycloak-extensions-*.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build --health-enabled=true

#for debug, show the config
RUN /opt/keycloak/bin/kc.sh show-config

#NOTE - This will run the server in developer mode. Production deployments should change 'start-dev' to 'start' 
# and will require additional configuration. See: https://www.keycloak.org/server/configuration  
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev"]

