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
}