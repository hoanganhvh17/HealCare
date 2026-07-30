package com.bookinghealthy.service.impl;

import com.bookinghealthy.dto.RescheduleRequestDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.DoctorBlockTime;
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorBlockTimeRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.ScheduleRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class BookingServiceImpl implements BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorBlockTimeRepository doctorBlockTimeRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private EmailService emailService;

    private final ConcurrentMap<String, ReentrantLock> slotLocks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter SLOT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter PAST_APPOINTMENT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    public Booking reserve(Booking booking) {
        final String slotKey = buildSlotKey(booking.getDoctor().getId(), booking.getAppointmentDate(), booking.getAppointmentTime());
        final ReentrantLock lock = slotLocks.computeIfAbsent(slotKey, key -> new ReentrantLock());
        final boolean[] releaseAfterReturn = {true};

        lock.lock();
        try {
            boolean booked = bookingRepository.existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNot(
                    booking.getDoctor().getId(),
                    booking.getAppointmentDate(),
                    booking.getAppointmentTime(),
                    BookingStatus.CANCELED
            );

            if (booked) {
                throw new IllegalStateException("Khung giờ này đã có người giữ chỗ, vui lòng chọn lịch khác.");
            }

            Booking savedBooking = bookingRepository.save(booking);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                releaseAfterReturn[0] = false;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        lock.unlock();
                        slotLocks.remove(slotKey, lock);
                    }
                });
            }

            return savedBooking;
        } finally {
            if (releaseAfterReturn[0]) {
                lock.unlock();
                slotLocks.remove(slotKey, lock);
            }
        }
    }

    // ===================== KIỂM TRA KHUNG GIỜ THEO LỊCH LÀM VIỆC =====================

    @Override
    public boolean isSlotWithinWorkingHours(Long doctorId, LocalDate date, String timeSlot) {
        // Lịch khám gắn với từng TUẦN — phải lấy đúng lịch có hiệu lực của tuần chứa ngày này,
        // không gộp lịch của mọi tuần lại với nhau.
        List<Schedule> schedules = scheduleRepository.findEffective(doctorId, date);
        // Bác sĩ chưa đăng ký lịch nào -> không giới hạn (giữ hành vi cũ cho dữ liệu seed).
        if (schedules.isEmpty()) {
            return true;
        }
        LocalTime[] bounds = parseSlotBounds(timeSlot);
        if (bounds == null) {
            return true;
        }
        return isWithinAnySchedule(schedules, date, bounds[0], bounds[1]);
    }

    @Override
    public List<String> slotsOutsideWorkingHours(Long doctorId, LocalDate date, List<String> slots) {
        List<Schedule> schedules = scheduleRepository.findEffective(doctorId, date);
        List<String> outside = new java.util.ArrayList<>();
        // Chưa đăng ký lịch -> không gạch bỏ khung nào.
        if (schedules.isEmpty()) {
            return outside;
        }
        for (String slot : slots) {
            LocalTime[] bounds = parseSlotBounds(slot);
            if (bounds != null && !isWithinAnySchedule(schedules, date, bounds[0], bounds[1])) {
                outside.add(slot);
            }
        }
        return outside;
    }

    /** Slot nằm trọn trong ít nhất một ca làm việc của bác sĩ đúng thứ trong tuần. */
    private boolean isWithinAnySchedule(List<Schedule> schedules, LocalDate date,
                                        LocalTime slotStart, LocalTime slotEnd) {
        for (Schedule schedule : schedules) {
            if (schedule.getDayOfWeek() == date.getDayOfWeek()
                    && !slotStart.isBefore(schedule.getStartTime())
                    && !slotEnd.isAfter(schedule.getEndTime())) {
                return true;
            }
        }
        return false;
    }

    /** "07:30 - 08:00" -> [07:30, 08:00]; null nếu sai định dạng. */
    private LocalTime[] parseSlotBounds(String timeSlot) {
        if (timeSlot == null || !timeSlot.contains(" - ")) {
            return null;
        }
        try {
            String[] parts = timeSlot.split(" - ");
            return new LocalTime[]{
                    LocalTime.parse(parts[0].trim(), SLOT_TIME_FORMATTER),
                    LocalTime.parse(parts[1].trim(), SLOT_TIME_FORMATTER)
            };
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Booking reassign(Long bookingId, Long newDoctorId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        Doctor newDoctor = doctorRepository.findById(newDoctorId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy bác sĩ tiếp nhận."));

        if (booking.getDoctor() != null && newDoctorId.equals(booking.getDoctor().getId())) {
            throw new IllegalStateException("Lịch hẹn đã thuộc về bác sĩ này rồi.");
        }

        if (booking.getStatus() == BookingStatus.CANCELED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Chỉ chuyển được lịch đang chờ hoặc đã xác nhận.");
        }

        // Ràng buộc chuyên môn: không được đẩy bệnh nhân sang bác sĩ khác khoa.
        // Kiểm tra ở đây (không chỉ ẩn trên giao diện) vì đây là chỗ duy nhất đổi bác sĩ.
        Long currentDepartmentId = (booking.getDoctor() != null && booking.getDoctor().getDepartment() != null)
                ? booking.getDoctor().getDepartment().getId() : null;
        Long newDepartmentId = (newDoctor.getDepartment() != null)
                ? newDoctor.getDepartment().getId() : null;

        if (currentDepartmentId == null || newDepartmentId == null || !currentDepartmentId.equals(newDepartmentId)) {
            throw new IllegalStateException("Chỉ chuyển được sang bác sĩ cùng chuyên khoa.");
        }

        final LocalDate date = booking.getAppointmentDate();
        final String time = booking.getAppointmentTime();

        // Dùng CHUNG cơ chế khóa với reserve() để hai luồng không cùng chiếm 1 slot
        final String slotKey = buildSlotKey(newDoctorId, date, time);
        final ReentrantLock lock = slotLocks.computeIfAbsent(slotKey, key -> new ReentrantLock());
        final boolean[] releaseAfterReturn = {true};

        lock.lock();
        try {
            boolean booked = bookingRepository.existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNot(
                    newDoctorId, date, time, BookingStatus.CANCELED);

            if (booked) {
                throw new IllegalStateException("Bác sĩ tiếp nhận đã có lịch vào khung giờ " + time + ".");
            }

            if (isBlockedForDoctor(newDoctorId, date, time)) {
                throw new IllegalStateException("Bác sĩ tiếp nhận đã chặn khung giờ " + time + ".");
            }

            // Giữ nguyên bookingPrice (giá đã chốt lúc đặt) — không thu thêm/hoàn lại
            booking.setDoctor(newDoctor);
            Booking savedBooking = bookingRepository.save(booking);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                releaseAfterReturn[0] = false;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        lock.unlock();
                        slotLocks.remove(slotKey, lock);
                    }
                });
            }

            return savedBooking;
        } finally {
            if (releaseAfterReturn[0]) {
                lock.unlock();
                slotLocks.remove(slotKey, lock);
            }
        }
    }

    @Override
    public boolean cancelWithRefund(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELED) {
            throw new IllegalStateException("Lịch hẹn #" + bookingId + " đã bị hủy trước đó.");
        }

        booking.setStatus(BookingStatus.CANCELED);

        boolean refunded = false;
        if ("PAID".equals(booking.getPaymentStatus())) {
            // Ghi luôn lý do vào sổ ví: bộ phận hỗ trợ cần biết AI hủy, không chỉ là "đã hoàn".
            walletService.refundToWallet(
                    booking.getUser(),
                    booking.getBookingPrice(),
                    "Hoàn tiền hủy lịch khám #" + booking.getId()
                            + ((reason != null && !reason.isBlank()) ? " — " + reason : "")
            );
            booking.setPaymentStatus("REFUNDED");
            refunded = true;
        } else {
            booking.setPaymentStatus("FAILED");
        }

        bookingRepository.save(booking);
        emailService.sendBookingCancellation(booking, reason);

        return refunded;
    }

    // ===================== BỆNH NHÂN TỰ ĐỔI LỊCH =====================

    @Override
    public LocalDateTime appointmentStart(Booking booking) {
        if (booking == null || booking.getAppointmentDate() == null) {
            return null;
        }
        LocalTime start = parseSlotStart(booking.getAppointmentTime());
        return (start == null) ? null : LocalDateTime.of(booking.getAppointmentDate(), start);
    }

    @Override
    public Long hoursUntilAppointment(Booking booking) {
        LocalDateTime start = appointmentStart(booking);
        if (start == null) {
            return null;
        }
        return ChronoUnit.HOURS.between(LocalDateTime.now(), start);
    }

    @Override
    public String whyCannotCancel(Booking booking) {
        if (booking == null) {
            return "Không tìm thấy lịch hẹn.";
        }
        if (booking.getStatus() == BookingStatus.CANCELED) {
            return "Lịch hẹn đã bị hủy trước đó.";
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            return "Lịch hẹn đã khám xong nên không hủy được nữa.";
        }

        Long hoursLeft = hoursUntilAppointment(booking);
        if (hoursLeft == null) {
            return "Không đọc được khung giờ của lịch hẹn, vui lòng liên hệ hotline.";
        }
        if (hoursLeft < MIN_HOURS_BEFORE_CHANGE) {
            return "Không thể hủy lịch khi còn dưới " + MIN_HOURS_BEFORE_CHANGE
                    + " tiếng trước giờ khám. Vui lòng liên hệ hotline 0985 123 888 để được hỗ trợ.";
        }
        return null;
    }

    @Override
    public String whyStaffCannotChange(Booking booking) {
        if (booking == null) {
            return "Không tìm thấy lịch hẹn.";
        }
        if (booking.getStatus() == BookingStatus.CANCELED) {
            return "Lịch hẹn đã bị hủy trước đó.";
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            return "Lịch hẹn đã khám xong nên không sửa được nữa.";
        }

        LocalDateTime start = appointmentStart(booking);
        if (start == null) {
            return "Không đọc được khung giờ của lịch hẹn, vui lòng liên hệ hotline.";
        }
        // So cả giờ chứ không chỉ ngày: ca 08:00 sáng nay đã trôi qua vào buổi chiều.
        if (start.isBefore(LocalDateTime.now())) {
            return "Lịch hẹn đã trôi qua (" + start.format(PAST_APPOINTMENT_FORMATTER)
                    + ") nên không sửa được nữa.";
        }
        return null;
    }

    @Override
    public String whyCannotReschedule(Booking booking) {
        // Đổi lịch có đủ mọi điều kiện của hủy lịch, cộng thêm hạn mức số lần
        String cancelBlock = whyCannotCancel(booking);
        if (cancelBlock != null) {
            // Riêng câu chữ về mốc 24 tiếng thì đổi lại cho đúng ngữ cảnh "đổi lịch"
            Long hoursLeft = hoursUntilAppointment(booking);
            if (hoursLeft != null && hoursLeft < MIN_HOURS_BEFORE_CHANGE
                    && booking.getStatus() != BookingStatus.CANCELED
                    && booking.getStatus() != BookingStatus.COMPLETED) {
                return "Chỉ đổi được lịch khi còn cách giờ khám trên " + MIN_HOURS_BEFORE_CHANGE
                        + " tiếng. Vui lòng liên hệ hotline để được hỗ trợ.";
            }
            return cancelBlock;
        }

        int used = rescheduleCountOf(booking);
        if (used >= MAX_RESCHEDULE_TIMES) {
            return "Bạn đã đổi lịch " + used + " lần (tối đa " + MAX_RESCHEDULE_TIMES
                    + " lần). Vui lòng liên hệ hotline để được hỗ trợ.";
        }
        return null;
    }

    @Override
    public Booking rescheduleByUser(Long bookingId, Long userId, RescheduleRequestDTO request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            throw new IllegalStateException("Bạn không có quyền sửa lịch hẹn này.");
        }

        // Kiểm tra lại ở server chứ không tin vào việc giao diện đã ẩn nút
        String blockedReason = whyCannotReschedule(booking);
        if (blockedReason != null) {
            throw new IllegalStateException(blockedReason);
        }

        if (request.getDoctorId() == null || request.getAppointmentDate() == null
                || request.getAppointmentTime() == null || request.getAppointmentTime().isBlank()) {
            throw new IllegalStateException("Vui lòng chọn đầy đủ bác sĩ, ngày khám và khung giờ.");
        }

        Doctor newDoctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy bác sĩ đã chọn."));

        // Cùng ràng buộc chuyên khoa như reassign(): người bệnh không được tự nhảy sang khoa khác,
        // vì giá đã chốt và hồ sơ bệnh án gắn với chuyên môn của khoa ban đầu.
        Long currentDepartmentId = (booking.getDoctor() != null && booking.getDoctor().getDepartment() != null)
                ? booking.getDoctor().getDepartment().getId() : null;
        Long newDepartmentId = (newDoctor.getDepartment() != null) ? newDoctor.getDepartment().getId() : null;

        if (currentDepartmentId == null || newDepartmentId == null || !currentDepartmentId.equals(newDepartmentId)) {
            throw new IllegalStateException("Chỉ đổi được sang bác sĩ cùng chuyên khoa với lịch hẹn ban đầu.");
        }

        final LocalDate newDate = request.getAppointmentDate();
        final String newTime = request.getAppointmentTime().trim();

        LocalTime newStart = parseSlotStart(newTime);
        if (newStart == null) {
            throw new IllegalStateException("Khung giờ không hợp lệ: " + newTime);
        }
        if (LocalDateTime.of(newDate, newStart).isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Khung giờ vừa chọn đã trôi qua, vui lòng chọn khung giờ khác.");
        }

        boolean slotUnchanged = booking.getDoctor() != null
                && newDoctor.getId().equals(booking.getDoctor().getId())
                && newDate.equals(booking.getAppointmentDate())
                && newTime.equals(booking.getAppointmentTime());

        // Chỉ sửa ghi chú / thông tin người khám thì không đụng tới slot và không tính là một lần đổi lịch
        if (slotUnchanged) {
            applyEditableFields(booking, request);
            return bookingRepository.save(booking);
        }

        // Giữ lại thông tin cũ để đưa vào email "trước → sau"
        final String oldDoctorName = (booking.getDoctor() != null && booking.getDoctor().getUser() != null)
                ? "Dr. " + booking.getDoctor().getUser().getFullName() : "Bác sĩ trước đó";
        final LocalDate oldDate = booking.getAppointmentDate();
        final String oldTime = booking.getAppointmentTime();

        // Dùng CHUNG khóa với reserve()/reassign() để hai người cùng nhắm một slot không thể cùng chiếm
        final String slotKey = buildSlotKey(newDoctor.getId(), newDate, newTime);
        final ReentrantLock lock = slotLocks.computeIfAbsent(slotKey, key -> new ReentrantLock());
        final boolean[] releaseAfterReturn = {true};

        lock.lock();
        try {
            boolean booked = bookingRepository.existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNot(
                    newDoctor.getId(), newDate, newTime, BookingStatus.CANCELED);

            if (booked) {
                throw new IllegalStateException("Khung giờ " + newTime + " đã có người giữ chỗ, vui lòng chọn giờ khác.");
            }

            if (isBlockedForDoctor(newDoctor.getId(), newDate, newTime)) {
                throw new IllegalStateException("Bác sĩ đã khóa khung giờ " + newTime + ", vui lòng chọn giờ khác.");
            }

            // Khung giờ phải nằm trong ca khám bác sĩ đã đăng ký. Giao diện /user/booking/edit đã
            // gạch sẵn các khung ngoài ca (qua booked-slots), nhưng POST thẳng thì vẫn lọt — đúng
            // lỗ hổng đã bịt cho processAppointment, chỗ này bị sót.
            if (!isSlotWithinWorkingHours(newDoctor.getId(), newDate, newTime)) {
                throw new IllegalStateException(
                        "Bác sĩ không nhận khám vào khung giờ " + newTime + " ngày "
                                + newDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                + ", vui lòng chọn khung giờ khác.");
            }

            booking.setDoctor(newDoctor);
            booking.setAppointmentDate(newDate);
            booking.setAppointmentTime(newTime);

            // Đổi sang khung khám mới thì vị trí hàng chờ cũ của lễ tân không còn ý nghĩa
            booking.setQueueOrder(null);
            booking.setLateMarkedAt(null);

            booking.setRescheduleCount(rescheduleCountOf(booking) + 1);
            booking.setLastRescheduledAt(LocalDateTime.now());

            // Giữ nguyên bookingPrice và paymentStatus: bệnh nhân không bị thu thêm
            // dù bác sĩ mới có giá niêm yết khác.
            applyEditableFields(booking, request);
            Booking savedBooking = bookingRepository.save(booking);

            emailService.sendBookingRescheduled(savedBooking, oldDoctorName, oldDate, oldTime);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                releaseAfterReturn[0] = false;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        lock.unlock();
                        slotLocks.remove(slotKey, lock);
                    }
                });
            }

            return savedBooking;
        } finally {
            if (releaseAfterReturn[0]) {
                lock.unlock();
                slotLocks.remove(slotKey, lock);
            }
        }
    }

    /** Các trường bệnh nhân được sửa tự do, không ảnh hưởng tới việc giữ slot. */
    private void applyEditableFields(Booking booking, RescheduleRequestDTO request) {
        if (request.getAppointmentType() != null && !request.getAppointmentType().isBlank()) {
            booking.setAppointmentType(request.getAppointmentType().trim());
        }

        // Bỏ trống thì quay về thông tin chủ tài khoản, giống lúc đặt lịch lần đầu
        String name = request.getPatientName();
        booking.setPatientName((name != null && !name.isBlank())
                ? name.trim() : booking.getUser().getFullName());

        String phone = request.getPatientPhone();
        booking.setPatientPhone((phone != null && !phone.isBlank())
                ? phone.trim() : booking.getUser().getPhone());

        booking.setNotes(request.getNotes());
    }

    private int rescheduleCountOf(Booking booking) {
        return booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount();
    }

    private LocalTime parseSlotStart(String appointmentTime) {
        if (appointmentTime == null || appointmentTime.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(appointmentTime.split(" - ")[0].trim(), SLOT_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Kiểm tra khung giờ "HH:mm - HH:mm" có giao với giờ bận đột xuất của bác sĩ không.
     * (Cùng logic giao khoảng đang dùng ở BookingApi và TimeSlotService)
     */
    private boolean isBlockedForDoctor(Long doctorId, LocalDate date, String appointmentTime) {
        List<DoctorBlockTime> blockedTimes = doctorBlockTimeRepository.findByDoctorIdAndBlockDate(doctorId, date);
        if (blockedTimes.isEmpty()) {
            return false;
        }

        String[] parts = appointmentTime.split(" - ");
        if (parts.length != 2) {
            return false; // Không parse được thì bỏ qua, tránh chặn nhầm
        }

        LocalTime slotStart = LocalTime.parse(parts[0].trim(), SLOT_TIME_FORMATTER);
        LocalTime slotEnd = LocalTime.parse(parts[1].trim(), SLOT_TIME_FORMATTER);

        for (DoctorBlockTime block : blockedTimes) {
            if (slotStart.isBefore(block.getEndTime()) && slotEnd.isAfter(block.getStartTime())) {
                return true;
            }
        }
        return false;
    }

    private String buildSlotKey(Long doctorId, LocalDate appointmentDate, String appointmentTime) {
        return doctorId + "|" + appointmentDate + "|" + appointmentTime;
    }

    // === THÊM 3 PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return bookingRepository.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        bookingRepository.deleteById(id);
    }

    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public List<Booking> findByUser(User user) {
        return bookingRepository.findByUser(user);
    }
}
