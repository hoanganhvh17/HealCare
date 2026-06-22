package com.bookinghealthy.service;

import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.DoctorBlockTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface DoctorBlockTimeService {

    // Hàm chính để chặn giờ
    String blockTime(Doctor doctor, LocalDate date, LocalTime startTime, LocalTime endTime, String reason);

    // Lấy tất cả giờ bị chặn của 1 bác sĩ
    List<DoctorBlockTime> getBlockedTimes(Long doctorId);

    // Lấy giờ bị chặn cho 1 ngày cụ thể
    List<DoctorBlockTime> getBlockedSlotsForDoctorAndDate(Long doctorId, LocalDate date);

    // Gỡ chặn
    void unblockTime(Long blockId);
}