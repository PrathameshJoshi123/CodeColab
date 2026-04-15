package com.example.responsiveui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.responsiveui.api.ApiConfig;
import com.example.responsiveui.api.CodeCollabApiService;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.Query;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private String partnerName;
    
    private ScrollView scrollMessages;
    private LinearLayout messagesContainer;
    private EditText messageInput;
    private Button btnSendMessage;
    private ProgressBar loadingProgress;
    private TextView tvPartnerName;
    
    private CodeCollabApiService apiService;
    private FirebaseFirestore firestore;
    private List<Map<String, Object>> messages;
    private com.google.firebase.firestore.ListenerRegistration messageListener;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_detail);
        
        // ==================== Extract Intent Data ====================
        sprintId = getIntent().getStringExtra("SPRINT_ID");
        partnerName = getIntent().getStringExtra("PARTNER_NAME");
        
        // ==================== Initialize Views ====================
        initializeViews();
        
        // ==================== Setup API Service ====================
        apiService = ApiConfig.getApiService(this);
        firestore = FirebaseFirestore.getInstance();
        
        // ==================== Setup Real-time Listeners ====================
        setupRealtimeMessageListener();
        
        // ==================== Setup Button Listeners ====================
        setupChatListeners();
    }
    
    // ==================== View Initialization ====================
    
    private void initializeViews() {
        scrollMessages = findViewById(R.id.scrollMessages);
        messagesContainer = findViewById(R.id.messagesContainer);
        messageInput = findViewById(R.id.messageInput);
        btnSendMessage = findViewById(R.id.btnSendMessage);
        loadingProgress = findViewById(R.id.loadingProgress);
        tvPartnerName = findViewById(R.id.tvPartnerName);
        
        // Set partner name
        if (tvPartnerName != null && partnerName != null) {
            tvPartnerName.setText("Chat with " + partnerName);
        }
    }
    
    // ==================== Real-time Message Listener ====================
    
    private void setupRealtimeMessageListener() {
        if (sprintId == null || sprintId.isEmpty()) {
            Toast.makeText(this, "Sprint ID not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
        
        // Set up Firestore real-time listener (like WhatsApp)
        messageListener = firestore.collection("chatMessages")
                .whereEqualTo("sprint_id", sprintId)
                .orderBy("created_at", Query.Direction.ASCENDING)
                .limit(100)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }
                    
                    if (error != null) {
                        Toast.makeText(ChatDetailActivity.this, "Error listening to messages: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (querySnapshot != null) {
                        // Handle changes (new messages, updates, deletions)
                        for (DocumentChange change : querySnapshot.getDocumentChanges()) {
                            if (change.getType() == DocumentChange.Type.ADDED) {
                                // New message added - refresh display
                                displayMessages(querySnapshot);
                                // Auto-scroll to bottom
                                if (scrollMessages != null) {
                                    scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
                                }
                            }
                        }
                        
                        // Initial load or update
                        if (messages == null || messages.isEmpty()) {
                            displayMessages(querySnapshot);
                            // Auto-scroll to bottom
                            if (scrollMessages != null) {
                                scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
                            }
                        }
                    }
                });
    }
    
    // ==================== Display Messages ====================
    
    private void displayMessages(QuerySnapshot querySnapshot) {
        if (messagesContainer == null || querySnapshot == null) {
            return;
        }
        
        messagesContainer.removeAllViews();
        
        // Convert to message list
        List<Map<String, Object>> allMessages = new java.util.ArrayList<>();
        for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
            allMessages.add(doc.getData());
        }
        
        messages = allMessages;
        
        for (Map<String, Object> message : messages) {
            String senderName = (String) message.getOrDefault("sender_name", "Unknown");
            String content = (String) message.getOrDefault("content", "");
            
            // Create message view
            TextView messageView = new TextView(this);
            messageView.setText(senderName + ": " + content);
            messageView.setTextSize(14);
            messageView.setTextColor(getResources().getColor(android.R.color.white));
            messageView.setPadding(16, 12, 16, 12);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);
            messageView.setLayoutParams(params);
            messageView.setBackgroundResource(R.drawable.bg_message_box);
            
            messagesContainer.addView(messageView);
        }
    }
    
    // ==================== Send Message ====================
    
    private void setupChatListeners() {
        if (btnSendMessage != null) {
            btnSendMessage.setOnClickListener(v -> sendMessage());
        }
    }
    
    private void sendMessage() {
        if (sprintId == null || sprintId.isEmpty()) {
            Toast.makeText(this, "Sprint ID not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (messageInput == null || messageInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String messageContent = messageInput.getText().toString().trim();
        
        // Prepare request
        Map<String, String> messageData = new HashMap<>();
        messageData.put("content", messageContent);
        
        Call<Map<String, Object>> call = apiService.sendMessage(sprintId, messageData);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    messageInput.setText("");  // Clear input
                    Toast.makeText(ChatDetailActivity.this, "Message sent", Toast.LENGTH_SHORT).show();
                    // Real-time listener will automatically update the UI
                } else {
                    Toast.makeText(ChatDetailActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(ChatDetailActivity.this, "Error sending message: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up Firestore listener to prevent memory leaks
        if (messageListener != null) {
            messageListener.remove();
        }
    }
}