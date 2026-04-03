package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Sprint session update request
 */
public class SprintSessionUpdateRequest {
    @SerializedName("goal_title")
    public String goalTitle;
    
    @SerializedName("description")
    public String description;
    
    @SerializedName("status")
    public String status;
    
    @SerializedName("meeting_link")
    public String meetingLink;
    
    @SerializedName("participants")
    public List<String> participants;
}
