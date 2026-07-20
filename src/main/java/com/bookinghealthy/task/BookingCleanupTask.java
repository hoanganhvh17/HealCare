package com.bookinghealthy.task;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BookingCleanupTask {

    @Autowired
    private BookingRepository bookingRepository;

    /**
     * @Scheduled(fixedRate = 60000): Cứ đúng 60 giây (1 phút) hàm này sẽ tự động chạy 1 lần.
     * Nó chạy hoàn toàn trên một luồng (thread) phụ riêng biệt, không làm lag web.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredBookings() {
        try {
            // 1. Xác định mốc thời gian: Lấy thời gian hiện tại LÙI LẠI 3 phút
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(3);

            // 2. Gọi Database: "Tìm cho tôi tất cả các ông nào PENDING, chưa trả tiền, và tạo trước mốc 3 phút kia"
            List<Booking> expiredBookings = bookingRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(
                    BookingStatus.PENDING, "UNPAID", cutoffTime
            );

            // 3. Nếu tìm thấy thì xử lý
            if (!expiredBookings.isEmpty()) {
                for (Booking booking : expiredBookings) {
                    booking.setStatus(BookingStatus.CANCELED); // Đổi thành Hủy
                    booking.setPaymentStatus("EXPIRED");       // Đánh dấu Hết hạn
                }

                // 4. Lưu lại toàn bộ vào DB cùng 1 lúc (saveAll để tối ưu tốc độ)
                bookingRepository.saveAll(expiredBookings);

                // In ra console để mày dễ theo dõi lúc test
                System.out.println("[CRON JOB] Đã tự động HỦY và dọn dẹp " + expiredBookings.size() + " lịch hẹn chưa thanh toán quá 3 phút.");
            }
        } catch (Exception e) {
            // Lọc lỗi để dù Cron Job có sập thì trang web đặt lịch của mày vẫn chạy bình thường
            System.err.println("[CRON JOB] Lỗi khi dọn dẹp lịch hẹn: " + e.getMessage());
        }
    }
}
