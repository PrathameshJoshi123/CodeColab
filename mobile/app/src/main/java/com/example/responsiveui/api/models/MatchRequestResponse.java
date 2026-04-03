package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Match request response from API
 */
public class MatchRequestResponse {
    @SerializedName("id")
    public String id;
    
    @SerializedName("userId")
    public String userId;
    
    @SerializedName("session_type")
    public String sessionType;
    
    @SerializedName("message")
    public String message;
    
    @SerializedName("required_skills")
    public List<String> requiredSkills;
    
    @SerializedName("status")
    public String status;
    
    @SerializedName("created_at")
    public String createdAt;
}
