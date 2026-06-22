package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title; // Tiêu đề bài viết

    @Column(length = 1000)
    private String summary; // Tóm tắt ngắn (hiển thị ở danh sách)

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // Nội dung chính (HTML dài)

    private String image; // Ảnh đại diện bài viết

    private LocalDateTime createdAt; // Ngày đăng

    // === THÊM CỘT NÀY ===
    private String category; // Giá trị: "NEWS" (Tin tức) hoặc "KNOWLEDGE" (Y học)
    
    @Column(nullable = false)
    private String status = "PUBLISHED"; // Giá trị: "DRAFT" (Nháp) hoặc "PUBLISHED" (Đã xuất bản). Mặc định là PUBLISHED để tương thích với dữ liệu cũ.

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author; // Người đăng (Admin)

    // Tự động gán ngày giờ hiện tại khi tạo mới
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}