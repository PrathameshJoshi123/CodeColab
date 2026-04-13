package com.example.responsiveui.api;

import android.content.Context;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessaging;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * ==================== FCM Token Manager ====================
 * Manages Firebase Cloud Messaging token registration and refresh
 * Handles automatic token refresh and backend synchronization
 */
public class FCMTokenManager {
    private static final String TAG = "FCMTokenManager";
    private final Context context;
    private final CodeCollabApiService apiService;
    private static final String PREF_NAME = "fcm_prefs";
    private static final String TOKEN_KEY = "fcm_token";

    public FCMTokenManager(Context context) {
        this.context = context;
        this.apiService = ApiConfig.getApiService(context);
    }

    /**
     * Initialize and register FCM token
     * Should be called once during app startup
     */
    public void initializeToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "getInstanceId failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);
                    
                    // Save token locally
                    saveTokenLocally(token);
                    
                    // Register token with backend
                    registerTokenWithBackend(token);
                });
    }

    /**
     * Get current FCM token
     */
    public String getToken() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(TOKEN_KEY, null);
    }

    /**
     * Save token locally (SharedPreferences)
     */
    private void saveTokenLocally(String token) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(TOKEN_KEY, token)
                .apply();
        Log.d(TAG, "Token saved locally");
    }

    /**
     * Register token with backend API
     */
    public void registerTokenWithBackend(String token) {
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put("fcm_token", token);
        
        Call<Void> call = apiService.registerFcmToken(tokenData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "FCM token registered with backend successfully");
                } else {
                    Log.w(TAG, "Failed to register FCM token with backend: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Error registering FCM token: " + t.getMessage());
            }
        });
    }

    /**
     * Refresh token
     * Called when token might have expired or changed
     */
    public void refreshToken() {
        FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener(task -> {
                    Log.d(TAG, "Old token deleted");
                    initializeToken();
                });
    }
}
