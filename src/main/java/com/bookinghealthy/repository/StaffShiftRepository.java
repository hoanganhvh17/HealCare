package com.bookinghealthy.repository;

import com.bookinghealthy.model.ApprovalStatus;
import com.bookinghealthy.model.StaffShift;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {

    /**
     * Ca của một người trong khoảng ngày.
     * Lùi from thêm 1 ngày ở tầng service để bắt được ca trực qua đêm bắt đầu từ hôm trước.
     */
    @EntityGraph(attributePaths = {"user", "department"})
    List<StaffShift> findByUserIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            Long userId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"user", "department"})
    List<StaffShift> findByUserIdAndStatusNotOrderByShiftDateDescStartTimeDesc(
            Long userId, ApprovalStatus excludedStatus);

    /** Lịch trực toàn khoa trong khoảng ngày — màn hình duyệt của trưởng khoa. */
    @EntityGraph(attributePaths = {"user", "department", "approver"})
    List<StaffShift> findByDepartmentIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            Long departmentId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"user", "department"})
    List<StaffShift> findByDepartmentIdAndStatusOrderByShiftDateAsc(
            Long departmentId, ApprovalStatus status);

    /** Ca đang cần người thay — thẻ "Cần người thay ca" trên trang lịch. */
    @EntityGraph(attributePaths = {"user", "department"})
    List<StaffShift> findByUserIdAndNeedsCoverTrueOrderByShiftDateAsc(Long userId);

    @EntityGraph(attributePaths = {"user", "department"})
    List<StaffShift> findByDepartmentIdAndNeedsCoverTrueAndShiftDateGreaterThanEqualOrderByShiftDateAsc(
            Long departmentId, LocalDate from);

    /** Ca cùng ngày của một người — kiểm tra chồng giờ trước khi đăng ký ca mới. */
    List<StaffShift> findByUserIdAndShiftDateAndStatusNot(
            Long userId, LocalDate shiftDate, ApprovalStatus excludedStatus);

    /** Phiên trực gần nhất đã kết thúc trước một ngày — dùng tính nghỉ bù bắt buộc. */
    @Query("SELECT s FROM StaffShift s WHERE s.user.id = :userId "
            + "AND s.status = com.bookinghealthy.model.ApprovalStatus.APPROVED "
            + "AND s.shiftDate BETWEEN :from AND :to "
            + "ORDER BY s.shiftDate DESC, s.startTime DESC")
    List<StaffShift> findApprovedBetween(@Param("userId") Long userId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    long countByDepartmentIdAndStatus(Long departmentId, ApprovalStatus status);

    /** Có ai trực khoa này trong ngày đó chưa (Thông tư 32/2023 yêu cầu tối thiểu 1 người/phiên). */
    @Query("SELECT COUNT(s) FROM StaffShift s WHERE s.department.id = :departmentId "
            + "AND s.shiftDate = :date "
            + "AND s.status = com.bookinghealthy.model.ApprovalStatus.APPROVED "
            + "AND s.shiftType IN (com.bookinghealthy.model.ShiftType.TRUC_NGOAI_GIO, "
            + "com.bookinghealthy.model.ShiftType.TRUC_12H_DEM, "
            + "com.bookinghealthy.model.ShiftType.TRUC_24H)")
    long countApprovedDutyOnDate(@Param("departmentId") Long departmentId,
                                 @Param("date") LocalDate date);

    long countByApproverId(Long approverId);

    List<StaffShift> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
