package com.bookinghealthy.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Toàn bộ nội dung thư "đã có hồ sơ bệnh án", dựng sẵn thành CHUỖI THUẦN.
 *
 * Tồn tại vì {@code EmailServiceImpl} gửi thư bằng {@code @Async}, tức trên MỘT LUỒNG KHÁC.
 * {@code Booking.user}, {@code Booking.doctor}, {@code Doctor.user} đều là
 * {@code @ManyToOne(fetch = LAZY)}: trao thẳng entity sang luồng đó thì mọi proxy chưa nạp sẽ
 * ném {@code LazyInitializationException} (open-in-view chỉ mở session cho luồng phục vụ
 * request), và ngoại lệ ấy rơi đúng vào khối catch nuốt lỗi của {@code EmailServiceImpl} —
 * thư lặng lẽ không tới nơi, còn dòng log thì trông y hệt một lỗi SMTP.
 *
 * Nên {@code MedicalRecordDeliveryServiceImpl} đọc hết dữ liệu khi còn ở luồng request rồi mới
 * trao DTO này đi. Đừng thêm entity vào đây.
 */
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecordMailDTO {

    private String toEmail;
    private String patientName;

    private Long bookingId;
    private Long recordId;

    private String doctorName;
    private String departmentName;

    /** dd/MM/yyyy */
    private String visitDate;

    /** Khung giờ khám, ví dụ "09:00 - 09:30". */
    private String visitTime;

    private String symptoms;
    private String diagnosis;

    /** Mã ICD-10, có thể null nếu bác sĩ không nhập. */
    private String diagnosisCode;

    private String doctorNotes;

    /** Một dòng chỉ số sinh tồn do {@code VitalSignFormatter} dựng, null khi bác sĩ không đo. */
    private String vitals;

    private List<MedicineLine> medicines = new ArrayList<>();

    /** Đơn thuốc bác sĩ gõ tự do; chỉ hiện khi bảng thuốc bên trên rỗng. */
    private String prescriptionText;

    /** Một dòng thuốc trong đơn thuốc điện tử. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicineLine {
        private String name;
        private String dosage;

        /** "10 Viên" — gộp sẵn số lượng với đơn vị để template khỏi phải nối chuỗi. */
        private String amount;

        private String instructions;
    }
}
