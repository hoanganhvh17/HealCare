package com.bookinghealthy.repository;

import com.bookinghealthy.model.ApprovalStatus;
import com.bookinghealthy.model.LeaveRequest;
import com.bookinghealthy.model.LeaveType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = {"user", "approver"})
    List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "approver"})
    List<LeaveRequest> findByUserIdAndStatus(Long userId, ApprovalStatus status);

    /** Đơn của một người có ngày nghỉ giao với khoảng đang xét (dùng để vẽ lịch, kiểm tra trùng). */
    @EntityGraph(attributePaths = {"user", "approver"})
    @Query("SELECT l FROM LeaveRequest l WHERE l.user.id = :userId "
            + "AND l.startDate <= :to AND l.endDate >= :from "
            + "ORDER BY l.startDate ASC")
    List<LeaveRequest> findOverlapping(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** Tổng số ngày đã nghỉ theo một loại trong năm — chỉ tính đơn đã được duyệt. */
    @Query("SELECT COALESCE(SUM(l.totalDays), 0) FROM LeaveRequest l "
            + "WHERE l.user.id = :userId AND l.leaveType = :leaveType "
            + "AND l.status = com.bookinghealthy.model.ApprovalStatus.APPROVED "
            + "AND YEAR(l.startDate) = :year")
    java.math.BigDecimal sumApprovedDays(@Param("userId") Long userId,
                                         @Param("leaveType") LeaveType leaveType,
                                         @Param("year") int year);

    /** Hàng đợi duyệt của trưởng khoa: đơn của bác sĩ trong khoa mình. */
    @EntityGraph(attributePaths = {"user", "approver"})
    @Query("SELECT l FROM LeaveRequest l WHERE l.status = :status AND l.user.id IN "
            + "(SELECT d.user.id FROM Doctor d WHERE d.department.id = :departmentId) "
            + "ORDER BY l.emergency DESC, l.createdAt ASC")
    List<LeaveRequest> findByDepartmentAndStatus(@Param("departmentId") Long departmentId,
                                                 @Param("status") ApprovalStatus status);

    /**
     * Số đơn trưởng khoa CÒN xử lý được: đơn có kỳ nghỉ đã kết thúc thì không duyệt cũng
     * không từ chối được nữa nên phải loại khỏi con số hiển thị, kẻo chuông báo mãi một
     * việc không ai làm được.
     */
    @Query("SELECT COUNT(l) FROM LeaveRequest l WHERE l.status = :status "
            + "AND l.endDate >= :today AND l.user.id IN "
            + "(SELECT d.user.id FROM Doctor d WHERE d.department.id = :departmentId)")
    long countByDepartmentAndStatus(@Param("departmentId") Long departmentId,
                                    @Param("status") ApprovalStatus status,
                                    @Param("today") LocalDate today);

    /** Đơn đang có hiệu lực chặn lịch trong một ngày — chặn đăng ký ca vào ngày đã nghỉ. */
    @Query("SELECT l FROM LeaveRequest l WHERE l.user.id = :userId "
            + "AND l.startDate <= :date AND l.endDate >= :date "
            + "AND (l.status = com.bookinghealthy.model.ApprovalStatus.APPROVED "
            + "  OR (l.emergency = true AND l.status = com.bookinghealthy.model.ApprovalStatus.PENDING))")
    List<LeaveRequest> findBlockingOnDate(@Param("userId") Long userId,
                                          @Param("date") LocalDate date);
}
