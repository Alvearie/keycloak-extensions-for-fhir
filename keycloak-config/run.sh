#!/usr/bin/env sh
###############################################################################
# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0
###############################################################################

# Execute the cli jar
exec java -cp 'jars/*' org.alvearie.keycloak.config.Main $@
