package com.bookinghealthy.config;

import com.bookinghealthy.model.PersonalLeaveReason;
import com.bookinghealthy.model.ShiftType;
import com.bookinghealthy.model.WorkCondition;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * NGUỒN DUY NHẤT cho mọi con số về chế độ nghỉ và ca trực theo pháp luật Việt Nam.
 * Sửa luật thì chỉ sửa file này — không rải hằng số ra service hay controller.
 *
 * Căn cứ:
 * - Bộ luật Lao động 2019: Điều 112 (nghỉ lễ), 113 & 114 (nghỉ hằng năm), 115 (việc riêng)
 * - Luật Bảo hiểm xã hội 2024: Điều 43 (thời gian hưởng chế độ ốm đau)
 * - Quyết định 73/2011/QĐ-TTg: Điều 2 khoản 4 (nghỉ bù sau phiên trực)
 * - Thông tư 32/2023/TT-BYT: tổ chức phiên trực khám bệnh, chữa bệnh
 */
public final class LeavePolicy {

    private LeavePolicy() {
        // Lớp hằng số, không khởi tạo.
    }

    // ===================== GIỜ HÀNH CHÍNH =====================

    /**
     * Ranh giới giờ hành chính. Ngoài khoảng này là phiên trực — không nhận đặt khám.
     * Khớp với MORNING_SLOTS / AFTERNOON_SLOTS của TimeSlotService và ALL_SLOTS của
     * BookingApi / AiController.
     */
    public static final LocalTime OFFICE_START = LocalTime.of(7, 30);
    public static final LocalTime OFFICE_END = LocalTime.of(17, 30);

    // ===================== TUẦN LÀM VIỆC & CHỐT LỊCH KHÁM =====================

    /**
     * Tuần làm việc bắt đầu từ Thứ Hai. Mọi chỗ cần "tuần" của một ngày đều gọi hàm này,
     * kể cả {@code Schedule.weekStart} — không tự tính lại bằng minusDays ở nơi khác.
     */
    public static LocalDate weekStartOf(LocalDate date) {
        return (date == null) ? null : date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    /**
     * Hạn chốt đăng ký ca khám tuần sau: 22:00 Chủ nhật — đúng thời điểm hệ thống tự xếp
     * lịch cho những bác sĩ chưa đăng ký ({@code ClinicRegistrationTask}). Sau mốc này lịch
     * tuần sau coi như đã công bố, muốn đổi phải qua trưởng khoa hoặc nhờ lễ tân dời lịch.
     */
    public static final DayOfWeek CLINIC_DEADLINE_DAY = DayOfWeek.SUNDAY;
    public static final LocalTime CLINIC_DEADLINE_TIME = LocalTime.of(22, 0);

    /** Còn được đăng ký / sửa lịch khám tuần sau không (chưa tới 22:00 Chủ nhật). */
    public static boolean isClinicRegistrationOpen(java.time.LocalDateTime now) {
        return now.getDayOfWeek() != CLINIC_DEADLINE_DAY
                || now.toLocalTime().isBefore(CLINIC_DEADLINE_TIME);
    }

    // ===================== NGHỈ HẰNG NĂM (PHÉP NĂM) =====================

    /** Cứ 5 năm làm việc cho một người sử dụng lao động thì được thêm 1 ngày phép — Điều 114. */
    public static final int SENIORITY_INTERVAL_YEARS = 5;

    /** Số ngày nghỉ lễ, Tết trong năm — Điều 112. Chỉ để hiển thị, không trừ quota phép. */
    public static final int PUBLIC_HOLIDAY_DAYS = 11;

    /**
     * Các khoa được xếp vào nhóm nghề nặng nhọc, độc hại, nguy hiểm — hưởng 14 ngày phép
     * (Điều 113 khoản 1 điểm b) và mức nghỉ ốm cao hơn (Luật BHXH 2024 Điều 43).
     * Dùng khi seed hồ sơ nhân sự; sau đó trưởng khoa/admin sửa được từng người.
     */
    public static final Set<String> HEAVY_DEPARTMENTS = Set.of(
            "Cấp cứu",
            "Gây mê hồi sức",
            "Ung bướu",
            "Chẩn đoán hình ảnh",
            "Tâm thần",
            "Huyết học"
    );

    /**
     * Số ngày phép năm được hưởng = mức cơ bản theo điều kiện lao động (12/14/16)
     * cộng thâm niên (+1 ngày mỗi 5 năm).
     *
     * @param condition      điều kiện lao động (Điều 113 khoản 1)
     * @param yearsOfService số năm đã làm việc, tính từ ngày vào làm
     */
    public static int annualQuota(WorkCondition condition, int yearsOfService) {
        WorkCondition safeCondition = (condition != null) ? condition : WorkCondition.NORMAL;
        int base = safeCondition.getBaseAnnualLeaveDays();
        int seniorityBonus = seniorityBonus(yearsOfService);
        return base + seniorityBonus;
    }

    /** Phần thâm niên cộng thêm — Điều 114. Làm việc chưa đủ 5 năm thì bằng 0. */
    public static int seniorityBonus(int yearsOfService) {
        if (yearsOfService < SENIORITY_INTERVAL_YEARS) {
            return 0;
        }
        return yearsOfService / SENIORITY_INTERVAL_YEARS;
    }

    /** Số năm làm việc tính đến hôm nay. hireDate null thì coi như 0 năm. */
    public static int yearsOfService(LocalDate hireDate, LocalDate today) {
        if (hireDate == null || today == null || hireDate.isAfter(today)) {
            return 0;
        }
        return java.time.Period.between(hireDate, today).getYears();
    }

    // ===================== NGHỈ ỐM (BHXH) =====================

    /**
     * Số ngày nghỉ ốm tối đa trong một năm — Luật BHXH 2024, Điều 43.
     *
     * Điều kiện bình thường: 30 ngày (đóng BHXH dưới 15 năm), 40 ngày (từ 15 đến dưới 30 năm),
     * 60 ngày (từ đủ 30 năm trở lên).
     * Nghề nặng nhọc, độc hại, nguy hiểm: 40 / 50 / 70 ngày tương ứng.
     */
    public static int sickQuota(WorkCondition condition, int insuranceYears) {
        boolean heavy = condition == WorkCondition.HEAVY || condition == WorkCondition.EXTRA_HEAVY;

        if (insuranceYears >= 30) {
            return heavy ? 70 : 60;
        }
        if (insuranceYears >= 15) {
            return heavy ? 50 : 40;
        }
        return heavy ? 40 : 30;
    }

    // ===================== NGHỈ THAI SẢN =====================

    /** Nghỉ thai sản 6 tháng — Luật BHXH. */
    public static final int MATERNITY_DAYS = 180;

    // ===================== NGHỈ VIỆC RIÊNG =====================

    /**
     * Số ngày tối đa cho từng trường hợp nghỉ việc riêng — BLLĐ 2019, Điều 115.
     * Trả về 0 khi không xác định được trường hợp (form bắt buộc phải chọn).
     */
    public static int personalLeaveMaxDays(PersonalLeaveReason reason) {
        return (reason != null) ? reason.getMaxDays() : 0;
    }

    // ===================== PHIÊN TRỰC =====================

    /**
     * Lịch trực phải được công bố trước ít nhất 1 tuần — Thông tư 32/2023/TT-BYT.
     * Đăng ký sát hơn mốc này vẫn được nhưng hệ thống cảnh báo để trưởng khoa biết.
     */
    public static final int DUTY_NOTICE_DAYS = 7;

    /**
     * Phiên trực phải đăng ký trước tối thiểu 24 giờ. Trong vòng 24 giờ là tình huống
     * đột xuất, phải đi qua chức năng "Báo bận đột xuất" chứ không phải đăng ký ca.
     */
    public static final int DUTY_MIN_HOURS_AHEAD = 24;

    /**
     * Số NGÀY nghỉ bù sau một phiên trực — Quyết định 73/2011/QĐ-TTg, Điều 2 khoản 4.
     *
     * Trực 24/24 giờ vào ngày thường hoặc ngày nghỉ hằng tuần: nghỉ bù 1 ngày;
     * vào ngày lễ, Tết: nghỉ bù 2 ngày.
     * Trực 12/24 hoặc 16/24 giờ: không nghỉ bù trọn ngày, chỉ nghỉ tối thiểu 12 giờ
     * (xem {@link #REST_HOURS_AFTER_PARTIAL_DUTY}).
     */
    public static int compensatoryDaysAfterDuty(ShiftType shiftType, boolean isHoliday) {
        if (shiftType != ShiftType.TRUC_24H) {
            return 0;
        }
        return isHoliday ? 2 : 1;
    }

    /** Sau phiên trực 12/24 hoặc 16/24 giờ, người trực được nghỉ ít nhất 12 giờ. */
    public static final int REST_HOURS_AFTER_PARTIAL_DUTY = 12;

    /**
     * Trực 24/24 giờ phủ trọn cả giờ hành chính nên chỉ tổ chức vào ngày nghỉ hằng tuần
     * hoặc ngày lễ. Ngày thường phải dùng trực 16/24 hoặc trực đêm 12/24.
     */
    public static boolean allowsFullDayDuty(LocalDate date) {
        return isWeekend(date) || isPublicHoliday(date);
    }

    public static boolean isWeekend(LocalDate date) {
        return date != null
                && (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY);
    }

    /**
     * Ngày lễ theo dương lịch — BLLĐ 2019, Điều 112.
     * Tết Âm lịch và Giỗ Tổ Hùng Vương theo lịch âm nên không tính được ở đây;
     * trưởng khoa vẫn duyệt được thủ công nếu rơi vào các dịp đó.
     */
    private static final List<java.time.MonthDay> SOLAR_HOLIDAYS = List.of(
            java.time.MonthDay.of(1, 1),   // Tết Dương lịch
            java.time.MonthDay.of(4, 30),  // Ngày Giải phóng miền Nam
            java.time.MonthDay.of(5, 1),   // Quốc tế Lao động
            java.time.MonthDay.of(9, 2),   // Quốc khánh
            java.time.MonthDay.of(9, 3)    // Ngày liền kề Quốc khánh
    );

    public static boolean isPublicHoliday(LocalDate date) {
        return date != null && SOLAR_HOLIDAYS.contains(java.time.MonthDay.from(date));
    }
}
