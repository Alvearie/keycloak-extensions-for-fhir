package org.alvearie.keycloak.freemarker;

public class PatientStruct {
    String id;
    String name;
    String dob;

    public PatientStruct(String id, String name, String dob) {
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
