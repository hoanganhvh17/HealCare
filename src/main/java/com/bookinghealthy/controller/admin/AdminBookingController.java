package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.WalletService; // <-- Import WalletService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminBookingController {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private WalletService walletService; // <-- Inject WalletService

    // 1. HIỂN THỊ DANH SÁCH LỊCH HẸN
    @GetMapping("/manage-booking")
    public String showBookingList(Model model) {
        // Lấy tất cả booking, sắp xếp mới nhất
        List<Booking> listBookings = bookingRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("listBookings", listBookings);
        return "admin/booking-list"; // Đảm bảo bạn có file html này (giống doctor/booking-manager)
    }

    // 2. XÁC NHẬN LỊCH HẸN
    @GetMapping("/manage-booking/confirm/{id}")
    public String confirmBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id).orElseThrow(() -> new Exception("Booking not found"));

            booking.setStatus(BookingStatus.CONFIRMED);
            bookingService.save(booking);

            emailService.sendBookingConfirmation(booking);

            ra.addFlashAttribute("successMessage", "Đã xác nhận lịch hẹn #" + id);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/manage-booking";
    }

    // 3. HỦY LỊCH & HOÀN TIỀN (NÂNG CẤP LOGIC VÍ)
    @GetMapping("/manage-booking/cancel/{id}")
    public String cancelBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id).orElseThrow(() -> new Exception("Booking not found"));

            // Cập nhật trạng thái Hủy
            booking.setStatus(BookingStatus.CANCELED);

            // === LOGIC HOÀN TIỀN VÀO VÍ ===
            if ("PAID".equals(booking.getPaymentStatus())) {
                // 1. Cộng tiền vào ví khách hàng
                walletService.refundToWallet(
                        booking.getUser(),
                        booking.getBookingPrice(),
                        "Admin hủy lịch khám #" + booking.getId()
                );

                // 2. Cập nhật trạng thái thanh toán
                booking.setPaymentStatus("REFUNDED");

                ra.addFlashAttribute("successMessage", "Đã hủy lịch #" + id + ". Hệ thống đã tự động hoàn tiền vào Ví khách hàng.");
            } else {
                booking.setPaymentStatus("FAILED");
                ra.addFlashAttribute("successMessage", "Đã hủy lịch hẹn #" + id);
            }
            // ==============================

            bookingService.save(booking);

            // Gửi email thông báo
            emailService.sendBookingCancellation(booking, "Admin hệ thống đã hủy lịch hẹn.");

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/manage-booking";
    }

    // 4. XÓA VĨNH VIỄN (Chỉ dùng cho admin)
    @GetMapping("/manage-booking/delete/{id}")
    public String deleteBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            bookingService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn lịch hẹn.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: Không thể xóa (Có thể do ràng buộc dữ liệu).");
        }
        return "redirect:/admin/manage-booking";
    }
}