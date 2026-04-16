package com.ozichat.notification.service.impl;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.ozichat.message.document.Message;
import com.ozichat.notification.entity.DeviceToken;
import com.ozichat.notification.repository.DeviceTokenRepository;
import com.ozichat.notification.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationServiceImpl implements PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Override
    @Async
    public void sendMessageNotification(Long recipientId, Message message, String senderName) {
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(recipientId);
        if (tokens.isEmpty()) {
            log.debug("[PUSH] No active device tokens for userId={}", recipientId);
            return;
        }

        String title = senderName != null ? senderName : "New message";
        String body  = buildPreview(message);

        for (DeviceToken deviceToken : tokens) {
            if (!firebaseEnabled || FirebaseApp.getApps().isEmpty()) {
                log.info("[PUSH][STUB] Would send to userId={} token={}... title='{}' body='{}'",
                        recipientId, deviceToken.getToken().substring(0, Math.min(20, deviceToken.getToken().length())),
                        title, body);
                continue;
            }

            sendFcmMessage(deviceToken, title, body, message, recipientId);
        }
    }

    @Override
    @Async
    public void sendSyncNotification(Long recipientId) {
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(recipientId);
        if (tokens.isEmpty()) return;

        if (!firebaseEnabled || FirebaseApp.getApps().isEmpty()) {
            log.info("[PUSH][STUB] Would send sync notification to userId={}", recipientId);
            return;
        }

        for (DeviceToken deviceToken : tokens) {
            try {
                com.google.firebase.messaging.Message fcmMessage =
                        com.google.firebase.messaging.Message.builder()
                                .setToken(deviceToken.getToken())
                                .putData("type", "SYNC")
                                .putData("timestamp", String.valueOf(Instant.now().toEpochMilli()))
                                .setAndroidConfig(AndroidConfig.builder()
                                        .setPriority(AndroidConfig.Priority.NORMAL)
                                        .build())
                                .build();

                FirebaseMessaging.getInstance().send(fcmMessage);
                updateLastUsed(deviceToken);
            } catch (FirebaseMessagingException e) {
                handleFcmError(e, deviceToken, recipientId);
            }
        }
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private void sendFcmMessage(DeviceToken deviceToken, String title, String body,
                                Message message, Long recipientId) {
        try {
            com.google.firebase.messaging.Message fcmMessage =
                    com.google.firebase.messaging.Message.builder()
                            .setToken(deviceToken.getToken())
                            // Notification payload (shown in system tray)
                            .setNotification(Notification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build())
                            // Data payload (for in-app handling when foregrounded)
                            .putData("type", "NEW_MESSAGE")
                            .putData("conversationId", String.valueOf(message.getConversationId()))
                            .putData("messageId", message.getId())
                            .putData("senderId", String.valueOf(message.getSenderId()))
                            // Android-specific: high priority ensures delivery even in Doze mode
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .setNotification(AndroidNotification.builder()
                                            .setChannelId("ozichat_messages")
                                            .setSound("default")
                                            .build())
                                    .build())
                            // APNs (iOS) specific
                            .setApnsConfig(ApnsConfig.builder()
                                    .setAps(Aps.builder()
                                            .setSound("default")
                                            .setBadge(1)
                                            .setContentAvailable(true)
                                            .build())
                                    .build())
                            .build();

            String messageId = FirebaseMessaging.getInstance().send(fcmMessage);
            updateLastUsed(deviceToken);
            log.debug("[PUSH] FCM message sent — fcmMessageId={} userId={}", messageId, recipientId);

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, recipientId);
        }
    }

    private String buildPreview(Message message) {
        return switch (message.getType()) {
            case TEXT     -> truncate(message.getContent(), 100);
            case IMAGE    -> "📷 Image";
            case VIDEO    -> "🎥 Video";
            case AUDIO    -> "🎵 Voice message";
            case DOCUMENT -> "📄 Document";
            case LOCATION -> "📍 Location";
            case CONTACT  -> "👤 Contact";
        };
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private void updateLastUsed(DeviceToken deviceToken) {
        deviceToken.setLastUsedAt(Instant.now());
        deviceTokenRepository.save(deviceToken);
    }

    private void handleFcmError(FirebaseMessagingException e, DeviceToken deviceToken, Long userId) {
        log.warn("[PUSH] FCM error for userId={} — code={} message={}",
                userId, e.getMessagingErrorCode(), e.getMessage());

        // Deactivate stale/unregistered tokens to avoid wasting sends
        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
            log.info("[PUSH] Deactivating invalid device token for userId={}", userId);
            deviceToken.setIsActive(false);
            deviceTokenRepository.save(deviceToken);
        }
    }
}
