package com.bookinghealthy.controller.doctor;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/doctor")
public class DoctorBookingManagerController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    private Doctor getLoggedInDoctor(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return doctorService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }

    // HIỂN THỊ TOÀN BỘ DANH SÁCH LỊCH (Quản lý chung)
    @GetMapping("/manage-bookings")
    public String showAllBookings(Model model, Authentication authentication) {
        Doctor currentDoctor = getLoggedInDoctor(authentication);

        // Lấy tất cả lịch của bác sĩ này (Sắp xếp mới nhất lên đầu)
        List<Booking> allBookings = bookingRepository.findByDoctor(currentDoctor);

        // Mới nhất lên đầu, dòng KHÔNG có createdAt xuống cuối.
        //
        // Bản cũ là `b2.getCreatedAt().compareTo(b1.getCreatedAt())` và ném NullPointerException
        // ngay khi gặp MỘT dòng có created_at NULL — tức HTTP 500 cho toàn bộ trang, không phải
        // mất một dòng. Cột này nullable và `@CreationTimestamp` chỉ điền cho dòng đi qua
        // Hibernate, nên mọi dòng nhập bằng SQL thô (dữ liệu cũ, import, sửa tay lúc vận hành)
        // đều là một quả mìn; sắp xếp là việc hiển thị, không đáng đánh sập màn hình.
        allBookings.sort(Comparator.comparing(Booking::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("listBookings", allBookings);
        model.addAttribute("actionBlockReasons", buildActionBlockReasons(allBookings));
        model.addAttribute("activePage", "manage-bookings"); // Để highlight menu

        return "doctor/booking-manager";
    }

    /**
     * Lý do từng lịch hẹn không còn được thao tác (id -> câu tiếng Việt). Không có khóa nghĩa
     * là còn thao tác được. Cùng một hàm kiểm tra với controller xử lý xác nhận/hủy nên giao
     * diện và server không bao giờ nói khác nhau.
     */
    private Map<Long, String> buildActionBlockReasons(List<Booking> bookings) {
        Map<Long, String> reasons = new HashMap<>();
        for (Booking booking : bookings) {
            String reason = bookingService.whyStaffCannotChange(booking);
            if (reason != null) {
                reasons.put(booking.getId(), reason);
            }
        }
        return reasons;
    }
}