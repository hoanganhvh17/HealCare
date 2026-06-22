package com.bookinghealthy.repository;

import com.bookinghealthy.model.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    // Lấy chỉ số sinh tồn của một lần khám cụ thể
    Optional<VitalSign> findByMedicalRecordId(Long medicalRecordId);
}