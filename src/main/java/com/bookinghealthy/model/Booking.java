package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal; // <-- Thêm import
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    // (Xóa liên kết Service)

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private String appointmentTime;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // === THÊM 2 TRƯỜNG MỚI ===
    @Column(nullable = false)
    private String appointmentType; // (Lưu 'BHYT', 'Dịch vụ', 'Từ xa', 'Tại nhà')

    @Column(nullable = false)
    private BigDecimal bookingPrice; // Giá cuối cùng tại thời điểm đặt

    // === THÊM CÁC TRƯỜNG NÀY ===
    @Column(name = "payment_status")
    private String paymentStatus; // "UNPAID", "PAID", "FAILED"

    @Column(name = "payment_method")
    private String paymentMethod; // "VNPAY" (Mặc định)

    // === CÁC TRƯỜNG MỚI: THÔNG TIN NGƯỜI ĐI KHÁM (ĐẶT HỘ) ===
    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_phone")
    private String patientPhone;

    // === HÀNG CHỜ KHÁM (LỄ TÂN XỬ LÝ TRỄ GIỜ) ===
    // null = bệnh nhân đúng giờ, xếp theo appointmentTime.
    // Có giá trị = đã bị lễ tân đẩy xuống cuối, xếp sau tất cả người đúng giờ.
    @Column(name = "queue_order")
    private Integer queueOrder;

    // Thời điểm lễ tân đánh dấu trễ (để hiển thị badge và tra cứu lại)
    @Column(name = "late_marked_at")
    private LocalDateTime lateMarkedAt;

    // === BỆNH NHÂN TỰ ĐỔI LỊCH ===
    // Số lần bệnh nhân đã tự đổi bác sĩ / ngày / giờ. Chỉ tăng khi khung khám thật sự đổi,
    // sửa ghi chú hay tên người khám thì không tính. Dùng để chặn giữ chỗ ảo.
    @Column(name = "reschedule_count")
    private Integer rescheduleCount;

    @Column(name = "last_rescheduled_at")
    private LocalDateTime lastRescheduledAt;
}