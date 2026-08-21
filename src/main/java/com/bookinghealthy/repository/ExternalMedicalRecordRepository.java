package com.bookinghealthy.repository;

import com.bookinghealthy.model.ExternalMedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalMedicalRecordRepository extends JpaRepository<ExternalMedicalRecord, Long> {

    /** Danh sách đầy đủ cho trang hồ sơ của bệnh nhân và thẻ của bác sĩ. Mới nhất lên đầu. */
    List<ExternalMedicalRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Nguồn cho khối tiêm ngữ cảnh của chatbot. Chỉ lấy hồ sơ đã phân tích xong
     * ({@code aiStatus = DONE}) và giới hạn 3 — prompt hệ thống đã dài, nhồi cả chục bản tóm tắt
     * vào đó vừa đắt vừa làm loãng phần triệu chứng khách vừa kể.
     */
    List<ExternalMedicalRecord> findTop3ByUserIdAndAiStatusOrderByCreatedAtDesc(Long userId, String aiStatus);
}
