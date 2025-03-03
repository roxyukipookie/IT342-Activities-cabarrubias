package com.cabarrubias.oauth2login.model;

public class Contact {
    private String name;
    private String email;
    private String phone;
    private String resourceName;

    public Contact(String resourceName, String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.resourceName = resourceName;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getResourceName() {return resourceName;}


    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setResourceName(String resourceName) {this.resourceName = resourceName;}


}
