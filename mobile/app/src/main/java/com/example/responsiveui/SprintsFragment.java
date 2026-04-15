package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.models.SprintSessionResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ==================== Sprints Fragment ====================
 * Displays all sprint sessions with filtering by status:
 * - All: All sprints (created and invited)
 * - Live: Currently active sprints
 * - Upcoming: Scheduled for future
 * - Attended: Completed sprints
 * 
 * Features:
 * - Shows sprints created by user
 * - Shows sprints where user is invited (not yet joined)
 * - Invited sprints display "Join" indicator
 */
public class SprintsFragment extends Fragment {
    
    private CodeCollabApiService apiService;
    private ProgressBar loadingProgress;
    private LinearLayout sprintsContainer;
    private LinearLayout emptyState;
    private View sprintsScrollView;
    private Button btnAllSprints;
    private Button btnLiveSprints;
    private Button btnUpcomingSprints;
    private Button btnAttendedSprints;
    
    private List<SprintSessionResponse> allSprints = new ArrayList<>();
    private String currentFilter = "all";
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sprints, container, false);
        
        // ==================== View Initialization ====================
        loadingProgress = view.findViewById(R.id.loadingProgress);
        sprintsContainer = view.findViewById(R.id.sprintsContainer);
        emptyState = view.findViewById(R.id.emptyState);
        sprintsScrollView = view.findViewById(R.id.sprintsScrollView);
        btnAllSprints = view.findViewById(R.id.btnAllSprints);
        btnLiveSprints = view.findViewById(R.id.btnLiveSprints);
        btnUpcomingSprints = view.findViewById(R.id.btnUpcomingSprints);
        btnAttendedSprints = view.findViewById(R.id.btnAttendedSprints);
        
        // Initialize API service
        apiService = ApiConfig.getApiService(requireContext());
        
        // ==================== Filter Button Listeners ====================
        btnAllSprints.setOnClickListener(v -> filterSprints("all"));
        btnLiveSprints.setOnClickListener(v -> filterSprints("live"));
        btnUpcomingSprints.setOnClickListener(v -> filterSprints("upcoming"));
        btnAttendedSprints.setOnClickListener(v -> filterSprints("attended"));
        
        // Load sprints
        loadSprints();
        
        return view;
    }
    
    // ==================== Data Loading ====================
    
    private void loadSprints() {
        loadingProgress.setVisibility(View.VISIBLE);
        sprintsScrollView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        
        // Load created sprints
        apiService.getCreatedSprints().enqueue(new Callback<List<SprintSessionResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Response<List<SprintSessionResponse>> response) {
                if (getActivity() == null || !isAdded()) {
                    return;
                }
                
                if (response.isSuccessful() && response.body() != null) {
                    allSprints.addAll(response.body());
                }
                
                // Load invited sprints
                loadInvitedSprints();
            }
            
            @Override
            public void onFailure(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Throwable t) {
                if (getActivity() == null || !isAdded()) {
                    return;
                }
                // Continue loading invited sprints even if created sprints fail
                loadInvitedSprints();
            }
        });
    }
    
    private void loadInvitedSprints() {
        apiService.getInvitedSprints().enqueue(new Callback<List<SprintSessionResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Response<List<SprintSessionResponse>> response) {
                if (getActivity() == null || !isAdded()) {
                    return;
                }
                
                loadingProgress.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null) {
                    allSprints.addAll(response.body());
                }
                
                displaySprints();
            }
            
            @Override
            public void onFailure(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Throwable t) {
                if (getActivity() == null || !isAdded()) {
                    return;
                }
                
                loadingProgress.setVisibility(View.GONE);
                
                if (allSprints.isEmpty()) {
                    showEmptyState();
                } else {
                    displaySprints();
                }
            }
        });
    }
    
    // ==================== Filtering ====================
    
    private void filterSprints(String filter) {
        currentFilter = filter;
        updateFilterButtons();
        displaySprints();
    }
    
    private void updateFilterButtons() {
        btnAllSprints.setBackgroundTintList(getResources().getColorStateList(R.color.input_bg));
        btnLiveSprints.setBackgroundTintList(getResources().getColorStateList(R.color.input_bg));
        btnUpcomingSprints.setBackgroundTintList(getResources().getColorStateList(R.color.input_bg));
        btnAttendedSprints.setBackgroundTintList(getResources().getColorStateList(R.color.input_bg));
        
        btnAllSprints.setTextColor(getResources().getColor(R.color.text_muted));
        btnLiveSprints.setTextColor(getResources().getColor(R.color.text_muted));
        btnUpcomingSprints.setTextColor(getResources().getColor(R.color.text_muted));
        btnAttendedSprints.setTextColor(getResources().getColor(R.color.text_muted));
        
        Button activeButton;
        switch (currentFilter) {
            case "live":
                activeButton = btnLiveSprints;
                break;
            case "upcoming":
                activeButton = btnUpcomingSprints;
                break;
            case "attended":
                activeButton = btnAttendedSprints;
                break;
            default:
                activeButton = btnAllSprints;
        }
        
        activeButton.setBackgroundTintList(getResources().getColorStateList(R.color.brand_blue));
        activeButton.setTextColor(getResources().getColor(R.color.text_white));
    }
    
    // ==================== Display ====================
    
    private void displaySprints() {
        sprintsContainer.removeAllViews();
        
        List<SprintSessionResponse> filteredSprints = getFilteredSprints();
        
        if (filteredSprints.isEmpty()) {
            showEmptyState();
            return;
        }
        
        sprintsScrollView.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        
        // Group sprints by category and display with headers
        if (currentFilter.equals("all")) {
            displayWithCategoryHeaders(filteredSprints);
        } else {
            // When filtering by category, just display without headers
            for (SprintSessionResponse sprint : filteredSprints) {
                CardView sprintCard = createSprintCard(sprint);
                sprintsContainer.addView(sprintCard);
            }
        }
    }
    
    private void displayWithCategoryHeaders(List<SprintSessionResponse> sprints) {
        // Group sprints by category
        List<SprintSessionResponse> liveSprints = new ArrayList<>();
        List<SprintSessionResponse> upcomingSprints = new ArrayList<>();
        List<SprintSessionResponse> attendedSprints = new ArrayList<>();
        
        for (SprintSessionResponse sprint : sprints) {
            String category = getSprintCategory(sprint);
            if (category.equals("live")) {
                liveSprints.add(sprint);
            } else if (category.equals("upcoming")) {
                upcomingSprints.add(sprint);
            } else if (category.equals("attended")) {
                attendedSprints.add(sprint);
            }
        }
        
        // Display Live sprints with header
        if (!liveSprints.isEmpty()) {
            sprintsContainer.addView(createCategoryHeader("Live"));
            for (SprintSessionResponse sprint : liveSprints) {
                CardView sprintCard = createSprintCard(sprint);
                sprintsContainer.addView(sprintCard);
            }
        }
        
        // Display Upcoming sprints with header
        if (!upcomingSprints.isEmpty()) {
            sprintsContainer.addView(createCategoryHeader("Upcoming"));
            for (SprintSessionResponse sprint : upcomingSprints) {
                CardView sprintCard = createSprintCard(sprint);
                sprintsContainer.addView(sprintCard);
            }
        }
        
        // Display Attended sprints with header
        if (!attendedSprints.isEmpty()) {
            sprintsContainer.addView(createCategoryHeader("Attended"));
            for (SprintSessionResponse sprint : attendedSprints) {
                CardView sprintCard = createSprintCard(sprint);
                sprintsContainer.addView(sprintCard);
            }
        }
    }
    
    private TextView createCategoryHeader(String category) {
        TextView categoryHeader = new TextView(requireContext());
        categoryHeader.setText(category);
        categoryHeader.setTextColor(getResources().getColor(R.color.text_white));
        categoryHeader.setTextSize(16);
        categoryHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, 24, 0, 12);
        categoryHeader.setLayoutParams(headerParams);
        
        return categoryHeader;
    }
    
    private List<SprintSessionResponse> getFilteredSprints() {
        List<SprintSessionResponse> filtered = new ArrayList<>();
        
        for (SprintSessionResponse sprint : allSprints) {
            if (currentFilter.equals("all")) {
                filtered.add(sprint);
            } else if (currentFilter.equals(getSprintCategory(sprint))) {
                filtered.add(sprint);
            }
        }
        
        return filtered;
    }
    
    private String getSprintCategory(SprintSessionResponse sprint) {
        if (sprint.status == null) return "scheduled";
        
        switch (sprint.status.toLowerCase()) {
            case "live":
            case "confirmed":
                return "live";
            case "completed":
            case "finished":
                return "attended";
            case "scheduled":
            default:
                return "upcoming";
        }
    }
    
    private CardView createSprintCard(SprintSessionResponse sprint) {
        CardView card = new CardView(requireContext());
        card.setCardBackgroundColor(getResources().getColor(R.color.input_bg));
        card.setCardElevation(0);
        card.setRadius(20);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        
        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(20, 20, 20, 20);
        
        // Title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(sprint.goalTitle != null ? sprint.goalTitle : "Untitled Sprint");
        tvTitle.setTextColor(getResources().getColor(R.color.text_white));
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        cardContent.addView(tvTitle);
        
        // Description
        if (sprint.description != null && !sprint.description.isEmpty()) {
            TextView tvDesc = new TextView(requireContext());
            tvDesc.setText(sprint.description);
            tvDesc.setTextColor(getResources().getColor(R.color.text_muted));
            tvDesc.setTextSize(13);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            descParams.setMargins(0, 12, 0, 0);
            tvDesc.setLayoutParams(descParams);
            cardContent.addView(tvDesc);
        }
        
        // Info Row
        LinearLayout infoRow = new LinearLayout(requireContext());
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        infoParams.setMargins(0, 12, 0, 0);
        infoRow.setLayoutParams(infoParams);
        
        // Duration
        TextView tvDuration = new TextView(requireContext());
        tvDuration.setText(sprint.durationMinutes + " min");
        tvDuration.setTextColor(getResources().getColor(R.color.brand_blue));
        tvDuration.setTextSize(12);
        infoRow.addView(tvDuration);
        
        // Participants
        TextView tvParticipants = new TextView(requireContext());
        int participantCount = (sprint.participants != null) ? sprint.participants.size() : 0;
        tvParticipants.setText(" • " + participantCount + " people");
        tvParticipants.setTextColor(getResources().getColor(R.color.text_muted));
        tvParticipants.setTextSize(12);
        LinearLayout.LayoutParams partParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        partParams.setMargins(4, 0, 0, 0);
        tvParticipants.setLayoutParams(partParams);
        infoRow.addView(tvParticipants);
        
        // Status Badge
        TextView tvStatus = new TextView(requireContext());
        tvStatus.setText(getSprintCategory(sprint).toUpperCase());
        tvStatus.setTextColor(getResources().getColor(R.color.text_white));
        tvStatus.setTextSize(10);
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStatus.setPadding(8, 4, 8, 4);
        tvStatus.setBackgroundResource(R.drawable.input_background);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, 0, 0, 0);
        statusParams.gravity = android.view.Gravity.END;
        tvStatus.setLayoutParams(statusParams);
        infoRow.addView(tvStatus);
        
        cardContent.addView(infoRow);
        
        card.addView(cardContent);
        
        // Click listener to view details
        card.setOnClickListener(v -> openSprintDetails(sprint.id));
        
        return card;
    }
    
    private void openSprintDetails(String sprintId) {
        if (getActivity() == null) {
            return;
        }
        Intent intent = new Intent(getActivity(), SprintDetailsActivity.class);
        intent.putExtra("SPRINT_ID", sprintId);
        startActivity(intent);
    }
    
    private void showEmptyState() {
        sprintsScrollView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }
}