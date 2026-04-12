package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Authentication response model with JWT token
 */
public class AuthResponseModel {
    @SerializedName("access_token")
    public String accessToken;
    
    @SerializedName("token_type")
    public String tokenType;
    
    @SerializedName("expires_in")
    public int expiresIn;
    
    @SerializedName("user_id")
    public String userId;
    
    @SerializedName("email")
    public String email;
    
    public AuthResponseModel() {}
    
    public AuthResponseModel(String accessToken, String tokenType, int expiresIn, String userId, String email) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.email = email;
    }
}
