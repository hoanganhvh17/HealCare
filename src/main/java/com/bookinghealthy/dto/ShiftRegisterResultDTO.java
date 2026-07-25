package com.bookinghealthy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả đăng ký ca. Tách riêng "từ chối" và "chấp nhận kèm cảnh báo" vì lịch trực có
 * quy định phải công bố trước 1 tuần (Thông tư 32/2023/TT-BYT) — đăng ký sát hơn thì vẫn
 * nhận nhưng phải báo cho người đăng ký và trưởng khoa biết.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRegisterResultDTO {

    private boolean success;

    /** Lý do từ chối, tiếng Việt, đã trích căn cứ pháp lý khi cần. */
    private String error;

    /** Cảnh báo hiển thị màu vàng khi đăng ký thành công nhưng chưa đúng thông lệ. */
    private String warning;

    public static ShiftRegisterResultDTO reject(String error) {
        return new ShiftRegisterResultDTO(false, error, null);
    }

    public static ShiftRegisterResultDTO ok() {
        return new ShiftRegisterResultDTO(true, null, null);
    }

    public static ShiftRegisterResultDTO okWithWarning(String warning) {
        return new ShiftRegisterResultDTO(true, null, warning);
    }
}
