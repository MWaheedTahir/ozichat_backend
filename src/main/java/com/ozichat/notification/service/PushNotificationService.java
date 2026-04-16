package com.ozichat.notification.service;

import com.ozichat.message.document.Message;

public interface PushNotificationService {

    /**
     * Send a push notification to all active devices of the given user.
     * Called when the recipient is OFFLINE (not connected via WebSocket).
     */
    void sendMessageNotification(Long recipientId, Message message, String senderName);

    /**
     * Send a silent push to sync missed messages after reconnect.
     */
    void sendSyncNotification(Long recipientId);
}
