package com.bookinghealthy.controller.user;

import com.bookinghealthy.config.VietQrProperties;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import com.bookinghealthy.util.PaymentMemo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class VietQRController {

    private static final Logger log = LoggerFactory.getLogger(VietQRController.class);

    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private VietQrProperties vietqr;

    @Value("${payment.webhook.secret:}")
    private String webhookSecret;

    @GetMapping("/checkout-qr")
    public String showQrPage(@RequestParam("id") Long bookingId, Authentication authentication, Model model) {
        Booking booking = bookingService.findById(bookingId).orElse(null);
        if (booking == null || !"UNPAID".equals(booking.getPaymentStatus())) {
            return "redirect:/";
        }
        if (!isOwnedByCurrentUser(booking, authentication)) {
            log.warn("[VietQR] Truy cập trang QR của lịch hẹn #{} không thuộc về người đang đăng nhập", bookingId);
            return "redirect:/";
        }

        // booking_price là cột nullable; một dòng thiếu giá làm nổ NPE giữa luồng thanh toán,
        // đúng lúc khách đang cầm điện thoại chờ quét mã.
        if (booking.getBookingPrice() == null) {
            log.warn("[VietQR] Lịch hẹn #{} không có giá khám, không dựng được mã QR", bookingId);
            return "redirect:/user/profile";
        }
        String addInfo = PaymentMemo.format(vietqr.getMemoPrefix(), booking.getId());
        long amount = booking.getBookingPrice().longValue();

        String qrUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s&accountName=%s",
                vietqr.getBankId(), vietqr.getAccountNo(), amount,
                URLEncoder.encode(addInfo, StandardCharsets.UTF_8),
                URLEncoder.encode(vietqr.getAccountName(), StandardCharsets.UTF_8));

        model.addAttribute("booking", booking);
        model.addAttribute("qrUrl", qrUrl);
        model.addAttribute("addInfo", addInfo);
        model.addAttribute("bankId", vietqr.getBankId());
        model.addAttribute("accountNo", vietqr.getAccountNo());
        model.addAttribute("accountName", vietqr.getAccountName());
        return "user/checkout-qr";
    }

    @GetMapping("/api/payment/check-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkPaymentStatus(@RequestParam("id") Long bookingId,
                                                                  Authentication authentication) {
        Booking booking = bookingService.findById(bookingId).orElse(null);
        if (booking == null || !isOwnedByCurrentUser(booking, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("paymentStatus", booking.getPaymentStatus());
        body.put("bookingStatus", booking.getStatus() == null ? null : booking.getStatus().name());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/payment/webhook")
    @ResponseBody
    public ResponseEntity<String> receiveWebhook(@RequestBody Map<String, Object> payload,
                                                 HttpServletRequest request) {
        if (!isAuthorizedWebhook(request)) {
            log.warn("[Webhook] Từ chối request không mang đúng bí mật.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        try {
            BankTransfer transfer = BankTransfer.from(payload);
            if (transfer == null) {
                log.warn("[Webhook] Không đọc được payload (thiếu nội dung chuyển khoản).");
                return ResponseEntity.ok("IGNORED");
            }

            if (transfer.reference() != null && bookingService.isBankTxnProcessed(transfer.reference())) {
                log.info("[Webhook] Giao dịch {} đã xử lý trước đó, bỏ qua.", transfer.reference());
                return ResponseEntity.ok("DUPLICATED");
            }

            Optional<Long> bookingId = PaymentMemo.parseBookingId(vietqr.getMemoPrefix(), transfer.description());
            if (bookingId.isEmpty()) {
                log.info("[Webhook] Nội dung chuyển khoản không chứa mã lịch hẹn, bỏ qua.");
                return ResponseEntity.ok("IGNORED");
            }

            Booking booking = bookingService.findById(bookingId.get()).orElse(null);
            if (booking == null) {
                log.warn("[Webhook] Không tìm thấy lịch hẹn #{}", bookingId.get());
                return ResponseEntity.ok("IGNORED");
            }
            if (!"UNPAID".equals(booking.getPaymentStatus())) {
                log.info("[Webhook] Lịch hẹn #{} không còn ở trạng thái chờ thanh toán.", booking.getId());
                return ResponseEntity.ok("ALREADY_SETTLED");
            }

            BigDecimal received = transfer.amount();
            if (received == null || booking.getBookingPrice().compareTo(received) > 0) {
                log.warn("[Webhook] Lịch hẹn #{} nhận thiếu tiền: chờ {} nhận {}",
                        booking.getId(), booking.getBookingPrice(), received);
                return ResponseEntity.ok("AMOUNT_MISMATCH");
            }

            booking.setPaymentStatus("PAID");
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setBankTxnRef(transfer.reference());
            bookingService.save(booking);

            emailService.sendBookingConfirmation(booking);
            notificationService.pushBookingEvent(booking, "bi-calendar-check text-success",
                    "Đặt lịch thành công");

            log.info("[Webhook] Đã xác nhận thanh toán cho lịch hẹn #{}", booking.getId());
            return ResponseEntity.ok("SUCCESS");

        } catch (Exception e) {
            // KHÔNG in payload ra log: đó là dữ liệu tài chính của khách.
            log.error("[Webhook] Lỗi xử lý: {}", e.getMessage());
            return ResponseEntity.ok("ERROR");
        }
    }

    private boolean isOwnedByCurrentUser(Booking booking, Authentication authentication) {
        User current = currentUserService.find(authentication).orElse(null);
        return current != null
                && booking.getUser() != null
                && booking.getUser().getId().equals(current.getId());
    }

    private boolean isAuthorizedWebhook(HttpServletRequest request) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        String presented = request.getHeader("Secure-Token");
        if (presented == null) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Apikey ", 0, 7)) {
                presented = auth.substring(7).trim();
            }
        }
        if (presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                webhookSecret.getBytes(StandardCharsets.UTF_8));
    }

    private record BankTransfer(String description, BigDecimal amount, String reference) {

        @SuppressWarnings("unchecked")
        static BankTransfer from(Map<String, Object> payload) {
            if (payload == null) {
                return null;
            }
            Map<String, Object> node = payload;

            Object data = payload.get("data");
            if (data instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                node = (Map<String, Object>) first;
            } else if (data instanceof Map<?, ?> map) {
                node = (Map<String, Object>) map;
            }

            String description = firstNonBlank(node, "description", "content");
            if (description == null) {
                return null;
            }
            return new BankTransfer(
                    description,
                    toAmount(firstNonBlank(node, "transferAmount", "amount")),
                    firstNonBlank(node, "referenceCode", "tid", "id"));
        }

        private static String firstNonBlank(Map<String, Object> node, String... keys) {
            for (String key : keys) {
                Object value = node.get(key);
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            }
            return null;
        }

        private static BigDecimal toAmount(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return new BigDecimal(raw.replaceAll("[,\\s]", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
