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
import java.time.LocalDateTime;
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
    @Autowired private NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url}")
    private String appBaseUrl;

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
                                      @RequestParam(value = "appointmentDate", required = false) LocalDate appointmentDate,
                                      Model model, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<Department> departments = departmentRepository.findAll();

        model.addAttribute("departments", departments);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("selectedDoctorId", doctorId);
        model.addAttribute("appointmentDate", appointmentDate);

        // Cùng một hàm quyết định cho cả giao diện lẫn server, nên hai bên không bao giờ
        // nói khác nhau — cùng khuôn với cancelBlockReasons / actionBlockReasons.
        model.addAttribute("payAtCounterBlockReason",
                bookingService.whyCannotBookWithoutPayment(currentUser));
        model.addAttribute("maxPayAtCounterBookings", BookingService.MAX_PAY_AT_COUNTER_BOOKINGS);
        return "user/appointment";
    }

    @PostMapping("/appointment")
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
            if (!bookingService.isSlotWithinWorkingHours(doctorId, appointmentDate, appointmentTime)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Bác sĩ không nhận khám vào khung giờ này. Vui lòng chọn khung giờ trong ca làm việc của bác sĩ.");
                return "redirect:/appointment?doctorId=" + doctorId;
            }

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
            LocalDateTime slotStart = bookingService.appointmentStart(booking);
            if (slotStart != null && slotStart.isBefore(LocalDateTime.now())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Khung giờ vừa chọn đã trôi qua, vui lòng chọn khung giờ khác.");
                return "redirect:/appointment?doctorId=" + doctorId;
            }

            // === XỬ LÝ THANH TOÁN ===
            booking.setStatus(BookingStatus.PENDING);
            booking.setPaymentStatus("UNPAID");

            // CASE 1: THANH TOÁN BẰNG VÍ (WALLET)
            if ("WALLET".equals(paymentMethod)) {
                booking.setPaymentMethod("WALLET");
                // Giữ chỗ + trừ tiền + chốt trạng thái nằm trong MỘT transaction của service.
                // Trước đây controller điều phối ba transaction rời nhau, nên một lỗi ở bước
                // cuối để lại đúng trạng thái tệ nhất: tiền đã trừ, lịch vẫn chưa thanh toán.
                Booking savedBooking = bookingService.reserveAndPayWithWallet(booking,
                        doctor.getPrice(),
                        "Thanh toán đặt lịch khám bác sĩ " + doctor.getUser().getFullName());

                if (savedBooking == null) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Số dư ví không đủ để thanh toán!");
                    return "redirect:/appointment";
                }

                // Email và chuông đặt NGOÀI transaction: thư đã gửi thì không rút lại được.
                emailService.sendBookingConfirmation(savedBooking);
                notificationService.pushBookingEvent(savedBooking, "bi-calendar-check text-success",
                        "Đặt lịch thành công");

                redirectAttributes.addFlashAttribute("successMessage",
                        "Đặt lịch và thanh toán bằng Ví thành công!");
                return "redirect:/user/profile"; // Về trang lịch sử
            }
            // === [THÊM MỚI] CASE 3: CHUYỂN KHOẢN NGÂN HÀNG (VietQR) ===
            else if ("BANK_TRANSFER".equals(paymentMethod)) {
                booking.setPaymentMethod("BANK_TRANSFER");
                Booking savedBooking = bookingService.reserve(booking);
                return "redirect:/checkout-qr?id=" + savedBooking.getId();
            }
            // === CASE 4: ĐẶT TRƯỚC, TRẢ TIỀN TẠI QUẦY ===
            // PHẢI đứng trước nhánh VNPay. Nhánh else cũ là catch-all ép MỌI giá trị lạ
            // thành VNPAY, nên thiếu nhánh này là bệnh nhân chọn "không trả trước" và bị
            // đẩy thẳng sang cổng thanh toán — không exception, không log.
            else if (BookingService.PAY_AT_COUNTER.equals(paymentMethod)) {
                String blocked = bookingService.whyCannotBookWithoutPayment(currentUser);
                if (blocked != null) {
                    redirectAttributes.addFlashAttribute("errorMessage", blocked);
                    return "redirect:/appointment?doctorId=" + doctorId;
                }
                booking.setPaymentMethod(BookingService.PAY_AT_COUNTER);
                // Giữ nguyên PENDING + UNPAID đã đặt phía trên: lịch chờ nhân viên xác nhận.
                Booking savedBooking = bookingService.reserve(booking);
                notificationService.pushBookingEvent(savedBooking, "bi-calendar-plus text-primary",
                        "Đã gửi yêu cầu đặt lịch");
                // CỐ Ý không gửi email: sendBookingConfirmation nói "đã xác nhận", sai với một
                // lịch PENDING; rồi khi lễ tân xác nhận thật bệnh nhân nhận lá thứ hai gần y hệt
                // — đúng thứ khiến người ta tưởng hệ thống lỗi rồi ĐẶT LẠI, tức chính cái spam
                // mà hạn mức ở trên đang chống.
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã gửi yêu cầu đặt lịch #" + savedBooking.getId() + ". Bệnh viện sẽ xác nhận "
                        + "trong thời gian sớm nhất — bạn không cần đặt lại. Vui lòng đến quầy lễ tân "
                        + "trước giờ khám 15 phút để thanh toán phí khám.");
                return "redirect:/user/profile";
            }
            // CASE 2: THANH TOÁN VNPAY
            else if ("VNPAY".equals(paymentMethod)) {
                booking.setPaymentMethod("VNPAY");
                Booking savedBooking = bookingService.reserve(booking);
                long amount = doctor.getPrice().longValue();
                String orderInfo = "Thanh toan lich kham #" + savedBooking.getId();
                PaymentService.VnPayPayment payment = paymentService.createVnPayPayment(
                        request, amount, orderInfo, appBaseUrl + "/payment-return");
                savedBooking.setVnpTxnRef(payment.txnRef());
                bookingService.save(savedBooking);

                return "redirect:" + payment.paymentUrl();
            }
            // KHÔNG còn nhánh catch-all: một giá trị lạ phải bị TỪ CHỐI tường minh chứ không
            // được âm thầm biến thành một hình thức thanh toán khác.
            else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Hình thức thanh toán không hợp lệ, vui lòng chọn lại.");
                return "redirect:/appointment?doctorId=" + doctorId;
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
