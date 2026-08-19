package com.bookinghealthy.repository;

import com.bookinghealthy.model.StaffProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    @EntityGraph(attributePaths = {"user", "headOfDepartment"})
    Optional<StaffProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Trưởng khoa của một khoa (dùng để định tuyến đơn cần duyệt). */
    @EntityGraph(attributePaths = {"user", "headOfDepartment"})
    List<StaffProfile> findByHeadOfDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"user", "headOfDepartment"})
    List<StaffProfile> findByHeadOfDepartmentIsNotNull();

    void deleteByUserId(Long userId);
}
