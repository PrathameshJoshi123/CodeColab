package com.example.responsiveui;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.models.MatchRequestCreateRequest;
import com.example.responsiveui.api.models.MatchRequestResponse;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

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
    private ChipGroup chipGroupSkills;
    private Button btnCreate;
    private Button btnCancel;
    private ImageButton btnCloseDialog;
    
    private CodeCollabApiService apiService;
    private CreateMatchListener listener;
    
    private String[] sessionTypes = {"Debug", "Interview", "Hackathon", "Learning"};
    private List<String> selectedSkills = new ArrayList<>();
    
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
        chipGroupSkills = view.findViewById(R.id.chipGroupSkills);
        btnCreate = view.findViewById(R.id.btnCreate);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnCloseDialog = view.findViewById(R.id.btnCloseDialog);
        
        // Initialize API service
        if (getContext() != null) {
            apiService = ApiConfig.getApiService(getContext());
        }
        
        // Setup UI elements
        setupSessionTypeSpinner();
        setupSkillsChips();
        setupButtonListeners();
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

    // ==================== Skills Chips Setup ====================
    
    private void setupSkillsChips() {
        String[] skills = {"Python", "Java", "JavaScript", "Kotlin", "Android", "React", 
                          "Node.js", "Firebase", "SQL", "Git", "Docker", "AWS"};
        
        for (String skill : skills) {
            Chip chip = new Chip(requireContext());
            chip.setText(skill);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedSkills.contains(skill)) {
                        selectedSkills.add(skill);
                    }
                } else {
                    selectedSkills.remove(skill);
                }
            });
            chipGroupSkills.addView(chip);
        }
    }

    // ==================== Button Listeners ====================
    
    private void setupButtonListeners() {
        btnCreate.setOnClickListener(v -> handleCreateRequest());
        btnCancel.setOnClickListener(v -> dismiss());
        btnCloseDialog.setOnClickListener(v -> dismiss());
    }

    // ==================== Create Request Handler ====================
    
    private void handleCreateRequest() {
        // Validate inputs
        String message = editMessage.getText().toString().trim();
        String sessionType = (String) spinnerSessionType.getSelectedItem();
        
        if (message.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (sessionType == null || sessionType.isEmpty()) {
            Toast.makeText(getContext(), "Please select a session type", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create request
        MatchRequestCreateRequest request = new MatchRequestCreateRequest(
                sessionType,
                message,
                selectedSkills.isEmpty() ? null : selectedSkills
        );
        
        btnCreate.setEnabled(false);
        btnCreate.setText("Creating...");
        
        // Make API call
        Call<MatchRequestResponse> call = apiService.createMatchRequest(request);
        call.enqueue(new Callback<MatchRequestResponse>() {
            @Override
            public void onResponse(@NonNull Call<MatchRequestResponse> call, 
                                 @NonNull Response<MatchRequestResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Match request created! 🎉", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onMatchCreated();
                    }
                    dismiss();
                } else {
                    resetButton();
                    Toast.makeText(getContext(), "Failed to create match request", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<MatchRequestResponse> call, @NonNull Throwable t) {
                resetButton();
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetButton() {
        btnCreate.setEnabled(true);
        btnCreate.setText("Create");
    }
}
