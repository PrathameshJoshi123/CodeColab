package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.AuthResponseModel;
import com.example.responsiveui.api.models.GoogleOAuthRequestModel;
import com.example.responsiveui.api.models.LoginRequestModel;
import com.example.responsiveui.api.models.ProfileStatusResponse;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvForgotPassword, tvSignUp;
    private GoogleSignInClient mGoogleSignInClient;
    private CodeCollabApiService apiService;
    private static final int RC_SIGN_IN = 9001;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // ==================== Initialize ====================
        TokenManager.init(this);
        apiService = ApiConfig.getApiService(this);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        
        // Interactive elements
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvSignUp = findViewById(R.id.tvSignUp);

        // ==================== Setup Listeners ====================
        
        // Email/Password login button
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter your credentials", Toast.LENGTH_SHORT).show();
            } else {
                loginWithEmailPassword(email, password);
            }
        });

        // Google Sign-In button
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        
        // Forgot password link
        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> 
                Toast.makeText(this, "Forgot password feature coming soon", Toast.LENGTH_SHORT).show()
            );
        }
        
        // Sign up link - navigate to SignupActivity
        if (tvSignUp != null) {
            tvSignUp.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                startActivity(intent);
            });
        }
    }

    // ==================== Authentication Methods ====================
    
    private void loginWithEmailPassword(String email, String password) {
        LoginRequestModel loginRequest = new LoginRequestModel(email, password);
        
        apiService.login(loginRequest).enqueue(new Callback<AuthResponseModel>() {
            @Override
            public void onResponse(Call<AuthResponseModel> call, Response<AuthResponseModel> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponseModel authResponse = response.body();
                    
                    // Save JWT token
                    TokenManager.saveToken(
                        authResponse.accessToken,
                        authResponse.userId,
                        authResponse.email,
                        authResponse.expiresIn
                    );
                    
                    Log.d(TAG, "Login successful for: " + authResponse.email);
                    Toast.makeText(LoginActivity.this, "Welcome " + authResponse.email, Toast.LENGTH_SHORT).show();
                    
                    // Check profile completion status
                    checkProfileCompletionAndNavigate();
                } else {
                    Toast.makeText(LoginActivity.this, "Login failed: Invalid credentials", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Login failed with response code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AuthResponseModel> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Login network error", t);
            }
        });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                com.google.android.gms.auth.api.signin.GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null && account.getIdToken() != null) {
                    googleOAuthWithBackend(account.getIdToken(), account.getDisplayName());
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Google sign-in error", e);
            }
        }
    }

    private void googleOAuthWithBackend(String idToken, String displayName) {
        GoogleOAuthRequestModel oauthRequest = new GoogleOAuthRequestModel(idToken, displayName);
        
        apiService.googleOAuth(oauthRequest).enqueue(new Callback<AuthResponseModel>() {
            @Override
            public void onResponse(Call<AuthResponseModel> call, Response<AuthResponseModel> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponseModel authResponse = response.body();
                    
                    // Save JWT token
                    TokenManager.saveToken(
                        authResponse.accessToken,
                        authResponse.userId,
                        authResponse.email,
                        authResponse.expiresIn
                    );
                    
                    Log.d(TAG, "Google login successful for: " + authResponse.email);
                    Toast.makeText(LoginActivity.this, "Welcome " + authResponse.email, Toast.LENGTH_SHORT).show();
                    
                    // Check profile completion status
                    checkProfileCompletionAndNavigate();
                } else {
                    Toast.makeText(LoginActivity.this, "Google authentication failed", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Google auth failed with response code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AuthResponseModel> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Google auth network error", t);
            }
        });
    }

    // ==================== Profile Completion Check ====================
    
    /**
     * Check if user's profile is complete
     * Navigate to profile completion page only if profile is incomplete
     */
    private void checkProfileCompletionAndNavigate() {
        apiService.getProfileCompletionStatus().enqueue(new Callback<ProfileStatusResponse>() {
            @Override
            public void onResponse(Call<ProfileStatusResponse> call, Response<ProfileStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ProfileStatusResponse status = response.body();
                    
                    if (status.isComplete) {
                        // Profile is complete, go directly to main container
                        Log.d(TAG, "Profile is complete, navigating to main container");
                        navigateToMainContainer();
                    } else {
                        // Profile is incomplete, go to profile completion page
                        Log.d(TAG, "Profile incomplete. Missing fields: " + status.missingFields);
                        navigateToProfileCompletion();
                    }
                } else {
                    // If we can't check status, assume incomplete and go to profile page
                    Log.w(TAG, "Could not check profile status, defaulting to profile page");
                    navigateToProfileCompletion();
                }
            }

            @Override
            public void onFailure(Call<ProfileStatusResponse> call, Throwable t) {
                // On network error, default to profile completion page
                Log.w(TAG, "Failed to check profile status, defaulting to profile page", t);
                navigateToProfileCompletion();
            }
        });
    }
    
    private void navigateToProfileCompletion() {
        Intent intent = new Intent(LoginActivity.this, SkillSelectionActivity.class);
        intent.putExtra("USER_EMAIL", TokenManager.getUserEmail());
        startActivity(intent);
        finish();
    }
    
    private void navigateToMainContainer() {
        Intent intent = new Intent(LoginActivity.this, MainContainerActivity.class);
        intent.putExtra("USER_EMAIL", TokenManager.getUserEmail());
        startActivity(intent);
        finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is already authenticated with valid token
        if (TokenManager.isUserAuthenticated()) {
            // User is logged in, check profile status
            Log.d(TAG, "User already authenticated, checking profile status");
            checkProfileCompletionAndNavigate();
        }
    }
}