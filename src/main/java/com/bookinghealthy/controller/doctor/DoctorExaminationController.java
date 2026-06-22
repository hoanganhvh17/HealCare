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
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/doctor")
public class DoctorExaminationController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private BookingRepository bookingRepository;

    private Doctor getLoggedInDoctor(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return doctorService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }

    // === TRANG DANH SÁCH KHÁM BỆNH ===
    @GetMapping("/examinations")
    public String showExaminationList(
            @RequestParam(value = "date", required = false) LocalDate date,
            Model model,
            Authentication authentication) {

        Doctor currentDoctor = getLoggedInDoctor(authentication);

        // Mặc định là ngày hôm nay nếu không chọn ngày
        if (date == null) {
            date = LocalDate.now();
        }

        // 1. Lấy danh sách bệnh nhân ĐÃ XÁC NHẬN (CONFIRMED) của ngày đó
        List<Booking> confirmedBookings = bookingRepository.findByDoctorIdAndAppointmentDateAndStatus(
                currentDoctor.getId(),
                date,
                BookingStatus.CONFIRMED
        );

        // 2. Lấy danh sách đã khám xong (COMPLETED) trong ngày đó (để xem lại nếu cần)
        List<Booking> completedBookings = bookingRepository.findByDoctorIdAndAppointmentDateAndStatus(
                currentDoctor.getId(),
                date,
                BookingStatus.COMPLETED
        );

        //Logic kiểm tra: Chỉ cho phép nút "Khám" hiện ra nếu ngày chọn là Hôm nay
        boolean isToday = date.equals(LocalDate.now());

        //boolean isToday = true;

        model.addAttribute("confirmedBookings", confirmedBookings);
        model.addAttribute("completedBookings", completedBookings);
        model.addAttribute("selectedDate", date);
        model.addAttribute("isToday", isToday);
        model.addAttribute("activePage", "examinations"); // Để highlight menu

        return "doctor/examination-list";
    }
}