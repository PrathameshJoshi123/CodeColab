package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Participant detail with user information
 */
public class ParticipantDetail {
    @SerializedName("userId")
    public String userId;
    
    @SerializedName("full_name")
    public String fullName;
    
    @SerializedName("email")
    public String email;
}
