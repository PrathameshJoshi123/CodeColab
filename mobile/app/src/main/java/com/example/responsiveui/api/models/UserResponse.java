package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * User response from API
 */
public class UserResponse {
    @SerializedName("uid")
    public String uid;
    
    @SerializedName("email")
    public String email;
    
    @SerializedName("oauth_provider")
    public String oauthProvider;
    
    @SerializedName("is_verified")
    public boolean isVerified;
    
    @SerializedName("is_active")
    public boolean isActive;
    
    @SerializedName("created_at")
    public String createdAt;
    
    @SerializedName("updated_at")
    public String updatedAt;
}
