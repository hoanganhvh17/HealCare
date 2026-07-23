package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/receptionist")
public class ReceptionistDashboardController {

    @Autowired
    private BookingRepository bookingRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<Booking> allBookings = bookingRepository.findAllByOrderByCreatedAtDesc();

        long todayTotal = allBookings.stream()
                .filter(b -> today.equals(b.getAppointmentDate()) && b.getStatus() != BookingStatus.CANCELED)
                .count();

        long todayWaiting = allBookings.stream()
                .filter(b -> today.equals(b.getAppointmentDate()) && b.getStatus() == BookingStatus.CONFIRMED)
                .count();

        long todayLate = allBookings.stream()
                .filter(b -> today.equals(b.getAppointmentDate()) && b.getLateMarkedAt() != null)
                .count();

        long pendingCount = bookingRepository.countByStatus(BookingStatus.PENDING);

        model.addAttribute("todayTotal", todayTotal);
        model.addAttribute("todayWaiting", todayWaiting);
        model.addAttribute("todayLate", todayLate);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("today", today);
        model.addAttribute("recentBookings", allBookings.stream().limit(10).toList());
        model.addAttribute("activePage", "dashboard");

        return "receptionist/dashboard";
    }
}
