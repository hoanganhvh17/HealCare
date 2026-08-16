package com.bookinghealthy.task;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class AppointmentReminderTask {

    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    @Autowired private BookingRepository bookingRepository;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    @Scheduled(cron = "0 30 7 * * ?")
    @SchedulerLock(name = "appointmentReminder", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void remindTomorrowAppointments() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<Booking> bookings = bookingRepository
                .findByAppointmentDateAndReminderSentFalseAndStatusIn(tomorrow, ACTIVE_STATUSES);

        int sent = 0;
        for (Booking booking : bookings) {
            try {
                if (booking.getUser() == null) {
                    continue;
                }

                emailService.sendAppointmentReminder(booking);
                notificationService.pushBookingEvent(booking, "bi-alarm text-primary",
                        "Nhắc lịch khám ngày mai");
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
