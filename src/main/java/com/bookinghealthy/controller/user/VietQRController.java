package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class VietQRController {

    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    // 1. HIỂN THỊ TRANG QUÉT MÃ QR
    @GetMapping("/checkout-qr")
    public String showQrPage(@RequestParam("id") Long bookingId, Model model) {
        Booking booking = bookingService.findById(bookingId).orElse(null);
        if (booking == null || !"UNPAID".equals(booking.getPaymentStatus())) {
            return "redirect:/"; // Lỗi hoặc đã thanh toán thì về trang chủ
        }

        // CẤU HÌNH NGÂN HÀNG CỦA BẠN TẠI ĐÂY
        String bankId = "MB"; // Mã ngân hàng (MB, VCB, VTB...)
        String accountNo = "0965170604"; // Số tài khoản của bạn
        String accountName = "VU HUU HOANG ANH"; // Tên chủ tài khoản (Viết không dấu)

        // Tạo cú pháp nội dung chuyển khoản: MDTRUST + BookingID (VD: MDTRUST 159)
        String addInfo = "HEALCARE " + booking.getId();
        long amount = booking.getBookingPrice().longValue();

        // Tạo link ảnh VietQR (Dùng API miễn phí của vietqr.io)
        String qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s&accountName=%s",
                bankId, accountNo, amount, addInfo.replace(" ", "%20"), accountName.replace(" ", "%20"));

        model.addAttribute("booking", booking);
        model.addAttribute("qrUrl", qrUrl);
        model.addAttribute("addInfo", addInfo);

        return "user/checkout-qr"; // Tí nữa tạo file HTML này
    }

    // 2. API KIỂM TRA TRẠNG THÁI (ĐỂ JAVASCRIPT TRÊN WEB GỌI MỖI 3 GIÂY)
    @GetMapping("/api/payment/check-status")
    @ResponseBody
    public ResponseEntity<String> checkPaymentStatus(@RequestParam("id") Long bookingId) {
        Booking booking = bookingService.findById(bookingId).orElse(null);
        if (booking != null && "PAID".equals(booking.getPaymentStatus())) {
            return ResponseEntity.ok("PAID");
        }
        return ResponseEntity.ok("UNPAID");
    }

    // 3. WEBHOOK ĐỂ CASSO / SEPAY BẮN VÀO KHI CÓ TIỀN
    @PostMapping("/api/payment/webhook")
    @ResponseBody
    public ResponseEntity<String> receiveWebhook(@RequestBody Map<String, Object> payload) {
        try {
            System.out.println(">>> CÓ TIỀN VÀO! Webhook Payload: " + payload);

            // Xử lý payload tùy theo bạn dùng Casso hay SePay (Dưới đây là form chung)
            // Giả sử lấy được nội dung CK là "MDTRUST 159"
            String description = payload.get("description").toString().toUpperCase();

            if (description.contains("MDTRUST")) {
                // Trích xuất ID 159 từ chuỗi
                String idStr = description.replaceAll("[^0-9]", "");
                Long bookingId = Long.parseLong(idStr);

                Booking booking = bookingService.findById(bookingId).orElse(null);
                if (booking != null && "UNPAID".equals(booking.getPaymentStatus())) {
                    // XÁC NHẬN THANH TOÁN THÀNH CÔNG
                    booking.setPaymentStatus("PAID");
                    booking.setStatus(BookingStatus.CONFIRMED);
                    bookingService.save(booking);

                    emailService.sendBookingConfirmation(booking);
                    notificationService.pushBookingEvent(booking, "bi-calendar-check text-success",
                            "Đặt lịch thành công");
                    System.out.println(">>> Đã duyệt tự động Booking #" + bookingId);
                }
            }
            return ResponseEntity.ok("SUCCESS");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("ERROR");
        }
    }
}