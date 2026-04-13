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
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.models.MatchRequestResponse;
import com.example.responsiveui.api.models.SprintSessionCreateRequest;
import com.example.responsiveui.api.models.SprintSessionResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 */
public class SprintSetupActivity extends AppCompatActivity {

    private String matchId;
    private String partnerName;
    private String userEmail;
    private MatchRequestResponse matchData;
    
    private Button btnConfirmSprint;
    private TextView btnCancel;
    private EditText etGoalTitle;
    private EditText etDescription;
    private EditText etRepoLink;
    private EditText etMeetingLink;
    private ProgressBar loadingProgress;
    private LinearLayout formContainer;
    
    private CodeCollabApiService apiService;
    private int selectedDuration = 60;  // Default 60 minutes
    private List<String> participants = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sprint_setup);

        // Extract data from intent
        Intent intent = getIntent();
        matchId = intent.getStringExtra("MATCH_ID");
        partnerName = intent.getStringExtra("PARTNER_NAME");
        userEmail = intent.getStringExtra("USER_EMAIL");

        // Initialize API service
        apiService = ApiConfig.getApiService(this);

        // Initialize views
        initializeViews();
        
        // Setup duration selection
        setupDurationButtons();
        
        // Setup button listeners
        setupButtonListeners();
    }

    // ==================== View Initialization ====================
    
    private void initializeViews() {
        btnConfirmSprint = findViewById(R.id.btnConfirmSprint);
        btnCancel = findViewById(R.id.btnCancel);
        etGoalTitle = findViewById(R.id.etGoalTitle);
        etDescription = findViewById(R.id.etDescription);
        etRepoLink = findViewById(R.id.etRepoLink);
        etMeetingLink = findViewById(R.id.etMeetingLink);
        loadingProgress = findViewById(R.id.loadingProgress);
        formContainer = findViewById(R.id.formContainer);
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

    // ==================== Button Listeners ====================
    
    private void setupButtonListeners() {
        btnConfirmSprint.setOnClickListener(v -> confirmAndCreateSprint());
        btnCancel.setOnClickListener(v -> finish());
    }

    // ==================== Sprint Creation ====================
    
    private void confirmAndCreateSprint() {
        String goal = etGoalTitle.getText().toString().trim();
        
        if (goal.isEmpty()) {
            Toast.makeText(this, "Please enter a goal title", Toast.LENGTH_SHORT).show();
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
        
        // Prepare participants list (current user + partner)
        participants.clear();
        if (userEmail != null && !userEmail.isEmpty()) {
            participants.add(userEmail);
        }
        if (partnerName != null && !partnerName.isEmpty()) {
            participants.add(partnerName);
        }
        
        // If empty, use defaults
        if (participants.isEmpty()) {
            participants.add("user1");
            participants.add("user2");
        }
        
        // Create request
        SprintSessionCreateRequest request = new SprintSessionCreateRequest(
                goal,
                description.isEmpty() ? "Sprint session" : description,
                repoLink,
                meetingLink,
                selectedDuration,
                participants
        );
        
        // Send to backend
        Call<SprintSessionResponse> call = apiService.createSprintSession(request);
        call.enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                showLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(SprintSetupActivity.this, 
                            "Sprint created successfully! 🚀", Toast.LENGTH_SHORT).show();
                    
                    // Go to sprint summary
                    Intent summaryIntent = new Intent(SprintSetupActivity.this, SprintSummaryActivity.class);
                    summaryIntent.putExtra("SPRINT_ID", response.body().id);
                    summaryIntent.putExtra("SPRINT_GOAL", goal);
                    summaryIntent.putExtra("PARTNER_NAME", partnerName);
                    summaryIntent.putExtra("USER_EMAIL", userEmail);
                    summaryIntent.putExtra("MATCH_ID", matchId);
                    startActivity(summaryIntent);
                    finish();
                } else {
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