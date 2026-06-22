package com.bookinghealthy.repository;

import com.bookinghealthy.model.Doctor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    // (Hàm này để sửa lỗi "trắng trang" sau này)
    @Override
    @EntityGraph(attributePaths = {"user", "user.roles", "department"})
    List<Doctor> findAll();

    // (Hàm này để sửa lỗi "trắng trang" sau này)
    @Override
    @EntityGraph(attributePaths = {"user", "user.roles", "department"})
    Optional<Doctor> findById(Long id);

    // === DÒNG BỊ THIẾU CỦA BẠN LÀ ĐÂY ===
    // Tìm bác sĩ bằng ID của Khoa
    @EntityGraph(attributePaths = {"user", "user.roles", "department"})
    List<Doctor> findByDepartmentId(Long departmentId);
    // === KẾT THÚC DÒNG BỊ THIẾU ===

    List<Doctor> findByUserFullNameContainingIgnoreCase(String fullName);

    // === THÊM HÀM MỚI NÀY ===
    // Tìm Doctor bằng username của User liên kết
    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Doctor> findByUser_Username(String username);

    Optional<Doctor> findByUserId(Long userId);
    
    Doctor findByUser(com.bookinghealthy.model.User user);

    // === THÊM HÀM TÌM KIẾM NÂNG CAO ===
    @EntityGraph(attributePaths = {"user", "user.roles", "department"})
    @Query("SELECT d FROM Doctor d WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(d.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:departmentId IS NULL OR d.department.id = :departmentId)")
    List<Doctor> searchDoctors(@Param("keyword") String keyword, @Param("departmentId") Long departmentId);
}
