package com.knowflow.notifications;

import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<Page<Notification>> getMyNotifications(@PageableDefault(size = 20) Pageable pageable) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(notificationRepository.findByUserIdOrderByCreatedAtDesc(user.id(), pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        long count = notificationRepository.countByUserIdAndReadAtIsNull(user.id());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PostMapping("/{id}/mark-read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found"));

        if (!user.id().equals(n.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        n.setReadAt(Instant.now());
        notificationRepository.save(n);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mark-all-read")
    @Transactional
    public ResponseEntity<Void> markAllRead() {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        notificationRepository.markAllReadForUser(user.id(), Instant.now());
        return ResponseEntity.noContent().build();
    }
}
