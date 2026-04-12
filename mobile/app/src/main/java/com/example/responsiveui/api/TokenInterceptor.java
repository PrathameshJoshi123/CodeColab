package com.example.responsiveui.api;

import android.util.Log;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Interceptor to add JWT token to all API requests
 * Uses tokens stored locally via TokenManager
 * This ensures authenticated requests are properly authorized
 */
public class TokenInterceptor implements Interceptor {
    
    private static final String TAG = "TokenInterceptor";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        
        // Get stored JWT token
        String token = TokenManager.getAccessToken();
        
        if (token != null && !token.isEmpty()) {
            // Add Bearer token to request
            Request authenticatedRequest = originalRequest.newBuilder()
                    .header(AUTHORIZATION_HEADER, "Bearer " + token)
                    .build();
            
            Log.d(TAG, "Request sent with JWT token for user: " + TokenManager.getUserId());
            return chain.proceed(authenticatedRequest);
        } else {
            Log.d(TAG, "No JWT token found, sending request without authentication");
        }
        
        // If no token, proceed with original request
        return chain.proceed(originalRequest);
    }
}
