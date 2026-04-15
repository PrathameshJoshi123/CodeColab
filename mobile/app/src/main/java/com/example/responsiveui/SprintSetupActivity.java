package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.MatchRequestResponse;
import com.example.responsiveui.api.models.SprintSessionCreateRequest;
import com.example.responsiveui.api.models.SprintSessionResponse;
import com.example.responsiveui.api.models.SprintTodoResponse;
import com.example.responsiveui.api.models.SprintTodoCreateRequest;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ==================== Sprint Setup Activity ====================
 * Allows users to configure sprint details after accepting a match
 * Shows:
 * - Goal title (required)
 * - Description
 * - Repository link
 * - Meeting link
 * - Session duration
 * - Participants list
 * - Dynamic todo checklist with add/delete functionality
 */
public class SprintSetupActivity extends AppCompatActivity implements SprintTodoAdapter.TodoActionListener {

    private String matchId;
    private String partnerName;
    private String partnerUID;
    private String userEmail;
    private String currentUserId;
    private MatchRequestResponse matchData;
    private String sprintSessionId;  // Store sprint ID after creation
    
    private Button btnConfirmSprint;
    private TextView btnCancel;
    private EditText etGoalTitle;
    private EditText etDescription;
    private EditText etRepoLink;
    private EditText etMeetingLink;
    private EditText etTodoTitle;
    private EditText etTodoDescription;
    private Button btnAddTodo;
    private RecyclerView todosRecyclerView;
    private TextView tvTodoCount;
    private ProgressBar loadingProgress;
    private LinearLayout formContainer;
    
    private CodeCollabApiService apiService;
    private int selectedDuration = 60;  // Default 60 minutes
    private List<String> participants = new ArrayList<>();
    private List<SprintTodoResponse> todos = new ArrayList<>();
    private SprintTodoAdapter todoAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sprint_setup);

        // Extract data from intent
        Intent intent = getIntent();
        matchId = intent.getStringExtra("MATCH_ID");
        partnerName = intent.getStringExtra("PARTNER_NAME");
        partnerUID = intent.getStringExtra("PARTNER_UID");
        userEmail = intent.getStringExtra("USER_EMAIL");

        TokenManager.init(this);
        currentUserId = TokenManager.getUserId();
        if (userEmail == null || userEmail.isEmpty()) {
            userEmail = TokenManager.getUserEmail();
        }

        // Initialize API service
        apiService = ApiConfig.getApiService(this);

        // Initialize views
        initializeViews();
        
        // Setup todo recycler view
        setupTodoRecyclerView();
        
        // Setup duration selection
        setupDurationButtons();
        
        // Setup button listeners
        setupButtonListeners();

        // Ensure partner UID/name are resolved from the linked match
        fetchMatchDetailsIfNeeded();
    }

    // ==================== View Initialization ====================
    
    private void initializeViews() {
        btnConfirmSprint = findViewById(R.id.btnConfirmSprint);
        btnCancel = findViewById(R.id.btnCancel);
        etGoalTitle = findViewById(R.id.etGoalTitle);
        etDescription = findViewById(R.id.etDescription);
        etRepoLink = findViewById(R.id.etRepoLink);
        etMeetingLink = findViewById(R.id.etMeetingLink);
        etTodoTitle = findViewById(R.id.etTodoTitle);
        etTodoDescription = findViewById(R.id.etTodoDescription);
        btnAddTodo = findViewById(R.id.btnAddTodo);
        todosRecyclerView = findViewById(R.id.todosRecyclerView);
        tvTodoCount = findViewById(R.id.tvTodoCount);
        loadingProgress = findViewById(R.id.loadingProgress);
        formContainer = findViewById(R.id.formContainer);
    }

    // ==================== Todo Setup ====================
    
    private void setupTodoRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        todosRecyclerView.setLayoutManager(layoutManager);
        
        todoAdapter = new SprintTodoAdapter(todos, this);
        todoAdapter.setListener(this);
        todosRecyclerView.setAdapter(todoAdapter);
        
        updateTodoCount();
    }

    private void setupButtonListeners() {
        btnConfirmSprint.setOnClickListener(v -> confirmAndCreateSprint());
        btnCancel.setOnClickListener(v -> finish());
        btnAddTodo.setOnClickListener(v -> addTodoLocally());
    }

    private void addTodoLocally() {
        String title = etTodoTitle.getText().toString().trim();
        String description = etTodoDescription.getText().toString().trim();
        
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a todo title", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create a local todo (will be saved to backend after sprint creation)
        SprintTodoResponse todo = new SprintTodoResponse();
        todo.title = title;
        todo.description = description.isEmpty() ? "" : description;
        todo.isCompleted = false;
        
        // Add to list
        todoAdapter.addTodo(todo);
        updateTodoCount();
        
        // Clear input fields
        etTodoTitle.setText("");
        etTodoDescription.setText("");
        
        Toast.makeText(this, "Todo added", Toast.LENGTH_SHORT).show();
    }

    private void updateTodoCount() {
        if (tvTodoCount != null) {
            int completed = todoAdapter.getCompletedCount();
            int total = todoAdapter.getTotalCount();
            tvTodoCount.setText(completed + "/" + total + " Completed");
        }
    }

    // ==================== Todo Actions ====================
    
    @Override
    public void onToggleComplete(SprintTodoResponse todo, int position) {
        todo.isCompleted = !todo.isCompleted;
        updateTodoCount();
        todoAdapter.notifyItemChanged(position);
    }

    @Override
    public void onDeleteTodo(SprintTodoResponse todo, int position) {
        todoAdapter.removeTodo(position);
        updateTodoCount();
    }

    // ==================== Duration Setup ====================
    
    private void setupDurationButtons() {
        try {
            // Find duration CardViews and set up listeners
            androidx.cardview.widget.CardView btn30 = findViewById(R.id.btn30min);
            androidx.cardview.widget.CardView btn60 = findViewById(R.id.btn60min);
            androidx.cardview.widget.CardView btn120 = findViewById(R.id.btn120min);
            
            if (btn30 != null) {
                btn30.setOnClickListener(v -> selectDuration(30, btn30, btn60, btn120));
            }
            if (btn60 != null) {
                btn60.setOnClickListener(v -> selectDuration(60, btn30, btn60, btn120));
                btn60.setBackgroundColor(getResources().getColor(R.color.brand_blue, null));
            }
            if (btn120 != null) {
                btn120.setOnClickListener(v -> selectDuration(120, btn30, btn60, btn120));
            }
        } catch (Exception e) {
            // Duration buttons not available
        }
    }

    private void selectDuration(int duration, androidx.cardview.widget.CardView btn30, androidx.cardview.widget.CardView btn60, androidx.cardview.widget.CardView btn120) {
        selectedDuration = duration;
        
        // Reset all CardViews to default color
        btn30.setBackgroundColor(getResources().getColor(R.color.input_bg, null));
        btn60.setBackgroundColor(getResources().getColor(R.color.input_bg, null));
        btn120.setBackgroundColor(getResources().getColor(R.color.input_bg, null));
        
        // Highlight selected CardView with blue color
        androidx.cardview.widget.CardView selectedBtn = null;
        if (duration == 30) selectedBtn = btn30;
        else if (duration == 60) selectedBtn = btn60;
        else if (duration == 120) selectedBtn = btn120;
        
        if (selectedBtn != null) {
            selectedBtn.setBackgroundColor(getResources().getColor(R.color.brand_blue, null));
        }
    }

    // ==================== Sprint Creation ====================
    
    private void confirmAndCreateSprint() {
        String goal = etGoalTitle.getText().toString().trim();
        String meetingLink = etMeetingLink.getText().toString().trim();
        
        if (goal.isEmpty()) {
            Toast.makeText(this, "Please enter a goal title", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // ==================== Meeting Link Now Mandatory ====================
        if (meetingLink.isEmpty()) {
            Toast.makeText(this, "Meeting link is required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading
        showLoading(true);
        
        // Create sprint session
        createSprintSession(goal);
    }

    private void createSprintSession(String goal) {
        String description = etDescription.getText().toString().trim();
        String repoLink = etRepoLink.getText().toString().trim();
        String meetingLink = etMeetingLink.getText().toString().trim();
        
        // Prepare participants list (requester UID + accepted partner UID)
        participants.clear();
        if (currentUserId != null && !currentUserId.isEmpty()) {
            participants.add(currentUserId);
        }
        if (partnerUID != null && !partnerUID.isEmpty()) {
            participants.add(partnerUID);
        }

        if (participants.size() < 2) {
            showLoading(false);
            Toast.makeText(this, "Unable to resolve match participants", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create request with match ID
        SprintSessionCreateRequest request = new SprintSessionCreateRequest(
                goal,
                description.isEmpty() ? "Sprint session" : description,
                repoLink,
                meetingLink,
                selectedDuration,
                participants,
                matchId
        );
        
        // Send to backend
        Call<SprintSessionResponse> call = apiService.createSprintSession(request);
        call.enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    sprintSessionId = response.body().id;
                    Toast.makeText(SprintSetupActivity.this, 
                            "Sprint created successfully! 🚀", Toast.LENGTH_SHORT).show();
                    
                    // Save todos if any exist
                    if (!todos.isEmpty()) {
                        saveTodosToBackend(sprintSessionId, goal);
                    } else {
                        // No todos, go directly to summary
                        navigateToSummary(goal);
                    }
                } else {
                    showLoading(false);
                    Toast.makeText(SprintSetupActivity.this, 
                            "Failed to create sprint", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SprintSessionResponse> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SprintSetupActivity.this, 
                        "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== Save Todos ====================
    
    private void saveTodosToBackend(String sprintId, String goal) {
        // Save todos sequentially
        saveTodoRecursively(sprintId, 0, goal);
    }

    private void saveTodoRecursively(String sprintId, int index, String goal) {
        if (index >= todos.size()) {
            // All todos saved, navigate to summary
            navigateToSummary(goal);
            return;
        }
        
        SprintTodoResponse todo = todos.get(index);
        
        // Create todo request
        SprintTodoCreateRequest request = new SprintTodoCreateRequest(
                sprintId,
                todo.title,
                todo.description
        );
        
        Call<SprintTodoResponse> call = apiService.createSprintTodo(sprintId, request);
        call.enqueue(new Callback<SprintTodoResponse>() {
            @Override
            public void onResponse(Call<SprintTodoResponse> call, Response<SprintTodoResponse> response) {
                if (response.isSuccessful()) {
                    // Save next todo
                    saveTodoRecursively(sprintId, index + 1, goal);
                } else {
                    Toast.makeText(SprintSetupActivity.this, 
                            "Failed to save todo: " + todo.title, Toast.LENGTH_SHORT).show();
                    // Continue with next todo anyway
                    saveTodoRecursively(sprintId, index + 1, goal);
                }
            }

            @Override
            public void onFailure(Call<SprintTodoResponse> call, Throwable t) {
                Toast.makeText(SprintSetupActivity.this, 
                        "Error saving todo: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                // Continue with next todo
                saveTodoRecursively(sprintId, index + 1, goal);
            }
        });
    }

    private void navigateToSummary(String goal) {
        showLoading(false);
        
        // Go to sprint summary
        Intent summaryIntent = new Intent(SprintSetupActivity.this, SprintSummaryActivity.class);
        summaryIntent.putExtra("SPRINT_ID", sprintSessionId);
        summaryIntent.putExtra("SPRINT_GOAL", goal);
        summaryIntent.putExtra("PARTNER_NAME", partnerName);
        summaryIntent.putExtra("PARTNER_UID", partnerUID);
        summaryIntent.putExtra("USER_EMAIL", userEmail);
        summaryIntent.putExtra("MATCH_ID", matchId);
        summaryIntent.putExtra("SESSION_LENGTH", String.valueOf(selectedDuration));
        startActivity(summaryIntent);
        finish();
    }

    // ==================== Match Partner Resolution ====================

    private void fetchMatchDetailsIfNeeded() {
        if (matchId == null || matchId.isEmpty() || apiService == null) {
            return;
        }

        Call<Map<String, Object>> call = apiService.getMatchDetails(matchId);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                Map<String, Object> body = response.body();
                Object partnerNameObj = body.get("partner_name");
                Object partnerUidObj = body.get("partner_uid");

                if (partnerNameObj instanceof String && !((String) partnerNameObj).isEmpty()) {
                    partnerName = (String) partnerNameObj;
                }
                if (partnerUidObj instanceof String && !((String) partnerUidObj).isEmpty()) {
                    partnerUID = (String) partnerUidObj;
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Keep fallback values from intent
            }
        });
    }

    // ==================== UI State ====================
    
    private void showLoading(boolean show) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (formContainer != null) {
            formContainer.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        btnConfirmSprint.setEnabled(!show);
    }
}