package com.bookinghealthy.repository;

import com.bookinghealthy.model.AiRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiRuleRepository extends JpaRepository<AiRule, Long> {
    // Spring Data JPA tự động viết câu Query lấy các rule có is_active = true
    List<AiRule> findByIsActiveTrue();
}