package com.bookinghealthy.repository;

import com.bookinghealthy.model.MedicalAddendum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalAddendumRepository extends JpaRepository<MedicalAddendum, Long> {
    // Kéo toàn bộ ghi chú bổ sung của 1 bệnh án, sắp xếp theo thời gian cũ -> mới
    List<MedicalAddendum> findByMedicalRecordIdOrderByCreatedAtAsc(Long medicalRecordId);
}