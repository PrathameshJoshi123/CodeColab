package com.example.responsiveui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * ==================== FCM Message Service ====================
 * Handles incoming Firebase Cloud Messaging notifications
 * Shows notifications to user and handles notification clicks
 */
public class FCMMessageService extends FirebaseMessagingService {
    private static final String TAG = "FCMMessageService";
    private static final String CHANNEL_ID = "match_notifications";
    private static final int NOTIFICATION_ID = 1001;
    private static final int CHAT_NOTIFICATION_BASE_ID = 3000;

    /**
     * Called when a message is received from Firebase Cloud Messaging
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        if (remoteMessage.getNotification() != null) {
            // Notification message received
            handleNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody(),
                    remoteMessage.getData()
            );
        }

        if (!remoteMessage.getData().isEmpty()) {
            // Data message received (used for chat messages)
            String messageType = remoteMessage.getData().get("type");
            if ("CHAT_MESSAGE".equals(messageType)) {
                Log.d(TAG, "Chat message received: " + remoteMessage.getData());
                showChatMessageNotification(remoteMessage.getData());
            } else {
                Log.d(TAG, "Data Payload: " + remoteMessage.getData());
            }
        }
    }

    /**
     * Display chat notification for data-only chat messages.
     */
    private void showChatMessageNotification(java.util.Map<String, String> data) {
        String sprintId = data.get("sprint_id");
        String senderName = data.get("sender_name");
        String content = data.get("content");

        if (senderName == null || senderName.trim().isEmpty()) {
            senderName = "Partner";
        }

        if (content == null || content.trim().isEmpty()) {
            content = "Sent a message";
        }

        createNotificationChannel();

        Intent intent = new Intent(this, ChatDetailActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("SPRINT_ID", sprintId);
        intent.putExtra("PARTNER_NAME", senderName);

        int requestCode = sprintId != null ? sprintId.hashCode() : 0;
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            int notificationId = CHAT_NOTIFICATION_BASE_ID + Math.abs(requestCode % 1000);
            notificationManager.notify(notificationId, builder.build());
        }
    }

    /**
     * Display notification to user
     */
    private void handleNotification(String title, String body, java.util.Map<String, String> data) {
        // Get notification data
        String notificationType = data.get("type");
        String matchId = data.get("match_id");
        String accepterName = data.get("accepter_name");
        String requesterName = data.get("requester_name");
        String confirmerName = data.get("confirmer_name");
        String sprintId = data.get("sprint_id");
        
        Log.d(TAG, "Notification Type: " + notificationType + ", Match ID: " + matchId + ", Sprint ID: " + sprintId);

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel();

        // Create intent to handle notification click
        Intent intent = new Intent(this, MainContainerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        // Add extras based on notification type
        if ("MATCH_CREATED".equals(notificationType)) {
            // User received a match created notification - open matches fragment with details
            intent.putExtra("OPEN_MATCHES_TAB", true);
            intent.putExtra("MATCH_ID", matchId);
            intent.putExtra("SHOW_MATCH_DETAILS", true);
            intent.putExtra("REQUESTER_NAME", requesterName != null ? requesterName : "Someone");
        } else if ("MATCH_ACCEPTED".equals(notificationType)) {
            intent.putExtra("OPEN_SPRINT_SETUP", true);
            intent.putExtra("MATCH_ID", matchId);
            intent.putExtra("PARTNER_NAME", accepterName != null ? accepterName : "Partner");
        } else if ("SPRINT_CONFIRMED".equals(notificationType)) {
            intent.putExtra("OPEN_SPRINT_DETAILS", true);
            intent.putExtra("SPRINT_ID", sprintId);
            intent.putExtra("PARTNER_NAME", confirmerName != null ? confirmerName : "Partner");
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title != null ? title : "CodeCollab")
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Show notification
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Notification shown");
        }
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = "Match Notifications";
            String description = "Notifications for match requests and collaboration";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channelName, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Called when FCM token is refreshed
     * This typically happens when:
     * - App is installed
     * - User reinstalls the app
     * - User clears app data
     * - App is uninstalled and reinstalled
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        
        // Save locally
        getApplicationContext().getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();
        
        // Register with backend
        new com.example.responsiveui.api.FCMTokenManager(getApplicationContext())
                .registerTokenWithBackend(token);
    }
}
