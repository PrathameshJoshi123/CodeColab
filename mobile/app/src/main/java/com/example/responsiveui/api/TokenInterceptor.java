package com.example.responsiveui.api;

import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Interceptor to add Firebase JWT token to all API requests
 * This ensures authenticated requests are properly authorized
 */
public class TokenInterceptor implements Interceptor {
    
    private static final String TAG = "TokenInterceptor";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        
        // Get current Firebase user and their ID token
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser != null) {
            try {
                // Get Firebase ID token synchronously using Tasks.await
                String idToken = Tasks.await(currentUser.getIdToken(false)).getToken();
                
                if (idToken != null) {
                    // Add Bearer token to request
                    Request authenticatedRequest = originalRequest.newBuilder()
                            .header(AUTHORIZATION_HEADER, "Bearer " + idToken)
                            .build();
                    
                    Log.d(TAG, "Request sent with authentication token for user: " + currentUser.getUid());
                    return chain.proceed(authenticatedRequest);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error getting token", e);
            } catch (Exception e) {
                Log.e(TAG, "Error adding token to request", e);
            }
        } else {
            Log.d(TAG, "No authenticated user found, sending request without token");
        }
        
        // If no token, proceed with original request
        return chain.proceed(originalRequest);
    }
}
