package com.bookinghealthy.controller.doctor;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
public class DoctorBookingManagerController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private BookingRepository bookingRepository;

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

        // Sắp xếp giảm dần theo ngày tạo (hoặc ngày hẹn tùy bạn)
        allBookings.sort((b1, b2) -> b2.getCreatedAt().compareTo(b1.getCreatedAt()));

        model.addAttribute("listBookings", allBookings);
        model.addAttribute("activePage", "manage-bookings"); // Để highlight menu

        return "doctor/booking-manager";
    }
}