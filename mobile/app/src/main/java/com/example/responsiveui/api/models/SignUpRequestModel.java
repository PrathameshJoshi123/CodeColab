package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Signup request model for email/password registration
 */
public class SignUpRequestModel {
    @SerializedName("email")
    public String email;
    
    @SerializedName("password")
    public String password;
    
    @SerializedName("full_name")
    public String fullName;
    
    public SignUpRequestModel(String email, String password, String fullName) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }
}
