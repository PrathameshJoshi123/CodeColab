package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Match request response from API with user details
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

    @SerializedName("accepted_by")
    public String acceptedBy;

    @SerializedName("accepted_at")
    public String acceptedAt;

    @SerializedName("linked_sprint_id")
    public String linkedSprintId;

    @SerializedName("sprint_status")
    public String sprintStatus;

    @SerializedName("is_exhausted")
    public boolean isExhausted;

    @SerializedName("can_setup_sprint")
    public boolean canSetupSprint;
    
    @SerializedName("scheduled_date_time")
    public String scheduledDateTime;
    
    @SerializedName("created_at")
    public String createdAt;
    
    @SerializedName("user")
    public UserMatchProfile user;
    
    @SerializedName("user_skills")
    public List<UserSkillInfo> userSkills;
    
    /**
     * Nested user profile for match browse
     */
    public static class UserMatchProfile {
        @SerializedName("uid")
        public String uid;
        
        @SerializedName("email")
        public String email;
        
        @SerializedName("full_name")
        public String fullName;
        
        @SerializedName("bio")
        public String bio;
        
        @SerializedName("college")
        public String college;
        
        @SerializedName("city")
        public String city;
        
        @SerializedName("profile_image_url")
        public String profileImageUrl;
        
        @SerializedName("is_available")
        public boolean isAvailable;
        
        @SerializedName("reputation_score")
        public float reputationScore;
    }
    
    /**
     * Nested skill information
     */
    public static class UserSkillInfo {
        @SerializedName("id")
        public String id;
        
        @SerializedName("name")
        public String name;
        
        @SerializedName("proficiency_level")
        public String proficiencyLevel;
        
        @SerializedName("years_of_experience")
        public Integer yearsOfExperience;
    }
}
