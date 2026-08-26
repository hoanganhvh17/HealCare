package com.bookinghealthy.service.impl;

import com.bookinghealthy.dto.BulkResultDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.DoctorBlockTimeService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import com.bookinghealthy.service.ReceptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ReceptionServiceImpl implements ReceptionService {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;
    @Autowired private DoctorBlockTimeService doctorBlockTimeService;

    private static final DateTimeFormatter SLOT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Biên các buổi, khớp với MORNING_SLOTS / AFTERNOON_SLOTS của TimeSlotService.
    // Chỉ có giờ hành chính: ngoài giờ là phiên trực, không nhận đặt khám.
    private static final LocalTime MORNING_START = LocalTime.of(7, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 30);
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 30);

    // ===================== 1. ĐỔI LỊCH HÀNG LOẠT =====================

    @Override
    public List<Booking> findBookingsForChange(Long doctorId, LocalDate date, String session) {
        List<Booking> bookings = bookingRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                doctorId, date, BookingStatus.CANCELED);

        List<Booking> result = new ArrayList<>();
        for (Booking booking : bookings) {
            // Lịch đã khám xong thì không cần dời nữa
            if (booking.getStatus() == BookingStatus.COMPLETED) {
                continue;
            }
            if (matchesSession(booking.getAppointmentTime(), session)) {
                result.add(booking);
            }
        }

        result.sort(Comparator.comparing(Booking::getAppointmentTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    public BulkResultDTO bulkCancel(List<Long> bookingIds, String reason) {
        BulkResultDTO result = new BulkResultDTO();
        if (bookingIds == null || bookingIds.isEmpty()) {
            result.addError("Chưa chọn lịch hẹn nào.");
            return result;
        }

        String finalReason = (reason == null || reason.isBlank())
                ? "Bác sĩ bận đột xuất, phòng khám rất xin lỗi vì sự bất tiện này."
                : reason;

        // Xử lý từng lịch độc lập: một lịch lỗi không làm hỏng các lịch còn lại
        for (Long id : bookingIds) {
            try {
                bookingService.cancelWithRefund(id, finalReason);
                result.addSuccess();
            } catch (Exception e) {
                result.addError("Lịch #" + id + ": " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public BulkResultDTO bulkTransfer(List<Long> bookingIds, Long newDoctorId, String reason) {
        BulkResultDTO result = new BulkResultDTO();
        if (bookingIds == null || bookingIds.isEmpty()) {
            result.addError("Chưa chọn lịch hẹn nào.");
            return result;
        }
        if (newDoctorId == null) {
            result.addError("Chưa chọn bác sĩ tiếp nhận.");
            return result;
        }

        String finalReason = (reason == null || reason.isBlank())
                ? "Bác sĩ bận đột xuất, phòng khám rất xin lỗi vì sự bất tiện này."
                : reason;

        for (Long id : bookingIds) {
            try {
                // Phải đọc tên bác sĩ cũ TRƯỚC khi reassign() ghi đè
                String oldDoctorName = bookingRepository.findById(id)
                        .filter(b -> b.getDoctor() != null && b.getDoctor().getUser() != null)
                        .map(b -> "Dr. " + b.getDoctor().getUser().getFullName())
                        .orElse("Bác sĩ trước đó");

                Booking transferred = bookingService.reassign(id, newDoctorId);

                // Thư xin lỗi + thông báo đổi bác sĩ, kèm lý do và lịch hẹn giữ nguyên
                emailService.sendBookingDoctorChange(transferred, oldDoctorName, finalReason);
                notificationService.pushBookingEvent(transferred, "bi-person-badge text-warning",
                        "Lịch hẹn đã đổi bác sĩ");
                result.addSuccess();
            } catch (Exception e) {
                result.addError("Lịch #" + id + ": " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public String blockSessionForDoctor(Long doctorId, LocalDate date, String session, String reason) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy bác sĩ."));

        LocalTime start;
        LocalTime end;
        switch (normalizeSession(session)) {
            case SESSION_MORNING -> { start = MORNING_START; end = MORNING_END; }
            case SESSION_AFTERNOON -> { start = AFTERNOON_START; end = AFTERNOON_END; }
            default -> { start = MORNING_START; end = AFTERNOON_END; }
        }

        String finalReason = (reason == null || reason.isBlank()) ? "Bác sĩ bận đột xuất" : reason;
        return doctorBlockTimeService.blockTime(doctor, date, start, end, finalReason);
    }

    // ===================== 2. HÀNG CHỜ KHÁM =====================

    @Override
    public List<Booking> getQueue(Long doctorId, LocalDate date) {
        List<Booking> bookings = bookingRepository.findByDoctorIdAndAppointmentDateAndStatus(
                doctorId, date, BookingStatus.CONFIRMED);
        return sortByQueue(new ArrayList<>(bookings));
    }

    /**
     * Hàng chờ chỉ có nghĩa cho ngày đang diễn ra: đẩy một bệnh nhân của tuần trước xuống
     * cuối hàng chờ không thay đổi được gì đã xảy ra, nhưng vẫn ghi đè {@code queueOrder} và
     * {@code lateMarkedAt} — tức là gắn nhãn "đến trễ" vào một ca đã khám xong từ lâu.
     *
     * @return null nếu còn điều phối được, ngược lại là lý do bằng tiếng Việt.
     */
    @Override
    public String whyCannotReorderQueue(Booking booking) {
        if (booking == null) {
            return "Không tìm thấy lịch hẹn.";
        }
        if (booking.getAppointmentDate() == null) {
            return "Lịch hẹn không có ngày khám.";
        }
        if (booking.getAppointmentDate().isBefore(LocalDate.now())) {
            return "Ngày khám đã qua, không điều phối hàng chờ được nữa.";
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return "Chỉ điều phối được lịch đã xác nhận và chưa khám.";
        }
        return null;
    }

    /* =========================== THU NGÂN TẠI QUẦY =========================== */

    @Override
    public String whyCannotCollectPayment(Booking booking) {
        if (booking == null) {
            return "Không tìm thấy lịch hẹn.";
        }
        if (booking.getStatus() == BookingStatus.CANCELED) {
            return "Lịch hẹn đã bị hủy nên không thu tiền.";
        }
        if (booking.getStatus() == BookingStatus.NO_SHOW) {
            return "Bệnh nhân không đến khám nên không thu tiền.";
        }
        if ("PAID".equals(booking.getPaymentStatus())) {
            return "Lịch hẹn này đã thanh toán rồi.";
        }
        if ("REFUNDED".equals(booking.getPaymentStatus())) {
            return "Lịch hẹn này đã được hoàn tiền.";
        }
        // Không có giá thì không biết thu bao nhiêu; ghi PAID cho một số tiền không xác định
        // là làm hỏng đúng con số mà tính năng này sinh ra để sửa.
        if (booking.getBookingPrice() == null) {
            return "Lịch hẹn không có giá khám, vui lòng liên hệ quản trị viên.";
        }
        return null;
    }

    @Override
    @Transactional
    public void collectCashPayment(Long bookingId, User collector) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        String blocked = whyCannotCollectPayment(booking);
        if (blocked != null) {
            throw new IllegalStateException(blocked);
        }

        booking.setPaymentStatus("PAID");
        booking.setPaidAt(LocalDateTime.now());
        booking.setCollectedBy(collector);

        // Trả tiền tại quầy CHÍNH LÀ hành động xác nhận: giữ nguyên PENDING sau khi đã cầm
        // tiền của người bệnh là để lịch treo ở trạng thái chờ duyệt một cách vô lý.
        // COMPLETED thì để nguyên — ca đã khám xong, chỉ là trả tiền muộn.
        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
        }
        bookingRepository.save(booking);

        // Chuông chứ KHÔNG email: một lá thư gần trùng lá "đã xác nhận" là thứ khiến người
        // bệnh tưởng hệ thống lỗi rồi đặt lại — xem lập luận ở nhánh PAY_AT_COUNTER.
        notificationService.pushBookingEvent(booking, "bi-cash-coin text-success",
                "Đã thanh toán tại quầy");
    }

    @Override
    public String whyCannotMarkNoShow(Booking booking) {
        if (booking == null) {
            return "Không tìm thấy lịch hẹn.";
        }
        if (booking.getStatus() == BookingStatus.NO_SHOW) {
            return "Lịch hẹn đã được đánh dấu vắng khám.";
        }
        if (booking.getStatus() == BookingStatus.CANCELED) {
            return "Lịch hẹn đã bị hủy trước đó.";
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            return "Bệnh nhân đã khám xong, không đánh dấu vắng được.";
        }

        // Chưa tới giờ hẹn thì chưa thể kết luận là vắng — bệnh nhân vẫn đang trên đường.
        LocalDateTime start = bookingService.appointmentStart(booking);
        if (start == null) {
            return "Không đọc được khung giờ của lịch hẹn.";
        }
        if (start.isAfter(LocalDateTime.now())) {
            return "Chưa tới giờ hẹn nên chưa đánh dấu vắng khám được.";
        }
        return null;
    }

    @Override
    @Transactional
    public void markNoShow(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        String blocked = whyCannotMarkNoShow(booking);
        if (blocked != null) {
            throw new IllegalStateException(blocked);
        }

        booking.setStatus(BookingStatus.NO_SHOW);
        booking.setNoShowMarkedAt(LocalDateTime.now());
        // KHÔNG đụng tới paymentStatus: xem giải thích ở ReceptionService.markNoShow.
        bookingRepository.save(booking);

        notificationService.pushBookingEvent(booking, "bi-person-x text-warning",
                "Bạn đã không tới khám theo lịch hẹn");
    }

    /* ========================================================================= */

    @Override
    public void pushToEndOfQueue(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        String blocked = whyCannotReorderQueue(booking);
        if (blocked != null) {
            throw new IllegalStateException(blocked);
        }

        // Thứ tự mới = lớn hơn mọi thứ tự đang có trong ngày của bác sĩ đó
        int nextOrder = bookingRepository
                .findByDoctorIdAndAppointmentDateAndStatusNot(
                        booking.getDoctor().getId(), booking.getAppointmentDate(), BookingStatus.CANCELED)
                .stream()
                .map(Booking::getQueueOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        booking.setQueueOrder(nextOrder);
        booking.setLateMarkedAt(LocalDateTime.now());
        bookingRepository.save(booking);
    }

    @Override
    public void resetQueuePosition(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        // Hoàn tác cũng là một lần ghi vào lịch hẹn, nên chịu đúng một luật với pushToEndOfQueue.
        // Trước đây hàm này không kiểm tra gì cả: xóa được nhãn "đến trễ" của một ca năm ngoái.
        String blocked = whyCannotReorderQueue(booking);
        if (blocked != null) {
            throw new IllegalStateException(blocked);
        }

        booking.setQueueOrder(null);
        booking.setLateMarkedAt(null);
        bookingRepository.save(booking);
    }

    @Override
    public List<Booking> sortByQueue(List<Booking> bookings) {
        List<Booking> sorted = new ArrayList<>(bookings);
        sorted.sort(
                // Người đúng giờ (queueOrder null) luôn đứng trước người bị đẩy xuống cuối
                Comparator.<Booking, Integer>comparing(b -> b.getQueueOrder() == null ? 0 : 1)
                        // Trong nhóm đúng giờ: theo giờ hẹn. Trong nhóm bị đẩy: theo thứ tự bị đẩy.
                        .thenComparing(b -> b.getQueueOrder() == null ? 0 : b.getQueueOrder())
                        .thenComparing(Booking::getAppointmentTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
        );
        return sorted;
    }

    // ===================== HELPERS =====================

    /**
     * Kiểm tra khung giờ "HH:mm - HH:mm" có thuộc buổi đang lọc không (so theo giờ bắt đầu).
     */
    private boolean matchesSession(String appointmentTime, String session) {
        String normalized = normalizeSession(session);
        if (SESSION_ALL_DAY.equals(normalized)) {
            return true;
        }
        if (appointmentTime == null || !appointmentTime.contains(" - ")) {
            return false;
        }

        LocalTime slotStart;
        try {
            slotStart = LocalTime.parse(appointmentTime.split(" - ")[0].trim(), SLOT_TIME_FORMATTER);
        } catch (Exception e) {
            return false;
        }

        return switch (normalized) {
            case SESSION_MORNING -> !slotStart.isBefore(MORNING_START) && slotStart.isBefore(MORNING_END);
            case SESSION_AFTERNOON -> !slotStart.isBefore(AFTERNOON_START) && slotStart.isBefore(AFTERNOON_END);
            default -> true;
        };
    }

    private String normalizeSession(String session) {
        if (session == null || session.isBlank()) {
            return SESSION_ALL_DAY;
        }
        return session.trim().toUpperCase();
    }
}
