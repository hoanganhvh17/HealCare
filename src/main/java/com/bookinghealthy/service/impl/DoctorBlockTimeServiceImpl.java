package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.DoctorBlockTime;
import com.bookinghealthy.repository.DoctorBlockTimeRepository;
import com.bookinghealthy.service.DoctorBlockTimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class DoctorBlockTimeServiceImpl implements DoctorBlockTimeService {

    @Autowired
    private DoctorBlockTimeRepository doctorBlockTimeRepository;

    @Override
    public String blockTime(Doctor doctor, LocalDate blockDate, LocalTime startTime, LocalTime endTime, String reason) {

        // 1. Kiểm tra logic giờ
        if (startTime.isAfter(endTime) || startTime.equals(endTime)) {
            return "Giờ kết thúc phải sau giờ bắt đầu.";
        }

        // 2. Kiểm tra trùng lặp (Logic kiểm tra giao nhau)
        // Spring JPA sẽ tự động chuyển đổi logic này thành câu query SQL kiểm tra trùng lặp.
        if (doctorBlockTimeRepository.existsByDoctorIdAndBlockDateAndStartTimeBeforeAndEndTimeAfter(
                doctor.getId(),
                blockDate,
                endTime, // New End phải sau Start cũ
                startTime // New Start phải trước End cũ
        )) {
            return "Khung giờ này đã bị chặn hoặc giao nhau với một khung giờ bận khác.";
        }

        // 3. Lưu vào Database
        DoctorBlockTime blockTime = new DoctorBlockTime();
        blockTime.setDoctor(doctor);
        blockTime.setBlockDate(blockDate);
        blockTime.setStartTime(startTime);
        blockTime.setEndTime(endTime);
        blockTime.setReason(reason);

        doctorBlockTimeRepository.save(blockTime);

        return null; // Thành công
    }

    @Override
    public List<DoctorBlockTime> getBlockedTimes(Long doctorId) {
        return doctorBlockTimeRepository.findByDoctorId(doctorId);
    }

    @Override
    public List<DoctorBlockTime> getBlockedSlotsForDoctorAndDate(Long doctorId, LocalDate date) {
        return doctorBlockTimeRepository.findByDoctorIdAndBlockDate(doctorId, date);
    }

    @Override
    public void unblockTime(Long blockId) {
        doctorBlockTimeRepository.deleteById(blockId);
    }
}