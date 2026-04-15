package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Sprint Todo create request for API
 */
public class SprintTodoCreateRequest {
    @SerializedName("sprint_id")
    public String sprintId;
    
    @SerializedName("title")
    public String title;
    
    @SerializedName("description")
    public String description;
    
    public SprintTodoCreateRequest(String sprintId, String title, String description) {
        this.sprintId = sprintId;
        this.title = title;
        this.description = description;
    }
    
    public SprintTodoCreateRequest(String sprintId, String title) {
        this(sprintId, title, "");
    }
}
