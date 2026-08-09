package com.bookinghealthy.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor // Tự động sinh ra hàm tạo rỗng: new AiRequest()
@AllArgsConstructor // Tự động sinh ra hàm tạo có tất cả tham số
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiRequest {
    private String model;
    private List<AiMessage> messages;
    private double temperature; // Độ sáng tạo (0.0 đến 1.0)

    /**
     * Trần độ dài câu trả lời. Trợ lý bệnh nhân bắt buộc trả JSON đủ 10 keys (kèm `patient_summary`
     * cộng dồn qua từng lượt); model nào có max_tokens mặc định thấp sẽ CẮT GIỮA CHỪNG khối JSON đó,
     * `JSON.parse` ở trình duyệt hỏng và khách nhìn thấy JSON thô trong khung chat.
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    public AiRequest(String model, String prompt) {
        this.model = model;
        this.messages = new ArrayList<>();
        this.messages.add(new AiMessage("user", prompt));
        this.temperature = 0.7; // Trả lời ổn định, không quá bay bổng
    }
}
