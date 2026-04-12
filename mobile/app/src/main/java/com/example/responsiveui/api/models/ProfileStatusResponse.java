package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Profile completion status response
 * Indicates if profile is complete and which fields are missing
 */
public class ProfileStatusResponse {
    @SerializedName("is_complete")
    public boolean isComplete;
    
    @SerializedName("missing_fields")
    public List<String> missingFields;
    
    @SerializedName("message")
    public String message;
}
