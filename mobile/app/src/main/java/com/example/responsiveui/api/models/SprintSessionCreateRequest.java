package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Sprint session creation request
 */
public class SprintSessionCreateRequest {
    @SerializedName("goal_title")
    public String goalTitle;
    
    @SerializedName("description")
    public String description;
    
    @SerializedName("repo_link")
    public String repoLink;
    
    @SerializedName("meeting_link")
    public String meetingLink;
    
    @SerializedName("duration_minutes")
    public int durationMinutes;
    
    @SerializedName("participants")
    public List<String> participants;
    
    public SprintSessionCreateRequest(String goalTitle, String description, 
                                   String repoLink, String meetingLink, 
                                   int durationMinutes, List<String> participants) {
        this.goalTitle = goalTitle;
        this.description = description;
        this.repoLink = repoLink;
        this.meetingLink = meetingLink;
        this.durationMinutes = durationMinutes;
        this.participants = participants;
    }
}
