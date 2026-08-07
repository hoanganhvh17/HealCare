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

    /** Bệnh nhân tự khai trong hồ sơ cá nhân. */
    public static final String SOURCE_PATIENT = "PATIENT";
    /** Bác sĩ ghi nhận trong lúc khám. */
    public static final String SOURCE_DOCTOR = "DOCTOR";

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

    /**
     * Ai đưa thông tin này vào hồ sơ: {@link #SOURCE_PATIENT} hay {@link #SOURCE_DOCTOR}.
     * Bác sĩ nhìn thẻ cảnh báo đỏ cần biết mục nào đã được đồng nghiệp xác nhận, mục nào chỉ
     * là lời bệnh nhân kể; đây cũng là căn cứ để bệnh nhân KHÔNG tự xoá được mục bác sĩ ghi.
     * <p>
     * Là {@code String} chứ KHÔNG phải enum: Hibernate 6 map {@code @Enumerated(EnumType.STRING)}
     * thành cột {@code ENUM(...)} native của MySQL với danh sách giá trị chốt lúc tạo bảng, nên
     * thêm/đổi giá trị về sau sẽ vỡ trên DB dev đã có bảng (cùng lý do {@code Notification}
     * không dùng enum cho "loại thông báo").
     * <p>
     * Cố ý ĐỂ NULL ĐƯỢC: {@code ddl-auto=update} thêm cột {@code NOT NULL} mà không kèm
     * DEFAULT, đúng cái bẫy mà hai cột mồ côi trên bảng {@code bookings} từng gây ra — mọi
     * INSERT bị MySQL từ chối. Java luôn set giá trị; chỗ hiển thị coi null là "Không rõ".
     */
    @Column(length = 20)
    private String source;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}