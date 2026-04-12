package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Login request model for email/password authentication
 */
public class LoginRequestModel {
    @SerializedName("email")
    public String email;
    
    @SerializedName("password")
    public String password;
    
    public LoginRequestModel(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
