package com.bookinghealthy.dto.ai;

import lombok.Data;
import java.util.List;

@Data
public class AiResponse {
    private List<Choice> choices;

    @Data
    public static class Choice {
        private int index;
        private AiMessage message;
    }
}