package com.example.responsiveui.api;

import com.example.responsiveui.api.models.*;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

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
    
    // ==================== Authentication ====================
    
    @POST("/auth/signup")
    Call<AuthResponseModel> signUp(@Body SignUpRequestModel request);
    
    @POST("/auth/login")
    Call<AuthResponseModel> login(@Body LoginRequestModel request);
    
    @POST("/auth/google")
    Call<AuthResponseModel> googleOAuth(@Body GoogleOAuthRequestModel request);
    
    @POST("/auth/logout")
    Call<java.util.Map<String, Object>> logout();
    
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
    
    // ==================== User Profile ====================
    
    @GET("/users/me/profile")
    Call<UserProfileResponse> getCurrentUserProfile();
    
    @GET("/users/{userId}/profile")
    Call<UserProfileResponse> getUserProfile(@Path("userId") String userId);
    
    @PUT("/users/me/profile")
    Call<UserProfileResponse> updateCurrentUserProfile(@Body ProfileUpdateRequest request);
    
    @GET("/users/me/profile/status")
    Call<ProfileStatusResponse> getProfileCompletionStatus();
    
    // ==================== FCM Notifications ====================
    
    @POST("/users/me/fcm-token")
    Call<Void> registerFcmToken(@Body java.util.Map<String, String> tokenData);
    
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
    
    @GET("/sprints/user/my-sessions")
    Call<List<SprintSessionResponse>> getUserSprints();
    
    @GET("/sprints/user/created")
    Call<List<SprintSessionResponse>> getCreatedSprints();
    
    @GET("/sprints/user/invited")
    Call<List<SprintSessionResponse>> getInvitedSprints();
    
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
    
    // ==================== Sprint Details & Todos ====================
    
    @GET("/sprints/{sprintId}/details")
    Call<SprintSessionResponse> getSprintDetails(@Path("sprintId") String sprintId);
    
    @POST("/sprints/{sprintId}/confirm")
    Call<java.util.Map<String, Object>> confirmSprintSession(@Path("sprintId") String sprintId);
    
    @GET("/sprints/{sprintId}/participants-status")
    Call<Map<String, Object>> getParticipantsStatus(@Path("sprintId") String sprintId);
    
    @POST("/sprints/{sprintId}/todos")
    Call<SprintTodoResponse> createSprintTodo(
            @Path("sprintId") String sprintId,
            @Body SprintTodoCreateRequest request
    );
    
    @GET("/sprints/{sprintId}/todos")
    Call<List<SprintTodoResponse>> getSprintTodos(@Path("sprintId") String sprintId);
    
    @PUT("/sprints/{sprintId}/todos/{todoId}")
    Call<SprintTodoResponse> updateSprintTodo(
            @Path("sprintId") String sprintId,
            @Path("todoId") String todoId,
            @Body SprintTodoUpdateRequest request
    );
    
    @DELETE("/sprints/{sprintId}/todos/{todoId}")
    Call<Void> deleteSprintTodo(
            @Path("sprintId") String sprintId,
            @Path("todoId") String todoId
    );
    
    // ==================== Matches ====================
    
    @POST("/matches")
    Call<MatchRequestResponse> createMatchRequest(@Body MatchRequestCreateRequest request);
    
    @GET("/matches/browse")
    Call<List<MatchRequestResponse>> browseMatchRequests(
            @Query("session_type") String sessionType
    );
    
    @GET("/matches/{matchId}")
    Call<MatchRequestResponse> getMatchRequest(@Path("matchId") String matchId);
    
    @GET("/matches/{matchId}/details")
    Call<Map<String, Object>> getMatchDetails(@Path("matchId") String matchId);
    
    @PUT("/matches/{matchId}/accept")
    Call<MatchRequestResponse> acceptMatchRequest(@Path("matchId") String matchId);
    
    @PUT("/matches/{matchId}/reject")
    Call<MatchRequestResponse> rejectMatchRequest(@Path("matchId") String matchId);
    
    @GET("/users/{userId}/matches")
    Call<List<MatchRequestResponse>> getUserMatches(@Path("userId") String userId);
    
    @GET("/matches/user/my-requests")
    Call<List<MatchRequestResponse>> getMyMatchRequests();
    
    @GET("/matches/user/received")
    Call<List<MatchRequestResponse>> getReceivedMatchRequests();
    
    @DELETE("/matches/{matchId}")
    Call<Void> cancelMatchRequest(@Path("matchId") String matchId);
    
    // ==================== Chat ====================
    
    @POST("/chat/conversations/{sprintId}/messages")
    Call<Map<String, Object>> sendMessage(
            @Path("sprintId") String sprintId,
            @Body Map<String, String> messageData
    );
    
    @GET("/chat/conversations/{sprintId}/messages")
    Call<List<Map<String, Object>>> getMessages(
            @Path("sprintId") String sprintId,
            @Query("limit") int limit
    );
    
    @DELETE("/chat/messages/{messageId}")
    Call<Map<String, Object>> deleteMessage(@Path("messageId") String messageId);
    
    // ==================== Scratchpad ====================
    
    @POST("/sprints/{sprintId}/scratchpad")
    Call<Map<String, Object>> updateScratchpad(
            @Path("sprintId") String sprintId,
            @Body Map<String, String> scratchpadData
    );
    
    @GET("/sprints/{sprintId}/scratchpad")
    Call<Map<String, Object>> getScratchpad(@Path("sprintId") String sprintId);
}

