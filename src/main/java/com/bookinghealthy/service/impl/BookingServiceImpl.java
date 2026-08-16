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
import com.bookinghealthy.service.NotificationService;
import com.bookinghealthy.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private NotificationService notificationService;

    private final ConcurrentMap<String, ReentrantLock> slotLocks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter SLOT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter PAST_APPOINTMENT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }

    private Booking saveGuardingSlot(Booking booking, String conflictMessage) {
        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(conflictMessage, e);
        }
    }

    @Override
    @Transactional
    public Booking reserve(Booking booking) {
        if (booking.getAppointmentDate() != null
                && booking.getAppointmentDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Ngày khám "
                    + booking.getAppointmentDate() + " đã qua, vui lòng chọn ngày khác.");
        }
        if (booking.getAppointmentTime() != null) {
            booking.setAppointmentTime(booking.getAppointmentTime().trim());
        }
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
            Booking savedBooking = saveGuardingSlot(booking,
                    "Khung giờ này đã có người giữ chỗ, vui lòng chọn lịch khác.");
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
    public boolean isSlotWithinWorkingHours(Long doctorId, LocalDate date, String timeSlot) {
        List<Schedule> schedules = scheduleRepository.findEffective(doctorId, date);
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

    @Override
    public boolean hasRegisteredSchedule(Long doctorId, LocalDate date) {
        return !scheduleRepository.findEffective(doctorId, date).isEmpty();
    }

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
    @Transactional
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
        Long currentDepartmentId = (booking.getDoctor() != null && booking.getDoctor().getDepartment() != null)
                ? booking.getDoctor().getDepartment().getId() : null;
        Long newDepartmentId = (newDoctor.getDepartment() != null)
                ? newDoctor.getDepartment().getId() : null;
        if (currentDepartmentId == null || newDepartmentId == null || !currentDepartmentId.equals(newDepartmentId)) {
            throw new IllegalStateException("Chỉ chuyển được sang bác sĩ cùng chuyên khoa.");
        }
        final LocalDate date = booking.getAppointmentDate();
        final String time = booking.getAppointmentTime();
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
            booking.setDoctor(newDoctor);
            Booking savedBooking = saveGuardingSlot(booking,
                    "Bác sĩ tiếp nhận đã có lịch vào khung giờ " + time + ".");
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
        notificationService.pushBookingEvent(booking, "bi-x-circle text-danger", "Lịch hẹn đã bị hủy");

        return refunded;
    }

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
        if (start.isBefore(LocalDateTime.now())) {
            return "Lịch hẹn đã trôi qua (" + start.format(PAST_APPOINTMENT_FORMATTER)
                    + ") nên không sửa được nữa.";
        }
        return null;
    }

    @Override
    public String whyCannotReschedule(Booking booking) {
        String cancelBlock = whyCannotCancel(booking);
        if (cancelBlock != null) {
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
    @Transactional
    public Booking rescheduleByUser(Long bookingId, Long userId, RescheduleRequestDTO request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            throw new IllegalStateException("Bạn không có quyền sửa lịch hẹn này.");
        }

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

        if (slotUnchanged) {
            applyEditableFields(booking, request);
            return bookingRepository.save(booking);
        }
        final String oldDoctorName = (booking.getDoctor() != null && booking.getDoctor().getUser() != null)
                ? "Dr. " + booking.getDoctor().getUser().getFullName() : "Bác sĩ trước đó";
        final LocalDate oldDate = booking.getAppointmentDate();
        final String oldTime = booking.getAppointmentTime();
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
            if (!isSlotWithinWorkingHours(newDoctor.getId(), newDate, newTime)) {
                throw new IllegalStateException(
                        "Bác sĩ không nhận khám vào khung giờ " + newTime + " ngày "
                                + newDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                + ", vui lòng chọn khung giờ khác.");
            }
            booking.setDoctor(newDoctor);
            booking.setAppointmentDate(newDate);
            booking.setAppointmentTime(newTime);
            booking.setQueueOrder(null);
            booking.setLateMarkedAt(null);
            booking.setRescheduleCount(rescheduleCountOf(booking) + 1);
            booking.setLastRescheduledAt(LocalDateTime.now());
            applyEditableFields(booking, request);
            Booking savedBooking = saveGuardingSlot(booking,
                    "Khung giờ " + newTime + " đã có người giữ chỗ, vui lòng chọn giờ khác.");
            emailService.sendBookingRescheduled(savedBooking, oldDoctorName, oldDate, oldTime);
            notificationService.pushBookingEvent(savedBooking, "bi-arrow-repeat text-primary",
                    "Dời lịch thành công");
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

    private void applyEditableFields(Booking booking, RescheduleRequestDTO request) {
        if (request.getAppointmentType() != null && !request.getAppointmentType().isBlank()) {
            booking.setAppointmentType(request.getAppointmentType().trim());
        }
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

    private boolean isBlockedForDoctor(Long doctorId, LocalDate date, String appointmentTime) {
        List<DoctorBlockTime> blockedTimes = doctorBlockTimeRepository.findByDoctorIdAndBlockDate(doctorId, date);
        if (blockedTimes.isEmpty()) {
            return false;
        }
        String[] parts = appointmentTime.split(" - ");
        if (parts.length != 2) {
            return false;
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

    @Override
    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return bookingRepository.findById(id);
    }

    @Override
    public Optional<Booking> findByVnpTxnRef(String vnpTxnRef) {
        if (vnpTxnRef == null || vnpTxnRef.isBlank()) {
            return Optional.empty();
        }
        return bookingRepository.findByVnpTxnRef(vnpTxnRef);
    }

    @Override
    public boolean isBankTxnProcessed(String bankTxnRef) {
        if (bankTxnRef == null || bankTxnRef.isBlank()) {
            return false;
        }
        return bookingRepository.existsByBankTxnRef(bankTxnRef);
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
