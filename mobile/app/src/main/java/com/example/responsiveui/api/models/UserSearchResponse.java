package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * User search response from chat search endpoint
 */
public class UserSearchResponse {
    @SerializedName("userId")
    public String userId;
    
    @SerializedName("email")
    public String email;
    
    @SerializedName("full_name")
    public String fullName;
    
    @SerializedName("profile_image_url")
    public String profileImageUrl;
    
    @SerializedName("bio")
    public String bio;
    
    @SerializedName("is_available")
    public boolean isAvailable;
    
    @SerializedName("level")
    public int level;
    
    @SerializedName("karma_score")
    public int karmaScore;
}
