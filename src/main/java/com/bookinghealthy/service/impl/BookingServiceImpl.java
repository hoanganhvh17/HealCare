package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.DoctorBlockTime;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorBlockTimeRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    private WalletService walletService;

    @Autowired
    private EmailService emailService;

    private final ConcurrentMap<String, ReentrantLock> slotLocks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter SLOT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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
            walletService.refundToWallet(
                    booking.getUser(),
                    booking.getBookingPrice(),
                    "Hoàn tiền hủy lịch khám #" + booking.getId()
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
