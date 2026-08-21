package com.bookinghealthy.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Kết quả MỘT lượt gọi model thị giác cho ảnh khách gửi vào khung chat.
 *
 * <p>Một lượt gọi trả về cả PHÂN LOẠI lẫn PHÂN TÍCH. Tách làm hai lượt (phân loại trước, phân tích
 * sau) là nhân đôi chi phí và độ trễ cho mọi tấm ảnh, trong khi model nhìn một lần là biết cả hai.
 *
 * <p>Dùng Lombok {@code @Getter} chứ KHÔNG dùng {@code record}: các DTO của dự án đều theo khuôn
 * này, và bất kỳ thứ gì có thể lọt ra Thymeleaf đều cần accessor dạng {@code getX()}.
 */
@Getter
@Setter
public class ImageAnalysis {

    /** Giấy tờ y tế — lưu thành {@code ExternalMedicalRecord}. */
    public static final String KIND_DOCUMENT = "DOCUMENT";
    /** Ảnh chụp chỗ đang bị đau trên cơ thể — TƯ VẤN rồi xoá, tuyệt đối không lưu. */
    public static final String KIND_SYMPTOM = "SYMPTOM";
    /** Không phải hai loại trên (ảnh phong cảnh, ảnh chụp màn hình...). */
    public static final String KIND_OTHER = "OTHER";

    /** Nguy cơ cần cấp cứu ngay — đi vào đúng nhánh {@code is_emergency} của khung chat. */
    public static final String URGENCY_EMERGENCY = "EMERGENCY";
    /** Nên khám sớm trong vài ngày. */
    public static final String URGENCY_SOON = "SOON";
    /** Khám khi thu xếp được. */
    public static final String URGENCY_ROUTINE = "ROUTINE";

    private String kind;

    // ===== phần dùng cho KIND_DOCUMENT =====
    /** Bản tóm tắt tiếng Việt đã ghép sẵn, lưu thẳng vào {@code ExternalMedicalRecord.aiSummary}. */
    private String documentSummary;
    /** Khoa gợi ý, ĐÃ đối chiếu {@code DepartmentRepository}. Null nếu model bịa id không có thật. */
    private Long documentDepartmentId;

    // ===== phần dùng cho KIND_SYMPTOM =====
    /** Vùng cơ thể model nhìn thấy, ví dụ "mắt trái". */
    private String bodyPart;
    /** Các dấu hiệu NHÌN THẤY được, không phải chẩn đoán. */
    private List<String> findings;
    /** Khoa gợi ý, ĐÃ đối chiếu DB. Rỗng thì khung chat không vẽ thẻ bác sĩ nào. */
    private List<Long> departmentIds;
    /** Một trong {@link #URGENCY_EMERGENCY} / {@link #URGENCY_SOON} / {@link #URGENCY_ROUTINE}. */
    private String urgency;
    /** Lời khuyên chăm sóc tạm thời, một hai câu. */
    private String advice;

    /** Câu giải thích cho nhánh {@link #KIND_OTHER}, hoặc lý do không đọc được ảnh. */
    private String message;

    public boolean isDocument() {
        return KIND_DOCUMENT.equals(kind);
    }

    public boolean isSymptom() {
        return KIND_SYMPTOM.equals(kind);
    }

    public boolean isEmergency() {
        return URGENCY_EMERGENCY.equals(urgency);
    }
}
