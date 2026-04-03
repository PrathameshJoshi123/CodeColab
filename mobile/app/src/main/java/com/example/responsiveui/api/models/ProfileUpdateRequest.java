package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Profile update request
 */
public class ProfileUpdateRequest {
    @SerializedName("full_name")
    public String fullName;
    
    @SerializedName("bio")
    public String bio;
    
    @SerializedName("college")
    public String college;
    
    @SerializedName("city")
    public String city;
    
    @SerializedName("github_username")
    public String githubUsername;
    
    @SerializedName("linkedin_url")
    public String linkedinUrl;
    
    @SerializedName("profile_image_url")
    public String profileImageUrl;
    
    @SerializedName("is_available")
    public boolean isAvailable;
}
