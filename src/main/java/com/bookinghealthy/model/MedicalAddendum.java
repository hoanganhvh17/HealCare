package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "medical_addendums")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicalAddendum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ N-1: Một bệnh án có thể có nhiều lần bổ sung ghi chú
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String notes; // Nội dung bổ sung (VD: Đã có kết quả máu, chỉ định thêm thuốc A)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // Thời gian ghi chú (Dấu thời gian pháp lý)
}