package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ n-1 với MedicalRecord (Một bệnh án có nhiều loại thuốc)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @Column(nullable = false)
    private String medicineName; // Tên thuốc (VD: Paracetamol)

    @Column(length = 50)
    private String dosage; // Hàm lượng (VD: 500mg)

    private Integer quantity; // Số lượng (VD: 10)

    @Column(length = 50)
    private String unit; // Đơn vị tính (VD: Viên, Gói, Lọ)

    @Column(columnDefinition = "TEXT")
    private String instructions; // Liều dùng & Cách dùng (VD: Uống ngày 2 lần, sau ăn)
}