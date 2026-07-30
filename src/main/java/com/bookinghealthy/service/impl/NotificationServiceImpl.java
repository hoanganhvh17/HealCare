package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Notification;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.NotificationRepository;
import com.bookinghealthy.service.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    /** Cắt bớt cho khớp độ dài cột, tránh 500 vì một lời phê quá dài của trưởng khoa. */
    private static final int MAX_TITLE = 200;
    private static final int MAX_MESSAGE = 255;

    @Autowired private NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void push(User recipient, String icon, String title, String message, String link) {
        if (recipient == null || title == null || title.isBlank()) {
            return;
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setIcon(icon);
        notification.setTitle(truncate(title, MAX_TITLE));
        notification.setMessage(truncate(message, MAX_MESSAGE));
        notification.setLink(link);
        notificationRepository.save(notification);
    }

    @Override
    public List<Notification> recent(Long userId) {
        return notificationRepository.findTop20ByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public long countUnread(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFlagFalse(userId);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return (value.length() <= max) ? value : value.substring(0, max - 1) + "…";
    }
}
