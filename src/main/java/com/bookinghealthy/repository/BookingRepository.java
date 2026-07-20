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

    // Lấy lịch sử hẹn của Bệnh nhân
    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    List<Booking> findByUser(User user);

    // === ĐÃ XÓA @Override GÂY LỖI ===
    // Thêm @EntityGraph để Doctor Dashboard tải đủ thông tin
    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user", "doctor.department"})
    List<Booking> findByDoctor(Doctor doctor);

    // (Hàm findAll() giữ nguyên @Override - vì nó đúng)
    @Override
    @EntityGraph(attributePaths = {
            "user",
            "doctor",
            "doctor.user",
            "doctor.department"
    })
    List<Booking> findAll();

    // (Hàm findById() giữ nguyên @Override - vì nó đúng)
    @Override
    @EntityGraph(attributePaths = {
            "user",
            "doctor",
            "doctor.user",
            "doctor.department"
    })
    Optional<Booking> findById(Long id);
//    // === THÊM HÀM MỚI NÀY ===
//    /**
//     * Lấy 5 lịch hẹn mới nhất (dựa trên thời gian tạo)
//     * Tải tất cả thông tin liên quan để hiển thị trên Dashboard
//     */
//    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
//    List<Booking> findFirst5ByOrderByCreatedAtDesc();
     @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
     List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);
    // === THÊM HÀM MỚI NÀY ===
    /**
     * Lấy TẤT CẢ lịch hẹn, sắp xếp mới nhất lên đầu
     * Tải tất cả thông tin liên quan để hiển thị trên Dashboard
     */
    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
    List<Booking> findAllByOrderByCreatedAtDesc();

    // === THÊM HÀM MỚI NÀY ===
    /**
     * Lấy dữ liệu thống kê cho Biểu đồ Dashboard.
     * Đếm số lượng lịch hẹn (bookings) theo từng ngày (DATE(created_at))
     * trong 7 ngày qua (CURDATE() - INTERVAL 7 DAY)
     * (Đây là một câu Native Query SQL)
     */
    @Query(value = "SELECT DATE(created_at) as date, COUNT(id) as count " +
            "FROM bookings " +
            "WHERE created_at >= CURDATE() - INTERVAL 7 DAY " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date ASC", nativeQuery = true)
    List<Object[]> getBookingStatsForLast7Days();

    // === THÊM HÀM MỚI NÀY ===
    // Đếm số lượng lịch hẹn theo Khoa và Trạng thái (ví dụ: COMPLETED)
    long countByDoctor_Department_IdAndStatus(Long departmentId, BookingStatus status);

    // === MODULE 9: THÊM HÀM NÀY ĐỂ CHẶN TRÙNG LỊCH ===
    // Tìm tất cả các lịch của Bác sĩ X vào Ngày Y mà trạng thái KHÔNG PHẢI là Z (Đã hủy)
    // (Nghĩa là lấy các lịch PENDING, CONFIRMED, COMPLETED để chặn giờ đó lại)
    List<Booking> findByDoctorIdAndAppointmentDateAndStatusNot(Long doctorId, LocalDate appointmentDate, BookingStatus status);

    boolean existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNot(Long doctorId, LocalDate appointmentDate, String appointmentTime, BookingStatus status);

    // === MODULE 11: LẤY DANH SÁCH KHÁM BỆNH ===
    // Tìm lịch hẹn của Bác sĩ + Ngày cụ thể + Trạng thái cụ thể
    // Dùng để lấy danh sách "Bệnh nhân cần khám hôm nay" (Status = CONFIRMED)
    // Thêm @EntityGraph để tải luôn User và Doctor, tránh lỗi HibernateProxy
    @EntityGraph(attributePaths = {"user", "doctor"})
    List<Booking> findByDoctorIdAndAppointmentDateAndStatus(Long doctorId, LocalDate appointmentDate, BookingStatus status);

    // Hàm này cũng nên thêm để tối ưu khi xem lịch sử
    @EntityGraph(attributePaths = {"user", "doctor"})
    List<Booking> findByDoctorIdAndStatus(Long doctorId, BookingStatus status);

    long countByStatus(BookingStatus status);

    // === THÊM MỚI: Lấy lịch khám HOÀN THÀNH gần nhất của User để AI đọc bệnh án ===
    @EntityGraph(attributePaths = {"doctor", "doctor.department"})
    Optional<Booking> findFirstByUserIdAndStatusOrderByAppointmentDateDesc(Long userId, BookingStatus status);

    // === THÊM MỚI: Hỗ trợ Cron Job dọn rác lịch hẹn treo ===
    // Tìm các lịch PENDING, UNPAID và có thời gian tạo trước một mốc thời gian (cutoffTime)
    List<Booking> findByStatusAndPaymentStatusAndCreatedAtBefore(BookingStatus status, String paymentStatus, java.time.LocalDateTime cutoffTime);

    // === AI AGENT FOR DOCTOR ===
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
}
