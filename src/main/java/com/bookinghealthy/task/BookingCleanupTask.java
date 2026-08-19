package com.bookinghealthy.task;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BookingCleanupTask {

    @Autowired
    private BookingRepository bookingRepository;

    @Scheduled(fixedRate = 60000)
    @SchedulerLock(name = "bookingCleanup", lockAtMostFor = "PT2M")
    public void cleanupExpiredBookings() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(3);
            // Chỉ dọn lịch chờ TRẢ TRƯỚC. Lịch trả-tại-quầy cố ý nằm ngoài: nó không có gì
            // để chờ, huỷ sau 3 phút là huỷ một lịch hợp lệ và bệnh nhân chỉ thấy nó biến mất.
            List<Booking> expiredBookings = bookingRepository.findAbandonedPrepayBookings(
                    BookingStatus.PENDING, "UNPAID", cutoffTime, BookingService.PAY_AT_COUNTER
            );
            if (!expiredBookings.isEmpty()) {
                for (Booking booking : expiredBookings) {
                    booking.setStatus(BookingStatus.CANCELED);
                    booking.setPaymentStatus("EXPIRED");
                }
                bookingRepository.saveAll(expiredBookings);
                System.out.println("[CRON JOB] Đã tự động HỦY và dọn dẹp " + expiredBookings.size() + " lịch hẹn chờ thanh toán trực tuyến quá 3 phút.");
            }
        } catch (Exception e) {
            System.err.println("[CRON JOB] Lỗi khi dọn dẹp lịch hẹn: " + e.getMessage());
        }
    }
}
