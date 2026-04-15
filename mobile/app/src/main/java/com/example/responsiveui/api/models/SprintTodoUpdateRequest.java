package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Sprint Todo update request for API
 */
public class SprintTodoUpdateRequest {
    @SerializedName("title")
    public String title;
    
    @SerializedName("description")
    public String description;
    
    @SerializedName("is_completed")
    public Boolean isCompleted;
    
    public SprintTodoUpdateRequest(String title, String description, Boolean isCompleted) {
        this.title = title;
        this.description = description;
        this.isCompleted = isCompleted;
    }
    
    public SprintTodoUpdateRequest(Boolean isCompleted) {
        this(null, null, isCompleted);
    }
}
