package com.example.responsiveui.api;

import com.example.responsiveui.api.models.*;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/**
 * CodeCollab API Service Interface
 * Defines all endpoints for the mobile app to communicate with the backend
 */
public interface CodeCollabApiService {
    
    // ==================== Health & Status ====================
    
    @GET("/")
    Call<HealthResponse> getHealthStatus();
    
    @GET("/health")
    Call<HealthResponse> getDetailedHealthStatus();
    
    // ==================== Users ====================
    
    @POST("/users/register")
    Call<UserResponse> registerUser(@Body UserRegisterRequest request);
    
    @GET("/users/me")
    Call<UserResponse> getCurrentUser();
    
    @PUT("/users/{userId}/profile")
    Call<UserResponse> updateUserProfile(
            @Path("userId") String userId,
            @Body ProfileUpdateRequest request
    );
    
    @GET("/users/{userId}")
    Call<UserResponse> getUserById(@Path("userId") String userId);
    
    @GET("/users")
    Call<List<UserResponse>> getAllUsers();
    
    // ==================== Skills ====================
    
    @GET("/skills")
    Call<List<SkillResponse>> getAllSkills();
    
    @POST("/skills")
    Call<SkillResponse> createSkill(@Body SkillCreateRequest request);
    
    @GET("/skills/{skillId}")
    Call<SkillResponse> getSkillById(@Path("skillId") String skillId);
    
    @PUT("/users/{userId}/skills")
    Call<List<UserSkillResponse>> updateUserSkills(
            @Path("userId") String userId,
            @Body UserSkillUpdateRequest request
    );
    
    @GET("/users/{userId}/skills")
    Call<List<UserSkillResponse>> getUserSkills(@Path("userId") String userId);
    
    // ==================== Sprints ====================
    
    @POST("/sprints")
    Call<SprintSessionResponse> createSprintSession(@Body SprintSessionCreateRequest request);
    
    @GET("/sprints")
    Call<List<SprintSessionResponse>> getSprintSessions();
    
    @GET("/sprints/{sprintId}")
    Call<SprintSessionResponse> getSprintSessionById(@Path("sprintId") String sprintId);
    
    @PUT("/sprints/{sprintId}")
    Call<SprintSessionResponse> updateSprintSession(
            @Path("sprintId") String sprintId,
            @Body SprintSessionUpdateRequest request
    );
    
    @POST("/sprints/{sprintId}/join")
    Call<SprintSessionResponse> joinSprintSession(@Path("sprintId") String sprintId);
    
    @POST("/sprints/{sprintId}/leave")
    Call<SprintSessionResponse> leaveSprintSession(@Path("sprintId") String sprintId);
    
    @POST("/sprints/{sprintId}/complete")
    Call<SprintSessionResponse> completeSprintSession(@Path("sprintId") String sprintId);
    
    // ==================== Matches ====================
    
    @POST("/matches")
    Call<MatchRequestResponse> createMatchRequest(@Body MatchRequestCreateRequest request);
    
    @GET("/matches/browse")
    Call<List<MatchRequestResponse>> browseMatchRequests(
            @Query("session_type") String sessionType
    );
    
    @GET("/matches/{matchId}")
    Call<MatchRequestResponse> getMatchRequest(@Path("matchId") String matchId);
    
    @PUT("/matches/{matchId}/accept")
    Call<MatchRequestResponse> acceptMatchRequest(@Path("matchId") String matchId);
    
    @PUT("/matches/{matchId}/reject")
    Call<MatchRequestResponse> rejectMatchRequest(@Path("matchId") String matchId);
    
    @GET("/users/{userId}/matches")
    Call<List<MatchRequestResponse>> getUserMatches(@Path("userId") String userId);
    
}
