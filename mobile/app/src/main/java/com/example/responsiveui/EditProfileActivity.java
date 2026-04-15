package com.example.responsiveui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.models.ProfileUpdateRequest;
import com.example.responsiveui.api.models.UserProfileResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private CodeCollabApiService apiService;

    // ==================== UI Components ====================
    private EditText etFullName;
    private EditText etBio;
    private EditText etCollege;
    private EditText etCity;
    private EditText etGithubUsername;
    private EditText etLinkedinUrl;
    private Button btnSaveProfile;
    private Button btnCancel;
    private LinearLayout llLoadingContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        apiService = ApiConfig.getApiService(this);

        // ==================== Initialize Views ====================
        etFullName = findViewById(R.id.etFullName);
        etBio = findViewById(R.id.etBio);
        etCollege = findViewById(R.id.etCollege);
        etCity = findViewById(R.id.etCity);
        etGithubUsername = findViewById(R.id.etGithubUsername);
        etLinkedinUrl = findViewById(R.id.etLinkedinUrl);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnCancel = findViewById(R.id.btnCancel);
        llLoadingContainer = findViewById(R.id.llLoadingContainer);

        // ==================== Load Current Profile ====================
        loadCurrentProfile();

        // ==================== Save Button ====================
        if (btnSaveProfile != null) {
            btnSaveProfile.setOnClickListener(v -> saveProfile());
        }

        // ==================== Cancel Button ====================
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }
    }

    // ==================== Load Current Profile ====================
    
    private void loadCurrentProfile() {
        if (llLoadingContainer != null) {
            llLoadingContainer.setVisibility(LinearLayout.VISIBLE);
        }

        apiService.getCurrentUserProfile().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfileResponse profile = response.body();
                    populateFields(profile);
                    
                    if (llLoadingContainer != null) {
                        llLoadingContainer.setVisibility(LinearLayout.GONE);
                    }
                } else {
                    handleLoadError("Failed to load profile");
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Log.e(TAG, "Profile load error", t);
                handleLoadError("Network error: " + t.getMessage());
            }
        });
    }

    private void populateFields(UserProfileResponse profile) {
        if (etFullName != null && profile.fullName != null) {
            etFullName.setText(profile.fullName);
        }
        if (etBio != null && profile.bio != null) {
            etBio.setText(profile.bio);
        }
        if (etCollege != null && profile.college != null) {
            etCollege.setText(profile.college);
        }
        if (etCity != null && profile.city != null) {
            etCity.setText(profile.city);
        }
        if (etGithubUsername != null && profile.githubUsername != null) {
            etGithubUsername.setText(profile.githubUsername);
        }
        if (etLinkedinUrl != null && profile.linkedinUrl != null) {
            etLinkedinUrl.setText(profile.linkedinUrl);
        }
    }

    private void handleLoadError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (llLoadingContainer != null) {
            llLoadingContainer.setVisibility(LinearLayout.GONE);
        }
        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
    }

    // ==================== Save Profile ====================
    
    private void saveProfile() {
        if (!validateFields()) {
            Toast.makeText(this, "Please fill in required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (llLoadingContainer != null) {
            llLoadingContainer.setVisibility(LinearLayout.VISIBLE);
        }

        // Create update request
        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.fullName = etFullName.getText().toString().trim();
        updateRequest.bio = etBio.getText().toString().trim();
        updateRequest.college = etCollege.getText().toString().trim();
        updateRequest.city = etCity.getText().toString().trim();
        updateRequest.githubUsername = etGithubUsername.getText().toString().trim();
        updateRequest.linkedinUrl = etLinkedinUrl.getText().toString().trim();

        // Send update request
        apiService.updateCurrentUserProfile(updateRequest).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (llLoadingContainer != null) {
                    llLoadingContainer.setVisibility(LinearLayout.GONE);
                }

                if (response.isSuccessful()) {
                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(EditProfileActivity.this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Update failed with code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                if (llLoadingContainer != null) {
                    llLoadingContainer.setVisibility(LinearLayout.GONE);
                }
                Log.e(TAG, "Profile update error", t);
                Toast.makeText(EditProfileActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validateFields() {
        // Full name is required
        if (etFullName.getText().toString().trim().isEmpty()) {
            etFullName.setError("Full name is required");
            return false;
        }
        return true;
    }
}
