package com.bookinghealthy.repository;

import com.bookinghealthy.model.Candidate;
import com.bookinghealthy.model.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    // Lấy danh sách ứng viên của một vị trí cụ thể
    List<Candidate> findByJobPosting(JobPosting jobPosting);

    // Lấy tất cả ứng viên mới nhất
    List<Candidate> findAllByOrderBySubmittedAtDesc();
}