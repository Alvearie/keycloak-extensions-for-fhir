/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak;

import org.jsmart.zerocode.core.domain.EnvProperty;
import org.jsmart.zerocode.core.domain.Scenario;
import org.jsmart.zerocode.core.domain.TargetEnv;
import org.jsmart.zerocode.core.runner.ZeroCodeUnitRunner;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@EnvProperty("_${env}")
@TargetEnv("kc_integration_test.properties")
@RunWith(ZeroCodeUnitRunner.class)
public class KeycloakLoginTest {

    @Test
    @Scenario("testcases/login/login_to_keycloak.json")
    public void testA_keycloak_login() throws Exception {}

}
