package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * User registration request
 */
public class UserRegisterRequest {
    @SerializedName("uid")
    public String uid;
    
    @SerializedName("email")
    public String email;
    
    @SerializedName("oauth_provider")
    public String oauthProvider;
    
    public UserRegisterRequest(String uid, String email, String oauthProvider) {
        this.uid = uid;
        this.email = email;
        this.oauthProvider = oauthProvider;
    }
}
