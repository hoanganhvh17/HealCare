package com.bookinghealthy.repository;

import com.bookinghealthy.model.Candidate;
import com.bookinghealthy.model.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.bookinghealthy.model.CandidateStatus;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    // Lấy danh sách ứng viên của một vị trí cụ thể
    List<Candidate> findByJobPosting(JobPosting jobPosting);

    // Badge "chờ duyệt" ở sidebar admin. Đếm được về 0 bằng thao tác duyệt/từ chối của chính
    // admin — đó là điều kiện để một badge đáng tồn tại (xem supporting-subsystems.md).
    long countByStatus(CandidateStatus status);

    // Lấy tất cả ứng viên mới nhất
    List<Candidate> findAllByOrderBySubmittedAtDesc();
}