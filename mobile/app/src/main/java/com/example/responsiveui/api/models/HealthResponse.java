package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;

/**
 * Health check response from API
 */
public class HealthResponse {
    @SerializedName("status")
    public String status;
    
    @SerializedName("database")
    public String database;
    
    @SerializedName("firebase")
    public String firebase;
    
    @SerializedName("service")
    public String service;
    
    @SerializedName("version")
    public String version;
}
