package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "medical_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicalAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ n-1 với MedicalRecord (Một lần khám có nhiều ảnh chụp)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @Column(nullable = false)
    private String fileUrl; // Đường dẫn file (Upload lên Cloud hoặc Local)

    @Column(length = 100)
    private String fileType; // Loại (Ví dụ: X-Quang, Siêu âm, Xét nghiệm máu)

    private String description; // Ghi chú của bác sĩ về hình ảnh này

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime uploadedAt;
}