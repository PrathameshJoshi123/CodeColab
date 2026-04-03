package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class DashboardFragment extends Fragment {

    private String userEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_dashboard, container, false);

        TextView tvTitle = view.findViewById(R.id.tvDashboardTitle);
        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        CardView cardSarah = view.findViewById(R.id.cardSarah);
        Button btnInvite = view.findViewById(R.id.btnInviteSarah);

        // Data Unpacking
        if (getActivity() != null && getActivity().getIntent() != null) {
            userEmail = getActivity().getIntent().getStringExtra("USER_EMAIL");
            if (userEmail != null && !userEmail.isEmpty()) {
                tvTitle.setText("Hi, " + userEmail.split("@")[0] + "!");
            }
        }

        // Gesture 4: Pull-to-Refresh
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Pull-to-Refresh performed", Toast.LENGTH_SHORT).show();
                }
                view.postDelayed(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    if (cardSarah != null) {
                        cardSarah.setVisibility(View.VISIBLE);
                        cardSarah.setAlpha(1.0f);
                        cardSarah.setTranslationX(0);
                        cardSarah.setScaleX(1.0f);
                        cardSarah.setScaleY(1.0f);
                    }
                }, 1500);
            });
        }

        // Setup Detectors
        if (cardSarah != null) {
            // Gestures 1, 2, 3: DoubleTap, Swipe, LongPress
            GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(@NonNull MotionEvent e) { return true; }

                @Override
                public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Single Tap performed", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(@NonNull MotionEvent e) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Double Tap performed", Toast.LENGTH_SHORT).show();
                    }
                    launchSprintSetup("Sarah Jenkins");
                    return true;
                }

                @Override
                public void onLongPress(@NonNull MotionEvent e) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Long Press performed", Toast.LENGTH_SHORT).show();
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Quick Preview")
                            .setMessage("Sarah Jenkins\nMIT '24 • CS Major")
                            .setPositiveButton("Invite", (d, w) -> launchSprintSetup("Sarah Jenkins"))
                            .show();
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                    if (e1 != null && e2 != null) {
                        float diffX = e1.getX() - e2.getX();
                        float diffY = e1.getY() - e2.getY();
                        
                        if (Math.abs(diffX) > Math.abs(diffY)) {
                            // Swipe Left - Dismiss
                            if (diffX > 100) {
                                if (getContext() != null) {
                                    Toast.makeText(getContext(), "Swipe Left (Dismiss) performed", Toast.LENGTH_SHORT).show();
                                }
                                cardSarah.animate().translationX(-cardSarah.getWidth()).alpha(0f).setDuration(300)
                                        .withEndAction(() -> cardSarah.setVisibility(View.GONE));
                                return true;
                            } 
                            // Swipe Right - Invite/Save (Replacing Pinch-to-Zoom)
                            else if (diffX < -100) {
                                if (getContext() != null) {
                                    Toast.makeText(getContext(), "Swipe Right (Invite) performed", Toast.LENGTH_SHORT).show();
                                }
                                cardSarah.animate().translationX(cardSarah.getWidth()).alpha(0f).setDuration(300)
                                        .withEndAction(() -> cardSarah.setVisibility(View.GONE));
                                launchSprintSetup("Sarah Jenkins");
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });

            cardSarah.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }

        if (btnInvite != null) {
            btnInvite.setOnClickListener(v -> launchSprintSetup("Sarah Jenkins"));
        }

        return view;
    }

    private void launchSprintSetup(String partnerName) {
        Intent intent = new Intent(getActivity(), SprintSetupActivity.class);
        intent.putExtra("USER_EMAIL", userEmail);
        intent.putExtra("PARTNER_NAME", partnerName);
        startActivity(intent);
    }
}