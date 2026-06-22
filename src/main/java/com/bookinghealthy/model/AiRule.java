package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String keyword; // Từ khóa kích hoạt (VD: đau bụng, khó thở)

    @Column(columnDefinition = "TEXT", nullable = false)
    private String ruleContent; // Nội dung hướng dẫn xử lý cho AI

    // === CÁC TRƯỜNG NÂNG CẤP CHO VERSION 2.0 (TRIAGE ENGINE) ===

    @Column(name = "rule_type")
    private String ruleType; // SYMPTOM (Triệu chứng), EMERGENCY (Cấp cứu), BOOKING (Hỏi đặt lịch)

    @Column(name = "severity_level")
    private String severityLevel; // LOW (Nhẹ), MEDIUM (Vừa), HIGH (Nặng), EMERGENCY (Khẩn cấp)

    @Column(name = "action_type")
    private String actionType; // ASK_MORE (Hỏi thêm), ADVISE (Khuyên), BOOK (Gợi ý khám), URGENT_BOOK (Khám ngay lập tức)

    @Column(name = "red_flags", columnDefinition = "TEXT")
    private String redFlags; // Các dấu hiệu nguy hiểm cần hỏi (VD: Nôn ra máu, sốt cao > 39 độ)

    @Column(name = "department_tag")
    private String departmentTag; // Mã Khoa để AI chèn vào (VD: [BOOK_DEPT_2])

    @Column(name = "is_active")
    private Boolean isActive = true;
}