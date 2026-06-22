package com.bookinghealthy.controller.api;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.DoctorBlockTime; // Import Entity mới
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorBlockTimeRepository; // Import Repo mới
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingApi {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DoctorBlockTimeRepository doctorBlockTimeRepository; // Inject Repo chặn giờ

    // Định nghĩa lại các khung giờ chuẩn để kiểm tra (Giống TimeSlotService)
    private final String[] ALL_SLOTS = {
            // Sáng
            "07:30 - 08:00", "08:00 - 08:30", "08:30 - 09:00", "09:00 - 09:30",
            "09:30 - 10:00", "10:00 - 10:30", "10:30 - 11:00", "11:00 - 11:30",
            // Chiều
            "13:30 - 14:00", "14:00 - 14:30", "14:30 - 15:00", "15:00 - 15:30",
            "15:30 - 16:00", "16:00 - 16:30", "16:30 - 17:00", "17:00 - 17:30",
            // Tối
            "17:30 - 18:00", "18:00 - 18:30", "18:30 - 19:00", "19:00 - 19:30",
            "19:30 - 20:00", "20:00 - 20:30"
    };

    @GetMapping("/booked-slots")
    public ResponseEntity<List<String>> getBookedSlots(
            @RequestParam Long doctorId,
            @RequestParam String date) {

        LocalDate localDate = LocalDate.parse(date);
        List<String> unavailableSlots = new ArrayList<>();

        // 1. Lấy các slot đã có người Đặt (Booking)
        List<Booking> bookings = bookingRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                doctorId, localDate, BookingStatus.CANCELED);

        unavailableSlots.addAll(bookings.stream()
                .map(Booking::getAppointmentTime)
                .collect(Collectors.toList()));

        // 2. Lấy các slot bị Bác sĩ CHẶN (DoctorBlockTime)
        List<DoctorBlockTime> blockedTimes = doctorBlockTimeRepository.findByDoctorIdAndBlockDate(doctorId, localDate);

        if (!blockedTimes.isEmpty()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

            // Duyệt qua tất cả các khung giờ chuẩn của hệ thống
            for (String slotStr : ALL_SLOTS) {
                String[] parts = slotStr.split(" - ");
                LocalTime slotStart = LocalTime.parse(parts[0], formatter);
                LocalTime slotEnd = LocalTime.parse(parts[1], formatter);

                // Kiểm tra xem slot chuẩn này có bị chặn bởi bất kỳ BlockTime nào không
                for (DoctorBlockTime block : blockedTimes) {
                    // Logic giao nhau: (SlotStart < BlockEnd) VÀ (SlotEnd > BlockStart)
                    if (slotStart.isBefore(block.getEndTime()) && slotEnd.isAfter(block.getStartTime())) {
                        // Nếu chưa có trong danh sách thì thêm vào
                        if (!unavailableSlots.contains(slotStr)) {
                            unavailableSlots.add(slotStr);
                        }
                        break; // Đã bị chặn rồi thì không cần check block khác nữa
                    }
                }
            }
        }

        // Trả về danh sách tổng hợp (Booking + Blocked) cho Frontend gạch đỏ
        return ResponseEntity.ok(unavailableSlots);
    }
}
