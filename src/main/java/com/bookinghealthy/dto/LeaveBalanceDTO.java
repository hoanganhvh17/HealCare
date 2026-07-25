package com.bookinghealthy.dto;

import com.bookinghealthy.model.WorkCondition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Số ngày nghỉ còn lại của một nhân viên trong năm, tính theo
 * {@code config/LeavePolicy} (BLLĐ 2019 Điều 113/114, Luật BHXH 2024 Điều 43).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBalanceDTO {

    private int year;

    /** Điều kiện lao động — quyết định mức phép cơ bản 12 / 14 / 16 ngày. */
    private WorkCondition workCondition;

    private int yearsOfService;

    /** Phần cộng thêm do thâm niên (+1 ngày mỗi 5 năm — Điều 114). */
    private int seniorityBonus;

    /** Số ngày phép năm được hưởng = mức cơ bản + thâm niên + ngày chuyển từ năm trước. */
    private int annualQuota;

    private BigDecimal annualUsed;

    private BigDecimal annualRemaining;

    /** Số ngày nghỉ ốm tối đa trong năm theo số năm đóng BHXH. */
    private int sickQuota;

    private BigDecimal sickUsed;

    private BigDecimal sickRemaining;

    /** Số ngày phép cũ được chuyển sang, đã cộng vào annualQuota. */
    private int carriedOverDays;

    /** Câu giải thích ngắn hiện dưới thẻ số dư, trích đúng điều luật. */
    private String explanation;
}
