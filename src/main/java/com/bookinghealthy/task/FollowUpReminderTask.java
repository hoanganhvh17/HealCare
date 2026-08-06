package com.bookinghealthy.task;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.MedicalRecord;
import com.bookinghealthy.model.MedicalRecordStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.MedicalRecordRepository;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nhắc bệnh nhân tái khám theo lời dặn của bác sĩ ({@code MedicalRecord.doctorNotes}).
 *
 * Phạm vi CỐ Ý thu hẹp: chỉ nhận dạng một mẫu câu tương đối "tái khám/khám lại ... sau N
 * ngày/tuần/tháng". KHÔNG parse ngày tuyệt đối dạng dd/mm — doctorNotes là văn bản tự do lẫn
 * cả số đo (huyết áp, liều dùng) chứa dấu '/' đúng cái bẫy đã ghi trong ai-assistant.md cho
 * bộ trích ngày phía chat (extractDateHint: "huyết áp 120/80" từng bị đọc thành ngày 20/80).
 * Thà bỏ sót lời dặn còn hơn suy ra sai ngày rồi giục bệnh nhân đến nhầm hôm.
 *
 * KHÔNG tự đặt lịch giúp bệnh nhân — chỉ nhắc, bệnh nhân tự bấm vào /appointment và xác nhận
 * qua form, cùng nguyên tắc "AI/job không bao giờ tạo booking ngầm định" áp dụng cho voice.
 */
@Component
public class FollowUpReminderTask {

    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    /** Còn trong khoảng này (ngày) trước hạn thì bắt đầu nhắc — chịu được job không chạy đúng ngày. */
    private static final int LEAD_DAYS = 3;
    /** Quá hạn quá lâu (job từng dừng, ví dụ downtime) thì bỏ qua, nhắc muộn vậy không còn ý nghĩa. */
    private static final int STALE_DAYS = 14;

    private static final Pattern REVISIT_PATTERN = Pattern.compile(
            "(?:tái\\s*khám|khám\\s*lại)[^\\n.;]{0,20}?sau\\s*(\\d{1,3})\\s*(ngày|tuần|tháng)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** 8h sáng mỗi ngày — không cần đợi đến Chủ nhật như lịch trực, đây là nhắc bệnh nhân hằng ngày. */
    @Scheduled(cron = "0 0 8 * * ?")
    public void remindUpcomingFollowUps() {
        List<MedicalRecord> candidates = medicalRecordRepository
                .findByStatusAndFollowUpReminderSentFalseAndDoctorNotesIsNotNull(MedicalRecordStatus.COMPLETED);

        LocalDate today = LocalDate.now();
        int sent = 0;

        for (MedicalRecord record : candidates) {
            try {
                if (processRecord(record, today)) sent++;
            } catch (Exception e) {
                System.err.println("[CRON] Lỗi xử lý nhắc tái khám cho hồ sơ #" + record.getId() + ": " + e.getMessage());
            }
        }

        System.out.println("[CRON] Đã nhắc tái khám cho " + sent + " bệnh nhân.");
    }

    /** @return true nếu đã thực sự gửi email + thông báo cho hồ sơ này. */
    private boolean processRecord(MedicalRecord record, LocalDate today) {
        Booking booking = record.getBooking();
        if (booking == null || booking.getAppointmentDate() == null || booking.getUser() == null) return false;

        Optional<Integer> revisitDays = extractRevisitDays(record.getDoctorNotes());
        if (revisitDays.isEmpty()) return false; // Không khớp mẫu câu -> để nguyên, thử lại vào lần chạy sau.

        LocalDate revisitDate = booking.getAppointmentDate().plusDays(revisitDays.get());
        long daysUntil = ChronoUnit.DAYS.between(today, revisitDate);

        if (daysUntil > LEAD_DAYS) return false; // Còn xa, chưa tới lúc nhắc -> thử lại vào lần chạy sau.

        // Từ đây job coi như đã XỬ LÝ hồ sơ này (dù có gửi hay không) -> luôn đánh dấu để không lặp lại.
        if (daysUntil < -STALE_DAYS) {
            markSent(record);
            return false; // Quá hạn quá lâu, im lặng bỏ qua thay vì nhắc muộn vô nghĩa.
        }

        boolean alreadyRebooked = bookingRepository.existsByUserIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                booking.getUser().getId(), today, BookingStatus.CANCELED);

        markSent(record);

        if (alreadyRebooked) return false; // Bệnh nhân đã tự đặt lịch mới, khỏi làm phiền.

        emailService.sendFollowUpReminder(booking, revisitDate);
        notificationService.push(booking.getUser(), "bi-calendar-heart text-danger", "Nhắc tái khám",
                "Theo lời dặn của bác sĩ, bạn nên tái khám khoảng ngày "
                        + revisitDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".",
                "/appointment");
        return true;
    }

    private void markSent(MedicalRecord record) {
        record.setFollowUpReminderSent(true);
        medicalRecordRepository.save(record);
    }

    /** @return số ngày kể từ ngày khám tới hạn tái khám, rỗng nếu không khớp mẫu câu đã hỗ trợ. */
    private Optional<Integer> extractRevisitDays(String doctorNotes) {
        if (doctorNotes == null || doctorNotes.isBlank()) return Optional.empty();

        Matcher m = REVISIT_PATTERN.matcher(doctorNotes);
        if (!m.find()) return Optional.empty();

        int amount;
        try {
            amount = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        int days = switch (m.group(2).toLowerCase()) {
            case "ngày" -> amount;
            case "tuần" -> amount * 7;
            case "tháng" -> amount * 30;
            default -> 0;
        };
        return days > 0 ? Optional.of(days) : Optional.empty();
    }
}
