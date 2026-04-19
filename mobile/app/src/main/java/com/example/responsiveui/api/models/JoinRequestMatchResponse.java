package com.example.responsiveui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Join requests grouped by a match created by current user.
 */
public class JoinRequestMatchResponse {
    @SerializedName("match_id")
    public String matchId;

    @SerializedName("session_type")
    public String sessionType;

    @SerializedName("message")
    public String message;

    @SerializedName("required_skills")
    public List<String> requiredSkills;

    @SerializedName("status")
    public String status;

    @SerializedName("scheduled_date_time")
    public String scheduledDateTime;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("interested_count")
    public int interestedCount;

    @SerializedName("interested_users")
    public List<InterestedUser> interestedUsers;

    public static class InterestedUser {
        @SerializedName("user_id")
        public String userId;

        @SerializedName("accepted_at")
        public String acceptedAt;

        @SerializedName("full_name")
        public String fullName;

        @SerializedName("email")
        public String email;

        @SerializedName("bio")
        public String bio;

        @SerializedName("college")
        public String college;

        @SerializedName("city")
        public String city;

        @SerializedName("github_username")
        public String githubUsername;

        @SerializedName("linkedin_url")
        public String linkedinUrl;

        @SerializedName("level")
        public int level;

        @SerializedName("streak_count")
        public int streakCount;

        @SerializedName("karma_score")
        public int karmaScore;

        @SerializedName("xp_points")
        public int xpPoints;

        @SerializedName("profile_image_url")
        public String profileImageUrl;

        @SerializedName("skills")
        public List<MatchRequestResponse.UserSkillInfo> skills;

        public String getDisplayName() {
            if (fullName != null && !fullName.trim().isEmpty()) {
                return fullName;
            }
            if (email != null && email.contains("@")) {
                return email.split("@")[0];
            }
            if (userId != null && !userId.trim().isEmpty()) {
                return userId;
            }
            return "Interested User";
        }
    }
}
