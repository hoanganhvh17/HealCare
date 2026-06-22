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
public class DoctorBookingRequestController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private BookingRepository bookingRepository;

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
                .sorted((b1, b2) -> b1.getAppointmentDate().compareTo(b2.getAppointmentDate())) // Sắp xếp ngày gần nhất lên đầu
                .collect(Collectors.toList());

        model.addAttribute("pendingBookings", pendingBookings);
        model.addAttribute("activePage", "requests"); // Để highlight menu (nếu có)

        return "doctor/booking-requests";
    }
}