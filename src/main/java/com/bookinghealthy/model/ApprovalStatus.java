package com.bookinghealthy.model;

/**
 * Trạng thái phê duyệt dùng chung cho đơn nghỉ, ca trực và yêu cầu đổi ca.
 */
public enum ApprovalStatus {

    PENDING("Đang chờ duyệt", "warning"),
    APPROVED("Đã phê duyệt", "success"),
    REJECTED("Từ chối", "danger"),
    CANCELED("Đã hủy", "secondary");

    private final String label;
    private final String badgeClass; // hậu tố class Bootstrap: text-bg-warning, text-bg-success...

    ApprovalStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
