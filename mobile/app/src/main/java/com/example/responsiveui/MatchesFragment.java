package com.example.responsiveui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.HorizontalScrollView;
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
import java.util.stream.Collectors;

/**
 * ==================== MatchesFragment ====================
 * Main matching hub for finding collaboration partners
 * Features:
 * - Create new match requests
 * - Browse matches by session type (All, Debug, Interview, Hackathon, Learning)
 * - Accept matches (Reject only available in My Requests)
 * - Display user profiles with skills, reputation, and created date
 * - Real-time filter switching
 */
public class MatchesFragment extends Fragment implements MatchAdapter.MatchActionListener, 
                                                        CreateMatchRequestDialogFragment.CreateMatchListener,
                                                        MyRequestsAdapter.MyRequestActionListener {
    
    private RecyclerView matchesRecyclerView;
    private RecyclerView myRequestsRecyclerView;
    private ProgressBar loadingProgress;
    private LinearLayout emptyState;
    private Button btnCreateMatch;
    private Button tabBrowse;
    private Button tabMyRequests;
    private EditText searchSkillsInput;
    private MatchAdapter matchAdapter;
    private MyRequestsAdapter myRequestsAdapter;
    private CodeCollabApiService apiService;
    
    private CardView filterAll;
    private CardView filterDebug;
    private CardView filterInterview;
    private CardView filterHackathon;
    private CardView filterLearning;
    
    private String currentFilter = null;  // null = All
    private String currentTab = "browse";  // "browse" or "requests"
    private HorizontalScrollView filterScroll;  // Filter buttons container
    private List<MatchRequestResponse> allMatches = new ArrayList<>();  // Cache all matches for search

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
        myRequestsRecyclerView = view.findViewById(R.id.myRequestsRecyclerView);
        loadingProgress = view.findViewById(R.id.loadingProgress);
        emptyState = view.findViewById(R.id.emptyState);
        btnCreateMatch = view.findViewById(R.id.btnCreateMatch);
        searchSkillsInput = view.findViewById(R.id.searchSkillsInput);
        
        tabBrowse = view.findViewById(R.id.tabBrowse);
        tabMyRequests = view.findViewById(R.id.tabMyRequests);
        
        filterAll = view.findViewById(R.id.filterAll);
        filterDebug = view.findViewById(R.id.filterDebug);
        filterInterview = view.findViewById(R.id.filterInterview);
        filterHackathon = view.findViewById(R.id.filterHackathon);
        filterLearning = view.findViewById(R.id.filterLearning);
        filterScroll = view.findViewById(R.id.filterScroll);
        
        // Initialize API service
        if (getContext() != null) {
            apiService = ApiConfig.getApiService(getContext());
        }
        
        // Setup RecyclerViews
        setupRecyclerView();
        setupMyRequestsRecyclerView();
        
        // Setup tab buttons
        setupTabButtons();
        
        // Setup search functionality
        setupSearchBar();
        
        // Setup filter buttons
        setupFilterButtons();
        
        // Setup create button
        setupCreateButton();
        
        // Load browse matches initially
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

    private void setupMyRequestsRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        myRequestsRecyclerView.setLayoutManager(layoutManager);
        
        myRequestsAdapter = new MyRequestsAdapter(new ArrayList<>(), getContext());
        myRequestsAdapter.setListener(this);
        myRequestsRecyclerView.setAdapter(myRequestsAdapter);
    }

    // ==================== Search Bar Setup ====================
    
    private void setupSearchBar() {
        searchSkillsInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterMatchesBySkill(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterMatchesBySkill(String query) {
        if (query.isEmpty()) {
            // Show all matches (or apply current filter)
            matchAdapter.updateMatches(allMatches);
        } else {
            // Filter matches by skill name in required_skills or user skills
            List<MatchRequestResponse> filtered = allMatches.stream()
                    .filter(match -> {
                        // Check required skills
                        if (match.requiredSkills != null) {
                            for (String skill : match.requiredSkills) {
                                if (skill.toLowerCase().contains(query.toLowerCase())) {
                                    return true;
                                }
                            }
                        }
                        // Check user skills
                        if (match.userSkills != null) {
                            for (MatchRequestResponse.UserSkillInfo skill : match.userSkills) {
                                if (skill.name.toLowerCase().contains(query.toLowerCase())) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            
            matchAdapter.updateMatches(filtered);
        }
    }

    // ==================== Create Button Setup ====================
    
    private void setupCreateButton() {
        btnCreateMatch.setOnClickListener(v -> showCreateMatchDialog());
    }

    // ==================== Tab Button Setup ====================
    
    private void setupTabButtons() {
        tabBrowse.setOnClickListener(v -> switchTab("browse"));
        tabMyRequests.setOnClickListener(v -> switchTab("requests"));
    }

    private void switchTab(String tab) {
        if (tab.equals(currentTab)) return;  // Already on this tab
        
        currentTab = tab;
        
        // Clear search when switching tabs
        searchSkillsInput.setText("");
        allMatches.clear();
        
        if (tab.equals("browse")) {
            // Switch to Browse tab
            tabBrowse.setBackgroundResource(R.drawable.bg_button_solid_blue);
            tabBrowse.setTextColor(getResources().getColor(R.color.text_white, null));
            
            tabMyRequests.setBackgroundResource(R.drawable.bg_button_outline);
            tabMyRequests.setTextColor(getResources().getColor(R.color.text_muted, null));
            
            // Show browse UI, hide my requests
            matchesRecyclerView.setVisibility(View.VISIBLE);
            myRequestsRecyclerView.setVisibility(View.GONE);
            filterScroll.setVisibility(View.VISIBLE);
            searchSkillsInput.setVisibility(View.VISIBLE);
            
            loadMatches(currentFilter);
        } else {
            // Switch to My Requests tab
            tabMyRequests.setBackgroundResource(R.drawable.bg_button_solid_blue);
            tabMyRequests.setTextColor(getResources().getColor(R.color.text_white, null));
            
            tabBrowse.setBackgroundResource(R.drawable.bg_button_outline);
            tabBrowse.setTextColor(getResources().getColor(R.color.text_muted, null));
            
            // Show my requests, hide browse
            matchesRecyclerView.setVisibility(View.GONE);
            myRequestsRecyclerView.setVisibility(View.VISIBLE);
            filterScroll.setVisibility(View.GONE);
            searchSkillsInput.setVisibility(View.GONE);
            
            loadMyRequests();
        }
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
                    
                    // Cache all matches for search
                    allMatches.clear();
                    allMatches.addAll(matches);
                    
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

    // ==================== Load My Requests ====================
    
    private void loadMyRequests() {
        showLoading(true);
        
        Call<List<MatchRequestResponse>> call = apiService.getMyMatchRequests();
        
        call.enqueue(new Callback<List<MatchRequestResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MatchRequestResponse>> call, 
                                 @NonNull Response<List<MatchRequestResponse>> response) {
                showLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    List<MatchRequestResponse> requests = response.body();
                    
                    if (requests.isEmpty()) {
                        showEmptyState(true);
                        myRequestsAdapter.updateMatches(new ArrayList<>());
                    } else {
                        showEmptyState(false);
                        myRequestsAdapter.updateMatches(requests);
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to load requests", Toast.LENGTH_SHORT).show();
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
    public void onCancel(MatchRequestResponse match) {
        if (apiService == null || match == null) return;
        
        Call<Void> call = apiService.cancelMatchRequest(match.id);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Request canceled", Toast.LENGTH_SHORT).show();
                    // Reload my requests
                    loadMyRequests();
                } else {
                    Toast.makeText(getContext(), "Failed to cancel request", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== UI State Management ====================
    
    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        
        if (currentTab.equals("browse")) {
            matchesRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        } else {
            myRequestsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void showEmptyState(boolean show) {
        emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
