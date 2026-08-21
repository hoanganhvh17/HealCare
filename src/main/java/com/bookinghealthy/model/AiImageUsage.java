package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Đếm số ảnh một tài khoản đã nhờ AI đọc trong ngày — một dòng cho mỗi (người dùng, ngày).
 *
 * <p>Tồn tại vì mỗi tấm ảnh là MỘT lượt gọi model thị giác trả tiền, và trước đây cả dự án không
 * có bất kỳ hạn mức nào trên AI: một vòng lặp gửi ảnh đốt sạch {@code OPENROUTER_API_KEY} dùng
 * chung, và khi hết credit thì khung chat hỏng cho MỌI người dùng chứ không riêng người gây ra.
 *
 * <p>Đếm CẢ giấy tờ hồ sơ lẫn ảnh triệu chứng: thứ tốn tiền là lượt gọi thị giác, không phải loại
 * nội dung trong ảnh.
 *
 * <p>Phải là bảng chứ không phải một map trong bộ nhớ như {@code AiController.softLockCache}: cái
 * map kia là bộ giảm va chạm, mất là không sao; hạn mức chi phí mà reset mỗi lần khởi động lại thì
 * chỉ cần restart là lách được, và nó cũng không đúng khi chạy nhiều instance.
 *
 * <p><b>Không khai {@code @AllArgsConstructor}</b> — cùng luật với {@code Notification} và
 * {@code ExternalMedicalRecord}. Mọi cột {@code NOT NULL} đều có giá trị khởi tạo phía Java, vì
 * {@code ddl-auto=update} thêm cột NOT NULL mà KHÔNG kèm DEFAULT.
 */
@Entity
@Table(
        name = "ai_image_usage",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_image_usage_user_day",
                columnNames = {"user_id", "usage_date"})
)
@Getter
@Setter
@NoArgsConstructor
public class AiImageUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate = LocalDate.now();

    @Column(nullable = false)
    private int count = 0;
}
