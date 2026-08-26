package com.bookinghealthy.controller.doctor;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
public class DoctorBookingRequestController {

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

    // TRANG QUẢN LÝ YÊU CẦU (PENDING)
    @GetMapping("/booking-requests")
    public String showBookingRequests(Model model, Authentication authentication) {
        Doctor currentDoctor = getLoggedInDoctor(authentication);

        // Lấy tất cả lịch hẹn của bác sĩ
        List<Booking> allBookings = bookingRepository.findByDoctor(currentDoctor);

        // Lọc ra các lịch đang CHỜ DUYỆT (PENDING)
        List<Booking> pendingBookings = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                // nullsLast: một dòng appointment_date NULL (dữ liệu cũ, import, SQL sửa tay)
                // làm NPE và mất CẢ trang chứ không phải mất một dòng. Khuôn đã dùng ở
                // DoctorBookingManagerController.
                .sorted(Comparator.comparing(Booking::getAppointmentDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // Lý do không còn xác nhận/từ chối được (lịch đã trôi qua) — id nào không có khóa
        // thì nút vẫn bấm được. Cùng hàm kiểm tra với DoctorDashboardController.
        Map<Long, String> actionBlockReasons = new HashMap<>();
        for (Booking booking : pendingBookings) {
            String reason = bookingService.whyStaffCannotChange(booking);
            if (reason != null) {
                actionBlockReasons.put(booking.getId(), reason);
            }
        }

        model.addAttribute("pendingBookings", pendingBookings);
        model.addAttribute("actionBlockReasons", actionBlockReasons);
        model.addAttribute("activePage", "requests"); // Để highlight menu (nếu có)

        return "doctor/booking-requests";
    }
}