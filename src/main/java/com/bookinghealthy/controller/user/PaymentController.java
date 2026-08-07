package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaymentController {

    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    @GetMapping("/payment-return")
    public String paymentReturn(
            @RequestParam(value = "vnp_ResponseCode") String responseCode,
            @RequestParam(value = "vnp_OrderInfo") String orderInfo,
            Model model) {

        try {
            // Lấy Booking ID từ OrderInfo ("Thanh toan lich kham #123")
            Long bookingId = Long.parseLong(orderInfo.replace("Thanh toan lich kham #", ""));
            Booking booking = bookingService.findById(bookingId).orElse(null);

            if (booking != null) {
                // === TRƯỜNG HỢP 1: THANH TOÁN THÀNH CÔNG (00) ===
                if ("00".equals(responseCode)) {
                    booking.setPaymentStatus("PAID");
                    booking.setStatus(BookingStatus.CONFIRMED); // Tự động xác nhận luôn
                    bookingService.save(booking);

                    // Gửi mail vé khám + thông báo trong ứng dụng (luôn đi thành cặp)
                    emailService.sendBookingConfirmation(booking);
                    notificationService.pushBookingEvent(booking, "bi-calendar-check text-success",
                            "Đặt lịch thành công");

                    model.addAttribute("successMessage", "Thanh toán thành công! Lịch khám của bạn đã được xác nhận.");
                }
                // === TRƯỜNG HỢP 2: NGƯỜI DÙNG HỦY HOẶC LỖI (Khác 00) ===
                else {
                    // Đánh dấu là Hủy/Thất bại ngay lập tức để không hiện như là "Đã đặt"
                    booking.setPaymentStatus("FAILED");
                    booking.setStatus(BookingStatus.CANCELED); // Hủy luôn booking này
                    bookingService.save(booking);

                    model.addAttribute("errorMessage", "Giao dịch thất bại hoặc đã bị hủy. Lịch hẹn chưa được xác nhận.");
                }
            } else {
                model.addAttribute("errorMessage", "Không tìm thấy thông tin lịch hẹn.");
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Lỗi xử lý kết quả thanh toán.");
        }

        return "user/payment-result";
    }
}