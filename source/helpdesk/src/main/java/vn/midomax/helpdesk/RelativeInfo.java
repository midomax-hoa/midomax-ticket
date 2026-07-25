package vn.midomax.helpdesk;

import jakarta.persistence.Embeddable;

@Embeddable
public class RelativeInfo {
    private String name;
    private String dob;

    public RelativeInfo() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }
}
