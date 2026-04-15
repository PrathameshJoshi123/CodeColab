package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.Map;

public class SprintSummaryActivity extends AppCompatActivity {

    // Variable declarations for the data we are carrying
    private String userEmail, partnerName, partnerUID, sprintGoal, sessionLength, sprintId, matchId, partnerFcmToken;
    private Button btnStartNow;
    private Button btnConfirmSprint;
    private Button btnEdit;
    private ProgressBar loadingProgress;
    private TextView tvPartnerName;
    private CodeCollabApiService apiService;
    private boolean isSprintConfirmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sprint_summary);

        // 1. Retrieve the complete "Suitcase" of data from the Intent
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        partnerName = getIntent().getStringExtra("PARTNER_NAME");
        partnerUID = getIntent().getStringExtra("PARTNER_UID");
        sprintGoal = getIntent().getStringExtra("SPRINT_GOAL");
        sessionLength = getIntent().getStringExtra("SESSION_LENGTH");
        sprintId = getIntent().getStringExtra("SPRINT_ID");
        matchId = getIntent().getStringExtra("MATCH_ID");

        // Initialize API service
        apiService = ApiConfig.getApiService(this);

        // 2. Initialize the UI elements from your XML
        tvPartnerName = findViewById(R.id.tvPartnerName);
        TextView tvDuration = findViewById(R.id.tvDuration);
        TextView tvGoalText = findViewById(R.id.tvGoalText);
        TextView tvFocusSkill = findViewById(R.id.tvFocusSkill);
        btnStartNow = findViewById(R.id.btnStartNow);
        btnConfirmSprint = findViewById(R.id.btnConfirmSprint);
        btnEdit = findViewById(R.id.btnEdit);
        ImageView btnClose = findViewById(R.id.btnClose);
        loadingProgress = findViewById(R.id.loadingProgress);

        // 3. Fetch partner details from match ID if available
        if (matchId != null && !matchId.isEmpty()) {
            fetchPartnerDetailsFromMatch();
        } else {
            // Fallback to existing partner name
            if (partnerName != null) tvPartnerName.setText(partnerName);
        }
        
        if (sessionLength != null) tvDuration.setText(sessionLength + " min\nDuration");
        if (sprintGoal != null) tvGoalText.setText("\"" + sprintGoal + "\"");
        
        // Display focus skill - derive from goal or set as "Sprint Focus"
        if (tvFocusSkill != null) {
            String folksSkillText = deriveFocusSkill(sprintGoal);
            tvFocusSkill.setText(folksSkillText);
        }

        // 4. Navigation: Launch the Live Sprint
        btnStartNow.setOnClickListener(v -> {
            if (!isSprintConfirmed) {
                Toast.makeText(SprintSummaryActivity.this,
                        "Confirm and notify partner before starting", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(SprintSummaryActivity.this, SprintDetailsActivity.class);
            intent.putExtra("SPRINT_ID", sprintId);
            startActivity(intent);
        });

        // ==================== Confirm Sprint & Send Notification ====================
        if (btnConfirmSprint != null) {
            btnConfirmSprint.setOnClickListener(v -> confirmSprintAndNotifyPartner());
        }

        // ==================== Edit Details Button ====================
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> editDetails());
        }

        // 5. Navigation: Close/Cancel (Go back to Setup)
        btnClose.setOnClickListener(v -> finish());
    }
    
    // ==================== Fetch Partner Details from Match ====================
    
    private void fetchPartnerDetailsFromMatch() {
        if (matchId == null || matchId.isEmpty()) {
            return;
        }
        
        // Show loading while fetching
        if (tvPartnerName != null) {
            tvPartnerName.setText("Loading partner info...");
        }
        
        Call<Map<String, Object>> call = apiService.getMatchDetails(matchId);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> matchDetails = response.body();
                    
                    // Extract partner information
                    if (matchDetails.containsKey("partner_name")) {
                        partnerName = (String) matchDetails.get("partner_name");
                        if (tvPartnerName != null) {
                            tvPartnerName.setText(partnerName);
                        }
                    }
                    
                    if (matchDetails.containsKey("partner_uid")) {
                        partnerUID = (String) matchDetails.get("partner_uid");
                    }
                    
                    if (matchDetails.containsKey("partner_fcm_token")) {
                        partnerFcmToken = (String) matchDetails.get("partner_fcm_token");
                    }
                } else {
                    // Fallback to existing partner name if API fails
                    if (partnerName != null) {
                        if (tvPartnerName != null) {
                            tvPartnerName.setText(partnerName);
                        }
                    } else if (tvPartnerName != null) {
                        tvPartnerName.setText("Partner");
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Fallback to existing partner name if request fails
                if (partnerName != null) {
                    if (tvPartnerName != null) {
                        tvPartnerName.setText(partnerName);
                    }
                } else if (tvPartnerName != null) {
                    tvPartnerName.setText("Partner");
                }
            }
        });
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Derive focus skill from sprint goal title
     */
    private String deriveFocusSkill(String goal) {
        if (goal == null || goal.isEmpty()) {
            return "Sprint\nFocus";
        }
        
        // Extract first meaningful word(s) from goal
        String[] words = goal.toLowerCase().split("\\s+");
        if (words.length > 0) {
            String focus = words[0];
            // Capitalize first letter
            if (focus.length() > 0) {
                focus = focus.substring(0, 1).toUpperCase() + focus.substring(1);
            }
            return focus + "\nFocus";
        }
        
        return "Sprint\nFocus";
    }
    
    /**
     * Navigate back to SprintSetupActivity to edit details
     */
    private void editDetails() {
        Intent intent = new Intent(SprintSummaryActivity.this, SprintSetupActivity.class);
        
        // Pass current data back to setup for editing
        intent.putExtra("MATCH_ID", getIntent().getStringExtra("MATCH_ID"));
        intent.putExtra("PARTNER_NAME", partnerName);
        intent.putExtra("PARTNER_UID", partnerUID);
        intent.putExtra("USER_EMAIL", userEmail);
        intent.putExtra("SPRINT_ID", sprintId);
        intent.putExtra("SPRINT_GOAL", sprintGoal);
        
        startActivity(intent);
        finish();
    }

    private void confirmSprintAndNotifyPartner() {
        if (sprintId == null || sprintId.isEmpty()) {
            Toast.makeText(this, "Sprint ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        Call<Map<String, Object>> call = apiService.confirmSprintSession(sprintId);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                showLoading(false);

                if (response.isSuccessful()) {
                    Toast.makeText(SprintSummaryActivity.this,
                            "Sprint confirmed! Notification sent to partner", Toast.LENGTH_SHORT).show();

                    isSprintConfirmed = true;
                    btnConfirmSprint.setEnabled(false);
                    btnConfirmSprint.setText("Sprint Confirmed ✅");
                } else {
                    Toast.makeText(SprintSummaryActivity.this,
                            "Failed to confirm sprint", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                showLoading(false);
                Toast.makeText(SprintSummaryActivity.this,
                        "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean show) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnConfirmSprint != null) {
            btnConfirmSprint.setEnabled(!show);
        }
    }
}