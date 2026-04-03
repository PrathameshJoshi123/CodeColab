package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * User skill update request
 */
public class UserSkillUpdateRequest {
    @SerializedName("skills")
    public List<SkillUpdate> skills;
    
    public static class SkillUpdate {
        @SerializedName("skillId")
        public String skillId;
        
        @SerializedName("proficiency_level")
        public String proficiencyLevel;
        
        public SkillUpdate(String skillId, String proficiencyLevel) {
            this.skillId = skillId;
            this.proficiencyLevel = proficiencyLevel;
        }
    }
}
