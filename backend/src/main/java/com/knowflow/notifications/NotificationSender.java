package com.knowflow.notifications;

import java.util.Map;
import java.util.UUID;

public interface NotificationSender {
    void sendNotification(UUID recipientUserId, String type, String title, String message,
                          Map<String, Object> payload, String dedupeKey);
}
