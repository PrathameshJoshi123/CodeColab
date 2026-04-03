package com.example.responsiveui.api;

import android.content.Context;
import android.util.Log;
import com.example.responsiveui.api.models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * API Helper - Provides easy-to-use methods for API calls
 * Simplifies API usage throughout the app
 */
public class ApiHelper {
    
    private static final String TAG = "ApiHelper";
    private final CodeCollabApiService apiService;
    
    public ApiHelper(Context context) {
        this.apiService = ApiConfig.getApiService(context);
    }
    
    /**
     * Generic callback handler
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }
    
    // ==================== Health & Status ====================
    
    public void checkHealth(ApiCallback<HealthResponse> callback) {
        apiService.getHealthStatus().enqueue(new Callback<HealthResponse>() {
            @Override
            public void onResponse(Call<HealthResponse> call, Response<HealthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Health check failed: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<HealthResponse> call, Throwable t) {
                Log.e(TAG, "Health check error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // ==================== Users ====================
    
    public void registerUser(String uid, String email, String provider, ApiCallback<UserResponse> callback) {
        UserRegisterRequest request = new UserRegisterRequest(uid, email, provider);
        apiService.registerUser(request).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Registration failed: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Log.e(TAG, "Registration error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    public void getCurrentUser(ApiCallback<UserResponse> callback) {
        apiService.getCurrentUser().enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to fetch user: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Log.e(TAG, "Get user error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // ==================== Skills ====================
    
    public void getAllSkills(ApiCallback<java.util.List<SkillResponse>> callback) {
        apiService.getAllSkills().enqueue(new Callback<java.util.List<SkillResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<SkillResponse>> call, Response<java.util.List<SkillResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to fetch skills: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<java.util.List<SkillResponse>> call, Throwable t) {
                Log.e(TAG, "Get skills error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // ==================== Sprints ====================
    
    public void createSprintSession(SprintSessionCreateRequest request, ApiCallback<SprintSessionResponse> callback) {
        apiService.createSprintSession(request).enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to create sprint: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<SprintSessionResponse> call, Throwable t) {
                Log.e(TAG, "Create sprint error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    public void getSprintSessions(ApiCallback<java.util.List<SprintSessionResponse>> callback) {
        apiService.getSprintSessions().enqueue(new Callback<java.util.List<SprintSessionResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<SprintSessionResponse>> call, Response<java.util.List<SprintSessionResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to fetch sprints: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<java.util.List<SprintSessionResponse>> call, Throwable t) {
                Log.e(TAG, "Get sprints error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    // ==================== Matches ====================
    
    public void createMatchRequest(MatchRequestCreateRequest request, ApiCallback<MatchRequestResponse> callback) {
        apiService.createMatchRequest(request).enqueue(new Callback<MatchRequestResponse>() {
            @Override
            public void onResponse(Call<MatchRequestResponse> call, Response<MatchRequestResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to create match: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<MatchRequestResponse> call, Throwable t) {
                Log.e(TAG, "Create match error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    public void browseMatchRequests(String sessionType, ApiCallback<java.util.List<MatchRequestResponse>> callback) {
        apiService.browseMatchRequests(sessionType).enqueue(new Callback<java.util.List<MatchRequestResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<MatchRequestResponse>> call, Response<java.util.List<MatchRequestResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to fetch matches: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<java.util.List<MatchRequestResponse>> call, Throwable t) {
                Log.e(TAG, "Browse matches error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
}
