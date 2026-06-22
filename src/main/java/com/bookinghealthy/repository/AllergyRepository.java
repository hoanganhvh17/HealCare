package com.bookinghealthy.repository;

import com.bookinghealthy.model.Allergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, Long> {
    // Tìm tất cả các dị ứng của một bệnh nhân để cảnh báo Bác sĩ
    List<Allergy> findByUserId(Long userId);
}