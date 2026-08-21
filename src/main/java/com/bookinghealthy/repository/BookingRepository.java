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

    /**
     * Lịch giữ chỗ chờ TRẢ TRƯỚC mà khách bỏ dở — dùng bởi BookingCleanupTask.
     *
     * Cố ý loại hình thức trả-tại-quầy: nó không có gì để chờ, huỷ nó sau 3 phút là huỷ
     * một lịch hoàn toàn hợp lệ.
     *
     * Vế "b.paymentMethod IS NULL OR" là BẮT BUỘC, không phải phòng xa: cột payment_method
     * nullable, mà trong SQL "NULL <> :x" cho UNKNOWN chứ không phải TRUE — thiếu vế đó là
     * mọi lịch cũ có paymentMethod NULL thoát job vĩnh viễn, treo khung giờ mãi mãi mà
     * không có lấy một dòng lỗi.
     *
     * Đây là DENY-LIST chứ không phải allow-list, để giữ nguyên hành vi hiện tại cho mọi
     * giá trị chưa biết. Cái giá: phương thức thanh toán THÊM SAU NÀY sẽ mặc định bị huỷ
     * sau 3 phút trừ khi có người nhớ thêm vào vế loại trừ. Bẫy sinh đôi với nhánh else
     * catch-all trong BookingController.processAppointment.
     */
    @Query("SELECT b FROM Booking b "
         + "WHERE b.status = :status "
         + "AND b.paymentStatus = :paymentStatus "
         + "AND b.createdAt < :cutoff "
         + "AND (b.paymentMethod IS NULL OR b.paymentMethod <> :keepMethod)")
    List<Booking> findAbandonedPrepayBookings(@Param("status") BookingStatus status,
                                              @Param("paymentStatus") String paymentStatus,
                                              @Param("cutoff") LocalDateTime cutoff,
                                              @Param("keepMethod") String keepMethod);

    // Hạn mức chống spam của BookingService.whyCannotBookWithoutPayment.
    // So theo NGÀY chứ không theo giờ vì appointmentTime là chuỗi "08:00 - 08:30".
    long countByUserIdAndPaymentMethodAndStatusInAndAppointmentDateGreaterThanEqual(
            Long userId, String paymentMethod,
            java.util.Collection<BookingStatus> statuses, LocalDate fromDate);

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

    /* =========================================================================================
     * SỐ LIỆU TIỀN CHO DASHBOARD ADMIN
     *
     * MỌI truy vấn tổng ở đây BẮT BUỘC bọc COALESCE(..., 0). SUM trên 0 dòng trả về NULL trong
     * SQL, và bốn truy vấn cũ ở đúng chỗ này không bọc — nên hai thẻ tiền trên /admin/dashboard
     * in ra đúng chữ "null đ" trên mọi cơ sở dữ liệu chưa phát sinh giao dịch, tức là trên
     * production ngay sau khi deploy. Khuôn đúng đã có sẵn ở LeaveRequestRepository.sumApprovedDays.
     *
     * paymentStatus là TRẠNG THÁI SỐNG, không phải sự kiện lịch sử. Một lịch đã thu tiền rồi hoàn
     * lại chuyển PAID -> REFUNDED, nên "đã thu" phải gồm CẢ HAI giá trị; lọc mỗi 'PAID' là số tiền
     * đó biến mất khỏi tổng đã thu mà không để lại dấu vết nào (trên DB dev: 1.500.000đ).
     *
     *     đã thu (gross) = ('PAID','REFUNDED')      đã hoàn = ('REFUNDED')
     *     thất thoát do bỏ dở = ('EXPIRED')         thực thu = gross − đã hoàn
     *
     * Cố ý KHÔNG có truy vấn riêng cho "thực thu": nó được trừ trong Java từ đúng hai số trên.
     * Đó là thứ bảo đảm ba thẻ trên màn hình luôn cộng trừ khớp nhau, kể cả khi dữ liệu lệch.
     *
     * Truy vấn cũ sumTotalRefund() lọc status = CANCELED AND paymentStatus = 'PAID' — một tổ hợp
     * KHÔNG BAO GIỜ tồn tại, vì mọi đường huỷ đều ghi đè paymentStatus trong cùng transaction
     * (BookingServiceImpl -> REFUNDED/FAILED, PaymentController -> FAILED, BookingCleanupTask ->
     * EXPIRED). Nó trả NULL vĩnh viễn. Đừng khôi phục lại vị từ đó.
     * ========================================================================================= */

    @Query("SELECT COALESCE(SUM(b.bookingPrice), 0) FROM Booking b WHERE b.paymentStatus IN :statuses")
    BigDecimal sumPriceByPaymentStatusIn(@Param("statuses") java.util.Collection<String> statuses);

    @Query("SELECT COALESCE(SUM(b.bookingPrice), 0) FROM Booking b "
         + "WHERE b.paymentStatus IN :statuses AND b.createdAt BETWEEN :from AND :to")
    BigDecimal sumPriceByPaymentStatusInAndCreatedAtBetween(@Param("statuses") java.util.Collection<String> statuses,
                                                            @Param("from") LocalDateTime from,
                                                            @Param("to") LocalDateTime to);

    /** Tiền còn phải thu: lịch chưa trả tiền mà vẫn còn hiệu lực (sắp tới hoặc đã khám xong). */
    @Query("SELECT COALESCE(SUM(b.bookingPrice), 0) FROM Booking b "
         + "WHERE b.paymentStatus = 'UNPAID' AND b.status IN :statuses")
    BigDecimal sumUnpaidByStatusIn(@Param("statuses") java.util.Collection<BookingStatus> statuses);

    long countByPaymentStatus(String paymentStatus);

    long countByPaymentStatusAndStatusIn(String paymentStatus, java.util.Collection<BookingStatus> statuses);

    /* ===== Nhóm gộp cho biểu đồ — mỗi cái MỘT truy vấn, không lặp theo từng khoa/từng ngày ===== */

    @Query("SELECT b.status, COUNT(b) FROM Booking b "
         + "WHERE b.createdAt BETWEEN :from AND :to GROUP BY b.status")
    List<Object[]> countByStatusInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT b.doctor.department.name, COUNT(b) FROM Booking b "
         + "WHERE b.createdAt BETWEEN :from AND :to AND b.doctor.department IS NOT NULL "
         + "GROUP BY b.doctor.department.name ORDER BY COUNT(b) DESC")
    List<Object[]> countByDepartmentInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT b.appointmentTime, COUNT(b) FROM Booking b "
         + "WHERE b.createdAt BETWEEN :from AND :to AND b.appointmentTime IS NOT NULL "
         + "GROUP BY b.appointmentTime")
    List<Object[]> countBySlotInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Số lượt đặt theo từng NGÀY trong kỳ. Ngày không có lịch hẹn nào thì KHÔNG có dòng nào ở đây —
     * nơi gọi BẮT BUỘC phải zero-fill trước khi vẽ, bằng không biểu đồ nhảy cóc qua ngày trống thay
     * vì vẽ số 0 (bản cũ getBookingStatsForLast7Days mắc đúng lỗi này: 7 ngày qua chỉ ra 1 điểm).
     */
    @Query("SELECT FUNCTION('DATE', b.createdAt), COUNT(b) FROM Booking b "
         + "WHERE b.createdAt BETWEEN :from AND :to "
         + "GROUP BY FUNCTION('DATE', b.createdAt) ORDER BY FUNCTION('DATE', b.createdAt)")
    List<Object[]> countPerDayInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /* ===== Lịch đã qua ngày hẹn nhưng chưa đóng (CONFIRMED và appointmentDate < hôm nay) ===== */

    long countByStatusAndAppointmentDateBefore(BookingStatus status, LocalDate date);

    @Query("SELECT COALESCE(SUM(b.bookingPrice), 0) FROM Booking b "
         + "WHERE b.status = :status AND b.appointmentDate < :date")
    BigDecimal sumPriceByStatusAndAppointmentDateBefore(@Param("status") BookingStatus status,
                                                        @Param("date") LocalDate date);

    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
    List<Booking> findTop10ByStatusAndAppointmentDateBeforeOrderByAppointmentDateAsc(BookingStatus status, LocalDate date);

    /* ===== Hôm nay ===== */

    long countByAppointmentDateAndStatusIn(LocalDate date, java.util.Collection<BookingStatus> statuses);

    long countByAppointmentDateAndStatus(LocalDate date, BookingStatus status);

    /**
     * Thay cho findAllByOrderByCreatedAtDesc() trên dashboard: bảng gắn nhãn "gần đây" mà đổ toàn
     * bộ bảng bookings ra là vừa sai nhãn vừa không có giới hạn tăng trưởng.
     */
    @EntityGraph(attributePaths = {"user", "doctor", "doctor.user"})
    List<Booking> findTop20ByOrderByCreatedAtDesc();

    /**
     * Đếm dòng hỏng mã ký tự. Cột appointment_type còn 5 dòng chứa dấu "?" thật (hex
     * 443F636820763F = "D?ch v?"), di chứng của lỗi characterEncoding trong DB_URL đã sửa 19/08 —
     * chúng tạo ra một nhóm "Dịch vụ" thứ hai trong mọi thống kê theo loại khám.
     */
    long countByAppointmentTypeContaining(String fragment);

    // Dùng cho UserService.whyCannotDelete — lịch hẹn kéo theo hồ sơ bệnh án.
    long countByUserId(Long userId);
}
