package com.bookinghealthy.service;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.DoctorBlockTime; // Import Entity mới
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorBlockTimeRepository; // Import Repo mới
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimeSlotService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DoctorBlockTimeRepository doctorBlockTimeRepository; // Inject Repository chặn giờ

    // === CẤU HÌNH GIỜ CHUẨN ===
    // Chỉ có ca khám trong GIỜ HÀNH CHÍNH. Ngoài giờ hành chính là phiên trực
    // (Thông tư 32/2023/TT-BYT) — trực không nhận đặt khám nên không có khung giờ buổi tối.
    private final String[] MORNING_SLOTS = {
            "07:30 - 08:00", "08:00 - 08:30", "08:30 - 09:00", "09:00 - 09:30",
            "09:30 - 10:00", "10:00 - 10:30", "10:30 - 11:00", "11:00 - 11:30"
    };

    private final String[] AFTERNOON_SLOTS = {
            "13:30 - 14:00", "14:00 - 14:30", "14:30 - 15:00", "15:00 - 15:30",
            "15:30 - 16:00", "16:00 - 16:30", "16:30 - 17:00", "17:00 - 17:30"
    };

    /**
     * Cả 16 khung giờ theo thứ tự trong ngày, dạng "HH:mm - HH:mm".
     *
     * Mở ra để nơi khác ĐỌC LẠI lưới thay vì tự liệt kê thêm một bản nữa —
     * `DoctorAiController` từng có bản sao riêng và bản đó còn sót các khung ca tối
     * (17:30/18:00/18:30/19:00) đã bị bỏ từ 24/07, nên trợ lý báo bác sĩ "rảnh lúc 18:30",
     * một giờ mà bệnh nhân không thể đặt ở bất kỳ đâu. Xem `/skills/sync-slot-grid`.
     */
    public List<String> allSlots() {
        List<String> all = new ArrayList<>(List.of(MORNING_SLOTS));
        all.addAll(List.of(AFTERNOON_SLOTS));
        return all;
    }

    public List<TimeSlotDTO> getAvailableSlots(Long doctorId, LocalDate date, List<Schedule> workingSchedules) {
        List<TimeSlotDTO> allSlots = new ArrayList<>();

        // 1. Lấy danh sách lịch đã bị đặt (Booking)
        List<Booking> existingBookings = bookingRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                doctorId, date, BookingStatus.CANCELED);

        List<String> bookedTimeStrings = existingBookings.stream()
                .map(Booking::getAppointmentTime)
                .collect(Collectors.toList());

        // 2. Lấy danh sách Giờ Bận Đột Xuất (DoctorBlockTime)
        List<DoctorBlockTime> blockedTimes = doctorBlockTimeRepository.findByDoctorIdAndBlockDate(doctorId, date);

        // 3. Duyệt và xử lý từng buổi (Truyền thêm blockedTimes vào)
        processSlots(allSlots, MORNING_SLOTS, "Sáng", date, workingSchedules, bookedTimeStrings, blockedTimes);
        processSlots(allSlots, AFTERNOON_SLOTS, "Chiều", date, workingSchedules, bookedTimeStrings, blockedTimes);

        return allSlots;
    }

    private void processSlots(List<TimeSlotDTO> targetList, String[] timeTemplates, String session,
                              LocalDate date, List<Schedule> workingSchedules,
                              List<String> bookedTimeStrings,
                              List<DoctorBlockTime> blockedTimes) { // Thêm tham số này

        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (String timeStr : timeTemplates) {
            String[] parts = timeStr.split(" - ");
            LocalTime slotStart = LocalTime.parse(parts[0], formatter);
            LocalTime slotEnd = LocalTime.parse(parts[1], formatter);

            // Check 1: Có nằm trong ca trực của bác sĩ không?
            boolean isWithinWorkingHours = false;
            for (Schedule sched : workingSchedules) {
                if (!slotStart.isBefore(sched.getStartTime()) && !slotEnd.isAfter(sched.getEndTime())) {
                    isWithinWorkingHours = true;
                    break;
                }
            }

            if (isWithinWorkingHours) {
                boolean isAvailable = true;
                String statusClass = "";

                // Check 2: Đã bị đặt chưa (Booking)?
                if (bookedTimeStrings.contains(timeStr)) {
                    isAvailable = false;
                    statusClass = "booked";
                }

                // === CHECK 3: CÓ BỊ CHẶN ĐỘT XUẤT KHÔNG (BlockTime)? ===
                if (isAvailable) { // Chỉ check nếu chưa bị booked
                    for (DoctorBlockTime block : blockedTimes) {
                        // Logic giao nhau: (SlotStart < BlockEnd) VÀ (SlotEnd > BlockStart)
                        if (slotStart.isBefore(block.getEndTime()) && slotEnd.isAfter(block.getStartTime())) {
                            isAvailable = false;
                            statusClass = "blocked"; // Class CSS để làm mờ/gạch chéo
                            break;
                        }
                    }
                }

                // Check 4: Đã qua giờ (Quá khứ)?
                if (date.isBefore(today)) {
                    isAvailable = false;
                    statusClass = "past";
                } else if (date.isEqual(today) && slotStart.isBefore(now)) {
                    // Nếu chưa có status nào (blocked/booked) thì mới gán past
                    if(isAvailable) {
                        isAvailable = false;
                        statusClass = "past";
                    }
                }

                targetList.add(new TimeSlotDTO(timeStr, session, isAvailable, statusClass));
            }
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TimeSlotDTO {
        private String time;
        private String session;
        private boolean available;
        private String statusClass; // "booked", "past", "blocked"
    }
}