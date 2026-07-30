package com.bookinghealthy.service;

import com.bookinghealthy.model.Notification;
import com.bookinghealthy.model.User;

import java.util.List;

/**
 * Thông báo trong ứng dụng. Dùng SONG SONG với email chứ không thay thế: email có thể vào
 * spam hoặc thất bại âm thầm (gửi bất đồng bộ, lỗi chỉ nằm trong log), còn thông báo ở đây
 * thì bác sĩ chắc chắn thấy khi mở hệ thống.
 *
 * Quy ước: mỗi chỗ thông báo cho nhân viên nên gọi CẢ hai — xem
 * {@code LeaveServiceImpl.notifyDecision} làm mẫu.
 */
public interface NotificationService {

    /**
     * Ghi một thông báo cho người nhận. Tự bỏ qua nếu {@code recipient} null, để chỗ gọi
     * không phải kiểm tra (một ca trực có thể mất người sở hữu do dữ liệu cũ).
     *
     * @param icon    class Bootstrap Icons, ví dụ "bi-check-circle text-success"
     * @param link    đường dẫn mở khi bấm, null nếu chỉ để đọc
     */
    void push(User recipient, String icon, String title, String message, String link);

    /** 20 thông báo mới nhất, mới trước. */
    List<Notification> recent(Long userId);

    long countUnread(Long userId);

    void markAllRead(Long userId);
}
