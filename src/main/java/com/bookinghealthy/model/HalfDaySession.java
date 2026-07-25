package com.bookinghealthy.model;

import java.time.LocalTime;

/**
 * Nghỉ nửa ngày: buổi sáng hay buổi chiều. NONE là nghỉ trọn ngày.
 * Giờ ở đây khớp với giờ hành chính để sinh đúng khoảng chặn lịch khám.
 */
public enum HalfDaySession {

    NONE("Cả ngày", LocalTime.of(7, 30), LocalTime.of(17, 30), 1.0),
    SANG("Nửa ngày (buổi sáng)", LocalTime.of(7, 30), LocalTime.of(11, 30), 0.5),
    CHIEU("Nửa ngày (buổi chiều)", LocalTime.of(13, 30), LocalTime.of(17, 30), 0.5);

    private final String label;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final double dayFactor;

    HalfDaySession(String label, LocalTime startTime, LocalTime endTime, double dayFactor) {
        this.label = label;
        this.startTime = startTime;
        this.endTime = endTime;
        this.dayFactor = dayFactor;
    }

    public String getLabel() {
        return label;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    /** 1.0 cho cả ngày, 0.5 cho nửa ngày — dùng khi tính số ngày trừ quota. */
    public double getDayFactor() {
        return dayFactor;
    }

    public boolean isHalfDay() {
        return this != NONE;
    }
}
