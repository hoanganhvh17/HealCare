package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Xem và xử lý lẻ từng lịch hẹn ở quầy tiếp đón.
 */
@Controller
@RequestMapping("/receptionist/bookings")
public class ReceptionistBookingController {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;

    @GetMapping
    public String list(@RequestParam(value = "status", required = false) String status, Model model) {
        List<Booking> listBookings = bookingRepository.findAllByOrderByCreatedAtDesc();

        if (status != null && !status.isBlank()) {
            BookingStatus filter = BookingStatus.valueOf(status);
            listBookings = listBookings.stream()
                    .filter(b -> b.getStatus() == filter)
                    .toList();
        }

        model.addAttribute("listBookings", listBookings);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("activePage", "bookings");
        return "receptionist/booking-list";
    }

    @GetMapping("/confirm/{id}")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + id));

            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new IllegalStateException("Chỉ xác nhận được lịch đang chờ duyệt.");
            }

            booking.setStatus(BookingStatus.CONFIRMED);
            bookingService.save(booking);
            emailService.sendBookingConfirmation(booking);

            ra.addFlashAttribute("successMessage", "Đã xác nhận lịch hẹn #" + id);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/receptionist/bookings";
    }

    @GetMapping("/cancel/{id}")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            String finalReason = (reason == null || reason.isBlank())
                    ? "Quầy lễ tân đã hủy lịch hẹn theo yêu cầu."
                    : reason;

            boolean refunded = bookingService.cancelWithRefund(id, finalReason);

            ra.addFlashAttribute("successMessage", refunded
                    ? "Đã hủy lịch #" + id + " và hoàn tiền vào ví bệnh nhân."
                    : "Đã hủy lịch hẹn #" + id);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/receptionist/bookings";
    }
}
