package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.UserProfileResponse;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private FirebaseAuth mAuth;
    private CodeCollabApiService apiService;

    // UI Components
    private TextView tvUserName;
    private TextView tvUserEmail;
    private TextView tvUserLevel;
    private TextView tvUserXP;
    private TextView btnEditProfile;
    private LinearLayout llEditProfile;
    private TextView btnChangePassword;
    private Button btnLogout;
    private LinearLayout llLoadingContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        apiService = ApiConfig.getApiService(getContext());

        // ==================== Initialize Views ====================
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvUserLevel = view.findViewById(R.id.tvUserLevel);
        tvUserXP = view.findViewById(R.id.tvUserXP);
        llEditProfile = view.findViewById(R.id.llEditProfile);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnLogout = view.findViewById(R.id.btnLogout);
        llLoadingContainer = view.findViewById(R.id.llLoadingContainer);

        // ==================== Load User Profile ====================
        loadUserProfile();

        // ==================== Edit Profile Button ====================
        if (llEditProfile != null) {
            llEditProfile.setOnClickListener(v -> openEditProfileActivity());
        }
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> openEditProfileActivity());
        }

        // ==================== Change Password Button ====================
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Change password feature coming soon", Toast.LENGTH_SHORT).show();
            });
        }

        // ==================== Logout Button ====================
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> performLogout());
        }

        return view;
    }

    // ==================== Load User Profile ====================
    
    private void loadUserProfile() {
        if (llLoadingContainer != null) {
            llLoadingContainer.setVisibility(View.VISIBLE);
        }

        apiService.getCurrentUserProfile().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfileResponse profile = response.body();
                    displayUserProfile(profile);
                    
                    if (llLoadingContainer != null) {
                        llLoadingContainer.setVisibility(View.GONE);
                    }
                } else {
                    handleProfileLoadError("Failed to load profile: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Log.e(TAG, "Profile load error", t);
                handleProfileLoadError("Network error: " + t.getMessage());
            }
        });
    }

    private void displayUserProfile(UserProfileResponse profile) {
        // Display user name with personalization
        if (tvUserName != null) {
            String displayName = profile.fullName != null && !profile.fullName.isEmpty() 
                ? profile.fullName 
                : "Profile User";
            tvUserName.setText(displayName);
        }

        // Display email
        if (tvUserEmail != null && mAuth.getCurrentUser() != null) {
            tvUserEmail.setText("Email: " + mAuth.getCurrentUser().getEmail());
        }

        // Display level info
        if (tvUserLevel != null) {
            tvUserLevel.setText("Level " + profile.level);
        }

        // Display XP points
        if (tvUserXP != null) {
            tvUserXP.setText(profile.xpPoints + " XP");
        }
    }

    private void handleProfileLoadError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (llLoadingContainer != null) {
            llLoadingContainer.setVisibility(View.GONE);
        }
        Toast.makeText(getContext(), "Failed to load profile", Toast.LENGTH_SHORT).show();
        
        // Show fallback email display
        if (tvUserEmail != null && mAuth.getCurrentUser() != null) {
            tvUserEmail.setText("Logged in as: " + mAuth.getCurrentUser().getEmail());
        }
    }

    // ==================== Edit Profile ====================
    
    private void openEditProfileActivity() {
        Intent intent = new Intent(getActivity(), EditProfileActivity.class);
        startActivity(intent);
    }

    // ==================== Logout ====================
    
    private void performLogout() {
        try {
            Log.d(TAG, "Starting logout process");
            
            // Call backend logout endpoint for logging/cleanup
            apiService.logout().enqueue(new Callback<java.util.Map<String, Object>>() {
                @Override
                public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                    Log.d(TAG, "Backend logout endpoint called successfully");
                    completeLogout();
                }
                
                @Override
                public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                    Log.w(TAG, "Backend logout endpoint failed, proceeding with local logout", t);
                    completeLogout();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initiating logout", e);
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void completeLogout() {
        try {
            Log.d(TAG, "Completing local logout");
            
            // Sign out from Firebase
            mAuth.signOut();
            Log.d(TAG, "Firebase sign out completed");
            
            // Clear JWT token using TokenManager (IMPORTANT!)
            TokenManager.clearToken();
            Log.d(TAG, "JWT token cleared from TokenManager");
            
            // Clear SharedPreferences
            android.content.SharedPreferences sharedPreferences = getContext().getSharedPreferences("MyAppPrefs", android.content.Context.MODE_PRIVATE);
            sharedPreferences.edit().clear().apply();
            Log.d(TAG, "SharedPreferences cleared");
            
            // Clear app cache
            try {
                File cacheDir = getContext().getCacheDir();
                if (cacheDir.isDirectory()) {
                    deleteDir(cacheDir);
                }
                Log.d(TAG, "App cache cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing cache", e);
            }
            
            Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Logout completed, navigating to LoginActivity");
            
            // Navigate back to LoginActivity
            if (getActivity() != null) {
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                getActivity().finish();
            } else {
                Log.e(TAG, "Activity is null, cannot navigate");
                Toast.makeText(getContext(), "Error: Activity not available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during logout completion", e);
            Toast.makeText(getContext(), "Error during logout: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // Helper method to delete directory recursively
    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : children) {
                boolean success = deleteDir(new File(dir, child));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }
}
