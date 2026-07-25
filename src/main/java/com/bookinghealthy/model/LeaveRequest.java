package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Đơn xin nghỉ của nhân viên (bác sĩ hoặc lễ tân), do trưởng khoa phê duyệt.
 *
 * Khi đơn của một BÁC SĨ được duyệt, hệ thống tự sinh các bản ghi
 * {@link DoctorBlockTime} phủ khoảng nghỉ. Nhờ đó luồng đặt lịch và trợ lý AI —
 * vốn đã tôn trọng DoctorBlockTime sẵn — tự động không xếp bệnh nhân vào ngày nghỉ,
 * không phải sửa gì thêm ở BookingApi/TimeSlotService/AiController.
 */
@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 30)
    private LeaveType leaveType;

    /** Trường hợp cụ thể khi nghỉ việc riêng (BLLĐ 2019 Điều 115). Null với loại khác. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sub_reason", length = 30)
    private PersonalLeaveReason subReason;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Nghỉ nửa ngày chỉ áp dụng khi startDate == endDate. */
    @Enumerated(EnumType.STRING)
    @Column(name = "half_day_session", nullable = false, length = 10)
    private HalfDaySession halfDaySession = HalfDaySession.NONE;

    @Column(name = "reason", length = 500)
    private String reason;

    /** Số ngày quy đổi (0.5 khi nghỉ nửa ngày) — dùng để trừ quota. */
    @Column(name = "total_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalDays = BigDecimal.ZERO;

    /** Đơn "Báo bận đột xuất" (trong vòng 24h): chặn lịch khám NGAY, duyệt sau. */
    @Column(name = "emergency", nullable = false)
    private boolean emergency = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    /** Lời phê của trưởng khoa, hiện lại cho nhân viên ở panel "Trạng thái phê duyệt". */
    @Column(name = "approver_comment", length = 500)
    private String approverComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Nhân viên chỉ tự hủy được đơn khi chưa ai duyệt (nút "Hủy yêu cầu"). */
    public boolean isCancelableByOwner() {
        return status == ApprovalStatus.PENDING;
    }

    /** Đơn đang có hiệu lực chặn lịch: đã duyệt, hoặc báo bận đột xuất đang chờ duyệt. */
    public boolean isBlockingSchedule() {
        return status == ApprovalStatus.APPROVED
                || (emergency && status == ApprovalStatus.PENDING);
    }

    /** Ngày nghỉ có bao trùm ngày này không. */
    public boolean covers(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
