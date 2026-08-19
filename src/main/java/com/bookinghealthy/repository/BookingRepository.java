package com.bookinghealthy.repository;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    List<Booking> findByUser(User user);

    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user", "doctor.department"})
    List<Booking> findByDoctor(Doctor doctor);

    @Override
    @EntityGraph(attributePaths = {
            "user",
            "doctor",
            "doctor.user",
            "doctor.department"
    })
    List<Booking> findAll();

    @Override
    @EntityGraph(attributePaths = {
            "user",
            "doctor",
            "doctor.user",
            "doctor.department"
    })
    Optional<Booking> findById(Long id);
     @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
     List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);
    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
    List<Booking> findAllByOrderByCreatedAtDesc();

    @Query(value = "SELECT DATE(created_at) as date, COUNT(id) as count " +
            "FROM bookings " +
            "WHERE created_at >= CURDATE() - INTERVAL 7 DAY " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date ASC", nativeQuery = true)
    List<Object[]> getBookingStatsForLast7Days();

    long countByDoctor_Department_IdAndStatus(Long departmentId, BookingStatus status);

    List<Booking> findByDoctorIdAndAppointmentDateAndStatusNot(Long doctorId, LocalDate appointmentDate, BookingStatus status);

    boolean existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNot(Long doctorId, LocalDate appointmentDate, String appointmentTime, BookingStatus status);

    @EntityGraph(attributePaths = {"user", "doctor"})
    List<Booking> findByDoctorIdAndAppointmentDateAndStatus(Long doctorId, LocalDate appointmentDate, BookingStatus status);

    @EntityGraph(attributePaths = {"user", "doctor"})
    List<Booking> findByDoctorIdAndStatus(Long doctorId, BookingStatus status);

    long countByStatus(BookingStatus status);

    @EntityGraph(attributePaths = {"doctor", "doctor.department"})
    Optional<Booking> findFirstByUserIdAndStatusOrderByAppointmentDateDesc(Long userId, BookingStatus status);

    List<Booking> findByStatusAndPaymentStatusAndCreatedAtBefore(BookingStatus status, String paymentStatus, java.time.LocalDateTime cutoffTime);

    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
    List<Booking> findByAppointmentDateAndReminderSentFalseAndStatusIn(
            LocalDate appointmentDate, java.util.Collection<BookingStatus> statuses);

    boolean existsByUserIdAndAppointmentDateGreaterThanEqualAndStatusNot(Long userId, LocalDate fromDate, BookingStatus status);

    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
    Optional<Booking> findByVnpTxnRef(String vnpTxnRef);

    boolean existsByBankTxnRef(String bankTxnRef);

    @EntityGraph(attributePaths = {"user"})
    List<Booking> findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(Long doctorId, LocalDate appointmentDate);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.doctor.id = :doctorId AND b.status != :status AND b.appointmentDate >= :startDate AND b.appointmentDate <= :endDate")
    long countByDoctorIdAndStatusNotAndDateRange(@Param("doctorId") Long doctorId, @Param("status") BookingStatus status, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.doctor.id = :doctorId AND b.status = :status AND b.appointmentDate >= :startDate AND b.appointmentDate <= :endDate")
    long countByDoctorIdAndStatusAndDateRange(@Param("doctorId") Long doctorId, @Param("status") BookingStatus status, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.doctor.id = :doctorId AND b.appointmentDate >= :startDate AND b.appointmentDate <= :endDate")
    long countByDoctorIdAndDateRange(@Param("doctorId") Long doctorId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.doctor.id = :doctorId AND b.status = :status AND b.appointmentDate <= :date AND NOT EXISTS (SELECT m FROM MedicalRecord m WHERE m.booking.id = b.id)")
    long countIncompleteRecordsByDoctor(@Param("doctorId") Long doctorId, @Param("status") BookingStatus status, @Param("date") LocalDate date);



    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT b FROM Booking b WHERE b.doctor.id = :doctorId AND b.appointmentDate >= :startDate AND b.appointmentDate <= :endDate ORDER BY b.appointmentDate ASC, b.appointmentTime ASC")
    List<Booking> findDetailedBookingsForAi(@Param("doctorId") Long doctorId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(b.bookingPrice) FROM Booking b WHERE b.paymentStatus = 'PAID'")
    BigDecimal sumTotalDeposit();

    @Query("SELECT SUM(b.bookingPrice) FROM Booking b WHERE b.paymentStatus = 'PAID' AND b.createdAt BETWEEN :start AND :end")
    BigDecimal sumDepositByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(b.bookingPrice) FROM Booking b WHERE b.status = com.bookinghealthy.model.BookingStatus.CANCELED AND b.paymentStatus = 'PAID'")
    BigDecimal sumTotalRefund();

    @Query("SELECT SUM(b.bookingPrice) FROM Booking b WHERE b.status = com.bookinghealthy.model.BookingStatus.CANCELED AND b.paymentStatus = 'PAID' AND b.createdAt BETWEEN :start AND :end")
    BigDecimal sumRefundByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Dùng cho UserService.whyCannotDelete — lịch hẹn kéo theo hồ sơ bệnh án.
    long countByUserId(Long userId);
}
