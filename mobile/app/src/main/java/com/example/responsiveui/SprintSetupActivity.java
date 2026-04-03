package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class SprintSetupActivity extends AppCompatActivity {

    private String userEmail, partnerName;
    private Button btnConfirmSprint;
    private TextView btnCancel;
    private EditText etGoalTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sprint_setup);

        // 1. Unpack the data from DashboardActivity
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        partnerName = getIntent().getStringExtra("PARTNER_NAME");

        // 2. Initialize Views
        btnConfirmSprint = findViewById(R.id.btnConfirmSprint);
        btnCancel = findViewById(R.id.btnCancel);

        // Ensure you add android:id="@+id/etGoalTitle" to your first EditText in XML
        etGoalTitle = findViewById(R.id.etGoalTitle);

        // 3. Confirm Sprint - Move to Summary
        btnConfirmSprint.setOnClickListener(v -> {
            String goal = etGoalTitle.getText().toString().trim();
            if (goal.isEmpty()) goal = "General Sprint"; // Default value

            Intent intent = new Intent(SprintSetupActivity.this, SprintSummaryActivity.class);

            // Carry everything forward!
            intent.putExtra("USER_EMAIL", userEmail);
            intent.putExtra("PARTNER_NAME", partnerName);
            intent.putExtra("SPRINT_GOAL", goal);
            intent.putExtra("SESSION_LENGTH", "60"); // The blue 60min card value

            startActivity(intent);
        });

        // 4. Cancel - Go back to Dashboard
        btnCancel.setOnClickListener(v -> finish());
    }
}