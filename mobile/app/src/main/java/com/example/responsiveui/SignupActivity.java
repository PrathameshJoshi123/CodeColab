package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.AuthResponseModel;
import com.example.responsiveui.api.models.SignUpRequestModel;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class SignupActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword, etConfirmPassword;
    private Button btnCreateAccount;
    private TextView tvLoginLink;
    private ImageView btnBack;
    private CodeCollabApiService apiService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        // Initialize TokenManager
        TokenManager.init(this);

        // Initialize API service
        apiService = ApiConfig.getApiService(this);

        // Initialize views
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        tvLoginLink = findViewById(R.id.tvLoginLink);
        btnBack = findViewById(R.id.btnBack);

        // Create Account button
        btnCreateAccount.setOnClickListener(v -> handleSignup());

        // Back button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        // Login link - navigate to LoginActivity
        if (tvLoginLink != null) {
            tvLoginLink.setOnClickListener(v -> {
                Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
        }
    }

    private void handleSignup() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create account
        signupWithEmailPassword(email, password, fullName);
    }

    private void signupWithEmailPassword(String email, String password, String fullName) {
        SignUpRequestModel signupRequest = new SignUpRequestModel(email, password, fullName);

        apiService.signUp(signupRequest).enqueue(new Callback<AuthResponseModel>() {
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

                    Toast.makeText(SignupActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                    // Navigate to next activity
                    Intent intent = new Intent(SignupActivity.this, SkillSelectionActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    // Parse error message from response
                    String errorMessage = "Signup failed";
                    if (response.errorBody() != null) {
                        try {
                            errorMessage = response.errorBody().string();
                        } catch (Exception e) {
                            errorMessage = "Email may already be registered";
                        }
                    }
                    Toast.makeText(SignupActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponseModel> call, Throwable t) {
                Toast.makeText(SignupActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}