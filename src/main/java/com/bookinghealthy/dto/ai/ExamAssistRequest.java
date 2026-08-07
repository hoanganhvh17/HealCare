package com.bookinghealthy.dto.ai;

import lombok.Data;

import java.util.List;

/**
 * Dữ liệu bác sĩ đang GÕ DỞ trên form khám, gửi lên cho các trợ lý AI ở
 * {@code DoctorExamAiController}.
 *
 * Cố ý nhận nội dung đang gõ từ trình duyệt chứ không đọc từ DB: lúc bác sĩ bấm "kiểm tra
 * đơn thuốc" hay "soạn lời dặn" thì {@code MedicalRecord} CHƯA tồn tại — hồ sơ chỉ được lưu
 * một lần khi bấm "Lưu Bệnh Án". Riêng phần nhạy cảm (dị ứng, tiền sử) thì server tự đọc từ
 * DB theo {@code bookingId}, không tin dữ liệu client gửi lên.
 */
@Data
public class ExamAssistRequest {

    /** Ca khám đang mở. Server dùng nó để kiểm tra quyền sở hữu và tra dữ liệu bệnh nhân. */
    private Long bookingId;

    private String symptoms;
    private String diagnosis;

    /** Các dòng thuốc đang có trên bảng kê đơn. */
    private List<Medicine> medicines;

    @Data
    public static class Medicine {
        private String name;
        private String dosage;
        private String instructions;
    }
}
