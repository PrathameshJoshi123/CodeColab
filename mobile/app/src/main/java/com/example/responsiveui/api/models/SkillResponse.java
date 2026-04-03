package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Skill response from API
 */
public class SkillResponse {
    @SerializedName("id")
    public String id;
    
    @SerializedName("name")
    public String name;
    
    @SerializedName("category")
    public String category;
}
