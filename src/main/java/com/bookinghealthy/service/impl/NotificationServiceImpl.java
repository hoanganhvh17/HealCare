package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Notification;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.NotificationRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    /** Cắt bớt cho khớp độ dài cột, tránh 500 vì một lời phê quá dài của trưởng khoa. */
    private static final int MAX_TITLE = 200;
    private static final int MAX_MESSAGE = 255;

    /** Ghi theo lô để một bài tin không sinh ra hàng nghìn lần round-trip xuống DB. */
    private static final int BATCH_SIZE = 500;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Mục "Lịch sử đặt lịch" trong hồ sơ — đúng anchor mà ProfileController đang redirect về. */
    private static final String BOOKING_LINK = "/user/profile#booking-history";

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

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
    public void pushBookingEvent(Booking booking, String icon, String title) {
        if (booking == null || booking.getUser() == null) {
            return;
        }
        push(booking.getUser(), icon, title, describe(booking), BOOKING_LINK);
    }

    /**
     * Fan-out tin tức tới toàn bộ bệnh nhân.
     *
     * {@code @Async} vì số bản ghi bằng số tài khoản bệnh nhân — để đồng bộ thì admin phải
     * ngồi đợi ghi xong mới thấy trang danh sách bài viết. Nuốt lỗi có chủ đích: fan-out hỏng
     * không được kéo theo việc xuất bản bài (bài đã lưu PUBLISHED trước khi gọi vào đây).
     *
     * CỐ Ý không gắn {@code @Transactional} kèm {@code @Async}: thứ tự hai proxy này không
     * được bảo đảm, nếu proxy transaction nằm ngoài thì transaction mở ở luồng GỌI còn công
     * việc lại chạy ở luồng khác — giữ kết nối mà không dùng. {@code saveAll} của Spring Data
     * vốn đã tự chạy trong transaction riêng, và mỗi lô commit riêng đúng là điều mong muốn.
     */
    @Override
    @Async
    public void pushToAllPatients(String icon, String title, String message, String link) {
        if (title == null || title.isBlank()) {
            return;
        }
        try {
            List<User> patients = userRepository.findByRoles_Name("ROLE_USER");
            List<Notification> batch = new ArrayList<>(BATCH_SIZE);

            for (User patient : patients) {
                Notification notification = new Notification();
                notification.setRecipient(patient);
                notification.setIcon(icon);
                notification.setTitle(truncate(title, MAX_TITLE));
                notification.setMessage(truncate(message, MAX_MESSAGE));
                notification.setLink(link);
                batch.add(notification);

                if (batch.size() >= BATCH_SIZE) {
                    notificationRepository.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                notificationRepository.saveAll(batch);
            }

            System.out.println("[NOTIFY] Đã gửi tin tức tới " + patients.size() + " bệnh nhân: " + title);
        } catch (Exception e) {
            System.err.println("[NOTIFY] Lỗi gửi tin tức tới bệnh nhân: " + e.getMessage());
        }
    }

    /** "BS. Trần Văn Bình — 09:00 - 09:30, 12/08/2026", bỏ qua phần nào thiếu dữ liệu. */
    private String describe(Booking booking) {
        StringBuilder text = new StringBuilder();

        if (booking.getDoctor() != null && booking.getDoctor().getUser() != null) {
            text.append("BS. ").append(booking.getDoctor().getUser().getFullName());
        }
        if (booking.getAppointmentTime() != null && !booking.getAppointmentTime().isBlank()) {
            text.append(text.length() > 0 ? " — " : "").append(booking.getAppointmentTime());
        }
        if (booking.getAppointmentDate() != null) {
            text.append(text.length() > 0 ? ", " : "").append(booking.getAppointmentDate().format(DATE_FORMAT));
        }
        return text.toString();
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
