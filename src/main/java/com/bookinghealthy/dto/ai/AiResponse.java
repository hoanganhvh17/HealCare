package com.bookinghealthy.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiResponse {
    private List<Choice> choices;

    /**
     * OpenRouter trả HTTP 200 kèm {"error": {...}} khi hết credit, dính rate limit, hoặc model id
     * không tồn tại — KHÔNG ném exception. Không map field này thì `choices` chỉ đơn giản là null,
     * code lặng lẽ nhảy sang model kế tiếp và cuối cùng khách nhận "Hệ thống bận. Thử lại sau."
     * với log hoàn toàn sạch, không có gì để lần ra nguyên nhân.
     */
    private ApiError error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private int index;
        private AiMessage message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiError {
        private Integer code;
        private String message;
        private String type;
    }
}
