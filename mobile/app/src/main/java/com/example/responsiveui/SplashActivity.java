package com.example.responsiveui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This keeps the Splash UI visible indefinitely
        setContentView(R.layout.activity_splash);
    }
}