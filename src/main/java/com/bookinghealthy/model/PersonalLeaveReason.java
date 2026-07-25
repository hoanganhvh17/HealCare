package com.bookinghealthy.model;

/**
 * Các trường hợp nghỉ việc riêng và số ngày tối đa tương ứng — BLLĐ 2019, Điều 115.
 *
 * Khoản 1 (hưởng nguyên lương): kết hôn 3 ngày; con đẻ/con nuôi kết hôn 1 ngày;
 * cha/mẹ đẻ, cha/mẹ nuôi, cha/mẹ vợ hoặc chồng, vợ/chồng, con đẻ/con nuôi chết 3 ngày.
 *
 * Khoản 2 (không hưởng lương): ông bà nội/ngoại, anh chị em ruột chết;
 * cha/mẹ kết hôn; anh chị em ruột kết hôn — 1 ngày.
 */
public enum PersonalLeaveReason {

    KET_HON("Bản thân kết hôn", 3, true),
    CON_KET_HON("Con đẻ / con nuôi kết hôn", 1, true),
    TANG_NGUOI_THAN("Cha mẹ, vợ/chồng, con qua đời", 3, true),

    TANG_HO_HANG("Ông bà, anh chị em ruột qua đời", 1, false),
    NGUOI_THAN_KET_HON("Cha mẹ / anh chị em ruột kết hôn", 1, false);

    private final String label;
    private final int maxDays;
    private final boolean paid;

    PersonalLeaveReason(String label, int maxDays, boolean paid) {
        this.label = label;
        this.maxDays = maxDays;
        this.paid = paid;
    }

    public String getLabel() {
        return label;
    }

    public int getMaxDays() {
        return maxDays;
    }

    public boolean isPaid() {
        return paid;
    }

    /** Loại nghỉ tương ứng, để form không cho chọn lệch giữa "có lương" và "không lương". */
    public LeaveType getLeaveType() {
        return paid ? LeaveType.VIEC_RIENG_CO_LUONG : LeaveType.VIEC_RIENG_KHONG_LUONG;
    }
}
