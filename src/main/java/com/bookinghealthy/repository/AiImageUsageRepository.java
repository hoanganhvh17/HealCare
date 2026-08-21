package com.bookinghealthy.repository;

import com.bookinghealthy.model.AiImageUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AiImageUsageRepository extends JpaRepository<AiImageUsage, Long> {

    Optional<AiImageUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
