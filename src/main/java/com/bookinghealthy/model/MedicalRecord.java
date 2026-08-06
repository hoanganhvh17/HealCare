package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

import static com.bookinghealthy.model.MedicalRecordStatus.*;

@Entity
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ 1-1 với Lịch hẹn (Mỗi lịch hẹn chỉ có 1 hồ sơ bệnh án)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(columnDefinition = "TEXT")
    private String symptoms; // Triệu chứng

    @Column(columnDefinition = "TEXT")
    private String diagnosis; // Chẩn đoán

    @Column(columnDefinition = "TEXT")
    private String prescription; // Đơn thuốc & Cách dùng

    @Column(columnDefinition = "TEXT")
    private String doctorNotes; // Lời dặn dò

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // Ngày giờ tạo hồ sơ

    // === THÊM MỚI (GIAI ĐOẠN 1): Mã chuẩn hóa bệnh ICD-10 ===
    @Column(name = "diagnosis_code", length = 50)
    private String diagnosisCode; // Mã ICD-10 (Ví dụ: J00, E11)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MedicalRecordStatus status = DRAFT; // DRAFT, COMPLETED

    /**
     * Đã gửi nhắc tái khám cho hồ sơ này chưa — cờ chống nhắc trùng của {@code FollowUpReminderTask}.
     * Đặt true sau khi job đã XỬ LÝ xong hồ sơ (đã gửi, hoặc xác định không cần gửi vì bệnh nhân đã
     * tự đặt lịch mới / đã quá hạn nhắc), KHÔNG đặt true chỉ vì chưa tới hạn — job cần thử lại các
     * hồ sơ đó vào những ngày sau.
     */
    @Column(nullable = false)
    private boolean followUpReminderSent = false;
}