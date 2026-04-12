package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Google OAuth request model for login/signup via Google
 */
public class GoogleOAuthRequestModel {
    @SerializedName("id_token")
    public String idToken;
    
    @SerializedName("full_name")
    public String fullName;
    
    public GoogleOAuthRequestModel(String idToken, String fullName) {
        this.idToken = idToken;
        this.fullName = fullName;
    }
    
    public GoogleOAuthRequestModel(String idToken) {
        this.idToken = idToken;
        this.fullName = null;
    }
}
