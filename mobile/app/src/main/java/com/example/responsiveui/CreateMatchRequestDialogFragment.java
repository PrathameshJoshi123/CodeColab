package com.example.responsiveui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.models.MatchRequestCreateRequest;
import com.example.responsiveui.api.models.MatchRequestResponse;
import com.example.responsiveui.api.models.SkillCreateRequest;
import com.example.responsiveui.api.models.SkillResponse;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ==================== CreateMatchRequestDialogFragment ====================
 * Bottom sheet dialog for users to create new match requests
 * 
 * Features:
 * - Session type selection (dropdown)
 * - Message input (what they're looking for)
 * - Required skills multi-select
 * - Create and cancel actions
 */
public class CreateMatchRequestDialogFragment extends BottomSheetDialogFragment {

    private Spinner spinnerSessionType;
    private EditText editMessage;
    private EditText editSkillSearch;
    private ChipGroup chipGroupSkills;
    private Button btnCreate;
    private Button btnCancel;
    private ImageButton btnCloseDialog;
    private Button btnSelectDate;
    private Button btnSelectTime;
    private TextView textSelectedDateTime;
    
    private CodeCollabApiService apiService;
    private CreateMatchListener listener;
    
    private String[] sessionTypes = {"Debug", "Interview", "Hackathon", "Learning"};
    private List<String> selectedSkills = new ArrayList<>();
    private List<SkillResponse> allSkills = new ArrayList<>();
    private List<SkillResponse> filteredSkills = new ArrayList<>();
    
    // Date and time variables
    private Calendar selectedDateTime = null;
    
    // ==================== Interface for Callbacks ====================
    
    public interface CreateMatchListener {
        void onMatchCreated();
    }
    
    public void setListener(CreateMatchListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.BottomSheetStyle);
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetStyle;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull android.view.LayoutInflater inflater,
            @Nullable android.view.ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_create_match_request, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI components
        spinnerSessionType = view.findViewById(R.id.spinnerSessionType);
        editMessage = view.findViewById(R.id.editMessage);
        editSkillSearch = view.findViewById(R.id.editSkillSearch);
        chipGroupSkills = view.findViewById(R.id.chipGroupSkills);
        btnCreate = view.findViewById(R.id.btnCreate);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnCloseDialog = view.findViewById(R.id.btnCloseDialog);
        btnSelectDate = view.findViewById(R.id.btnSelectDate);
        btnSelectTime = view.findViewById(R.id.btnSelectTime);
        textSelectedDateTime = view.findViewById(R.id.textSelectedDateTime);
        
        // Initialize API service
        if (getContext() != null) {
            apiService = ApiConfig.getApiService(getContext());
        }
        
        // Setup UI elements
        setupSessionTypeSpinner();
        setupSkillsSearch();
        setupButtonListeners();
        setupDateTimeListeners();
        
        // Load all skills from API
        loadSkillsFromAPI();
    }

    // ==================== Session Type Spinner Setup ====================
    
    private void setupSessionTypeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                sessionTypes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSessionType.setAdapter(adapter);
        
        spinnerSessionType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Handle selection if needed
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ==================== Load Skills from API ====================
    
    private void loadSkillsFromAPI() {
        Call<List<SkillResponse>> call = apiService.getAllSkills();
        call.enqueue(new Callback<List<SkillResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SkillResponse>> call, 
                                 @NonNull Response<List<SkillResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allSkills = response.body();
                    filteredSkills = new ArrayList<>(allSkills);
                    displaySkillChips(filteredSkills);
                } else {
                    Toast.makeText(getContext(), "Failed to load skills", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<SkillResponse>> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "Error loading skills: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== Skills Search Setup ====================
    
    private void setupSkillsSearch() {
        editSkillSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndDisplaySkills(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterAndDisplaySkills(String query) {
        filteredSkills.clear();
        
        if (query.isEmpty()) {
            filteredSkills.addAll(allSkills);
        } else {
            for (SkillResponse skill : allSkills) {
                if (skill.name.toLowerCase().contains(query.toLowerCase())) {
                    filteredSkills.add(skill);
                }
            }
        }
        
        displaySkillChips(filteredSkills);
        
        // If no results and query is not empty, show "Add New Skill" option
        if (filteredSkills.isEmpty() && !query.isEmpty()) {
            showAddNewSkillOption(query);
        }
    }

    private void displaySkillChips(List<SkillResponse> skills) {
        chipGroupSkills.removeAllViews();
        
        for (SkillResponse skill : skills) {
            Chip chip = new Chip(requireContext());
            chip.setText(skill.name);
            chip.setCheckable(true);
            chip.setChecked(selectedSkills.contains(skill.name));
            
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedSkills.contains(skill.name)) {
                        selectedSkills.add(skill.name);
                    }
                } else {
                    selectedSkills.remove(skill.name);
                }
            });
            chipGroupSkills.addView(chip);
        }
    }

    private void showAddNewSkillOption(String skillName) {
        Chip chip = new Chip(requireContext());
        chip.setText("+ Add '" + skillName + "'");
        chip.setCheckable(false);
        chip.setCloseIconVisible(false);
        
        chip.setOnClickListener(v -> createAndAddSkill(skillName));
        chipGroupSkills.addView(chip);
    }

    // ==================== Create New Skill ====================
    
    private void createAndAddSkill(String skillName) {
        if (apiService == null) return;
        
        SkillCreateRequest request = new SkillCreateRequest(skillName, "custom");
        
        Call<SkillResponse> call = apiService.createSkill(request);
        call.enqueue(new Callback<SkillResponse>() {
            @Override
            public void onResponse(@NonNull Call<SkillResponse> call, 
                                 @NonNull Response<SkillResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SkillResponse newSkill = response.body();
                    
                    // Add to our lists
                    if (!allSkills.contains(newSkill)) {
                        allSkills.add(newSkill);
                    }
                    selectedSkills.add(newSkill.name);
                    
                    // Clear search and reload
                    editSkillSearch.setText("");
                    Toast.makeText(getContext(), "Skill added! ✓", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Failed to add skill", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<SkillResponse> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== Button Listeners ====================
    
    private void setupButtonListeners() {
        btnCreate.setOnClickListener(v -> handleCreateRequest());
        btnCancel.setOnClickListener(v -> dismiss());
        btnCloseDialog.setOnClickListener(v -> dismiss());
    }

    // ==================== Date/Time Picker Setup ====================
    
    private void setupDateTimeListeners() {
        btnSelectDate.setOnClickListener(v -> showDatePicker());
        btnSelectTime.setOnClickListener(v -> showTimePicker());
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Sprint Date")
                .setSelection(System.currentTimeMillis())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (selectedDateTime == null) {
                selectedDateTime = Calendar.getInstance();
            }
            
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.setTimeInMillis(selection);
            
            selectedDateTime.set(Calendar.YEAR, selectedDate.get(Calendar.YEAR));
            selectedDateTime.set(Calendar.MONTH, selectedDate.get(Calendar.MONTH));
            selectedDateTime.set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH));
            
            updateDateTimeDisplay();
        });

        datePicker.show(getChildFragmentManager(), "DATE_PICKER");
    }

    private void showTimePicker() {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                .setMinute(Calendar.getInstance().get(Calendar.MINUTE))
                .setTitleText("Select Sprint Time")
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            if (selectedDateTime == null) {
                selectedDateTime = Calendar.getInstance();
            }
            
            selectedDateTime.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
            selectedDateTime.set(Calendar.MINUTE, timePicker.getMinute());
            
            updateDateTimeDisplay();
        });

        timePicker.show(getChildFragmentManager(), "TIME_PICKER");
    }

    private void updateDateTimeDisplay() {
        if (selectedDateTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            textSelectedDateTime.setText(sdf.format(selectedDateTime.getTime()));
            textSelectedDateTime.setVisibility(View.VISIBLE);
        }
    }

    // ==================== Create Match Request ====================
    
    private void handleCreateRequest() {
        String sessionType = spinnerSessionType.getSelectedItem().toString();
        String message = editMessage.getText().toString().trim();
        
        if (message.isEmpty()) {
            editMessage.setError("Message is required");
            return;
        }
        
        if (selectedSkills.isEmpty()) {
            Toast.makeText(getContext(), "Please select at least one skill", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedDateTime == null) {
            Toast.makeText(getContext(), "Please select a date and time", Toast.LENGTH_SHORT).show();
            return;
        }

        // Format date for API (ISO 8601)
        SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formattedDate = apiFormat.format(selectedDateTime.getTime());
        
        MatchRequestCreateRequest request = new MatchRequestCreateRequest(
                sessionType,
                message,
                selectedSkills,
                formattedDate
        );
        
        btnCreate.setEnabled(false);
        btnCreate.setText("Creating...");
        
        Call<MatchRequestResponse> call = apiService.createMatchRequest(request);
        call.enqueue(new Callback<MatchRequestResponse>() {
            @Override
            public void onResponse(@NonNull Call<MatchRequestResponse> call, 
                                 @NonNull Response<MatchRequestResponse> response) {
                btnCreate.setEnabled(true);
                btnCreate.setText("Create Request");
                
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Match request created!", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onMatchCreated();
                    }
                    dismiss();
                } else {
                    Toast.makeText(getContext(), "Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<MatchRequestResponse> call, @NonNull Throwable t) {
                btnCreate.setEnabled(true);
                btnCreate.setText("Create Request");
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
