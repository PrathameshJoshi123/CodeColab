package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.SkillResponse;
import com.example.responsiveui.api.models.UserSkillUpdateRequest;
import com.example.responsiveui.api.models.UserSkillResponse;
import com.example.responsiveui.api.models.SkillCreateRequest;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Build your Tech Profile Activity
 * Allows users to select multiple skills for their profile
 * Skills are fetched from backend and grouped by category
 * Saves selected skills with proficiency levels to the backend
 */
public class SkillSelectionActivity extends AppCompatActivity {

    private static final String TAG = "SkillSelection";
    private static final int MIN_SKILLS_REQUIRED = 3;
    
    private Button btnContinue;
    private String userEmail;
    private String userId;
    private EditText searchSkills;
    private LinearLayout skillsContainer;
    
    private CodeCollabApiService apiService;
    private List<SkillResponse> allSkills = new ArrayList<>();
    private Map<String, List<SkillResponse>> skillsByCategory = new HashMap<>();
    private Map<String, String> selectedSkills = new HashMap<>(); // skillId -> proficiency_level

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_skill_selection);

        // ==================== Initialize ====================
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        userId = TokenManager.getUserId();
        apiService = ApiConfig.getApiService(this);
        
        initializeViews();
        setupListeners();
        loadSkillsFromBackend();
        loadUserExistingSkills();
    }

    // ==================== View Initialization ====================
    
    private void initializeViews() {
        btnContinue = findViewById(R.id.btnContinue);
        searchSkills = findViewById(R.id.etSearchSkills);
        skillsContainer = findViewById(R.id.skillsContainer);
    }
    
    private void setupListeners() {
        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        // Skip button
        findViewById(R.id.tvSkip).setOnClickListener(v -> goToMainContainer());
        
        // Continue button
        btnContinue.setOnClickListener(v -> saveSkillsAndContinue());
        
        // Search functionality
        searchSkills.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSkills(s.toString());
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // ==================== API Methods ====================
    
    /**
     * Fetch all available skills from backend
     */
    private void loadSkillsFromBackend() {
        apiService.getAllSkills().enqueue(new Callback<List<SkillResponse>>() {
            @Override
            public void onResponse(Call<List<SkillResponse>> call, Response<List<SkillResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allSkills = response.body();
                    organizeSkillsByCategory();
                    displaySkills(allSkills);
                    Log.d(TAG, "Loaded " + allSkills.size() + " skills from backend");
                } else {
                    Log.e(TAG, "Failed to load skills: " + response.code());
                    Toast.makeText(SkillSelectionActivity.this, "Failed to load skills", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<List<SkillResponse>> call, Throwable t) {
                Log.e(TAG, "Error loading skills", t);
                Toast.makeText(SkillSelectionActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Load user's already-selected skills (if any exist)
     */
    private void loadUserExistingSkills() {
        if (userId == null) return;
        
        apiService.getUserSkills(userId).enqueue(new Callback<List<UserSkillResponse>>() {
            @Override
            public void onResponse(Call<List<UserSkillResponse>> call, Response<List<UserSkillResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (UserSkillResponse skill : response.body()) {
                        selectedSkills.put(skill.skillId, skill.proficiencyLevel);
                    }
                    // Update UI to show selected skills
                    updateSelectedSkillsUI();
                    Log.d(TAG, "Loaded existing " + selectedSkills.size() + " skills");
                }
            }
            
            @Override
            public void onFailure(Call<List<UserSkillResponse>> call, Throwable t) {
                Log.d(TAG, "No existing skills found (new user)");
            }
        });
    }
    
    /**
     * Save selected skills to backend
     */
    private void saveSkillsToBackend() {
        if (selectedSkills.size() < MIN_SKILLS_REQUIRED) {
            Toast.makeText(this, "Please select at least " + MIN_SKILLS_REQUIRED + " skills", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Convert selectedSkills map to API format
        List<UserSkillUpdateRequest.SkillUpdate> skillsList = new ArrayList<>();
        for (Map.Entry<String, String> entry : selectedSkills.entrySet()) {
            skillsList.add(new UserSkillUpdateRequest.SkillUpdate(entry.getKey(), entry.getValue()));
        }
        
        UserSkillUpdateRequest request = new UserSkillUpdateRequest();
        request.skills = skillsList;
        
        apiService.updateUserSkills(userId, request).enqueue(new Callback<List<UserSkillResponse>>() {
            @Override
            public void onResponse(Call<List<UserSkillResponse>> call, Response<List<UserSkillResponse>> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Skills saved successfully");
                    Toast.makeText(SkillSelectionActivity.this, "Skills saved!", Toast.LENGTH_SHORT).show();
                    goToMainContainer();
                } else {
                    Log.e(TAG, "Failed to save skills: " + response.code());
                    Toast.makeText(SkillSelectionActivity.this, "Failed to save skills", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<List<UserSkillResponse>> call, Throwable t) {
                Log.e(TAG, "Error saving skills", t);
                Toast.makeText(SkillSelectionActivity.this, "Error saving skills: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== UI Methods ====================
    
    /**
     * Organize skills by category for better display
     */
    private void organizeSkillsByCategory() {
        skillsByCategory.clear();
        
        for (SkillResponse skill : allSkills) {
            String category = skill.category != null ? skill.category : "Other";
            skillsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(skill);
        }
        
        Log.d(TAG, "Organized skills into " + skillsByCategory.size() + " categories");
    }
    
    /**
     * Display skills grouped by category
     */
    private void displaySkills(List<SkillResponse> skills) {
        skillsContainer.removeAllViews();
        
        for (Map.Entry<String, List<SkillResponse>> entry : skillsByCategory.entrySet()) {
            String category = entry.getKey();
            List<SkillResponse> categorySkills = new ArrayList<>(entry.getValue());
            
            // Filter if search is active
            if (searchSkills != null && !searchSkills.getText().toString().isEmpty()) {
                String searchTerm = searchSkills.getText().toString().toLowerCase();
                categorySkills = new ArrayList<>();
                for (SkillResponse skill : entry.getValue()) {
                    if (skill.name.toLowerCase().contains(searchTerm)) {
                        categorySkills.add(skill);
                    }
                }
            }
            
            if (categorySkills.isEmpty()) continue;
            
            // Add category header
            TextView categoryHeader = new TextView(this);
            categoryHeader.setText(category.toUpperCase());
            categoryHeader.setTextColor(getResources().getColor(R.color.text_muted));
            categoryHeader.setTextSize(12);
            categoryHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            headerParams.setMargins(0, 24, 0, 12);
            categoryHeader.setLayoutParams(headerParams);
            skillsContainer.addView(categoryHeader);
            
            // Add skills as chips
            ChipGroup chipGroup = new ChipGroup(this);
            chipGroup.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            
            for (SkillResponse skill : categorySkills) {
                Chip chip = new Chip(this);
                chip.setText(skill.name);
                chip.setCheckable(true);
                chip.setChecked(selectedSkills.containsKey(skill.id));
                
                // Handle chip selection
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        // Show proficiency level dialog
                        showProficiencyDialog(skill.id, skill.name);
                        chip.setChecked(true); // Keep checked
                    } else {
                        selectedSkills.remove(skill.id);
                    }
                });
                
                chipGroup.addView(chip);
            }
            
            skillsContainer.addView(chipGroup);
        }
    }
    
    private void filterSkills(String query) {
        List<SkillResponse> filtered = new ArrayList<>();
        
        if (query.isEmpty()) {
            filtered = allSkills;
            displaySkills(filtered);
        } else {
            String lowerQuery = query.toLowerCase();
            for (SkillResponse skill : allSkills) {
                if (skill.name.toLowerCase().contains(lowerQuery) ||
                    skill.category.toLowerCase().contains(lowerQuery)) {
                    filtered.add(skill);
                }
            }
            
            // Show matching skills and "Add new skill" option if no matches
            if (filtered.isEmpty()) {
                displayNoSkillsFoundUI(query);
            } else {
                displaySkills(filtered);
            }
        }
    }
    
    /**
     * Show UI when no skills match the search query
     * Provides option to create a new skill
     */
    private void displayNoSkillsFoundUI(String searchQuery) {
        skillsContainer.removeAllViews();
        
        // No results message
        TextView noResultsMsg = new TextView(this);
        noResultsMsg.setText("No skills found for \"" + searchQuery + "\"");
        noResultsMsg.setTextColor(getResources().getColor(R.color.text_muted));
        noResultsMsg.setTextSize(14);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgParams.setMargins(0, 24, 0, 12);
        noResultsMsg.setLayoutParams(msgParams);
        skillsContainer.addView(noResultsMsg);
        
        // Add new skill button
        Button btnAddSkill = new Button(this);
        btnAddSkill.setText("+ Add \"" + searchQuery + "\" as new skill");
        btnAddSkill.setTextColor(getResources().getColor(R.color.text_white));
        btnAddSkill.setBackgroundColor(getResources().getColor(R.color.brand_blue));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 12, 0, 0);
        btnAddSkill.setLayoutParams(btnParams);
        
        btnAddSkill.setOnClickListener(v -> showAddNewSkillDialog(searchQuery));
        skillsContainer.addView(btnAddSkill);
    }
    
    /**
     * Show dialog to create a new skill
     */
    private void showAddNewSkillDialog(String skillNameSuggestion) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog);
        builder.setTitle("Add New Skill");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        
        // Skill name input
        EditText etSkillName = new EditText(this);
        etSkillName.setHint("Skill name");
        etSkillName.setText(skillNameSuggestion);
        etSkillName.setTextColor(getResources().getColor(R.color.text_white));
        etSkillName.setHintTextColor(getResources().getColor(R.color.text_muted));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, 0, 0, 12);
        etSkillName.setLayoutParams(nameParams);
        layout.addView(etSkillName);
        
        // Category spinner/dropdown
        Spinner spinnerCategory = new Spinner(this);
        String[] categories = {"Frontend Development", "Backend & Infrastructure", "Mobile Development", 
                              "Data Science", "DevOps", "QA & Testing", "Design", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, categories
        );
        spinnerCategory.setAdapter(adapter);
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        categoryParams.setMargins(0, 0, 0, 12);
        spinnerCategory.setLayoutParams(categoryParams);
        layout.addView(spinnerCategory);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Add Skill", (dialog, which) -> {
            String skillName = etSkillName.getText().toString().trim();
            String category = spinnerCategory.getSelectedItem().toString();
            
            if (skillName.isEmpty()) {
                Toast.makeText(SkillSelectionActivity.this, "Please enter a skill name", Toast.LENGTH_SHORT).show();
                return;
            }
            
            createNewSkillOnBackend(skillName, category);
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    /**
     * Create new skill on backend
     */
    private void createNewSkillOnBackend(String skillName, String category) {
        SkillCreateRequest request = new SkillCreateRequest();
        request.name = skillName;
        request.category = category;
        
        apiService.createSkill(request).enqueue(new Callback<SkillResponse>() {
            @Override
            public void onResponse(Call<SkillResponse> call, Response<SkillResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SkillResponse newSkill = response.body();
                    
                    // Add to allSkills list
                    allSkills.add(newSkill);
                    organizeSkillsByCategory();
                    
                    // Auto-select the new skill with beginner proficiency
                    selectedSkills.put(newSkill.id, "beginner");
                    updateSelectedSkillsUI();
                    
                    // Refresh display
                    displaySkills(allSkills);
                    clearSearchBar();
                    
                    Log.d(TAG, "New skill created: " + skillName);
                    Toast.makeText(SkillSelectionActivity.this, 
                        "Skill \"" + skillName + "\" added and selected!", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Failed to create skill: " + response.code());
                    Toast.makeText(SkillSelectionActivity.this, "Failed to add skill", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<SkillResponse> call, Throwable t) {
                Log.e(TAG, "Error creating skill", t);
                Toast.makeText(SkillSelectionActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Clear search bar
     */
    private void clearSearchBar() {
        searchSkills.setText("");
    }
    
    /**
     * Show proficiency level selection dialog
     */
    private void showProficiencyDialog(String skillId, String skillName) {
        String[] proficiencyLevels = {"Beginner", "Intermediate", "Advanced", "Expert"};
        
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Select proficiency level for " + skillName)
            .setSingleChoiceItems(proficiencyLevels, 0, (dialog, which) -> {
                String selectedLevel = proficiencyLevels[which].toLowerCase();
                selectedSkills.put(skillId, selectedLevel);
                dialog.dismiss();
                updateSelectedSkillsUI();
                Log.d(TAG, "Selected " + skillName + " at " + selectedLevel + " level");
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                selectedSkills.remove(skillId);
                dialog.dismiss();
            })
            .show();
    }
    
    /**
     * Update UI to reflect currently selected skills
     */
    private void updateSelectedSkillsUI() {
        int selectedCount = selectedSkills.size();
        if (selectedCount > 0) {
            btnContinue.setText("Continue (" + selectedCount + " selected)");
        } else {
            btnContinue.setText("Continue");
        }
    }

    // ==================== Navigation ====================
    
    /**
     * Save skills and navigate to main container
     */
    private void saveSkillsAndContinue() {
        saveSkillsToBackend();
    }
    
    /**
     * Navigate to main container activity
     */
    private void goToMainContainer() {
        Intent intent = new Intent(SkillSelectionActivity.this, MainContainerActivity.class);
        intent.putExtra("USER_EMAIL", userEmail);
        intent.putExtra("SKILL_COUNT", selectedSkills.size() + "+");
        startActivity(intent);
        finish();
    }
}