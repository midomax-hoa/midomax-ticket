package vn.midomax.helpdesk;

import jakarta.persistence.Embeddable;

@Embeddable
public class EmergencyContact {
    private String name;
    private String phone;
    private String relationship;
    private String address;

    public EmergencyContact() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
