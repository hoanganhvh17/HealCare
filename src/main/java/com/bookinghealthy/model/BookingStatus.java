package com.bookinghealthy.model;

public enum BookingStatus {
    PENDING,   // Chờ xác nhận
    CONFIRMED, // Bác sĩ đã xác nhận
    CANCELED,  // Bị hủy (bởi user hoặc doctor)
    COMPLETED  // Đã hoàn thành
}