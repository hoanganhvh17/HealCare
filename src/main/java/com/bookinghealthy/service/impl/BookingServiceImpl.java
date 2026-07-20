package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class BookingServiceImpl implements BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    private final ConcurrentMap<String, ReentrantLock> slotLocks = new ConcurrentHashMap<>();

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
