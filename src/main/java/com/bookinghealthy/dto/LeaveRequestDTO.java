package com.bookinghealthy.dto;

import com.bookinghealthy.model.HalfDaySession;
import com.bookinghealthy.model.LeaveType;
import com.bookinghealthy.model.PersonalLeaveReason;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Dữ liệu form "Đăng ký nghỉ phép" và "Báo bận đột xuất".
 */
@Data
public class LeaveRequestDTO {

    private LeaveType leaveType;

    /** Bắt buộc khi leaveType là nghỉ việc riêng (BLLĐ 2019 Điều 115). */
    private PersonalLeaveReason subReason;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private HalfDaySession halfDaySession = HalfDaySession.NONE;

    private String reason;

    /** Đơn báo bận đột xuất: chặn lịch khám ngay, chờ duyệt sau. */
    private boolean emergency;
}
