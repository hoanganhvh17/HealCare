package com.bookinghealthy.service;

import com.bookinghealthy.model.Booking;
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

    /**
     * Thông báo một sự kiện của lịch hẹn cho CHÍNH bệnh nhân đã đặt lịch đó
     * (đặt lịch, xác nhận, dời lịch, hủy lịch, đổi bác sĩ, nhắc lịch khám).
     *
     * Tồn tại để 11 chỗ gọi không phải chép lại cùng một câu tóm tắt và cùng một đường dẫn:
     * phần thân luôn là "BS. &lt;tên&gt; — &lt;khung giờ&gt;, &lt;dd/MM/yyyy&gt;" và link luôn trỏ về
     * mục lịch sử đặt lịch trong hồ sơ. Tự bỏ qua nếu lịch hẹn không có người đặt.
     */
    void pushBookingEvent(Booking booking, String icon, String title);

    /**
     * Bắn một thông báo giống nhau tới MỌI tài khoản bệnh nhân (ROLE_USER) — dùng cho tin tức
     * hệ thống khi admin xuất bản bài viết.
     *
     * Chạy bất đồng bộ: số bản ghi bằng số bệnh nhân, admin không việc gì phải đợi ghi xong
     * mới thấy trang. Chỗ gọi chịu trách nhiệm CHỐNG TRÙNG (đừng gọi lại cho bài đã xuất bản).
     */
    void pushToAllPatients(String icon, String title, String message, String link);

    /** 20 thông báo mới nhất, mới trước. */
    List<Notification> recent(Long userId);

    long countUnread(Long userId);

    void markAllRead(Long userId);
}
