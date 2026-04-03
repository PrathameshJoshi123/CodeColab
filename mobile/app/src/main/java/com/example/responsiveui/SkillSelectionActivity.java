package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SkillSelectionActivity extends AppCompatActivity {

    private Button btnContinue;
    private String userEmail;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_skill_selection);

        userEmail = getIntent().getStringExtra("USER_EMAIL");
        btnContinue = findViewById(R.id.btnContinue);

        btnContinue.setOnClickListener(v->{
            Intent intent = new Intent(SkillSelectionActivity.this, MainContainerActivity.class);
            intent.putExtra("USER_EMAIL",userEmail);

            intent.putExtra("SKILL_COUNT","3+");

            startActivity(intent);
        });

        findViewById(R.id.btnBack).setOnClickListener(v->finish());

//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
    }
}