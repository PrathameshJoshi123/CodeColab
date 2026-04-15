package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Sprint session response from API
 */
public class SprintSessionResponse {
    @SerializedName("id")
    public String id;
    
    @SerializedName("createdBy")
    public String createdBy;
    
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
    
    @SerializedName("status")
    public String status;
    
    @SerializedName("start_time")
    public String startTime;
    
    @SerializedName("end_time")
    public String endTime;
    
    @SerializedName("participants")
    public List<String> participants;
    
    @SerializedName("participantDetails")
    public List<ParticipantDetail> participantDetails;
    
    @SerializedName("match_id")
    public String matchId;
}
