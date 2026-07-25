package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Một ca trong lịch làm việc của nhân viên: CA KHÁM (giờ hành chính) hoặc PHIÊN TRỰC
 * (ngoài giờ hành chính). Xem {@link ShiftType} để biết ranh giới giữa hai loại.
 *
 * Chỉ ca khám mới sinh khung giờ cho bệnh nhân đặt lịch, và việc đó đi qua bảng
 * {@link Schedule} (lịch làm việc định kỳ hằng tuần). Phiên trực nằm ở đây và KHÔNG
 * bao giờ chạm vào luồng đặt lịch — đó là điểm mấu chốt của nghiệp vụ này.
 */
@Entity
@Table(name = "staff_shifts")
@Getter
@Setter
@NoArgsConstructor
public class StaffShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Khoa trực. Lễ tân không thuộc khoa nào nên để null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Ngày BẮT ĐẦU ca. Ca trực kết thúc vào ngày hôm sau (xem {@link #overnight}). */
    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 30)
    private ShiftType shiftType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** true khi ca kết thúc sang ngày hôm sau — mọi phiên trực đều vậy. */
    @Column(name = "overnight", nullable = false)
    private boolean overnight = false;

    /** Vị trí trong phiên trực (Thông tư 32/2023/TT-BYT). Null với ca khám. */
    @Enumerated(EnumType.STRING)
    @Column(name = "duty_role", length = 30)
    private DutyRole dutyRole;

    @Column(name = "note", length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "approver_comment", length = 500)
    private String approverComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Ca đang cần người thay (chủ ca đã gửi yêu cầu đổi ca nhưng chưa ai nhận). */
    @Column(name = "needs_cover", nullable = false)
    private boolean needsCover = false;

    /** Thời điểm ca bắt đầu, dùng để so sánh chồng lấn và tính nghỉ bù. */
    public LocalDateTime getStartsAt() {
        return LocalDateTime.of(shiftDate, startTime);
    }

    /** Thời điểm ca kết thúc — cộng 1 ngày nếu ca qua đêm. */
    public LocalDateTime getEndsAt() {
        LocalDate endDate = overnight ? shiftDate.plusDays(1) : shiftDate;
        return LocalDateTime.of(endDate, endTime);
    }

    public boolean isDuty() {
        return shiftType != null && shiftType.isDuty();
    }

    public boolean isClinic() {
        return shiftType != null && shiftType.isClinic();
    }
}
