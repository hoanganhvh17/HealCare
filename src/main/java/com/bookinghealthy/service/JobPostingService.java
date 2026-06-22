package com.bookinghealthy.service;

import com.bookinghealthy.model.JobPosting;
import java.util.List;
import java.util.Optional;

public interface JobPostingService {
    List<JobPosting> findAll(); // Lấy tất cả (cho Admin)
    List<JobPosting> findActiveJobs(); // Lấy tin đang mở (cho User)
    Optional<JobPosting> findById(Long id);
    JobPosting save(JobPosting jobPosting);
    void deleteById(Long id);
}