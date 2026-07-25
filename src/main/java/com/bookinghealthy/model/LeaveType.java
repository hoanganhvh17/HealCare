package com.bookinghealthy.model;

/**
 * Loại nghỉ, bám theo pháp luật lao động Việt Nam.
 * Số ngày tối đa của từng loại nằm ở {@code config/LeavePolicy} — KHÔNG hardcode ở nơi khác.
 */
public enum LeaveType {

    /** Nghỉ hằng năm (phép năm) — BLLĐ 2019, Điều 113 & 114. */
    PHEP_NAM("Phép năm", true),

    /** Nghỉ ốm hưởng chế độ BHXH — Luật BHXH 2024, Điều 43. */
    OM_DAU("Nghỉ ốm", false),

    /** Nghỉ việc riêng hưởng nguyên lương — BLLĐ 2019, Điều 115 khoản 1. */
    VIEC_RIENG_CO_LUONG("Việc riêng (hưởng lương)", false),

    /** Nghỉ việc riêng không hưởng lương — BLLĐ 2019, Điều 115 khoản 2. */
    VIEC_RIENG_KHONG_LUONG("Việc riêng (không lương)", false),

    /** Nghỉ không lương theo thỏa thuận — BLLĐ 2019, Điều 115 khoản 3, không giới hạn ngày. */
    KHONG_LUONG_THOA_THUAN("Không lương (thỏa thuận)", false),

    /** Nghỉ thai sản — Luật BHXH, 6 tháng. */
    THAI_SAN("Thai sản", false),

    /**
     * Nghỉ bù sau phiên trực — Quyết định 73/2011/QĐ-TTg, Điều 2 khoản 4.
     * Hệ thống TỰ SINH và tự duyệt khi phiên trực được phê duyệt; nhân viên không tự tạo.
     */
    NGHI_BU_TRUC("Nghỉ bù sau trực", false);

    private final String label;
    private final boolean deductsAnnualQuota;

    LeaveType(String label, boolean deductsAnnualQuota) {
        this.label = label;
        this.deductsAnnualQuota = deductsAnnualQuota;
    }

    public String getLabel() {
        return label;
    }

    /** Chỉ phép năm mới trừ vào quota nghỉ hằng năm. */
    public boolean isDeductsAnnualQuota() {
        return deductsAnnualQuota;
    }

    /** Loại nghỉ do hệ thống sinh, không hiện trong form đăng ký của nhân viên. */
    public boolean isSystemGenerated() {
        return this == NGHI_BU_TRUC;
    }
}
