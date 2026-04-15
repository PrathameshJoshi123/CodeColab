package com.example.responsiveui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.example.responsiveui.api.models.ParticipantDetail;
import com.example.responsiveui.api.models.SprintSessionResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatFragment extends Fragment {

    // ==================== Variables ====================

    private CodeCollabApiService apiService;
    private String currentUserId;

    private EditText etSearchChat;
    private Button btnAllChats;
    private LinearLayout chatsContainer;
    private TextView tvChatEmptyState;
    private ProgressBar loadingProgress;

    private final List<ChatSessionItem> allChatSessions = new ArrayList<>();
    private boolean hasCreatedLoadFailure = false;
    private boolean hasInvitedLoadFailure = false;
    private boolean hasChatLoadFailure = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_chat_list, container, false);

        // ==================== Setup Dependencies ====================
        apiService = ApiConfig.getApiService(requireContext());
        TokenManager.init(requireContext());
        currentUserId = TokenManager.getUserId();

        // ==================== View Initialization ====================
        etSearchChat = view.findViewById(R.id.etSearchChat);
        btnAllChats = view.findViewById(R.id.btnAllChats);
        chatsContainer = view.findViewById(R.id.chatsContainer);
        tvChatEmptyState = view.findViewById(R.id.tvChatEmptyState);
        loadingProgress = view.findViewById(R.id.loadingProgress);

        // ==================== Listeners ====================
        setupSearchListener();
        setupAllChatsListener();

        // ==================== Load Chat Sessions ====================
        loadChatSessions();

        return view;
    }

    // ==================== Data Loading ====================

    private void loadChatSessions() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }

        hasCreatedLoadFailure = false;
        hasInvitedLoadFailure = false;
        hasChatLoadFailure = false;
        allChatSessions.clear();
        hideEmptyState();

        loadCreatedChatSessions();
    }

    private void loadCreatedChatSessions() {
        apiService.getCreatedSprints().enqueue(new Callback<List<SprintSessionResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Response<List<SprintSessionResponse>> response) {
                if (!isAdded()) {
                    return;
                }

                if (response.isSuccessful()) {
                    addChatSessions(response.body());
                } else {
                    hasCreatedLoadFailure = true;
                }

                loadInvitedChatSessions();
            }

            @Override
            public void onFailure(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Throwable t) {
                if (!isAdded()) {
                    return;
                }

                hasCreatedLoadFailure = true;
                loadInvitedChatSessions();
            }
        });
    }

    private void loadInvitedChatSessions() {
        apiService.getInvitedSprints().enqueue(new Callback<List<SprintSessionResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Response<List<SprintSessionResponse>> response) {
                if (!isAdded()) {
                    return;
                }

                if (response.isSuccessful()) {
                    addChatSessions(response.body());
                } else {
                    hasInvitedLoadFailure = true;
                }

                finishChatLoad(null);
            }

            @Override
            public void onFailure(@NonNull Call<List<SprintSessionResponse>> call, @NonNull Throwable t) {
                if (!isAdded()) {
                    return;
                }

                hasInvitedLoadFailure = true;
                finishChatLoad(t);
            }
        });
    }

    private void finishChatLoad(@Nullable Throwable error) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.GONE);
        }

        allChatSessions.sort(
            Comparator.comparing((ChatSessionItem item) -> item.statusPriority)
                .thenComparing(item -> item.partnerName.toLowerCase())
        );

        hasChatLoadFailure = allChatSessions.isEmpty() && hasCreatedLoadFailure && hasInvitedLoadFailure;
        if (hasChatLoadFailure) {
            showEmptyState("Unable to load chats");
            if (error != null) {
                Toast.makeText(requireContext(), "Error loading chats: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        applySearchFilter();
    }

    private void addChatSessions(@Nullable List<SprintSessionResponse> sessions) {
        if (sessions == null) {
            return;
        }

        for (SprintSessionResponse sprint : sessions) {
            ChatSessionItem item = createChatSessionItem(sprint);
            if (item == null || containsChatSession(item.sprintId)) {
                continue;
            }
            allChatSessions.add(item);
        }
    }

    private boolean containsChatSession(String sprintId) {
        for (ChatSessionItem existingItem : allChatSessions) {
            if (existingItem.sprintId.equals(sprintId)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Search ====================

    private void setupSearchListener() {
        if (etSearchChat == null) {
            return;
        }

        etSearchChat.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applySearchFilter();
            }
        });
    }

    private void setupAllChatsListener() {
        if (btnAllChats == null) {
            return;
        }

        btnAllChats.setOnClickListener(v -> {
            if (etSearchChat != null) {
                etSearchChat.setText("");
            }
            loadChatSessions();
        });
    }

    private void applySearchFilter() {
        if (chatsContainer == null) {
            return;
        }

        if (hasChatLoadFailure) {
            showEmptyState("Unable to load chats");
            return;
        }

        String query = "";
        if (etSearchChat != null && etSearchChat.getText() != null) {
            query = etSearchChat.getText().toString().trim().toLowerCase();
        }

        chatsContainer.removeAllViews();

        int matchCount = 0;
        for (ChatSessionItem item : allChatSessions) {
            if (!query.isEmpty()
                && !item.partnerName.toLowerCase().contains(query)
                && !item.statusLabel.toLowerCase().contains(query)
                && !item.subtitle.toLowerCase().contains(query)) {
                continue;
            }

            chatsContainer.addView(createChatRow(item));
            matchCount++;
        }

        if (matchCount == 0) {
            String emptyText = query.isEmpty() ? "No chats available" : "No chats found";
            showEmptyState(emptyText);
        } else {
            hideEmptyState();
        }
    }

    // ==================== UI Builders ====================

    private View createChatRow(ChatSessionItem item) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 16, 0, 16);

        ImageView avatar = new ImageView(requireContext());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dpToPx(55), dpToPx(55));
        avatar.setLayoutParams(avatarParams);
        avatar.setImageResource(R.drawable.ic_profile);
        avatar.setColorFilter(getResources().getColor(R.color.text_muted, null));
        row.addView(avatar);

        LinearLayout content = new LinearLayout(requireContext());
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        );
        contentParams.setMargins(16, 0, 0, 0);
        content.setLayoutParams(contentParams);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(requireContext());
        tvName.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));
        tvName.setText(item.partnerName);
        tvName.setTextColor(getResources().getColor(R.color.text_white, null));
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        titleRow.addView(tvName);

        TextView tvStatus = new TextView(requireContext());
        tvStatus.setText(item.statusLabel);
        tvStatus.setTextColor(getResources().getColor(R.color.brand_blue, null));
        tvStatus.setTextSize(12);
        titleRow.addView(tvStatus);

        TextView tvSubtitle = new TextView(requireContext());
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, 4, 0, 0);
        tvSubtitle.setLayoutParams(subtitleParams);
        tvSubtitle.setText(item.subtitle);
        tvSubtitle.setTextColor(getResources().getColor(R.color.text_muted, null));
        tvSubtitle.setTextSize(14);

        content.addView(titleRow);
        content.addView(tvSubtitle);
        row.addView(content);

        row.setOnClickListener(v -> openChat(item));

        return row;
    }

    private void showEmptyState(String message) {
        if (tvChatEmptyState != null) {
            tvChatEmptyState.setText(message);
            tvChatEmptyState.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmptyState() {
        if (tvChatEmptyState != null) {
            tvChatEmptyState.setVisibility(View.GONE);
        }
    }

    // ==================== Navigation ====================

    private void openChat(ChatSessionItem item) {
        Intent intent = new Intent(requireContext(), ChatDetailActivity.class);
        intent.putExtra("SPRINT_ID", item.sprintId);
        intent.putExtra("PARTNER_NAME", item.partnerName);
        startActivity(intent);
    }

    // ==================== Mapping Helpers ====================

    @Nullable
    private ChatSessionItem createChatSessionItem(@Nullable SprintSessionResponse sprint) {
        if (sprint == null || sprint.id == null || sprint.id.trim().isEmpty()) {
            return null;
        }

        String partnerName = resolvePartnerName(sprint);
        String status = sprint.status != null ? sprint.status.trim().toLowerCase() : "setupped";

        String statusLabel;
        int statusPriority;
        if ("started".equals(status) || "live".equals(status)) {
            statusLabel = "Live";
            statusPriority = 0;
        } else if ("end".equals(status) || "completed".equals(status)) {
            statusLabel = "Ended";
            statusPriority = 2;
        } else {
            statusLabel = "Upcoming";
            statusPriority = 1;
        }

        String subtitle = "Tap to open chat";
        if ("Live".equals(statusLabel)) {
            subtitle = "Sprint in progress";
        }

        return new ChatSessionItem(sprint.id, partnerName, statusLabel, statusPriority, subtitle);
    }

    private String resolvePartnerName(SprintSessionResponse sprint) {
        if (sprint.participantDetails != null) {
            for (ParticipantDetail participant : sprint.participantDetails) {
                if (participant == null || participant.userId == null) {
                    continue;
                }

                if (currentUserId != null && currentUserId.equals(participant.userId)) {
                    continue;
                }

                if (participant.fullName != null && !participant.fullName.trim().isEmpty()) {
                    return participant.fullName.trim();
                }

                if (participant.email != null && !participant.email.trim().isEmpty()) {
                    return localNameFromEmail(participant.email.trim());
                }

                return participant.userId;
            }
        }

        if (sprint.participants != null) {
            for (String participantId : sprint.participants) {
                if (participantId == null || participantId.isEmpty()) {
                    continue;
                }
                if (currentUserId != null && currentUserId.equals(participantId)) {
                    continue;
                }
                return participantId;
            }
        }

        return "Partner";
    }

    private String localNameFromEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // ==================== Item Model ====================

    private static class ChatSessionItem {
        final String sprintId;
        final String partnerName;
        final String statusLabel;
        final int statusPriority;
        final String subtitle;

        ChatSessionItem(String sprintId, String partnerName, String statusLabel, int statusPriority, String subtitle) {
            this.sprintId = sprintId;
            this.partnerName = partnerName;
            this.statusLabel = statusLabel;
            this.statusPriority = statusPriority;
            this.subtitle = subtitle;
        }
    }
}