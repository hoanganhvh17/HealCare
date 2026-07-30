package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Thông báo trong ứng dụng, gửi tới MỘT người nhận.
 *
 * Lý do có bảng này: trước đây mọi quyết định của trưởng khoa chỉ được thông báo qua email
 * ({@code EmailServiceImpl} chạy {@code @Async} và lỗi thì chỉ in ra {@code System.err}),
 * còn chuông thì tính lại từ đầu mỗi lần gọi API nên không có trạng thái đã đọc, không có
 * lịch sử và không thể bấm vào. Bác sĩ vì thế không biết đơn của mình được duyệt hay không.
 *
 * KHÔNG dùng {@code @AllArgsConstructor} (xem lý do ở {@link StaffProfile}), và cố tình
 * không dùng enum cho "loại thông báo": Hibernate map enum thành cột {@code ENUM(...)} của
 * MySQL với danh sách giá trị chốt lúc tạo bảng, nên thêm loại mới về sau sẽ vỡ trên DB cũ.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** Class icon của Bootstrap Icons, ví dụ "bi-check-circle text-success". */
    @Column(length = 60)
    private String icon;

    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Dòng phụ: khoảng ngày, tên ca, lời phê của trưởng khoa…
     * <p>
     * Giữ đúng 255 (độ dài mặc định của Hibernate cho String): {@code ddl-auto=update} chỉ
     * THÊM cột, không nới rộng cột đã có, nên khai 500 sẽ chạy trên DB mới mà vỡ
     * ("Data truncated") trên DB dev đã có bảng này từ trước.
     */
    @Column(length = 255)
    private String message;

    /** Đường dẫn mở khi bấm vào thông báo. Null thì thông báo chỉ để đọc. */
    @Column(length = 255)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean readFlag = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
