package com.bookinghealthy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyBookingStatsDTO {
    private String date;  // Ngày (vd: "2025-11-07")
    private long count; // Số lượng lịch hẹn
}