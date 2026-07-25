package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Yêu cầu tìm người thay ca. Khi được chấp nhận, {@link StaffShift#getUser()} đổi sang
 * người nhận ca và cờ needsCover được gỡ.
 *
 * targetUser == null nghĩa là mở cho cả khoa: ai trong khoa nhận trước thì được.
 */
@Entity
@Table(name = "shift_cover_requests")
@Getter
@Setter
@NoArgsConstructor
public class ShiftCoverRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private StaffShift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    /** Người được nhờ đích danh. Null = mở lời mời cho toàn khoa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public boolean isOpenToDepartment() {
        return targetUser == null;
    }
}
