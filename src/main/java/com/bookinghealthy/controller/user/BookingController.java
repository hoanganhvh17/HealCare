package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
public class BookingController {

    @Autowired private DoctorService doctorService;
    @Autowired private BookingService bookingService;
    @Autowired private UserService userService;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmailService emailService;
    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService; // <-- 1. Inject WalletService

    private User getCurrentUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String usernameOrEmail;
        if (principal instanceof OAuth2User) {
            usernameOrEmail = ((OAuth2User) principal).getAttribute("email");
        } else if (principal instanceof UserDetails) {
            usernameOrEmail = ((UserDetails) principal).getUsername();
        } else {
            usernameOrEmail = principal.toString();
        }
        return userService.findByUsername(usernameOrEmail)
                .or(() -> userService.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new RuntimeException("User not found: " + usernameOrEmail));
    }

    @GetMapping("/appointment")
    public String showAppointmentForm(@RequestParam(value = "doctorId", required = false) Long doctorId,
                                      Model model, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<Department> departments = departmentRepository.findAll();

        model.addAttribute("departments", departments);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("selectedDoctorId", doctorId);
        return "user/appointment";
    }

    @PostMapping("/appointment")
    @Transactional
    public String processAppointment(
            @RequestParam("appointmentType") String appointmentType,
            @RequestParam("doctorId") Long doctorId,
            @RequestParam("appointmentDate") LocalDate appointmentDate,
            @RequestParam("appointmentTime") String appointmentTime,
            @RequestParam("notes") String notes,
            @RequestParam(value = "patientName", required = false) String patientName,
            @RequestParam(value = "patientPhone", required = false) String patientPhone,
            @RequestParam("paymentMethod") String paymentMethod, // <-- 2. Nhận phương thức thanh toán
            HttpServletRequest request,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            User currentUser = getCurrentUser(authentication);
            Doctor doctor = doctorService.findById(doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));

            // Tạo Booking object
            Booking booking = new Booking();
            booking.setUser(currentUser);
            booking.setDoctor(doctor);
            booking.setAppointmentDate(appointmentDate);
            booking.setAppointmentTime(appointmentTime);
            booking.setNotes(notes);
            booking.setAppointmentType(appointmentType);
            booking.setBookingPrice(doctor.getPrice()); // Giá tiền

            String finalName = (patientName != null && !patientName.trim().isEmpty()) ? patientName : currentUser.getFullName();
            String finalPhone = (patientPhone != null && !patientPhone.trim().isEmpty()) ? patientPhone : currentUser.getPhone();
            booking.setPatientName(finalName);
            booking.setPatientPhone(finalPhone);

            // === XỬ LÝ THANH TOÁN ===
            booking.setStatus(BookingStatus.PENDING);
            booking.setPaymentStatus("UNPAID");

            // CASE 1: THANH TOÁN BẰNG VÍ (WALLET)
            if ("WALLET".equals(paymentMethod)) {
                booking.setPaymentMethod("WALLET");
                Booking reservedBooking = bookingService.reserve(booking);

                // Gọi Service trừ tiền sau khi slot đã được giữ
                boolean success = walletService.payWithWallet(currentUser, doctor.getPrice(), "Thanh toán đặt lịch khám bác sĩ " + doctor.getUser().getFullName());

                if (success) {
                    reservedBooking.setStatus(BookingStatus.CONFIRMED);
                    reservedBooking.setPaymentStatus("PAID");
                    Booking savedBooking = bookingService.save(reservedBooking);
                    emailService.sendBookingConfirmation(savedBooking);

                    redirectAttributes.addFlashAttribute("successMessage", "Đặt lịch và thanh toán bằng Ví thành công!");
                    return "redirect:/user/profile"; // Về trang lịch sử
                } else {
                    reservedBooking.setStatus(BookingStatus.CANCELED);
                    reservedBooking.setPaymentStatus("FAILED");
                    bookingService.save(reservedBooking);
                    redirectAttributes.addFlashAttribute("errorMessage", "Số dư ví không đủ để thanh toán!");
                    return "redirect:/appointment";
                }
            }
            // === [THÊM MỚI] CASE 3: CHUYỂN KHOẢN NGÂN HÀNG (VietQR) ===
            else if ("BANK_TRANSFER".equals(paymentMethod)) {
                booking.setPaymentMethod("BANK_TRANSFER");
                Booking savedBooking = bookingService.reserve(booking);

                // Chuyển hướng sang trang quét mã QR, truyền theo ID lịch hẹn
                return "redirect:/checkout-qr?id=" + savedBooking.getId();
            }
            // CASE 2: THANH TOÁN VNPAY (Mặc định)
            else {
                booking.setPaymentMethod("VNPAY");
                Booking savedBooking = bookingService.reserve(booking);

                long amount = doctor.getPrice().longValue();
                String orderInfo = "Thanh toan lich kham #" + savedBooking.getId();
                String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
                String vnpayUrl = paymentService.createVnPayPayment(request, amount, orderInfo, baseUrl + "/payment-return");

                return "redirect:" + vnpayUrl;
            }

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/appointment";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
            return "redirect:/appointment";
        }
    }
}
