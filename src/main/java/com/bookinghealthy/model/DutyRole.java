package com.bookinghealthy.model;

/**
 * Vị trí trong một phiên trực, theo Thông tư 32/2023/TT-BYT:
 * trực lãnh đạo, trực lâm sàng, trực cận lâm sàng và trực hậu cần.
 */
public enum DutyRole {

    TRUC_LANH_DAO("Trực lãnh đạo"),
    TRUC_LAM_SANG("Trực lâm sàng"),
    TRUC_CAN_LAM_SANG("Trực cận lâm sàng"),
    TRUC_HAU_CAN("Trực hậu cần");

    private final String label;

    DutyRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
