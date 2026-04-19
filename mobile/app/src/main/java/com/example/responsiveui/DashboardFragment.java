package com.example.responsiveui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.MatchRequestResponse;
import com.example.responsiveui.api.models.UserProfileResponse;
import com.example.responsiveui.api.models.UserSkillResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ==================== Dashboard Fragment ====================
 * Displays the Growth Dashboard with stats and metrics
 */
public class DashboardFragment extends Fragment {

    private static final int XP_PER_LEVEL = 300;
    private static final long DASHBOARD_REFRESH_INTERVAL_MS = 20_000L;
    private static final long DASHBOARD_REQUEST_DEBOUNCE_MS = 800L;

    private CodeCollabApiService apiService;
    private String currentUserId;
    private boolean forceRefreshOnResume = true;
    private boolean dashboardLoadInProgress = false;
    private int activeDashboardRequests = 0;
    private long lastDashboardLoadStartedAtMs = 0L;
    private long lastDashboardLoadCompletedAtMs = 0L;

    private int myAcceptedCollaborations = 0;
    private int receivedAcceptedCollaborations = 0;
    private Map<String, Integer> topCollaboratorCounts = new HashMap<>();

    // Header cards
    private TextView tvCollaborationsCount;
    private TextView tvSkillsCount;

    // Rank and streak
    private TextView tvCurrentLevel;
    private TextView tvCurrentXp;
    private ProgressBar progressLevel;
    private TextView tvStreakSummary;
    private TextView[] streakDayViews;

    // Skills
    private TextView tvSkillsEmpty;
    private LinearLayout skillsProgressContainer;
    private Button btnEditSkills;

    // Collaborators
    private TextView tvCollaboratorPrimary;
    private TextView tvCollaboratorSecondary;
    private View rowCollaboratorSecondary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_growth_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() == null) {
            return;
        }

        TokenManager.init(getContext());
        apiService = ApiConfig.getApiService(getContext());
        currentUserId = TokenManager.getUserId();

        bindViews(view);
        setupActions();
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean shouldForceRefresh = forceRefreshOnResume;
        forceRefreshOnResume = false;
        loadDashboardData(shouldForceRefresh);
    }

    private void bindViews(@NonNull View view) {
        tvCollaborationsCount = view.findViewById(R.id.tvCollaborationsCount);
        tvSkillsCount = view.findViewById(R.id.tvSkillsCount);

        tvCurrentLevel = view.findViewById(R.id.tvCurrentLevel);
        tvCurrentXp = view.findViewById(R.id.tvCurrentXp);
        progressLevel = view.findViewById(R.id.progressLevel);
        tvStreakSummary = view.findViewById(R.id.tvStreakSummary);

        streakDayViews = new TextView[] {
                view.findViewById(R.id.tvDayMon),
                view.findViewById(R.id.tvDayTue),
                view.findViewById(R.id.tvDayWed),
                view.findViewById(R.id.tvDayThu),
                view.findViewById(R.id.tvDayFri),
                view.findViewById(R.id.tvDaySat),
                view.findViewById(R.id.tvDaySun)
        };

        tvSkillsEmpty = view.findViewById(R.id.tvSkillsEmpty);
        skillsProgressContainer = view.findViewById(R.id.skillsProgressContainer);
        btnEditSkills = view.findViewById(R.id.btnEditSkills);

        tvCollaboratorPrimary = view.findViewById(R.id.tvCollaboratorPrimary);
        tvCollaboratorSecondary = view.findViewById(R.id.tvCollaboratorSecondary);
        rowCollaboratorSecondary = view.findViewById(R.id.rowCollaboratorSecondary);

        renderProfileFallback();
        renderTopCollaborators(new HashMap<>());
    }

    private void setupActions() {
        if (btnEditSkills != null) {
            btnEditSkills.setOnClickListener(v -> {
                if (getActivity() == null) {
                    return;
                }

                forceRefreshOnResume = true;
                Intent intent = new Intent(getActivity(), SkillSelectionActivity.class);
                intent.putExtra("USER_EMAIL", TokenManager.getUserEmail());
                startActivity(intent);
            });
        }
    }

    private void loadDashboardData(boolean forceRefresh) {
        if (apiService == null || !isAdded()) {
            return;
        }

        long nowMs = SystemClock.elapsedRealtime();
        if (dashboardLoadInProgress) {
            if (forceRefresh) {
                forceRefreshOnResume = true;
            }
            return;
        }

        if (!forceRefresh && nowMs - lastDashboardLoadStartedAtMs < DASHBOARD_REQUEST_DEBOUNCE_MS) {
            return;
        }

        if (!forceRefresh
                && lastDashboardLoadCompletedAtMs > 0
                && nowMs - lastDashboardLoadCompletedAtMs < DASHBOARD_REFRESH_INTERVAL_MS) {
            return;
        }

        dashboardLoadInProgress = true;
        lastDashboardLoadStartedAtMs = nowMs;

        myAcceptedCollaborations = 0;
        receivedAcceptedCollaborations = 0;
        topCollaboratorCounts = new HashMap<>();

        loadProfileAndSkills();
        loadCollaborationStats();
    }

    private void beginDashboardRequest() {
        activeDashboardRequests++;
    }

    private void finishDashboardRequest() {
        if (activeDashboardRequests > 0) {
            activeDashboardRequests--;
        }

        if (activeDashboardRequests == 0) {
            dashboardLoadInProgress = false;
            lastDashboardLoadCompletedAtMs = SystemClock.elapsedRealtime();
        }
    }

    // ==================== Profile & Streak ====================

    private void loadProfileAndSkills() {
        String normalizedCurrentUserId = normalizeUserId(currentUserId);
        final String fallbackSkillsUserId = normalizedCurrentUserId != null
            ? normalizedCurrentUserId
                : normalizeUserId(TokenManager.getUserId());

        if (fallbackSkillsUserId != null) {
            loadSkills(fallbackSkillsUserId);
        } else {
            renderSkills(new ArrayList<>());
        }

        beginDashboardRequest();
        apiService.getCurrentUserProfile().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call, @NonNull Response<UserProfileResponse> response) {
                try {
                    if (!isAdded()) {
                        return;
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        UserProfileResponse profile = response.body();
                        renderProfile(profile);

                        currentUserId = normalizeUserId(profile.userId);
                        if (currentUserId != null && !currentUserId.equals(fallbackSkillsUserId)) {
                            loadSkills(currentUserId);
                        }
                    } else {
                        renderProfileFallback();
                    }
                } finally {
                    finishDashboardRequest();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                try {
                    if (!isAdded()) {
                        return;
                    }
                    renderProfileFallback();
                } finally {
                    finishDashboardRequest();
                }
            }
        });
    }

    private void renderProfileFallback() {
        if (tvCurrentLevel != null) {
            tvCurrentLevel.setText("Level 1");
        }

        if (tvCurrentXp != null) {
            tvCurrentXp.setText("0 / " + XP_PER_LEVEL + " XP");
        }

        if (progressLevel != null) {
            progressLevel.setMax(XP_PER_LEVEL);
            progressLevel.setProgress(0);
        }

        if (tvStreakSummary != null) {
            tvStreakSummary.setText("0 Day Streak!");
        }

        updateStreakDayViews(0);
    }

    @Nullable
    private String normalizeUserId(String userId) {
        if (userId == null) {
            return null;
        }

        String trimmedUserId = userId.trim();
        return trimmedUserId.isEmpty() ? null : trimmedUserId;
    }

    private void renderProfile(@NonNull UserProfileResponse profile) {
        if (tvCurrentLevel != null) {
            tvCurrentLevel.setText("Level " + profile.level);
        }

        int levelStartXp = Math.max((profile.level - 1) * XP_PER_LEVEL, 0);
        int nextLevelXp = Math.max(profile.level * XP_PER_LEVEL, XP_PER_LEVEL);
        int progressInLevel = Math.max(profile.xpPoints - levelStartXp, 0);
        int boundedProgress = Math.min(progressInLevel, XP_PER_LEVEL);

        if (tvCurrentXp != null) {
            tvCurrentXp.setText(profile.xpPoints + " / " + nextLevelXp + " XP");
        }

        if (progressLevel != null) {
            progressLevel.setMax(XP_PER_LEVEL);
            progressLevel.setProgress(boundedProgress);
        }

        int streakCount = Math.max(profile.streakCount, 0);
        if (tvStreakSummary != null) {
            tvStreakSummary.setText(streakCount + " Day Streak!");
        }

        updateStreakDayViews(streakCount);
    }

    private void updateStreakDayViews(int streakCount) {
        if (streakDayViews == null) {
            return;
        }

        int activeDays = Math.min(Math.max(streakCount, 0), streakDayViews.length);
        for (int i = 0; i < streakDayViews.length; i++) {
            TextView dayView = streakDayViews[i];
            if (dayView == null) {
                continue;
            }

            boolean isActive = i < activeDays;
            dayView.setBackgroundResource(isActive ? R.drawable.circle_day_active : R.drawable.input_background);
            dayView.setTextColor(ContextCompat.getColor(requireContext(), isActive ? R.color.text_white : R.color.text_muted));
            dayView.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    // ==================== Skills ====================

    private void loadSkills(String userId) {
        beginDashboardRequest();
        apiService.getUserSkills(userId).enqueue(new Callback<List<UserSkillResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<UserSkillResponse>> call, @NonNull Response<List<UserSkillResponse>> response) {
                try {
                    if (!isAdded()) {
                        return;
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        renderSkills(response.body());
                    } else {
                        renderSkills(new ArrayList<>());
                    }
                } finally {
                    finishDashboardRequest();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<UserSkillResponse>> call, @NonNull Throwable t) {
                try {
                    if (!isAdded()) {
                        return;
                    }
                    renderSkills(new ArrayList<>());
                } finally {
                    finishDashboardRequest();
                }
            }
        });
    }

    private void renderSkills(@NonNull List<UserSkillResponse> skills) {
        if (tvSkillsCount != null) {
            tvSkillsCount.setText(String.valueOf(skills.size()));
        }

        if (skillsProgressContainer == null || tvSkillsEmpty == null) {
            return;
        }

        skillsProgressContainer.removeAllViews();

        if (skills.isEmpty()) {
            tvSkillsEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvSkillsEmpty.setVisibility(View.GONE);

        for (int index = 0; index < skills.size(); index++) {
            UserSkillResponse skill = skills.get(index);

            TextView skillLabel = new TextView(requireContext());
            skillLabel.setText(resolveSkillName(skill));
            skillLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_white));

            LinearLayout.LayoutParams skillLabelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            skillLabelParams.topMargin = index == 0 ? 0 : 16;
            skillLabel.setLayoutParams(skillLabelParams);
            skillsProgressContainer.addView(skillLabel);

            ProgressBar skillProgress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            skillProgress.setMax(100);
            skillProgress.setProgress(proficiencyToProgress(skill.proficiencyLevel));
            skillProgress.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brand_blue)));

            LinearLayout.LayoutParams skillProgressParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            skillProgressParams.topMargin = 8;
            skillProgress.setLayoutParams(skillProgressParams);
            skillsProgressContainer.addView(skillProgress);
        }
    }

    private int proficiencyToProgress(String proficiencyLevel) {
        if (proficiencyLevel == null) {
            return 25;
        }

        switch (proficiencyLevel.toLowerCase(Locale.ROOT)) {
            case "intermediate":
                return 50;
            case "advanced":
                return 75;
            case "expert":
                return 100;
            default:
                return 25;
        }
    }

    private String resolveSkillName(UserSkillResponse skill) {
        if (skill.name != null && !skill.name.trim().isEmpty()) {
            return skill.name;
        }
        if (skill.skillId != null && !skill.skillId.trim().isEmpty()) {
            return skill.skillId;
        }
        return "Skill";
    }

    // ==================== Collaborations ====================

    private void loadCollaborationStats() {
        beginDashboardRequest();
        apiService.getMyMatchRequests().enqueue(new Callback<List<MatchRequestResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MatchRequestResponse>> call, @NonNull Response<List<MatchRequestResponse>> myRequestsResponse) {
                try {
                    if (!isAdded()) {
                        return;
                    }

                    myAcceptedCollaborations = countAcceptedMatches(myRequestsResponse.body());
                    renderCollaborationStats();
                } finally {
                    finishDashboardRequest();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MatchRequestResponse>> call, @NonNull Throwable t) {
                try {
                    if (!isAdded()) {
                        return;
                    }

                    myAcceptedCollaborations = 0;
                    renderCollaborationStats();
                } finally {
                    finishDashboardRequest();
                }
            }
        });

        beginDashboardRequest();
        apiService.getReceivedMatchRequests().enqueue(new Callback<List<MatchRequestResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MatchRequestResponse>> call, @NonNull Response<List<MatchRequestResponse>> receivedResponse) {
                try {
                    if (!isAdded()) {
                        return;
                    }

                    List<MatchRequestResponse> receivedMatches = receivedResponse.body() != null
                            ? receivedResponse.body()
                            : new ArrayList<>();

                    receivedAcceptedCollaborations = countAcceptedMatches(receivedMatches);
                    topCollaboratorCounts = buildCollaboratorCountMap(receivedMatches);
                    renderCollaborationStats();
                } finally {
                    finishDashboardRequest();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MatchRequestResponse>> call, @NonNull Throwable t) {
                try {
                    if (!isAdded()) {
                        return;
                    }

                    receivedAcceptedCollaborations = 0;
                    topCollaboratorCounts = new HashMap<>();
                    renderCollaborationStats();
                } finally {
                    finishDashboardRequest();
                }
            }
        });
    }

    private void renderCollaborationStats() {
        int totalCollaborations = myAcceptedCollaborations + receivedAcceptedCollaborations;

        if (tvCollaborationsCount != null) {
            tvCollaborationsCount.setText(String.valueOf(totalCollaborations));
        }

        renderTopCollaborators(topCollaboratorCounts);
    }

    private int countAcceptedMatches(List<MatchRequestResponse> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (MatchRequestResponse match : matches) {
            if (match == null || match.status == null) {
                continue;
            }

            String normalizedStatus = match.status.trim().toLowerCase(Locale.ROOT);
            if ("accepted".equals(normalizedStatus) || "exhausted".equals(normalizedStatus)) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Integer> buildCollaboratorCountMap(List<MatchRequestResponse> acceptedMatches) {
        Map<String, Integer> collaboratorCounts = new HashMap<>();

        if (acceptedMatches == null) {
            return collaboratorCounts;
        }

        for (MatchRequestResponse match : acceptedMatches) {
            if (match == null || match.status == null) {
                continue;
            }

            String normalizedStatus = match.status.trim().toLowerCase(Locale.ROOT);
            if (!"accepted".equals(normalizedStatus) && !"exhausted".equals(normalizedStatus)) {
                continue;
            }

            String collaboratorName = resolveCollaboratorName(match);

            collaboratorCounts.put(collaboratorName, collaboratorCounts.getOrDefault(collaboratorName, 0) + 1);
        }

        return collaboratorCounts;
    }

    private String resolveCollaboratorName(@NonNull MatchRequestResponse match) {
        if (match.user != null) {
            if (match.user.fullName != null && !match.user.fullName.trim().isEmpty()) {
                return match.user.fullName.trim();
            }

            if (match.user.email != null && !match.user.email.trim().isEmpty()) {
                return nameFromEmail(match.user.email.trim());
            }

            if (match.user.uid != null && !match.user.uid.trim().isEmpty()) {
                return match.user.uid.trim();
            }
        }

        if (match.userId != null && !match.userId.trim().isEmpty()) {
            return match.userId.trim();
        }

        return "Partner";
    }

    private void renderTopCollaborators(Map<String, Integer> collaboratorCounts) {
        if (tvCollaboratorPrimary == null || tvCollaboratorSecondary == null || rowCollaboratorSecondary == null) {
            return;
        }

        if (collaboratorCounts == null || collaboratorCounts.isEmpty()) {
            tvCollaboratorPrimary.setText("No collaborations yet");
            tvCollaboratorSecondary.setText("");
            rowCollaboratorSecondary.setVisibility(View.GONE);
            return;
        }

        List<Map.Entry<String, Integer>> rankedCollaborators = new ArrayList<>(collaboratorCounts.entrySet());
        Collections.sort(rankedCollaborators, (left, right) -> Integer.compare(right.getValue(), left.getValue()));

        Map.Entry<String, Integer> first = rankedCollaborators.get(0);
        tvCollaboratorPrimary.setText(first.getKey() + "\n" + first.getValue() + " sessions together");

        if (rankedCollaborators.size() > 1) {
            Map.Entry<String, Integer> second = rankedCollaborators.get(1);
            tvCollaboratorSecondary.setText(second.getKey() + "\n" + second.getValue() + " sessions together");
            rowCollaboratorSecondary.setVisibility(View.VISIBLE);
        } else {
            tvCollaboratorSecondary.setText("");
            rowCollaboratorSecondary.setVisibility(View.GONE);
        }
    }

    private String nameFromEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }
}