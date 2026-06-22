package com.bookinghealthy.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor // Tự động sinh ra hàm tạo rỗng: new AiRequest()
@AllArgsConstructor // Tự động sinh ra hàm tạo có tất cả tham số
public class AiRequest {
    private String model;
    private List<AiMessage> messages;
    private double temperature; // Độ sáng tạo (0.0 đến 1.0)

    public AiRequest(String model, String prompt) {
        this.model = model;
        this.messages = new ArrayList<>();
        this.messages.add(new AiMessage("user", prompt));
        this.temperature = 0.7; // Trả lời ổn định, không quá bay bổng
    }
}