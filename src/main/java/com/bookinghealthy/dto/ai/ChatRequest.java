package com.bookinghealthy.dto.ai;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId; // Mã phiên chat (Frontend sẽ tự sinh ra dạng chuỗi random)
    private String prompt;    // Câu hỏi của người dùng
    private String pageContent;// ngữ cảnh từ màn hình
}