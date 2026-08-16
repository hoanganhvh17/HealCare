package com.bookinghealthy.service;

import com.bookinghealthy.dto.RescheduleRequestDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingService {

    int MIN_HOURS_BEFORE_CHANGE = 24;

    int MAX_RESCHEDULE_TIMES = 2;

    Booking save(Booking booking);
    Booking reserve(Booking booking);

    boolean isSlotWithinWorkingHours(Long doctorId, java.time.LocalDate date, String timeSlot);

    List<String> slotsOutsideWorkingHours(Long doctorId, java.time.LocalDate date, List<String> slots);

    boolean hasRegisteredSchedule(Long doctorId, java.time.LocalDate date);

    List<Booking> findAll();
    Optional<Booking> findById(Long id);

    Optional<Booking> findByVnpTxnRef(String vnpTxnRef);

    boolean isBankTxnProcessed(String bankTxnRef);
    void deleteById(Long id);
    List<Booking> findByUser(User user);

    Booking reassign(Long bookingId, Long newDoctorId);

    boolean cancelWithRefund(Long bookingId, String reason);

    String whyCannotReschedule(Booking booking);

    String whyCannotCancel(Booking booking);

    String whyStaffCannotChange(Booking booking);

    Long hoursUntilAppointment(Booking booking);

    LocalDateTime appointmentStart(Booking booking);

    Booking rescheduleByUser(Long bookingId, Long userId, RescheduleRequestDTO request);
}
