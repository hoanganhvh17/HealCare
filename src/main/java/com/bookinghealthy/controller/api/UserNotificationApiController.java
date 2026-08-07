package com.bookinghealthy.controller.api;

import com.bookinghealthy.model.Notification;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuông thông báo của BỆNH NHÂN.
 *
 * Tách khỏi {@code /api/staff/notifications} chứ không dùng lại: endpoint đó gộp thêm 4 khối
 * nhắc tính động chỉ có nghĩa với nhân viên (chưa đăng ký lịch tuần sau, lời mời nhận ca, ca
 * cần người thay, hàng chờ duyệt của trưởng khoa), và bản thân tiền tố {@code /api/staff}
 * cũng sai ngữ nghĩa khi người gọi là bệnh nhân.
 *
 * Ở đây chỉ đọc thông báo ĐÃ LƯU trong bảng {@code notifications} — mọi sự kiện của bệnh nhân
 * (đặt lịch, dời lịch, hủy lịch, đổi bác sĩ, nhắc tái khám, nhắc lịch ngày mai, tin tức mới)
 * đều được ghi hẳn một bản ghi lúc xảy ra, nên không có gì phải tính lại.
 *
 * Shape JSON giữ ĐÚNG như chuông nhân viên (icon / title / subtitle / link / read / time) để
 * {@code user-notifications.js} chỉ là bản port của {@code staff-notifications.js}.
 */
@RestController
@RequestMapping("/api/notifications")
public class UserNotificationApiController {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired private CurrentUserService currentUserService;
    @Autowired private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(Authentication authentication) {
        User user = currentUserService.require(authentication);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Notification saved : notificationService.recent(user.getId())) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("icon", saved.getIcon());
            entry.put("title", saved.getTitle());
            entry.put("subtitle", saved.getMessage());
            entry.put("link", saved.getLink());
            entry.put("read", saved.isReadFlag());
            entry.put("time", saved.getCreatedAt() != null
                    ? saved.getCreatedAt().format(DATE_TIME_FORMAT) : null);
            items.add(entry);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("count", items.size());
        response.put("unreadCount", notificationService.countUnread(user.getId()));
        response.put("items", items);
        return ResponseEntity.ok(response);
    }

    /** Mở chuông là coi như đã đọc — chuông không có nút đánh dấu riêng từng mục. */
    @PostMapping("/read")
    public ResponseEntity<Map<String, Object>> markRead(Authentication authentication) {
        User user = currentUserService.require(authentication);
        notificationService.markAllRead(user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", 0);
        return ResponseEntity.ok(response);
    }
}
