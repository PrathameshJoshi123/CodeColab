package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class SprintSummaryActivity extends AppCompatActivity {

    // Variable declarations for the data we are carrying
    private String userEmail, partnerName, sprintGoal, sessionLength;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sprint_summary);

        // 1. Retrieve the complete "Suitcase" of data from the Intent
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        partnerName = getIntent().getStringExtra("PARTNER_NAME");
        sprintGoal = getIntent().getStringExtra("SPRINT_GOAL");
        sessionLength = getIntent().getStringExtra("SESSION_LENGTH");

        // 2. Initialize the UI elements from your XML
        TextView tvPartnerName = findViewById(R.id.tvPartnerName); // Add this ID to the Partner Name TextView
        TextView tvDuration = findViewById(R.id.tvDuration);       // Add this ID to the Duration TextView
        TextView tvGoalText = findViewById(R.id.tvGoalText);       // Add this ID to the Goal Text TextView
        Button btnStartNow = findViewById(R.id.btnStartNow);
        ImageView btnClose = findViewById(R.id.btnClose);

        // 3. Display the passed data
        if (partnerName != null) tvPartnerName.setText(partnerName);
        if (sessionLength != null) tvDuration.setText(sessionLength + " min\nDuration");
        if (sprintGoal != null) tvGoalText.setText("\"" + sprintGoal + "\"");

        // 4. Navigation: Launch the Live Sprint
        btnStartNow.setOnClickListener(v -> {
            Intent intent = new Intent(SprintSummaryActivity.this, LiveSprintActivity.class);

            // Continue carrying the context forward
            intent.putExtra("USER_EMAIL", userEmail);
            intent.putExtra("PARTNER_NAME", partnerName);
            intent.putExtra("SESSION_LENGTH", sessionLength);

            startActivity(intent);
        });

        // 5. Navigation: Close/Cancel (Go back to Setup)
        btnClose.setOnClickListener(v -> finish());
    }
}