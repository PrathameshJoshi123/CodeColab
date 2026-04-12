package com.example.responsiveui.api;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Token Manager utility for storing and retrieving JWT tokens
 * Uses SharedPreferences for persistent local storage
 */
public class TokenManager {
    
    private static final String PREFS_NAME = "CodeCollabAuth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_TOKEN_EXPIRY = "token_expiry";
    
    private static SharedPreferences sharedPreferences;
    
    /**
     * Initialize TokenManager with context
     */
    public static void init(Context context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
    
    /**
     * Save authentication token and user info
     */
    public static void saveToken(String accessToken, String userId, String email, int expiresIn) {
        if (sharedPreferences == null) {
            return;
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_ACCESS_TOKEN, accessToken);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_EMAIL, email);
        
        // Calculate expiry time (current time + expiresIn seconds)
        long expiryTime = System.currentTimeMillis() + (expiresIn * 1000L);
        editor.putLong(KEY_TOKEN_EXPIRY, expiryTime);
        
        editor.apply();
    }
    
    /**
     * Get stored access token
     */
    public static String getAccessToken() {
        if (sharedPreferences == null) {
            return null;
        }
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
    }
    
    /**
     * Get stored user ID
     */
    public static String getUserId() {
        if (sharedPreferences == null) {
            return null;
        }
        return sharedPreferences.getString(KEY_USER_ID, null);
    }
    
    /**
     * Get stored user email
     */
    public static String getUserEmail() {
        if (sharedPreferences == null) {
            return null;
        }
        return sharedPreferences.getString(KEY_USER_EMAIL, null);
    }
    
    /**
     * Check if token is valid (not expired)
     */
    public static boolean isTokenValid() {
        if (sharedPreferences == null) {
            return false;
        }
        
        long expiryTime = sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0);
        return System.currentTimeMillis() < expiryTime;
    }
    
    /**
     * Check if user is authenticated
     */
    public static boolean isUserAuthenticated() {
        String token = getAccessToken();
        return token != null && !token.isEmpty() && isTokenValid();
    }
    
    /**
     * Clear all stored tokens and user data
     */
    public static void clearToken() {
        if (sharedPreferences == null) {
            return;
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_ACCESS_TOKEN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_EMAIL);
        editor.remove(KEY_TOKEN_EXPIRY);
        editor.apply();
    }
}
