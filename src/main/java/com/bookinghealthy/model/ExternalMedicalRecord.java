package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Hồ sơ bệnh án bệnh nhân mang từ NƠI KHÁC tới (bệnh viện khác, tuyến dưới) và tự tải lên.
 *
 * <p><b>Vì sao không dùng lại {@link MedicalAttachment}:</b> entity đó khai
 * {@code @JoinColumn(name = "medical_record_id", nullable = false)}, tức bắt buộc phải có một
 * {@link MedicalRecord} — thứ chỉ tồn tại SAU khi bác sĩ của viện này khám xong. Hồ sơ cũ tồn tại
 * TRƯỚC mọi lịch hẹn, nên nó phải khoá thẳng vào {@link User}.
 *
 * <p><b>Không khai {@code @AllArgsConstructor}</b> — cùng lý do với {@code Notification} và
 * {@code StaffProfile}: {@code DataInitializer} dựng {@code User}/{@code Doctor}/{@code Department}
 * theo VỊ TRÍ tham số, nên entity nào mang constructor đó là entity ấy có thể làm vỡ bộ seed khi
 * thêm field. Dựng bằng setter.
 *
 * <p><b>Tệp nằm ở {@code app.private-dir}, không phải {@code app.upload-dir}.</b> Đây là dữ liệu
 * sức khoẻ; {@code /uploads/**} là {@code permitAll} và trên production nginx phục vụ thẳng thư mục
 * đó — nơi Spring Security không hề chạy. Cùng lập luận đã dùng cho CV ứng viên.
 */
@Entity
@Table(name = "external_medical_records")
@Getter
@Setter
@NoArgsConstructor
public class ExternalMedicalRecord {

    /** Ảnh chụp giấy tờ — đọc bằng model có thị giác. */
    public static final String TYPE_IMAGE = "IMAGE";
    /** Tệp PDF — bóc chữ bằng PDFBox. */
    public static final String TYPE_PDF = "PDF";

    /** Đã lưu tệp nhưng chưa phân tích (hoặc AI đang tắt bằng cờ cấu hình). */
    public static final String AI_PENDING = "PENDING";
    /** Phân tích xong, {@link #aiSummary} dùng được. */
    public static final String AI_DONE = "DONE";
    /** Gọi AI hỏng hoặc JSON trả về không đọc được — bệnh nhân bấm "Phân tích lại". */
    public static final String AI_FAILED = "FAILED";
    /** PDF bản scan: không có lớp chữ nào để bóc, phải mời bệnh nhân chụp ảnh từng trang. */
    public static final String AI_UNREADABLE = "UNREADABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Bệnh nhân tự đặt, ví dụ "Khám tại BV Bạch Mai 03/2026". */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * CHỈ tên tệp đã lưu, không phải đường dẫn — giống {@code Candidate.cvFile}.
     * {@code FileStorageService.resolveMedicalDocument} mới là chỗ biến nó thành {@link java.nio.file.Path}
     * tuyệt đối đã kiểm path traversal.
     */
    @Column(nullable = false, length = 255)
    private String storedFileName;

    /** Tên gốc lúc bệnh nhân tải lên, chỉ để hiển thị. */
    @Column(length = 255)
    private String originalFileName;

    /** Content-Type trình duyệt gửi lên, trả lại đúng thứ đó lúc tải xuống. */
    @Column(length = 100)
    private String contentType;

    @Column
    private Long fileSize;

    /**
     * {@link #TYPE_IMAGE} hay {@link #TYPE_PDF} — quyết định đường bóc nội dung.
     * Là {@code String} chứ KHÔNG phải enum, cùng cái bẫy cột {@code ENUM(...)} native của MySQL
     * đã ghi ở {@link Allergy#getSource()}: {@code ddl-auto=update} không bao giờ viết lại danh
     * sách giá trị, nên thêm một loại tệp về sau sẽ ném "Data truncated" trên DB dev đã có bảng.
     */
    @Column(length = 20)
    private String docType;

    /** Bệnh nhân tự ghi chú thêm (triệu chứng lúc đó, lý do khám...). */
    @Column(columnDefinition = "TEXT")
    private String patientNote;

    /**
     * Bản tóm tắt AI dựng MỘT LẦN lúc tải lên và lưu lại.
     * Khối tiêm ngữ cảnh trong {@code AiService.chatWithMemory} đọc thẳng trường này, nên một lượt
     * chat KHÔNG phát sinh thêm lượt gọi AI nào.
     */
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    /** Một trong {@link #AI_PENDING} / {@link #AI_DONE} / {@link #AI_FAILED} / {@link #AI_UNREADABLE}. */
    @Column(length = 20)
    private String aiStatus;

    /**
     * Khoa AI gợi ý. Bắt buộc đối chiếu {@code DepartmentRepository.findById} TRƯỚC khi ghi —
     * model bịa id là chuyện đã xảy ra nhiều lần trong dự án này; id không có thật thì để null.
     */
    @Column
    private Long aiDepartmentId;

    @Column
    private LocalDateTime analyzedAt;

    /**
     * Cột {@code NOT NULL} nhưng có sẵn giá trị khởi tạo phía JAVA — không dùng
     * {@code @CreationTimestamp} để giữ đúng khuôn {@code Notification}: {@code ddl-auto=update}
     * thêm cột NOT NULL mà KHÔNG kèm DEFAULT, nên mọi giá trị non-null phải do Java bảo đảm.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
