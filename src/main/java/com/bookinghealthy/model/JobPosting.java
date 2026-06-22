package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "job_postings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title; // Vị trí (VD: Bác sĩ Nội trú)

    private Integer quantity; // Số lượng cần tuyển

    @Column(columnDefinition = "TEXT")
    private String description; // Mô tả công việc

    @Column(columnDefinition = "TEXT")
    private String requirements; // Yêu cầu ứng viên

    @Column(columnDefinition = "TEXT")
    private String benefits; // Quyền lợi

    private String degree; // Bằng cấp yêu cầu (Đại học, Thạc sĩ...)

    private String salary; // Mức lương (VD: "Thỏa thuận", "15-20 triệu")

    private boolean isActive; // Trạng thái: true = Đang tuyển, false = Đã đóng

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        isActive = true; // Mặc định khi tạo là đang tuyển
    }
}