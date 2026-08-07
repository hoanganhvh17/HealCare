package com.bookinghealthy.task;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Nhắc bệnh nhân về lịch khám của NGÀY MAI, qua email + thông báo trong ứng dụng.
 *
 * Khác {@link FollowUpReminderTask} ở chỗ: task kia suy ra ngày tái khám từ lời dặn dạng
 * văn bản tự do của bác sĩ (nên phải dè dặt và có thể bỏ sót), còn ở đây ngày giờ là dữ liệu
 * đã có sẵn trên chính lịch hẹn — không phải đoán gì cả.
 *
 * KHÔNG tự đổi trạng thái lịch hẹn, chỉ nhắc: giữ đúng nguyên tắc "job không bao giờ ra
 * quyết định thay bệnh nhân" đã áp dụng cho tầng AI và nhắc tái khám.
 */
@Component
public class AppointmentReminderTask {

    /** Chỉ lịch còn hiệu lực mới đáng nhắc — CANCELED/COMPLETED thì nhắc là sai. */
    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    @Autowired private BookingRepository bookingRepository;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    /** 7h30 sáng, chạy trước job nhắc tái khám (8h) để hai loại thư không dồn cùng một lúc. */
    @Scheduled(cron = "0 30 7 * * ?")
    public void remindTomorrowAppointments() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<Booking> bookings = bookingRepository
                .findByAppointmentDateAndReminderSentFalseAndStatusIn(tomorrow, ACTIVE_STATUSES);

        int sent = 0;
        for (Booking booking : bookings) {
            // Bọc từng lịch: một dòng dữ liệu hỏng không được giết cả lượt chạy, cùng cách
            // FollowUpReminderTask đang làm.
            try {
                if (booking.getUser() == null) {
                    continue;
                }

                emailService.sendAppointmentReminder(booking);
                notificationService.pushBookingEvent(booking, "bi-alarm text-primary",
                        "Nhắc lịch khám ngày mai");

                // Đánh dấu SAU khi đã gửi: gửi mail chạy @Async nên lỗi của nó không quay lại
                // đây được, nhưng nếu chính chỗ này ném thì lần chạy sau vẫn còn cơ hội nhắc.
                booking.setReminderSent(true);
                bookingRepository.save(booking);
                sent++;
            } catch (Exception e) {
                System.err.println("[CRON] Lỗi nhắc lịch khám #" + booking.getId() + ": " + e.getMessage());
            }
        }

        System.out.println("[CRON] Đã nhắc lịch khám ngày mai cho " + sent + " bệnh nhân.");
    }
}
