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

    /**
     * Link bài gốc trên báo, với bài do MedicalNewsTask thu thập. Bài admin tự viết thì để null.
     *
     * Đây cũng là KHÓA CHỐNG TRÙNG thật sự khi thu thập ({@code existsBySourceUrl}) — trước đây
     * task so trùng bằng chuỗi con của nguyên cái tiêu đề, cách đó gần như không bao giờ khớp.
     *
     * Cả hai cột đều CỐ Ý nullable: đây là cột thêm vào bảng posts vốn đã có dữ liệu, mà
     * ddl-auto=update thì thêm cột NOT NULL không kèm DEFAULT — đúng cái bẫy đã làm hỏng toàn bộ
     * việc đặt lịch (xem environment-setup.md).
     */
    @Column(length = 500)
    private String sourceUrl;

    /** Tên tòa soạn để hiện ở khối "Nguồn:" cuối bài, vd "VnExpress Sức khỏe". */
    private String sourceName;

    // Tự động gán ngày giờ hiện tại khi tạo mới
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}