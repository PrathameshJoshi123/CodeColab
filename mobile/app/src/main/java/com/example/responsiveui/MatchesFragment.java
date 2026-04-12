package com.example.responsiveui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.models.MatchRequestResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * ==================== MatchesFragment ====================
 * Main matching hub for finding collaboration partners
 * Features:
 * - Create new match requests
 * - Browse matches by session type (All, Debug, Interview, Hackathon, Learning)
 * - Accept/Reject matches
 * - Display user profiles with skills and reputation
 * - Real-time filter switching
 */
public class MatchesFragment extends Fragment implements MatchAdapter.MatchActionListener, 
                                                        CreateMatchRequestDialogFragment.CreateMatchListener {
    
    private RecyclerView matchesRecyclerView;
    private ProgressBar loadingProgress;
    private LinearLayout emptyState;
    private Button btnCreateMatch;
    private MatchAdapter matchAdapter;
    private CodeCollabApiService apiService;
    
    private CardView filterAll;
    private CardView filterDebug;
    private CardView filterInterview;
    private CardView filterHackathon;
    private CardView filterLearning;
    
    private String currentFilter = null;  // null = All

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_matches, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI components
        matchesRecyclerView = view.findViewById(R.id.matchesRecyclerView);
        loadingProgress = view.findViewById(R.id.loadingProgress);
        emptyState = view.findViewById(R.id.emptyState);
        btnCreateMatch = view.findViewById(R.id.btnCreateMatch);
        
        filterAll = view.findViewById(R.id.filterAll);
        filterDebug = view.findViewById(R.id.filterDebug);
        filterInterview = view.findViewById(R.id.filterInterview);
        filterHackathon = view.findViewById(R.id.filterHackathon);
        filterLearning = view.findViewById(R.id.filterLearning);
        
        // Initialize API service
        if (getContext() != null) {
            apiService = ApiConfig.getApiService(getContext());
        }
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Setup filter buttons
        setupFilterButtons();
        
        // Setup create button
        setupCreateButton();
        
        // Load matches
        loadMatches(null);
    }

    // ==================== RecyclerView Setup ====================
    
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        matchesRecyclerView.setLayoutManager(layoutManager);
        
        matchAdapter = new MatchAdapter(new ArrayList<>(), getContext());
        matchAdapter.setListener(this);
        matchesRecyclerView.setAdapter(matchAdapter);
    }

    // ==================== Create Button Setup ====================
    
    private void setupCreateButton() {
        btnCreateMatch.setOnClickListener(v -> showCreateMatchDialog());
    }

    private void showCreateMatchDialog() {
        CreateMatchRequestDialogFragment dialog = new CreateMatchRequestDialogFragment();
        dialog.setListener(this);
        dialog.show(getChildFragmentManager(), "CreateMatchRequest");
    }

    // ==================== Create Match Callback ====================
    
    @Override
    public void onMatchCreated() {
        // Reload matches after successful creation
        loadMatches(currentFilter);
    }

    // ==================== Filter Setup ====================
    
    private void setupFilterButtons() {
        filterAll.setOnClickListener(v -> {
            setActiveFilter(filterAll, null);
            loadMatches(null);
        });
        
        filterDebug.setOnClickListener(v -> {
            setActiveFilter(filterDebug, "Debug");
            loadMatches("Debug");
        });
        
        filterInterview.setOnClickListener(v -> {
            setActiveFilter(filterInterview, "Interview");
            loadMatches("Interview");
        });
        
        filterHackathon.setOnClickListener(v -> {
            setActiveFilter(filterHackathon, "Hackathon");
            loadMatches("Hackathon");
        });
        
        filterLearning.setOnClickListener(v -> {
            setActiveFilter(filterLearning, "Learning");
            loadMatches("Learning");
        });
        
        // Set initial active filter
        setActiveFilter(filterAll, null);
    }

    private void setActiveFilter(CardView activeFilter, String filterType) {
        // Reset all filters
        filterAll.setCardBackgroundColor(getResources().getColor(R.color.input_bg, null));
        filterDebug.setCardBackgroundColor(getResources().getColor(R.color.input_bg, null));
        filterInterview.setCardBackgroundColor(getResources().getColor(R.color.input_bg, null));
        filterHackathon.setCardBackgroundColor(getResources().getColor(R.color.input_bg, null));
        filterLearning.setCardBackgroundColor(getResources().getColor(R.color.input_bg, null));
        
        // Set active filter
        activeFilter.setCardBackgroundColor(getResources().getColor(R.color.brand_blue, null));
        currentFilter = filterType;
    }

    // ==================== Load Matches ====================
    
    private void loadMatches(String sessionType) {
        showLoading(true);
        
        Call<List<MatchRequestResponse>> call = apiService.browseMatchRequests(sessionType);
        
        call.enqueue(new Callback<List<MatchRequestResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MatchRequestResponse>> call, 
                                 @NonNull Response<List<MatchRequestResponse>> response) {
                showLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    List<MatchRequestResponse> matches = response.body();
                    
                    if (matches.isEmpty()) {
                        showEmptyState(true);
                        matchAdapter.updateMatches(new ArrayList<>());
                    } else {
                        showEmptyState(false);
                        matchAdapter.updateMatches(matches);
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to load matches", Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MatchRequestResponse>> call, @NonNull Throwable t) {
                showLoading(false);
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                showEmptyState(true);
            }
        });
    }

    // ==================== Match Actions ====================
    
    @Override
    public void onAccept(MatchRequestResponse match) {
        if (apiService == null || match == null) return;
        
        Call<MatchRequestResponse> call = apiService.acceptMatchRequest(match.id);
        call.enqueue(new Callback<MatchRequestResponse>() {
            @Override
            public void onResponse(@NonNull Call<MatchRequestResponse> call, 
                                 @NonNull Response<MatchRequestResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Match accepted! 🎉", Toast.LENGTH_SHORT).show();
                    // Reload matches
                    loadMatches(currentFilter);
                } else {
                    Toast.makeText(getContext(), "Failed to accept match", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<MatchRequestResponse> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onReject(MatchRequestResponse match) {
        if (apiService == null || match == null) return;
        
        Call<MatchRequestResponse> call = apiService.rejectMatchRequest(match.id);
        call.enqueue(new Callback<MatchRequestResponse>() {
            @Override
            public void onResponse(@NonNull Call<MatchRequestResponse> call, 
                                 @NonNull Response<MatchRequestResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Match rejected", Toast.LENGTH_SHORT).show();
                    // Reload matches
                    loadMatches(currentFilter);
                } else {
                    Toast.makeText(getContext(), "Failed to reject match", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<MatchRequestResponse> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== UI State Management ====================
    
    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        matchesRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
