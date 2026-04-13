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
    
    @SerializedName("scheduled_date_time")
    public String scheduledDateTime;
    
    public MatchRequestCreateRequest(String sessionType, String message, List<String> requiredSkills) {
        this.sessionType = sessionType;
        this.message = message;
        this.requiredSkills = requiredSkills;
        this.scheduledDateTime = null;
    }
    
    public MatchRequestCreateRequest(String sessionType, String message, List<String> requiredSkills, String scheduledDateTime) {
        this.sessionType = sessionType;
        this.message = message;
        this.requiredSkills = requiredSkills;
        this.scheduledDateTime = scheduledDateTime;
    }
}
