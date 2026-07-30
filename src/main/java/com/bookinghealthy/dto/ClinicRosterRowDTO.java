package com.bookinghealthy.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

/**
 * Một hàng trong bảng xếp ca khám của khoa: một bác sĩ và các buổi họ làm trong tuần.
 *
 * Dùng {@code Set<DayOfWeek>} thay vì mảng boolean để template hỏi thẳng
 * {@code row.morning.contains(day)} — Thymeleaf không có cách gọn để đọc mảng theo chỉ số
 * lấy từ {@code day.value}.
 */
@Getter
@Setter
@NoArgsConstructor
public class ClinicRosterRowDTO {

    private Long doctorId;
    private Long userId;
    private String doctorName;

    /** Học vị / chức danh, để trưởng khoa nhận ra ai là ai khi khoa có 6 bác sĩ. */
    private String degree;

    private Set<DayOfWeek> morning = new HashSet<>();
    private Set<DayOfWeek> afternoon = new HashSet<>();

    /** Ngày bác sĩ đang có đơn nghỉ chặn lịch — ô tương ứng bị vô hiệu trên bảng. */
    private Set<DayOfWeek> onLeave = new HashSet<>();

    /** Bác sĩ đã tự đăng ký (hoặc được xếp) lịch cho tuần này chưa. */
    private boolean registered;
}
