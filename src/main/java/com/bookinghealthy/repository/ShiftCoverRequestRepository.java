package com.bookinghealthy.repository;

import com.bookinghealthy.model.ApprovalStatus;
import com.bookinghealthy.model.ShiftCoverRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftCoverRequestRepository extends JpaRepository<ShiftCoverRequest, Long> {

    @EntityGraph(attributePaths = {"shift", "shift.user", "shift.department", "requester", "targetUser"})
    List<ShiftCoverRequest> findByShiftIdAndStatus(Long shiftId, ApprovalStatus status);

    @EntityGraph(attributePaths = {"shift", "shift.user", "shift.department", "requester", "targetUser"})
    List<ShiftCoverRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    /**
     * Lời mời nhận ca đang chờ một người trả lời: gửi đích danh cho họ, HOẶC mở cho cả
     * khoa mà họ đang thuộc về. Không tính ca của chính họ.
     */
    @EntityGraph(attributePaths = {"shift", "shift.user", "shift.department", "requester", "targetUser"})
    @Query("SELECT c FROM ShiftCoverRequest c WHERE c.status = com.bookinghealthy.model.ApprovalStatus.PENDING "
            + "AND c.requester.id <> :userId "
            + "AND (c.targetUser.id = :userId "
            + "  OR (c.targetUser IS NULL AND c.shift.department.id = :departmentId)) "
            + "ORDER BY c.createdAt DESC")
    List<ShiftCoverRequest> findPendingForUser(@Param("userId") Long userId,
                                               @Param("departmentId") Long departmentId);

    void deleteByShiftId(Long shiftId);
}
