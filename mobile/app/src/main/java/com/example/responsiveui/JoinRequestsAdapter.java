package com.example.responsiveui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.responsiveui.api.models.JoinRequestMatchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Adapter for displaying pending join requests grouped by match request.
 */
public class JoinRequestsAdapter extends RecyclerView.Adapter<JoinRequestsAdapter.JoinRequestViewHolder> {

    private final List<JoinRequestMatchResponse> joinRequests;
    private final Context context;
    private JoinRequestActionListener listener;

    public interface JoinRequestActionListener {
        void onOpenInterestedUsers(JoinRequestMatchResponse item);
    }

    public JoinRequestsAdapter(List<JoinRequestMatchResponse> joinRequests, Context context) {
        this.joinRequests = joinRequests;
        this.context = context;
    }

    public void setListener(JoinRequestActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public JoinRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_join_request_card, parent, false);
        return new JoinRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JoinRequestViewHolder holder, int position) {
        holder.bind(joinRequests.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return joinRequests.size();
    }

    public void updateJoinRequests(List<JoinRequestMatchResponse> newItems) {
        joinRequests.clear();
        joinRequests.addAll(newItems);
        notifyDataSetChanged();
    }

    static class JoinRequestViewHolder extends RecyclerView.ViewHolder {
        private final TextView textSessionType;
        private final TextView textMessage;
        private final TextView textRequiredSkills;
        private final TextView textInterestedCount;
        private final TextView textInterestedPreview;
        private final TextView textCreatedAt;
        private final Button btnViewInterested;

        public JoinRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            textSessionType = itemView.findViewById(R.id.textSessionType);
            textMessage = itemView.findViewById(R.id.textMessage);
            textRequiredSkills = itemView.findViewById(R.id.textRequiredSkills);
            textInterestedCount = itemView.findViewById(R.id.textInterestedCount);
            textInterestedPreview = itemView.findViewById(R.id.textInterestedPreview);
            textCreatedAt = itemView.findViewById(R.id.textCreatedAt);
            btnViewInterested = itemView.findViewById(R.id.btnViewInterested);
        }

        public void bind(JoinRequestMatchResponse item, JoinRequestActionListener listener) {
            textSessionType.setText(item.sessionType != null ? item.sessionType : "Match Request");
            textMessage.setText(item.message != null ? item.message : "No message");

            List<String> required = item.requiredSkills != null ? item.requiredSkills : new ArrayList<>();
            if (required.isEmpty()) {
                textRequiredSkills.setText("Required Skills: Any");
            } else {
                textRequiredSkills.setText("Required Skills: " + String.join(", ", required));
            }

            textInterestedCount.setText(String.format(Locale.getDefault(),
                    "Interested Users: %d", item.interestedCount));

            if (item.interestedUsers != null && !item.interestedUsers.isEmpty()) {
                String preview = item.interestedUsers.stream()
                        .limit(3)
                        .map(JoinRequestMatchResponse.InterestedUser::getDisplayName)
                        .collect(Collectors.joining(", "));

                if (item.interestedUsers.size() > 3) {
                    preview += " +" + (item.interestedUsers.size() - 3) + " more";
                }

                textInterestedPreview.setText(preview);
                textInterestedPreview.setVisibility(View.VISIBLE);
                btnViewInterested.setEnabled(true);
            } else {
                textInterestedPreview.setText("No interested users yet");
                textInterestedPreview.setVisibility(View.VISIBLE);
                btnViewInterested.setEnabled(false);
            }

            textCreatedAt.setText(formatDateForDisplay(item.createdAt));

            btnViewInterested.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenInterestedUsers(item);
                }
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenInterestedUsers(item);
                }
            });
        }

        private String formatDateForDisplay(String isoDateTime) {
            if (isoDateTime == null || isoDateTime.isEmpty()) {
                return "Posted recently";
            }

            try {
                String[] parts = isoDateTime.split("T");
                if (parts.length >= 2) {
                    String date = parts[0];
                    String time = parts[1].split("\\.")[0];
                    return "Posted: " + formatToReadableDate(date, time);
                }
                return "Posted: " + isoDateTime;
            } catch (Exception e) {
                return "Posted: " + isoDateTime;
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

                String[] monthNames = {
                        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                };
                String monthStr = monthNames[month - 1];

                String amPm = hour >= 12 ? "PM" : "AM";
                int displayHour = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);

                return String.format(Locale.getDefault(), "%s %d, %d at %d:%02d %s",
                        monthStr, day, year, displayHour, minute, amPm);
            } catch (Exception e) {
                return "Recently";
            }
        }
    }
}
