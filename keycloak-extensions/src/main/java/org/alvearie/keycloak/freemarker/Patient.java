/*
(C) Copyright IBM Corp. 2021

SPDX-License-Identifier: Apache-2.0
*/
package org.alvearie.keycloak.freemarker;

/**
 * Simple struct for passing patient info to the patient-select-form freemarker template
 */
public class Patient {
    String id;
    String name;
    String dob;

    public Patient(String id, String name, String dob) {
        this.id = id;
        this.name = name;
        this.dob = dob;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDob() {
        return dob;
    }
}
