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

    /**
     * Gỡ chặn một khung giờ bận. Trả {@code null} nếu gỡ được, ngược lại là câu tiếng Việt
     * giải thích — cùng khuôn {@code whyCannot…()} dùng khắp dự án.
     *
     * <p>BẮT BUỘC nhận {@code doctorId}: bản cũ là {@code deleteById(blockId)} mù, gọi từ một
     * {@code @GetMapping} không nhận cả {@code Authentication}, nên bác sĩ A duyệt id là xoá
     * sạch giờ bận của mọi bác sĩ — và vì là GET nên một thẻ {@code <img>} trên trang lạ cũng
     * kích hoạt được (CsrfFilter không xét GET).
     */
    String unblockTime(Long blockId, Long doctorId);
}