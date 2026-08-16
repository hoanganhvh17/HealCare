package com.bookinghealthy.task;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
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
            List<Booking> expiredBookings = bookingRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(
                    BookingStatus.PENDING, "UNPAID", cutoffTime
            );
            if (!expiredBookings.isEmpty()) {
                for (Booking booking : expiredBookings) {
                    booking.setStatus(BookingStatus.CANCELED);
                    booking.setPaymentStatus("EXPIRED");
                }
                bookingRepository.saveAll(expiredBookings);
                System.out.println("[CRON JOB] Đã tự động HỦY và dọn dẹp " + expiredBookings.size() + " lịch hẹn chưa thanh toán quá 3 phút.");
            }
        } catch (Exception e) {
            System.err.println("[CRON JOB] Lỗi khi dọn dẹp lịch hẹn: " + e.getMessage());
        }
    }
}
