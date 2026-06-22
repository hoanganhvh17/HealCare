package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "allergies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Gắn trực tiếp với Bệnh nhân (Một người có thể có nhiều dị ứng)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String allergen; // Tác nhân dị ứng (Ví dụ: Penicillin, Hải sản, Phấn hoa)

    @Column(columnDefinition = "TEXT")
    private String reaction; // Biểu hiện (Ví dụ: Nổi mề đay, Khó thở)

    @Column(length = 50)
    private String severity; // Mức độ (Ví dụ: Nhẹ, Trung bình, Nặng - Sốc phản vệ)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}