package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.models.SprintSessionResponse;
import com.example.responsiveui.api.models.SprintTodoResponse;
import com.example.responsiveui.api.models.SprintTodoUpdateRequest;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ==================== Live Sprint Activity ====================
 * Displays live sprint session with dynamic data:
 * - Partner name from sprint participants
 * - Countdown timer from session duration
 * - Sprint objectives loaded from backend
 * - Progress calculation from completed todos
 * - Real-time todo updates
 */
public class LiveSprintActivity extends AppCompatActivity {

    // ==================== Variables ====================
    
    private String userEmail;
    private String partnerName;
    private String sessionLength;
    private String sprintId;
    
    private TextView tvTimer;
    private TextView tvPartnerName;
    private TextView tvFocusMode;
    private TextView tvObjectivesTitle;
    private TextView tvCompletionStatus;
    private ProgressBar progressBar;
    private LinearLayout todosContainer;
    private EditText etScratchpad;
    private Button btnPause;
    private Button btnEnd;
    private FloatingActionButton fabChat;
    private ProgressBar loadingProgress;
    
    private CodeCollabApiService apiService;
    private CountDownTimer countDownTimer;
    private Handler handler;
    private List<SprintTodoResponse> todos = new ArrayList<>();
    private int sprintNumber = 1;  // Default sprint number
    
    private boolean timerStarted = false;
    private boolean allParticipantsJoined = false;
    private boolean scratchpadLoaded = false;
    private boolean isApplyingRemoteScratchpadUpdate = false;
    private String lastSyncedScratchpadContent = "";
    private long lastScratchpadSaveTime = 0;
    private static final long SCRATCHPAD_SAVE_DELAY_MS = 2000;  // Auto-save every 2 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_live_sprint);
        
        // ==================== Intent Data Extraction ====================
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        partnerName = getIntent().getStringExtra("PARTNER_NAME");
        sessionLength = getIntent().getStringExtra("SESSION_LENGTH");
        sprintId = getIntent().getStringExtra("SPRINT_ID");
        
        // Initialize API service
        apiService = ApiConfig.getApiService(this);
        handler = new Handler(Looper.getMainLooper());
        
        // ==================== View Initialization ====================
        initializeViews();
        
        // ==================== Setup Data ====================
        setupPartnerName();
        joinSprintSession();  // Join the sprint first
        loadSprintTodos();
        loadScratchpad();  // Load existing scratchpad content
        // Timer will be started after checking participants status
        setupButtonListeners();
        setupScratchpadListener();  // Setup auto-save for scratchpad
        startScratchpadSync();
    }
    
    // ==================== View Initialization ====================
    
    private void initializeViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvPartnerName = findViewById(R.id.tvPartnerName);
        tvFocusMode = findViewById(R.id.tvFocusMode);
        tvObjectivesTitle = findViewById(R.id.tvObjectivesTitle);
        tvCompletionStatus = findViewById(R.id.tvCompletionStatus);
        progressBar = findViewById(R.id.progressBarObjectives);
        todosContainer = findViewById(R.id.todosContainer);
        btnPause = findViewById(R.id.btnPause);
        btnEnd = findViewById(R.id.btnEnd);
        fabChat = findViewById(R.id.fabChat);
        loadingProgress = findViewById(R.id.loadingProgress);
        etScratchpad = findViewById(R.id.etScratchpad);
        
        // If some views don't exist, create them or handle gracefully
        if (tvObjectivesTitle == null) {
            tvObjectivesTitle = new TextView(this);
        }
        if (progressBar == null) {
            progressBar = new ProgressBar(this);
        }
        if (tvCompletionStatus == null) {
            tvCompletionStatus = new TextView(this);
        }
        if (todosContainer == null) {
            todosContainer = new LinearLayout(this);
            todosContainer.setOrientation(LinearLayout.VERTICAL);
        }
    }
    
    // ==================== Partner Name Setup ====================
    
    private void setupPartnerName() {
        if (tvPartnerName != null && partnerName != null && !partnerName.isEmpty()) {
            tvPartnerName.setText("Pairing with " + partnerName);
        }
    }
    
    // ==================== Join Sprint Session ====================
    
    private void joinSprintSession() {
        if (sprintId == null || sprintId.isEmpty()) {
            Toast.makeText(this, "Sprint ID not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Call<SprintSessionResponse> call = apiService.joinSprintSession(sprintId);
        call.enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                if (response.isSuccessful()) {
                    // Now check if all participants have joined
                    checkParticipantsStatus();
                } else {
                    Toast.makeText(LiveSprintActivity.this, "Failed to join sprint", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<SprintSessionResponse> call, Throwable t) {
                Toast.makeText(LiveSprintActivity.this, "Error joining sprint: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // ==================== Check Participants Status ====================
    
    private void checkParticipantsStatus() {
        if (sprintId == null || sprintId.isEmpty()) {
            return;
        }
        
        Call<Map<String, Object>> call = apiService.getParticipantsStatus(sprintId);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> status = response.body();
                    Boolean allJoined = (Boolean) status.get("all_joined");

                    Object sprintStatusObj = status.get("status");
                    String sprintStatus = sprintStatusObj != null ? sprintStatusObj.toString() : "setupped";

                    if ("end".equalsIgnoreCase(sprintStatus)) {
                        Toast.makeText(LiveSprintActivity.this, "Sprint has ended", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    if (allJoined != null && allJoined && "started".equalsIgnoreCase(sprintStatus)) {
                        allParticipantsJoined = true;
                        if (!timerStarted) {
                            startCountdownTimer();
                        }
                    } else {
                        if (allJoined == null || !allJoined) {
                            tvTimer.setText("Waiting for partner...");
                        } else {
                            tvTimer.setText("Waiting for host to start...");
                        }
                        handler.postDelayed(LiveSprintActivity.this::checkParticipantsStatus, 2000);
                    }
                } else {
                    Toast.makeText(LiveSprintActivity.this, "Failed to check participant status", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Retry on failure
                handler.postDelayed(LiveSprintActivity.this::checkParticipantsStatus, 3000);
            }
        });
    }
    
    // ==================== Load Sprint Todos ====================
    
    private void loadSprintTodos() {
        if (sprintId == null || sprintId.isEmpty()) {
            Toast.makeText(this, "Sprint ID not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
        
        Call<List<SprintTodoResponse>> call = apiService.getSprintTodos(sprintId);
        call.enqueue(new Callback<List<SprintTodoResponse>>() {
            @Override
            public void onResponse(Call<List<SprintTodoResponse>> call, Response<List<SprintTodoResponse>> response) {
                if (loadingProgress != null) {
                    loadingProgress.setVisibility(View.GONE);
                }
                
                if (response.isSuccessful() && response.body() != null) {
                    todos = response.body();
                    displayTodos();
                    updateProgressDisplay();
                } else {
                    Toast.makeText(LiveSprintActivity.this, "Failed to load objectives", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<List<SprintTodoResponse>> call, Throwable t) {
                if (loadingProgress != null) {
                    loadingProgress.setVisibility(View.GONE);
                }
                Toast.makeText(LiveSprintActivity.this, "Error loading objectives: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // ==================== Display Todos as Checkboxes ====================
    
    private void displayTodos() {
        if (todosContainer == null || todos == null) {
            return;
        }
        
        // Clear existing views
        todosContainer.removeAllViews();
        
        // Add each todo as a checkbox
        for (int i = 0; i < todos.size(); i++) {
            SprintTodoResponse todo = todos.get(i);
            CheckBox checkBox = createTodoCheckbox(todo, i);
            todosContainer.addView(checkBox);
        }
    }
    
    private CheckBox createTodoCheckbox(SprintTodoResponse todo, int position) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(todo.title);
        checkBox.setChecked(todo.isCompleted);
        checkBox.setTag(position);  // Store position for reference
        checkBox.setTextColor(todo.isCompleted ? 
            getResources().getColor(R.color.text_muted, null) : 
            getResources().getColor(R.color.text_white, null));
        checkBox.setButtonTintList(getResources().getColorStateList(R.color.brand_blue));
        
        // Set layout params
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 12, 0, 12);
        checkBox.setLayoutParams(params);
        
        // Handle checkbox toggle
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SprintTodoResponse currentTodo = todos.get(position);
            currentTodo.isCompleted = isChecked;
            updateTodoOnBackend(currentTodo, position);
            updateProgressDisplay();
            
            // Update text color based on completion status
            buttonView.setTextColor(isChecked ?
                getResources().getColor(R.color.text_muted, null) :
                getResources().getColor(R.color.text_white, null));
        });
        
        return checkBox;
    }
    
    // ==================== Update Todo on Backend ====================
    
    private void updateTodoOnBackend(SprintTodoResponse todo, int position) {
        if (todo.id == null || sprintId == null) {
            return;
        }
        
        SprintTodoUpdateRequest updateRequest = new SprintTodoUpdateRequest(todo.isCompleted);
        
        Call<SprintTodoResponse> call = apiService.updateSprintTodo(sprintId, todo.id, updateRequest);
        call.enqueue(new Callback<SprintTodoResponse>() {
            @Override
            public void onResponse(Call<SprintTodoResponse> call, Response<SprintTodoResponse> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(LiveSprintActivity.this, "Failed to update objective", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<SprintTodoResponse> call, Throwable t) {
                Toast.makeText(LiveSprintActivity.this, "Error updating objective: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // ==================== Scratchpad Management ====================
    
    private void loadScratchpad() {
        if (sprintId == null || sprintId.isEmpty()) {
            return;
        }
        
        Call<Map<String, Object>> call = apiService.getScratchpad(sprintId);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> responseBody = response.body();
                    Boolean exists = (Boolean) responseBody.get("exists");
                    
                    if (exists != null && exists) {
                        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                        if (data != null && etScratchpad != null) {
                            Object content = data.get("content");
                            if (content != null) {
                                String contentValue = content.toString();
                                isApplyingRemoteScratchpadUpdate = true;
                                etScratchpad.setText(contentValue);
                                isApplyingRemoteScratchpadUpdate = false;
                                lastSyncedScratchpadContent = contentValue;
                            }
                        }
                    }
                    scratchpadLoaded = true;
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Scratchpad load failure is non-critical
                scratchpadLoaded = true;
            }
        });
    }
    
    private void saveScratchpad() {
        if (sprintId == null || sprintId.isEmpty() || etScratchpad == null) {
            return;
        }
        
        String content = etScratchpad.getText().toString().trim();

        if (content.equals(lastSyncedScratchpadContent)) {
            return;
        }
        
        Map<String, String> scratchpadData = new java.util.HashMap<>();
        scratchpadData.put("content", content);
        
        Call<Map<String, Object>> call = apiService.updateScratchpad(sprintId, scratchpadData);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    lastSyncedScratchpadContent = content;
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Silently fail - not critical to user experience
            }
        });
    }
    
    private void setupScratchpadListener() {
        if (etScratchpad == null) {
            return;
        }
        
        etScratchpad.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isApplyingRemoteScratchpadUpdate) {
                    return;
                }
                // Auto-save scratchpad with delay
                handler.removeCallbacks(saveScratchpadRunnable);
                long timeSinceLastSave = System.currentTimeMillis() - lastScratchpadSaveTime;
                long delayMs = Math.max(SCRATCHPAD_SAVE_DELAY_MS - timeSinceLastSave, 0);
                handler.postDelayed(saveScratchpadRunnable, delayMs);
            }
        });
    }

    private void syncScratchpadFromServer() {
        if (!scratchpadLoaded || sprintId == null || sprintId.isEmpty() || etScratchpad == null) {
            return;
        }

        Call<Map<String, Object>> call = apiService.getScratchpad(sprintId);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                Map<String, Object> responseBody = response.body();
                Boolean exists = (Boolean) responseBody.get("exists");
                if (exists == null || !exists) {
                    return;
                }

                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data == null || data.get("content") == null) {
                    return;
                }

                String remoteContent = data.get("content").toString();
                String localContent = etScratchpad.getText().toString();

                if (!remoteContent.equals(localContent)) {
                    isApplyingRemoteScratchpadUpdate = true;
                    etScratchpad.setText(remoteContent);
                    etScratchpad.setSelection(remoteContent.length());
                    isApplyingRemoteScratchpadUpdate = false;
                }

                lastSyncedScratchpadContent = remoteContent;
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Keep UI responsive if sync fails intermittently.
            }
        });
    }

    private void startScratchpadSync() {
        handler.postDelayed(scratchpadSyncRunnable, 2000);
    }

    private final Runnable scratchpadSyncRunnable = new Runnable() {
        @Override
        public void run() {
            syncScratchpadFromServer();
            handler.postDelayed(this, 2000);
        }
    };
    
    private final Runnable saveScratchpadRunnable = new Runnable() {
        @Override
        public void run() {
            lastScratchpadSaveTime = System.currentTimeMillis();
            saveScratchpad();
        }
    };
    
    // ==================== Progress Display ====================
    
    private void updateProgressDisplay() {
        if (todos == null || todos.isEmpty()) {
            if (tvCompletionStatus != null) {
                tvCompletionStatus.setText("0/0 Completed");
            }
            if (progressBar != null) {
                progressBar.setProgress(0);
            }
            return;
        }
        
        int completed = 0;
        for (SprintTodoResponse todo : todos) {
            if (todo.isCompleted) {
                completed++;
            }
        }
        
        int total = todos.size();
        int progress = (int) ((completed * 100.0f) / total);
        
        if (tvCompletionStatus != null) {
            tvCompletionStatus.setText(completed + "/" + total + " Completed");
        }
        
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
    }
    
    // ==================== Countdown Timer ====================
    
    private void startCountdownTimer() {
        if (timerStarted) {
            return;  // Timer already started
        }
        
        timerStarted = true;
        
        // Parse session length to milliseconds
        int durationMinutes = 60;  // Default
        if (sessionLength != null && !sessionLength.isEmpty()) {
            try {
                durationMinutes = Integer.parseInt(sessionLength.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        
        long totalMillis = (long) durationMinutes * 60 * 1000;
        
        countDownTimer = new CountDownTimer(totalMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTimerDisplay(millisUntilFinished);
            }
            
            @Override
            public void onFinish() {
                if (tvTimer != null) {
                    tvTimer.setText("00:00");
                    Toast.makeText(LiveSprintActivity.this, "Sprint time is up!", Toast.LENGTH_LONG).show();
                }
            }
        };
        
        countDownTimer.start();
        Toast.makeText(this, "Both participants joined! Timer started.", Toast.LENGTH_SHORT).show();
    }
    
    private void updateTimerDisplay(long millisUntilFinished) {
        int minutes = (int) (millisUntilFinished / 1000) / 60;
        int seconds = (int) (millisUntilFinished / 1000) % 60;
        
        String timeString = String.format("%02d:%02d", minutes, seconds);
        
        if (tvTimer != null) {
            tvTimer.setText(timeString);
        }
    }
    
    // ==================== Button Listeners ====================
    
    private void setupButtonListeners() {
        if (btnPause != null) {
            btnPause.setOnClickListener(v -> pauseSprintSession());
        }
        
        if (btnEnd != null) {
            btnEnd.setOnClickListener(v -> endSprintSession());
        }
        
        if (fabChat != null) {
            fabChat.setOnClickListener(v -> openChat());
        }
    }
    
    private void pauseSprintSession() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
            Toast.makeText(this, "Sprint paused", Toast.LENGTH_SHORT).show();
            btnPause.setText("Resume");
            btnPause.setOnClickListener(v -> resumeSprintSession());
        }
    }
    
    private void resumeSprintSession() {
        // Get remaining time and resume
        String timeString = tvTimer.getText().toString();
        String[] timeParts = timeString.split(":");
        int minutes = Integer.parseInt(timeParts[0]);
        int seconds = Integer.parseInt(timeParts[1]);
        
        long remainingMillis = (long) (minutes * 60 + seconds) * 1000;
        startCountdownTimerFromRemaining(remainingMillis);
        
        Toast.makeText(this, "Sprint resumed", Toast.LENGTH_SHORT).show();
        btnPause.setText("Pause");
        btnPause.setOnClickListener(v -> pauseSprintSession());
    }
    
    private void startCountdownTimerFromRemaining(long remainingMillis) {
        countDownTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTimerDisplay(millisUntilFinished);
            }
            
            @Override
            public void onFinish() {
                if (tvTimer != null) {
                    tvTimer.setText("00:00");
                    Toast.makeText(LiveSprintActivity.this, "Sprint time is up!", Toast.LENGTH_LONG).show();
                }
            }
        };
        
        countDownTimer.start();
    }
    
    private void endSprintSession() {
        // Cancel timer
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        // Persist end state on backend
        updateSprintStatusToCompleted();
    }
    
    private void updateSprintStatusToCompleted() {
        if (sprintId == null) {
            finish();
            return;
        }

        Call<SprintSessionResponse> call = apiService.completeSprintSession(sprintId);
        call.enqueue(new Callback<SprintSessionResponse>() {
            @Override
            public void onResponse(Call<SprintSessionResponse> call, Response<SprintSessionResponse> response) {
                Toast.makeText(LiveSprintActivity.this, "Sprint session ended", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(Call<SprintSessionResponse> call, Throwable t) {
                Toast.makeText(LiveSprintActivity.this, "Error ending sprint: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    
    private void openChat() {
        if (sprintId == null || sprintId.isEmpty()) {
            Toast.makeText(this, "Sprint ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent chatIntent = new Intent(this, ChatDetailActivity.class);
        chatIntent.putExtra("SPRINT_ID", sprintId);
        chatIntent.putExtra("PARTNER_NAME", partnerName != null ? partnerName : "Partner");
        startActivity(chatIntent);
    }
    
    // ==================== Lifecycle ====================
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (handler != null) {
            handler.removeCallbacks(saveScratchpadRunnable);
            handler.removeCallbacks(scratchpadSyncRunnable);
        }
    }
}