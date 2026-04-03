package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Skill creation request
 */
public class SkillCreateRequest {
    @SerializedName("name")
    public String name;
    
    @SerializedName("category")
    public String category;
    
    public SkillCreateRequest(String name, String category) {
        this.name = name;
        this.category = category;
    }
}
