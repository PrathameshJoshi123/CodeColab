package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Sprint Todo response from API
 */
public class SprintTodoResponse {
    @SerializedName("id")
    public String id;
    
    @SerializedName("sprint_id")
    public String sprintId;
    
    @SerializedName("title")
    public String title;
    
    @SerializedName("description")
    public String description;
    
    @SerializedName("is_completed")
    public boolean isCompleted;
    
    @SerializedName("created_by")
    public String createdBy;
    
    @SerializedName("completed_at")
    public String completedAt;
    
    @SerializedName("created_at")
    public String createdAt;
    
    @SerializedName("updated_at")
    public String updatedAt;
}
