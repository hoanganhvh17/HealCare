package com.bookinghealthy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một ô "AI Insight" trên dashboard bác sĩ.
 *
 * Chữ trong ô do luật cố định bên {@code DoctorInsightService} sinh ra — KHÔNG gọi LLM.
 * AI thật chỉ chạy khi bác sĩ BẤM vào ô: template gắn {@link #prompt} vào thuộc tính
 * {@code data-ai-prompt}, JS mở khung chat và bắn câu đó sang {@code /api/doctor/chat/ask}.
 *
 * Để {@code prompt} nằm chung DTO là có chủ đích: câu hỏi gửi cho AI nằm ngay cạnh luật
 * sinh ra câu nhận định, thay vì rải rác trong 8 thuộc tính onclick của template.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorInsightDTO {

    /** Câu nhận định tiếng Việt hiển thị trong ô. */
    private String advice;

    /** Class Bootstrap tô màu câu nhận định: text-success / text-warning / text-danger / text-muted. */
    private String colorClass;

    /** Câu hỏi bắn vào trợ lý AI khi bác sĩ bấm vào ô. */
    private String prompt;
}
