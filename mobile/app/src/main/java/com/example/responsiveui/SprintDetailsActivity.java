package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.SprintSessionResponse;
import com.example.responsiveui.api.models.SprintSessionUpdateRequest;
import com.example.responsiveui.api.models.SprintTodoResponse;
import com.example.responsiveui.api.models.SprintTodoCreateRequest;
import com.example.responsiveui.api.models.SprintTodoUpdateRequest;
import com.example.responsiveui.api.models.ParticipantDetail;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * ==================== Sprint Details Activity ====================
 * Shows complete sprint details including:
 * - Goal title, description, repository link, meeting link
 * - Duration
 * - Participant information
 * - Dynamic todo checklist
 * - Option to add/remove/check todos
 * - Join button for invited users (shows when sprint is confirmed)
 * - Start button for sprint creator
 */
public class SprintDetailsActivity extends AppCompatActivity {

    private String sprintId;
    private String currentUserId;
    private SprintSessionResponse currentSprint;
    private CodeCollabApiService apiService;
    
    private TextView tvGoalTitle;
    private TextView tvDescription;
    private TextView tvRepoLink;
    private TextView tvMeetingLink;
    private TextView tvDuration;
    private LinearLayout participantsLayout;
    private LinearLayout todosLayout;
    private EditText etNewTodo;
    private Button btnAddTodo;
    private Button btnJoinSprint;
    private Button btnStartSprint;
    private ProgressBar loadingProgress;
    private TextView btnCancel;
    
    private List<String> todoIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sprint_details);
        
        // Extract data from intent
        Intent intent = getIntent();
        sprintId = intent.getStringExtra("SPRINT_ID");
        
        // Initialize TokenManager and get current user
        TokenManager.init(this);
        currentUserId = TokenManager.getUserId();
        
        // Initialize API service
        apiService = ApiConfig.getApiService(this);
        
        // Initialize views
        initializeViews();
        
        // Load sprint details
        loadSprintDetails();
    }
    
    // ==================== View Initialization ====================
    
    private void initializeViews() {
        btnCancel = findViewById(R.id.btnCancelDetails);
        tvGoalTitle = findViewById(R.id.tvGoalTitle);
        tvDescription = findViewById(R.id.tvDescription);
        tvRepoLink = findViewById(R.id.tvRepoLink);
        tvMeetingLink = findViewById(R.id.tvMeetingLink);
        tvDuration = findViewById(R.id.tvDuration);
        participantsLayout = findViewById(R.id.participantsLayout);
        todosLayout = findViewById(R.id.todosLayout);
        etNewTodo = findViewById(R.id.etNewTodo);
        btnAddTodo = findViewById(R.id.btnAddTodo);
        loadingProgress = findViewById(R.id.loadingProgress);
        btnJoinSprint = findViewById(R.id.btnJoinSprint);
        btnStartSprint = findViewById(R.id.btnStartSprint);
        
        btnCancel.setOnClickListener(v -> finish());
        btnAddTodo.setOnClickListener(v -> addNewTodo());
        
        // Set up action buttons visibility
        if (btnJoinSprint != null) {
            btnJoinSprint.setOnClickListener(v -> joinSprint());
        }
        
        if (btnStartSprint != null) {
            btnStartSprint.setOnClickListener(v -> startSprint());
        }
    }
    
    // ==================== Sprint Details Loading ====================
    
    private void loadSprintDetails() {
        showLoading(true);
        
        Call<SprintSessionResponse> call = apiService.getSprintDetails(sprintId);
        call.enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                showLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    displaySprintDetails(response.body());
                } else {
                    Toast.makeText(SprintDetailsActivity.this, "Failed to load sprint details", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<SprintSessionResponse> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SprintDetailsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void displaySprintDetails(SprintSessionResponse sprint) {
        // Store current sprint for later reference
        currentSprint = sprint;
        
        // Display basic info
        tvGoalTitle.setText(sprint.goalTitle);
        tvDescription.setText(sprint.description);
        tvRepoLink.setText(sprint.repoLink != null ? sprint.repoLink : "No repository");
        tvMeetingLink.setText(sprint.meetingLink != null ? sprint.meetingLink : "No meeting link");
        tvDuration.setText(sprint.durationMinutes + " minutes");
        
        // Display participants
        if (sprint.participantDetails != null && !sprint.participantDetails.isEmpty()) {
            participantsLayout.removeAllViews();
            for (ParticipantDetail participant : sprint.participantDetails) {
                TextView tvParticipant = new TextView(this);
                String participantName = resolveParticipantDisplayName(participant);
                if (participantName == null || participantName.isEmpty()) {
                    participantName = "Partner";
                }
                tvParticipant.setText("• " + participantName);
                tvParticipant.setTextColor(getResources().getColor(R.color.text_white, null));
                tvParticipant.setTextSize(14);
                participantsLayout.addView(tvParticipant);
            }
        } else if (sprint.participants != null) {
            participantsLayout.removeAllViews();
            for (String participant : sprint.participants) {
                TextView tvParticipant = new TextView(this);
                tvParticipant.setText("• " + participant);
                tvParticipant.setTextColor(getResources().getColor(R.color.text_white, null));
                tvParticipant.setTextSize(14);
                participantsLayout.addView(tvParticipant);
            }
        }
        
        // Update action buttons visibility based on user role and sprint status
        updateActionButtons();
        
        // Load todos
        loadSprintTodos();
    }
    
    // ==================== Action Buttons Management ====================
    
    private void updateActionButtons() {
        boolean isCreator = currentSprint.createdBy != null && currentSprint.createdBy.equals(currentUserId);
        boolean isSetup = "setupped".equalsIgnoreCase(currentSprint.status);
        boolean isStarted = "started".equalsIgnoreCase(currentSprint.status);
        boolean isEnded = "end".equalsIgnoreCase(currentSprint.status);
        
        if (btnJoinSprint != null) {
            // Show join button if user is NOT creator and sprint is in setup/started phase
            if (!isCreator && !isEnded && (isSetup || isStarted)) {
                btnJoinSprint.setVisibility(View.VISIBLE);
            } else {
                btnJoinSprint.setVisibility(View.GONE);
            }
        }
        
        if (btnStartSprint != null) {
            // Show start button only for creator after setup confirmation
            if (isCreator && isSetup) {
                btnStartSprint.setVisibility(View.VISIBLE);
            } else {
                btnStartSprint.setVisibility(View.GONE);
            }
        }
    }
    
    private void joinSprint() {
        if (currentSprint == null) {
            Toast.makeText(this, "Sprint data not loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if ("end".equalsIgnoreCase(currentSprint.status)) {
            Toast.makeText(this, "This sprint has already ended", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);
        
        Call<SprintSessionResponse> call = apiService.joinSprintSession(sprintId);
        call.enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                showLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(SprintDetailsActivity.this, "Joined sprint successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Update the sprint data
                    currentSprint = response.body();
                    updateActionButtons();
                    
                    // Navigate to live sprint view
                    navigateToLiveSprint();
                } else {
                    Toast.makeText(SprintDetailsActivity.this, "Failed to join sprint", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<SprintSessionResponse> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SprintDetailsActivity.this, "Error joining sprint: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void startSprint() {
        if (currentSprint == null) {
            Toast.makeText(this, "Sprint data not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!"setupped".equalsIgnoreCase(currentSprint.status)) {
            Toast.makeText(this, "Sprint is already started or ended", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);

        SprintSessionUpdateRequest request = new SprintSessionUpdateRequest();
        request.status = "started";

        Call<SprintSessionResponse> call = apiService.updateSprintSession(sprintId, request);
        call.enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                showLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    currentSprint = response.body();
                    Toast.makeText(SprintDetailsActivity.this, "Sprint started", Toast.LENGTH_SHORT).show();
                    navigateToLiveSprint();
                } else {
                    Toast.makeText(SprintDetailsActivity.this, "Failed to start sprint", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<SprintSessionResponse> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SprintDetailsActivity.this, "Error starting sprint: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void navigateToLiveSprint() {
        if (currentSprint == null) {
            return;
        }
        
        Intent intent = new Intent(this, LiveSprintActivity.class);
        intent.putExtra("USER_EMAIL", TokenManager.getUserEmail());
        intent.putExtra("SPRINT_ID", sprintId);
        intent.putExtra("SESSION_LENGTH", String.valueOf(currentSprint.durationMinutes));
        
        // Get partner name from participantDetails (name instead of just ID)
        String partnerName = null;
        
        if (currentSprint.participantDetails != null && currentSprint.participantDetails.size() > 0) {
            // Find partner from participantDetails (not current user)
            for (ParticipantDetail participant : currentSprint.participantDetails) {
                if (participant == null) {
                    continue;
                }

                String participantId = participant.userId != null ? participant.userId : "";
                if (currentUserId != null && currentUserId.equals(participantId)) {
                    continue;
                }

                partnerName = resolveParticipantDisplayName(participant);
                if (partnerName == null || partnerName.isEmpty()) {
                    partnerName = "Partner";
                }
                break;
            }
        } else if (currentSprint.participants != null && currentSprint.participants.size() > 0) {
            // Fallback to participants list if participantDetails is not available
            for (String participant : currentSprint.participants) {
                if (participant == null || participant.isEmpty()) {
                    continue;
                }
                if (currentUserId != null && currentUserId.equals(participant)) {
                    continue;
                }
                partnerName = participant;
                break;
            }
        }
        
        if (partnerName == null || partnerName.trim().isEmpty()) {
            partnerName = "Partner";
        }

        intent.putExtra("PARTNER_NAME", partnerName);
        
        startActivity(intent);
        finish();
    }

    private String resolveParticipantDisplayName(ParticipantDetail participant) {
        if (participant == null) {
            return null;
        }

        if (participant.fullName != null && !participant.fullName.trim().isEmpty()) {
            return participant.fullName.trim();
        }

        if (participant.email != null && !participant.email.trim().isEmpty()) {
            return nameFromEmail(participant.email.trim());
        }

        if (participant.userId != null) {
            return participant.userId;
        }

        return null;
    }

    private String nameFromEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }
    
    // ==================== Todo Management ====================
    
    private void loadSprintTodos() {
        Call<List<SprintTodoResponse>> call = apiService.getSprintTodos(sprintId);
        call.enqueue(new Callback<List<SprintTodoResponse>>() {
            @Override
            public void onResponse(Call<List<SprintTodoResponse>> call, Response<List<SprintTodoResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    displayTodos(response.body());
                }
            }
            
            @Override
            public void onFailure(Call<List<SprintTodoResponse>> call, Throwable t) {
                Toast.makeText(SprintDetailsActivity.this, "Error loading todos", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void displayTodos(List<SprintTodoResponse> todos) {
        todosLayout.removeAllViews();
        todoIds.clear();
        
        for (SprintTodoResponse todo : todos) {
            addTodoView(todo);
        }
    }
    
    private void addTodoView(SprintTodoResponse todo) {
        // Create a horizontal layout for each todo
        LinearLayout todoItem = new LinearLayout(this);
        todoItem.setOrientation(LinearLayout.HORIZONTAL);
        todoItem.setPadding(16, 12, 16, 12);
        todoItem.setBackgroundResource(R.drawable.input_background);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        todoItem.setLayoutParams(params);
        
        // Checkbox
        CheckBox checkbox = new CheckBox(this);
        checkbox.setChecked(todo.isCompleted);
        checkbox.setId(View.generateViewId());
        checkbox.setButtonTintList(
            android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.brand_blue, null)
            )
        );
        
        // Todo title
        TextView tvTodo = new TextView(this);
        tvTodo.setText(todo.title);
        tvTodo.setTextColor(getResources().getColor(R.color.text_white, null));
        tvTodo.setTextSize(14);
        tvTodo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tvTodo.setPadding(8, 0, 8, 0);
        
        // Delete button
        ImageButton btnDelete = new ImageButton(this);
        btnDelete.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        
        todoItem.addView(checkbox);
        todoItem.addView(tvTodo);
        todoItem.addView(btnDelete);
        
        todosLayout.addView(todoItem);
        
        // Set up listeners
        final String todoId = todo.id;
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> 
            updateTodoStatus(todoId, isChecked));
        
        btnDelete.setOnClickListener(v -> deleteTodo(todoId));
        
        todoIds.add(todoId);
    }
    
    private void addNewTodo() {
        String todoTitle = etNewTodo.getText().toString().trim();
        
        if (todoTitle.isEmpty()) {
            Toast.makeText(this, "Please enter a todo title", Toast.LENGTH_SHORT).show();
            return;
        }
        
        SprintTodoCreateRequest request = new SprintTodoCreateRequest(sprintId, todoTitle);
        
        Call<SprintTodoResponse> call = apiService.createSprintTodo(sprintId, request);
        call.enqueue(new Callback<SprintTodoResponse>() {
            @Override
            public void onResponse(Call<SprintTodoResponse> call, Response<SprintTodoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    addTodoView(response.body());
                    etNewTodo.setText("");
                    Toast.makeText(SprintDetailsActivity.this, "Todo added", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<SprintTodoResponse> call, Throwable t) {
                Toast.makeText(SprintDetailsActivity.this, "Error adding todo", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateTodoStatus(String todoId, boolean isCompleted) {
        SprintTodoUpdateRequest request = new SprintTodoUpdateRequest(isCompleted);

        Call<SprintTodoResponse> call = apiService.updateSprintTodo(sprintId, todoId, request);
        call.enqueue(new Callback<SprintTodoResponse>() {
            @Override
            public void onResponse(Call<SprintTodoResponse> call, Response<SprintTodoResponse> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(SprintDetailsActivity.this, "Failed to update todo", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SprintTodoResponse> call, Throwable t) {
                Toast.makeText(SprintDetailsActivity.this, "Error updating todo", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void deleteTodo(String todoId) {
        Call<Void> call = apiService.deleteSprintTodo(sprintId, todoId);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    loadSprintTodos();
                    Toast.makeText(SprintDetailsActivity.this, "Todo deleted", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(SprintDetailsActivity.this, "Error deleting todo", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // ==================== UI State ====================
    
    private void showLoading(boolean show) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
