package com.bookinghealthy.repository;

import com.bookinghealthy.model.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    // Lấy danh sách tin đang tuyển (isActive = true), mới nhất lên đầu
    List<JobPosting> findByIsActiveTrueOrderByCreatedAtDesc();

    // Lấy tất cả (cho Admin), mới nhất lên đầu
    List<JobPosting> findAllByOrderByCreatedAtDesc();
}