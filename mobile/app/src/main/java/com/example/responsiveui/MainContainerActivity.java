package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.responsiveui.api.FCMTokenManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainContainerActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_container);

        // ==================== Notification Permission Request ====================
        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        PERMISSION_REQUEST_CODE);
            }
        }

        // ==================== FCM Initialization ====================
        // Initialize and register FCM token on app start
        FCMTokenManager fcmTokenManager = new FCMTokenManager(this);
        fcmTokenManager.initializeToken();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            // Check if opened from notification
            Intent intent = getIntent();
            if (intent != null && intent.getBooleanExtra("OPEN_SPRINT_SETUP", false)) {
                // Opened from match acceptance notification
                String matchId = intent.getStringExtra("MATCH_ID");
                String partnerName = intent.getStringExtra("PARTNER_NAME");
                
                // Navigate to SprintSetupActivity
                Intent sprintIntent = new Intent(this, SprintSetupActivity.class);
                sprintIntent.putExtra("MATCH_ID", matchId);
                sprintIntent.putExtra("PARTNER_NAME", partnerName);
                startActivity(sprintIntent);
            } else if (intent != null && intent.getBooleanExtra("OPEN_SPRINT_DETAILS", false)) {
                // Opened from sprint confirmation notification
                String sprintId = intent.getStringExtra("SPRINT_ID");
                
                // Navigate to SprintDetailsActivity
                Intent detailsIntent = new Intent(this, SprintDetailsActivity.class);
                detailsIntent.putExtra("SPRINT_ID", sprintId);
                startActivity(detailsIntent);
            } else {
                // Normal flow - show matches
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new MatchesFragment())
                        .commit();
            }
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_match) {
                selectedFragment = new MatchesFragment();
            } else if (id == R.id.nav_sprints) {
                selectedFragment = new SprintsFragment();
            } else if (id == R.id.nav_dashboard) {
                selectedFragment = new DashboardFragment();
            } else if (id == R.id.nav_chat) {
                selectedFragment = new ChatFragment();
            } else if (id == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });
    }
}