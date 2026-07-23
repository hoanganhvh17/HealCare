package com.bookinghealthy.dto;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả của một thao tác hàng loạt ở quầy lễ tân (hủy lịch / chuyển bác sĩ).
 * Mỗi lịch được xử lý độc lập: một lịch lỗi không làm hỏng các lịch còn lại.
 */
@Getter
public class BulkResultDTO {

    private int successCount = 0;
    private final List<String> errors = new ArrayList<>();

    public void addSuccess() {
        this.successCount++;
    }

    public void addError(String message) {
        this.errors.add(message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
