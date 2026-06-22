package com.bookinghealthy.repository;

import com.bookinghealthy.model.MedicalAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalAttachmentRepository extends JpaRepository<MedicalAttachment, Long> {
    // Lấy tất cả ảnh X-Quang, siêu âm... của một lần khám
    List<MedicalAttachment> findByMedicalRecordId(Long medicalRecordId);
}