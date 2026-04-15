package com.example.responsiveui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.responsiveui.api.models.MatchRequestResponse;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

/**
 * ==================== MatchAdapter ====================
 * RecyclerView adapter for displaying match cards
 * Shows user profiles with skills, reputation, and created date
 * Mode: "browse" = shows accept button, "received" = no action buttons
 */
public class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.MatchViewHolder> {
    
    private final List<MatchRequestResponse> matches;
    private final Context context;
    private MatchActionListener listener;
    private String mode = "browse";  // "browse" or "received"

    public interface MatchActionListener {
        void onAccept(MatchRequestResponse match);
    }

    public MatchAdapter(List<MatchRequestResponse> matches, Context context) {
        this.matches = matches;
        this.context = context;
    }

    public MatchAdapter(List<MatchRequestResponse> matches, Context context, String mode) {
        this.matches = matches;
        this.context = context;
        this.mode = mode;
    }

    public void setListener(MatchActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_match_card, parent, false);
        return new MatchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
        MatchRequestResponse match = matches.get(position);
        holder.bind(match, listener, mode);
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    public void updateMatches(List<MatchRequestResponse> newMatches) {
        matches.clear();
        matches.addAll(newMatches);
        notifyDataSetChanged();
    }

    // ==================== ViewHolder ====================
    
    static class MatchViewHolder extends RecyclerView.ViewHolder {
        private ImageView userProfileImage;
        private TextView userFullName;
        private TextView userCollegeCity;
        private TextView userReputationScore;
        private TextView sessionTypeText;
        private TextView userBio;
        private TextView matchMessage;
        private TextView scheduledDateTimeText;
        private TextView createdAtText;
        private ChipGroup skillsChipGroup;
        private ChipGroup requiredSkillsChipGroup;
        private Button btnAccept;
        private Button btnReject;

        public MatchViewHolder(@NonNull View itemView) {
            super(itemView);
            
            userProfileImage = itemView.findViewById(R.id.userProfileImage);
            userFullName = itemView.findViewById(R.id.userFullName);
            userCollegeCity = itemView.findViewById(R.id.userCollegeCity);
            userReputationScore = itemView.findViewById(R.id.userReputationScore);
            sessionTypeText = itemView.findViewById(R.id.sessionTypeText);
            userBio = itemView.findViewById(R.id.userBio);
            matchMessage = itemView.findViewById(R.id.matchMessage);
            scheduledDateTimeText = itemView.findViewById(R.id.scheduledDateTimeText);
            createdAtText = itemView.findViewById(R.id.createdAtText);
            skillsChipGroup = itemView.findViewById(R.id.skillsChipGroup);
            requiredSkillsChipGroup = itemView.findViewById(R.id.requiredSkillsChipGroup);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
        }

        public void bind(MatchRequestResponse match, MatchActionListener listener, String mode) {
            // User Details
            MatchRequestResponse.UserMatchProfile user = match.user;
            if (user != null) {
                userFullName.setText(user.fullName != null ? user.fullName : "Unknown");
                String collegeCity = (user.college != null ? user.college : "") 
                        + " • " 
                        + (user.city != null ? user.city : "");
                userCollegeCity.setText(collegeCity);
                userReputationScore.setText(String.format("%.1f", user.reputationScore));
            }

            // Session Type
            sessionTypeText.setText(match.sessionType);

            // Bio
            if (user != null && user.bio != null) {
                userBio.setText(user.bio);
                userBio.setVisibility(View.VISIBLE);
            } else {
                userBio.setVisibility(View.GONE);
            }

            // Match Message
            matchMessage.setText(match.message);

            // ==================== Scheduled Sprint Date/Time ====================
            // This is the most important info - when the sprint is scheduled
            if (match.scheduledDateTime != null && !match.scheduledDateTime.isEmpty()) {
                String formattedScheduledTime = formatDateForDisplay(match.scheduledDateTime);
                scheduledDateTimeText.setText("🗓️ Scheduled: " + formattedScheduledTime);
                scheduledDateTimeText.setVisibility(View.VISIBLE);
            } else {
                scheduledDateTimeText.setVisibility(View.GONE);
            }

            // Created At Date/Time
            if (match.createdAt != null) {
                String formattedDate = formatDateForDisplay(match.createdAt);
                createdAtText.setText("Posted: " + formattedDate);
                createdAtText.setVisibility(View.VISIBLE);
            } else {
                createdAtText.setVisibility(View.GONE);
            }

            // User Skills
            skillsChipGroup.removeAllViews();
            if (match.userSkills != null && !match.userSkills.isEmpty()) {
                for (MatchRequestResponse.UserSkillInfo skill : match.userSkills) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(skill.name + " (" + skill.proficiencyLevel + ")");
                    chip.setCheckable(false);
                    skillsChipGroup.addView(chip);
                }
            } else {
                TextView noSkillsText = new TextView(itemView.getContext());
                noSkillsText.setText("No skills added yet");
                skillsChipGroup.addView(noSkillsText);
            }

            // Required Skills
            requiredSkillsChipGroup.removeAllViews();
            if (match.requiredSkills != null && !match.requiredSkills.isEmpty()) {
                for (String skill : match.requiredSkills) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(skill);
                    chip.setCheckable(false);
                    requiredSkillsChipGroup.addView(chip);
                }
            } else {
                TextView noRequiredText = new TextView(itemView.getContext());
                noRequiredText.setText("Any skills welcome");
                requiredSkillsChipGroup.addView(noRequiredText);
            }

            // ==================== Action Buttons ====================
            // Show/hide based on mode
            if ("received".equals(mode)) {
                // Received tab - no action buttons
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
            } else {
                // Browse tab - show accept button only
                btnAccept.setVisibility(View.VISIBLE);
                btnReject.setVisibility(View.GONE);
                
                btnAccept.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAccept(match);
                    }
                });
            }
        }

        // ==================== Date Formatting ====================
        
        private String formatDateForDisplay(String isoDateTime) {
            try {
                // Parse ISO format: 2026-04-13T10:30:45.123456
                String[] parts = isoDateTime.split("T");
                if (parts.length >= 2) {
                    String date = parts[0];  // 2026-04-13
                    String time = parts[1].split("\\.")[0];  // 10:30:45
                    
                    // Convert to readable format: Apr 13, 2026 at 10:30 AM
                    return formatToReadableDate(date, time);
                }
                return isoDateTime;
            } catch (Exception e) {
                return isoDateTime;
            }
        }

        private String formatToReadableDate(String date, String time) {
            try {
                String[] dateParts = date.split("-");
                int year = Integer.parseInt(dateParts[0]);
                int month = Integer.parseInt(dateParts[1]);
                int day = Integer.parseInt(dateParts[2]);
                
                String[] timeParts = time.split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                
                String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                String monthStr = monthNames[month - 1];
                
                String amPm = hour >= 12 ? "PM" : "AM";
                int displayHour = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
                
                return String.format("%s %d, %d at %d:%02d %s", 
                        monthStr, day, year, displayHour, minute, amPm);
            } catch (Exception e) {
                return "";
            }
        }
    }
}
