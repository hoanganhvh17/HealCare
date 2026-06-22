package com.bookinghealthy.repository;

import com.bookinghealthy.model.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {
    // Lấy chi tiết đơn thuốc (các loại thuốc) của một lần khám
    List<PrescriptionItem> findByMedicalRecordId(Long medicalRecordId);
}