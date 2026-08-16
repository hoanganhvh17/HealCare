package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import com.bookinghealthy.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.Optional;

@Controller
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;
    @Autowired private PaymentService paymentService;
    @Autowired private CurrentUserService currentUserService;

    @GetMapping("/payment-return")
    public String paymentReturn(HttpServletRequest request, Authentication authentication, Model model) {
        if (!paymentService.isValidSignature(request.getParameterMap())) {
            log.warn("[VNPay] Chữ ký không hợp lệ, từ chối xử lý. txnRef={}", request.getParameter("vnp_TxnRef"));
            model.addAttribute("errorMessage",
                    "Không xác thực được kết quả thanh toán. Vui lòng kiểm tra lại lịch hẹn trong hồ sơ của bạn.");
            return "user/payment-result";
        }

        if (!paymentService.isOurMerchant(request.getParameter("vnp_TmnCode"))) {
            log.warn("[VNPay] Sai mã merchant: {}", request.getParameter("vnp_TmnCode"));
            model.addAttribute("errorMessage", "Kết quả thanh toán không thuộc về hệ thống này.");
            return "user/payment-result";
        }

        String txnRef = request.getParameter("vnp_TxnRef");
        Optional<Booking> found = (txnRef == null || txnRef.isBlank())
                ? Optional.empty()
                : bookingService.findByVnpTxnRef(txnRef);

        if (found.isEmpty()) {
            model.addAttribute("errorMessage", "Không tìm thấy thông tin lịch hẹn.");
            return "user/payment-result";
        }
        Booking booking = found.get();
        User currentUser = currentUserService.find(authentication).orElse(null);
        if (currentUser == null || booking.getUser() == null
                || !booking.getUser().getId().equals(currentUser.getId())) {
            log.warn("[VNPay] Người dùng {} mở kết quả của lịch hẹn #{} không thuộc về mình",
                    currentUser == null ? "?" : currentUser.getUsername(), booking.getId());
            model.addAttribute("errorMessage", "Bạn không có quyền xem kết quả thanh toán của lịch hẹn này.");
            return "user/payment-result";
        }

        if (!"UNPAID".equals(booking.getPaymentStatus())) {
            if ("PAID".equals(booking.getPaymentStatus())) {
                model.addAttribute("successMessage", "Lịch khám của bạn đã được xác nhận thanh toán.");
            } else {
                model.addAttribute("errorMessage", "Giao dịch không thành công. Lịch hẹn chưa được xác nhận.");
            }
            return "user/payment-result";
        }

        String responseCode = request.getParameter("vnp_ResponseCode");
        String transactionStatus = request.getParameter("vnp_TransactionStatus");
        boolean succeeded = "00".equals(responseCode) && "00".equals(transactionStatus);

        if (succeeded) {
            BigDecimal expected = booking.getBookingPrice().multiply(BigDecimal.valueOf(100));
            BigDecimal actual;
            try {
                actual = new BigDecimal(request.getParameter("vnp_Amount"));
            } catch (Exception e) {
                actual = BigDecimal.valueOf(-1);
            }
            if (expected.compareTo(actual) != 0) {
                log.warn("[VNPay] Số tiền lệch cho lịch hẹn #{}: chờ {} nhận {}",
                        booking.getId(), expected, actual);
                model.addAttribute("errorMessage",
                        "Số tiền thanh toán không khớp với giá khám. Vui lòng liên hệ phòng khám.");
                return "user/payment-result";
            }

            booking.setPaymentStatus("PAID");
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingService.save(booking);

            emailService.sendBookingConfirmation(booking);
            notificationService.pushBookingEvent(booking, "bi-calendar-check text-success",
                    "Đặt lịch thành công");

            model.addAttribute("successMessage", "Thanh toán thành công! Lịch khám của bạn đã được xác nhận.");
        } else {
            booking.setPaymentStatus("FAILED");
            booking.setStatus(BookingStatus.CANCELED);
            bookingService.save(booking);

            model.addAttribute("errorMessage",
                    "Giao dịch thất bại hoặc đã bị hủy. Lịch hẹn chưa được xác nhận.");
        }

        return "user/payment-result";
    }
}
