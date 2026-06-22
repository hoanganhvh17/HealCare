package com.bookinghealthy.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {
    private String role; // "user" (người dùng hỏi), "system" (kịch bản), "assistant" (AI trả lời)
    private String content; // Nội dung câu chat
}