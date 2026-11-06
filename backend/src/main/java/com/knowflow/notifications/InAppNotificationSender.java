package com.knowflow.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class InAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationSender.class);
    private final NotificationRepository notificationRepository;

    public InAppNotificationSender(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void sendNotification(UUID recipientUserId, String type, String title, String message,
                                 Map<String, Object> payload, String dedupeKey) {
        if (dedupeKey != null && notificationRepository.findByDedupeKey(dedupeKey).isPresent()) {
            log.info("Duplicate notification prevented via dedupeKey={}", dedupeKey);
            return;
        }

        Notification notification = new Notification(recipientUserId, type, title, message, payload, dedupeKey);
        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            log.warn("Notification dedupe conflict caught for key={}", dedupeKey);
        }
    }
}
