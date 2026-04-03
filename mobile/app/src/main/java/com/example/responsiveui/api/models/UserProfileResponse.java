package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * User profile response
 */
public class UserProfileResponse {
    @SerializedName("userId")
    public String userId;
    
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
    
    @SerializedName("karma_score")
    public int karmaScore;
    
    @SerializedName("xp_points")
    public int xpPoints;
    
    @SerializedName("level")
    public int level;
    
    @SerializedName("streak_count")
    public int streakCount;
    
    @SerializedName("is_available")
    public boolean isAvailable;
}
