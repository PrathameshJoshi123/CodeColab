package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.responsiveui.api.FCMTokenManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainContainerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_container);

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