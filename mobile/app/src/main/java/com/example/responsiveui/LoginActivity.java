package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvForgotPassword, tvSignUp;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

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
        
        // GitHub button (UI only)
        Button btnGithubSignIn = findViewById(R.id.btnGithubSignIn);
        btnGithubSignIn.setOnClickListener(v -> Toast.makeText(this, "GitHub sign-in not implemented", Toast.LENGTH_SHORT).show());

        // Traditional login button
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter your credentials", Toast.LENGTH_SHORT).show();
            } else {
                signInWithEmailPassword(email, password);
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
        
        // Sign up link
        if (tvSignUp != null) {
            tvSignUp.setOnClickListener(v -> 
                Toast.makeText(this, "Sign up feature coming soon", Toast.LENGTH_SHORT).show()
            );
        }
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
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(LoginActivity.this, "Welcome " + user.getEmail(), Toast.LENGTH_SHORT).show();
                        
                        // Navigate to next activity
                        Intent intent = new Intent(LoginActivity.this, SkillSelectionActivity.class);
                        intent.putExtra("USER_EMAIL", user.getEmail());
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void signInWithEmailPassword(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(LoginActivity.this, "Welcome " + user.getEmail(), Toast.LENGTH_SHORT).show();
                        
                        Intent intent = new Intent(LoginActivity.this, SkillSelectionActivity.class);
                        intent.putExtra("USER_EMAIL", email);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is signed in, navigate to next activity
            Intent intent = new Intent(LoginActivity.this, SkillSelectionActivity.class);
            intent.putExtra("USER_EMAIL", currentUser.getEmail());
            startActivity(intent);
            finish();
        }
    }
}