package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvDashboardTitle;
    private Button btnInviteSarah;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        // 1. Unpack the "Suitcase" from the previous screen
        userEmail = getIntent().getStringExtra("USER_EMAIL");

        // 2. Personalize the Header
        // In your XML, add android:id="@+id/tvDashboardTitle" to the "Find Partners" TextView
        tvDashboardTitle = findViewById(R.id.tvDashboardTitle);
        if (userEmail != null && !userEmail.isEmpty()) {
            String name = userEmail.split("@")[0]; // Get 'sarah' from 'sarah@email.com'
            tvDashboardTitle.setText("Hi, " + name + "!");
        }

        // 3. Set up the "Invite" button for Sarah Jenkins
        // In your XML, add android:id="@+id/btnInviteSarah" to Sarah's blue Invite button
        btnInviteSarah = findViewById(R.id.btnInviteSarah);

        btnInviteSarah.setOnClickListener(v -> {
            // Create Intent to move to SprintSetupActivity
            Intent intent = new Intent(DashboardActivity.this, SprintSetupActivity.class);

            // Carry both the User's Email AND the Partner's Name to the next screen
            intent.putExtra("USER_EMAIL", userEmail);
            intent.putExtra("PARTNER_NAME", "Sarah Jenkins");

            startActivity(intent);
        });
    }
}