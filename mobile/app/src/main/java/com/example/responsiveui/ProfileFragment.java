package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

public class ProfileFragment extends Fragment {

    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        // Display current user email
        if (mAuth.getCurrentUser() != null) {
            tvUserEmail.setText("Logged in as: " + mAuth.getCurrentUser().getEmail());
        }

        // Logout button click listener
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
            
            // Navigate back to LoginActivity
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        return view;
    }
}
