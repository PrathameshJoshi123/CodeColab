package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Match request creation request
 */
public class MatchRequestCreateRequest {
    @SerializedName("session_type")
    public String sessionType;
    
    @SerializedName("message")
    public String message;
    
    @SerializedName("required_skills")
    public List<String> requiredSkills;
    
    public MatchRequestCreateRequest(String sessionType, String message, List<String> requiredSkills) {
        this.sessionType = sessionType;
        this.message = message;
        this.requiredSkills = requiredSkills;
    }
}
