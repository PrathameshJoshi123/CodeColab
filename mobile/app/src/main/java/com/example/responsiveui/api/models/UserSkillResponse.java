package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * User skill response
 */
public class UserSkillResponse {
    @SerializedName("name")
    public String name;

    @SerializedName("category")
    public String category;

    @SerializedName("id")
    public String id;
    
    @SerializedName("userId")
    public String userId;
    
    @SerializedName("skillId")
    public String skillId;
    
    @SerializedName("proficiency_level")
    public String proficiencyLevel;
    
    @SerializedName("years_of_experience")
    public double yearsOfExperience;
    
    @SerializedName("endorsements")
    public int endorsements;
}
