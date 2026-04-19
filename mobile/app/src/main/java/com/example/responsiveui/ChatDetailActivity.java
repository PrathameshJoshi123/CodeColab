package com.example.responsiveui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.example.responsiveui.api.TokenManager;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ==================== Chat Detail Activity ====================
 * Displays real-time chat for a sprint session with instant message updates
 * Features:
 * - Real-time Firestore listeners for instant messages (like WhatsApp)
 * - Send messages to sprint partner
 * - FCM notifications for background message alerts
 * - Auto-scroll to latest messages
 */
public class ChatDetailActivity extends AppCompatActivity {
    
    // ==================== Variables ====================
    
    private String sprintId;
    private String conversationId;
    private String partnerName;
    private String partnerId;
    private String currentUserId;
    private boolean isDirectMessage = false;
    
    private ScrollView scrollMessages;
    private LinearLayout messagesContainer;
    private EditText messageInput;
    private FloatingActionButton btnSendMessage;
    private ImageView btnBack;
    private ImageView btnAttachMedia;
    private ImageView btnHeaderCamera;
    private ProgressBar loadingProgress;
    private TextView tvPartnerName;
    private TextView tvEmptyMessages;
    
    private CodeCollabApiService apiService;
    private FirebaseStorage storage;
    private List<Map<String, Object>> messages;
    private String pendingMediaType = "image";
    private boolean isMessageRequestInFlight = false;

    private static final long MESSAGE_POLL_INTERVAL_MS = 3000L;
    private final Handler messagePollHandler = new Handler(Looper.getMainLooper());
    private final Runnable messagePollRunnable = new Runnable() {
        @Override
        public void run() {
            loadMessagesFromApi(false);
            messagePollHandler.postDelayed(this, MESSAGE_POLL_INTERVAL_MS);
        }
    };

    private final ActivityResultLauncher<String> mediaPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleSelectedMedia);
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_detail);
        
        // ==================== Extract Intent Data ====================
        sprintId = getIntent().getStringExtra("SPRINT_ID");
        conversationId = getIntent().getStringExtra("CONVERSATION_ID");
        partnerName = getIntent().getStringExtra("PARTNER_NAME");
        partnerId = getIntent().getStringExtra("PARTNER_ID");
        
        // Determine if this is a direct message or sprint chat
        isDirectMessage = conversationId != null && !conversationId.isEmpty();

        // ==================== User Identity Setup ====================
        TokenManager.init(this);
        currentUserId = TokenManager.getUserId();
        
        // ==================== Initialize Views ====================
        initializeViews();
        
        // ==================== Setup API Service ====================
        apiService = ApiConfig.getApiService(this);
        storage = null;
        
        // ==================== Initial Message Load ====================
        loadMessagesFromApi(true);
        startMessagePolling();
        
        // ==================== Setup Button Listeners ====================
        setupChatListeners();
    }
    
    // ==================== View Initialization ====================
    
    private void initializeViews() {
        scrollMessages = findViewById(R.id.scrollMessages);
        messagesContainer = findViewById(R.id.messagesContainer);
        messageInput = findViewById(R.id.messageInput);
        btnSendMessage = findViewById(R.id.btnSendMessage);
        btnBack = findViewById(R.id.btnBack);
        btnAttachMedia = findViewById(R.id.btnAttachMedia);
        btnHeaderCamera = findViewById(R.id.btnHeaderCamera);
        loadingProgress = findViewById(R.id.loadingProgress);
        tvPartnerName = findViewById(R.id.tvPartnerName);
        tvEmptyMessages = findViewById(R.id.tvEmptyMessages);
        
        // Set partner name
        if (tvPartnerName != null && partnerName != null) {
            tvPartnerName.setText("Chat with " + partnerName);
        }
    }
    
    // ==================== Message Loading ====================

    private void startMessagePolling() {
        messagePollHandler.removeCallbacks(messagePollRunnable);
        messagePollHandler.postDelayed(messagePollRunnable, MESSAGE_POLL_INTERVAL_MS);
    }

    private void loadMessagesFromApi(boolean showLoader) {
        // Check if we have either sprintId or conversationId
        String idToUse = isDirectMessage ? conversationId : sprintId;
        
        if (idToUse == null || idToUse.isEmpty()) {
            Toast.makeText(this, isDirectMessage ? "Conversation ID not available" : "Sprint ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isMessageRequestInFlight) {
            return;
        }

        if (showLoader && loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }

        isMessageRequestInFlight = true;
        
        // Call appropriate API method based on message type
        if (isDirectMessage) {
            apiService.getConversationHistory(conversationId, 100).enqueue(new Callback<List<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<List<Map<String, Object>>> call, @NonNull Response<List<Map<String, Object>>> response) {
                    isMessageRequestInFlight = false;

                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        displayMessages(response.body());
                        android.util.Log.d("CHAT_DM", "Loaded " + response.body().size() + " messages");
                    } else if (showLoader) {
                        android.util.Log.e("CHAT_DM", "Failed to load messages: " + response.code());
                        Toast.makeText(ChatDetailActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<Map<String, Object>>> call, @NonNull Throwable t) {
                    isMessageRequestInFlight = false;

                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }

                    if (showLoader) {
                        android.util.Log.e("CHAT_DM", "Error loading messages: " + t.getMessage(), t);
                        Toast.makeText(ChatDetailActivity.this, "Error loading messages: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            // Sprint messages
            apiService.getMessages(sprintId, 100).enqueue(new Callback<List<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<List<Map<String, Object>>> call, @NonNull Response<List<Map<String, Object>>> response) {
                    isMessageRequestInFlight = false;

                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        displayMessages(response.body());
                    } else if (showLoader) {
                        Toast.makeText(ChatDetailActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<Map<String, Object>>> call, @NonNull Throwable t) {
                    isMessageRequestInFlight = false;

                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }

                    if (showLoader) {
                        Toast.makeText(ChatDetailActivity.this, "Error loading messages: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
    
    // ==================== Display Messages ====================
    
    private void displayMessages(@Nullable List<Map<String, Object>> latestMessages) {
        if (messagesContainer == null) {
            return;
        }
        
        messagesContainer.removeAllViews();

        messages = latestMessages != null ? latestMessages : new java.util.ArrayList<>();

        if (tvEmptyMessages != null) {
            tvEmptyMessages.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        }
        
        for (Map<String, Object> message : messages) {
            View messageRow = createMessageRow(message);
            messagesContainer.addView(messageRow);
        }

        if (scrollMessages != null) {
            scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
        }
    }

    private View createMessageRow(Map<String, Object> message) {
        String senderId = readString(message.get("sender_id"));
        String senderName = readString(message.get("sender_name"));
        String content = readString(message.get("content"));
        String messageType = readString(message.get("message_type"));
        String mediaUrl = readString(message.get("media_url"));
        String mediaName = readString(message.get("media_name"));

        if (messageType.isEmpty()) {
            messageType = "text";
        }
        if (senderName.isEmpty()) {
            senderName = "Unknown";
        }

        boolean isMyMessage = currentUserId != null && currentUserId.equals(senderId);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isMyMessage ? Gravity.END : Gravity.START);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(0, 8, 0, 8);
        wrapper.setLayoutParams(wrapperParams);

        TextView messageView = new TextView(this);
        messageView.setTextSize(14);
        messageView.setTextColor(getResources().getColor(R.color.text_white, null));
        messageView.setPadding(16, 12, 16, 12);
        messageView.setBackgroundResource(R.drawable.bg_message_box);

        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageParams.setMargins(isMyMessage ? dpToPx(60) : 0, 0, isMyMessage ? 0 : dpToPx(60), 0);
        messageView.setLayoutParams(messageParams);

        if (isMyMessage) {
            messageView.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.brand_blue, null)));
        }

        String displayText = buildMessageDisplayText(senderName, messageType, content, mediaName, mediaUrl, isMyMessage);
        messageView.setText(displayText);

        if (!mediaUrl.isEmpty()) {
            messageView.setOnClickListener(v -> openMedia(mediaUrl));
        }

        TextView timeView = new TextView(this);
        timeView.setText(formatTimestamp(message.get("created_at")));
        timeView.setTextColor(getResources().getColor(R.color.text_muted, null));
        timeView.setTextSize(10);

        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(8, 4, 8, 0);
        timeView.setLayoutParams(timeParams);

        wrapper.addView(messageView);
        wrapper.addView(timeView);
        return wrapper;
    }

    private String buildMessageDisplayText(
        String senderName,
        String messageType,
        String content,
        String mediaName,
        String mediaUrl,
        boolean isMyMessage
    ) {
        String senderLabel = isMyMessage ? "You" : senderName;

        if ("image".equals(messageType)) {
            String label = mediaName.isEmpty() ? "Image" : mediaName;
            return senderLabel + ": 📷 " + label + "\nTap to open";
        }
        if ("video".equals(messageType)) {
            String label = mediaName.isEmpty() ? "Video" : mediaName;
            return senderLabel + ": 🎥 " + label + "\nTap to open";
        }
        if ("file".equals(messageType)) {
            String label = mediaName.isEmpty() ? "Attachment" : mediaName;
            return senderLabel + ": 📎 " + label + "\nTap to open";
        }

        String safeContent = content;
        if (safeContent.isEmpty() && !mediaUrl.isEmpty()) {
            safeContent = mediaUrl;
        }
        return senderLabel + ": " + safeContent;
    }

    private String formatTimestamp(@Nullable Object rawTimestamp) {
        Date date = null;

        if (rawTimestamp instanceof Timestamp) {
            date = ((Timestamp) rawTimestamp).toDate();
        } else if (rawTimestamp instanceof Date) {
            date = (Date) rawTimestamp;
        } else if (rawTimestamp instanceof String) {
            date = parseApiTimestamp((String) rawTimestamp);
        }

        if (date == null) {
            return "";
        }

        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date);
    }

    @Nullable
    private Date parseApiTimestamp(@NonNull String rawTimestamp) {
        try {
            String trimmed = rawTimestamp.trim();
            if (trimmed.length() < 19) {
                return null;
            }

            String datePortion = trimmed.substring(0, 19);
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(datePortion);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readString(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    // ==================== Send Message ====================
    
    private void setupChatListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnHeaderCamera != null) {
            btnHeaderCamera.setOnClickListener(v -> launchMediaPicker("image"));
        }

        if (btnAttachMedia != null) {
            btnAttachMedia.setOnClickListener(v -> showAttachmentTypePicker());
        }

        if (btnSendMessage != null) {
            btnSendMessage.setOnClickListener(v -> sendTextMessage());
        }
    }
    
    private void sendTextMessage() {
        if (!hasValidChatTarget()) {
            Toast.makeText(this, getMissingChatTargetMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (messageInput == null || messageInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String messageContent = messageInput.getText().toString().trim();
        sendMessagePayload(messageContent, "text", null, null, true);
    }

    // ==================== Media Messages ====================

    private void showAttachmentTypePicker() {
        String[] options = new String[] {"Share image", "Share video", "Share file"};
        new AlertDialog.Builder(this)
            .setTitle("Select attachment type")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    launchMediaPicker("image");
                } else if (which == 1) {
                    launchMediaPicker("video");
                } else {
                    launchMediaPicker("file");
                }
            })
            .show();
    }

    private void launchMediaPicker(@NonNull String mediaType) {
        pendingMediaType = mediaType;
        String mimeType;
        if ("image".equals(mediaType)) {
            mimeType = "image/*";
        } else if ("video".equals(mediaType)) {
            mimeType = "video/*";
        } else {
            mimeType = "*/*";
        }
        mediaPickerLauncher.launch(mimeType);
    }

    private void handleSelectedMedia(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        if (!hasValidChatTarget()) {
            Toast.makeText(this, getMissingChatTargetMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        String mimeType = getContentResolver().getType(uri);
        String messageType = pendingMediaType;

        if (mimeType != null) {
            if (mimeType.startsWith("video/")) {
                messageType = "video";
            } else if (mimeType.startsWith("image/")) {
                messageType = "image";
            } else {
                messageType = "file";
            }
        }

        uploadMediaAndSendMessage(uri, messageType, mimeType);
    }

    private void uploadMediaAndSendMessage(@NonNull Uri uri, @NonNull String messageType, @Nullable String mimeType) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }

        if (storage == null) {
            storage = FirebaseStorage.getInstance();
        }

        String mediaName = resolveDisplayName(uri);
        String extension = mimeType != null ? MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) : null;

        if (TextUtils.isEmpty(extension)) {
            if ("image".equals(messageType)) {
                extension = "jpg";
            } else if ("video".equals(messageType)) {
                extension = "mp4";
            } else {
                extension = "bin";
            }
        }

        String objectPath = "chat_media/" + getChatStorageKey() + "/" + UUID.randomUUID() + "." + extension;
        StorageReference mediaRef = storage.getReference().child(objectPath);

        UploadTask uploadTask = mediaRef.putFile(uri);
        uploadTask.continueWithTask((Task<UploadTask.TaskSnapshot> task) -> {
            if (!task.isSuccessful()) {
                Exception exception = task.getException();
                if (exception != null) {
                    throw exception;
                }
            }
            return mediaRef.getDownloadUrl();
        }).addOnSuccessListener(downloadUri -> {
            sendMessagePayload("", messageType, downloadUri.toString(), mediaName, false);
        }).addOnFailureListener(error -> {
            if (loadingProgress != null) {
                loadingProgress.setVisibility(View.GONE);
            }
            Toast.makeText(ChatDetailActivity.this, "Failed to upload media: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private String resolveDisplayName(@NonNull Uri uri) {
        String name = null;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex);
            }
            cursor.close();
        }

        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }

        if (name == null || name.trim().isEmpty()) {
            return "attachment";
        }

        return name;
    }

    private void openMedia(@NonNull String mediaUrl) {
        try {
            Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl));
            startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this attachment", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== API Operations ====================

    private void sendMessagePayload(
        @NonNull String content,
        @NonNull String messageType,
        @Nullable String mediaUrl,
        @Nullable String mediaName,
        boolean clearInputOnSuccess
    ) {
        // Prepare request
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("content", content);
        messageData.put("message_type", messageType);

        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            messageData.put("media_url", mediaUrl);
        }
        if (mediaName != null && !mediaName.trim().isEmpty()) {
            messageData.put("media_name", mediaName);
        }
        
        // Use appropriate API method based on message type
        Call<Map<String, Object>> call;
        if (isDirectMessage) {
            call = apiService.sendDirectMessage(conversationId, messageData);
            android.util.Log.d("SEND_MSG_DM", "Sending direct message to conversation: " + conversationId);
        } else {
            call = apiService.sendMessage(sprintId, messageData);
            android.util.Log.d("SEND_MSG_SPRINT", "Sending sprint message to sprint: " + sprintId);
        }
        
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (loadingProgress != null) {
                    loadingProgress.setVisibility(View.GONE);
                }

                if (response.isSuccessful()) {
                    if (clearInputOnSuccess && messageInput != null) {
                        messageInput.setText("");
                    }
                    loadMessagesFromApi(false);
                    android.util.Log.d("SEND_MSG", "Message sent successfully");
                } else {
                    android.util.Log.e("SEND_MSG", "Failed to send message: " + response.code());
                    Toast.makeText(ChatDetailActivity.this, "Failed to send message (Code: " + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (loadingProgress != null) {
                    loadingProgress.setVisibility(View.GONE);
                }
                android.util.Log.e("SEND_MSG", "Error sending message: " + t.getMessage(), t);
                Toast.makeText(ChatDetailActivity.this, "Error sending message: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean hasValidChatTarget() {
        String idToUse = isDirectMessage ? conversationId : sprintId;
        return idToUse != null && !idToUse.trim().isEmpty();
    }

    @NonNull
    private String getMissingChatTargetMessage() {
        return isDirectMessage ? "Conversation ID not available" : "Sprint ID not available";
    }

    @NonNull
    private String getChatStorageKey() {
        String idToUse = isDirectMessage ? conversationId : sprintId;
        return (idToUse == null || idToUse.trim().isEmpty()) ? "unknown" : idToUse.trim();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        messagePollHandler.removeCallbacks(messagePollRunnable);
    }
}