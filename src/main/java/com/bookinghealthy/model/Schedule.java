package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lịch này của bác sĩ nào?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    // Làm việc vào ngày nào trong tuần (MONDAY, TUESDAY...)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime; // Giờ bắt đầu (VD: 08:00)

    @Column(nullable = false)
    private LocalTime endTime; // Giờ kết thúc (VD: 11:00)

    /**
     * TUẦN mà ca khám này áp dụng — luôn là ngày Thứ Hai của tuần đó
     * ({@link com.bookinghealthy.config.LeavePolicy#weekStartOf}).
     *
     * Bác sĩ đăng ký ca khám cho TUẦN SAU, nên mỗi lần đăng ký sinh ra một bộ bản ghi gắn
     * với đúng tuần đó. Nhờ vậy tuần hiện tại và các tuần đã qua KHÔNG bao giờ bị sửa —
     * trước đây lịch chỉ có {@code dayOfWeek} nên lưu một lần là đổi cả quá khứ lẫn tương lai.
     *
     * {@code null} = lịch định kỳ mặc định (dữ liệu seed và lịch cũ trước khi tách theo tuần),
     * dùng cho những tuần chưa có bất kỳ lần đăng ký nào.
     */
    @Column(name = "week_start")
    private LocalDate weekStart;
}