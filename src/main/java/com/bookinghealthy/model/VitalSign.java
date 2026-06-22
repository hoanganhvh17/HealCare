package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vital_signs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ 1-1 với MedicalRecord (Mỗi lần khám đo 1 bộ chỉ số)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false, unique = true)
    private MedicalRecord medicalRecord;

    private Double height; // Chiều cao (cm)
    private Double weight; // Cân nặng (kg)
    private Double systolicBp; // Huyết áp tâm thu (mmHg) - Số trên
    private Double diastolicBp; // Huyết áp tâm trương (mmHg) - Số dưới
    private Double temperature; // Nhiệt độ (°C)
    private Integer heartRate; // Nhịp tim (bpm)
    private Double spo2; // Nồng độ oxy trong máu (%)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime measuredAt; // Thời gian đo
}