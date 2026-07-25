package com.bookinghealthy.model;

/**
 * Điều kiện lao động, quyết định số ngày phép năm theo BLLĐ 2019, Điều 113 khoản 1
 * và số ngày nghỉ ốm theo Luật BHXH 2024, Điều 43.
 */
public enum WorkCondition {

    /** Điều kiện bình thường — 12 ngày phép/năm. */
    NORMAL("Điều kiện bình thường", 12),

    /** Nghề, công việc nặng nhọc, độc hại, nguy hiểm — 14 ngày phép/năm. */
    HEAVY("Nặng nhọc, độc hại, nguy hiểm", 14),

    /** Nghề, công việc đặc biệt nặng nhọc, độc hại, nguy hiểm — 16 ngày phép/năm. */
    EXTRA_HEAVY("Đặc biệt nặng nhọc, độc hại", 16);

    private final String label;
    private final int baseAnnualLeaveDays;

    WorkCondition(String label, int baseAnnualLeaveDays) {
        this.label = label;
        this.baseAnnualLeaveDays = baseAnnualLeaveDays;
    }

    public String getLabel() {
        return label;
    }

    /** Số ngày phép năm CƠ BẢN, chưa cộng thâm niên (Điều 114). */
    public int getBaseAnnualLeaveDays() {
        return baseAnnualLeaveDays;
    }
}
