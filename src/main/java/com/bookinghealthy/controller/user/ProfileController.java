package com.bookinghealthy.controller.user;

import com.bookinghealthy.dto.UpdateProfileDTO;
import com.bookinghealthy.model.Allergy;
import com.bookinghealthy.model.ExternalMedicalRecord;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.User;
import com.bookinghealthy.security.CustomUserDetails;
import com.bookinghealthy.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/user")
public class ProfileController {

    @Autowired private ProfileService profileService;
    @Autowired private BookingService bookingService;
    @Autowired private UserService userService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;
    @Autowired private WalletService walletService;
    @Autowired private AllergyService allergyService;
    @Autowired private com.bookinghealthy.service.ExternalMedicalRecordService externalMedicalRecordService;
    @Autowired private ReviewService reviewService;
    @Autowired private FileStorageService fileStorageService;

    // Helper lấy User
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

    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());

        List<Booking> myBookings = bookingService.findByUser(user);
        Map<Long, String> editBlockReasons = new HashMap<>();
        Map<Long, String> cancelBlockReasons = new HashMap<>();
        Map<Long, Long> hoursLeft = new HashMap<>();
        Set<Long> reviewedBookingIds = new HashSet<>();
        List<Booking> upcomingBookings = new ArrayList<>();
        List<Booking> pastBookings = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Booking booking : myBookings) {
            editBlockReasons.put(booking.getId(), bookingService.whyCannotReschedule(booking));
            cancelBlockReasons.put(booking.getId(), bookingService.whyCannotCancel(booking));
            hoursLeft.put(booking.getId(), bookingService.hoursUntilAppointment(booking));
            if (booking.getStatus() == BookingStatus.COMPLETED
                    && reviewService.hasReview(booking.getId())) {
                reviewedBookingIds.add(booking.getId());
            }

            LocalDateTime start = bookingService.appointmentStart(booking);
            boolean stillOpen = booking.getStatus() == BookingStatus.PENDING
                    || booking.getStatus() == BookingStatus.CONFIRMED;

            if (stillOpen && start != null && !start.isBefore(now)) {
                upcomingBookings.add(booking);
            } else {
                pastBookings.add(booking);
            }
        }

        Comparator<Booking> byStart = Comparator.comparing(
                bookingService::appointmentStart, Comparator.nullsLast(Comparator.naturalOrder()));
        upcomingBookings.sort(byStart);
        pastBookings.sort(byStart.reversed());

        long completedCount = myBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
        long canceledCount = myBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELED).count();

        model.addAttribute("user", user);
        model.addAttribute("updateProfile", dto);
        model.addAttribute("myBookings", myBookings);
        model.addAttribute("upcomingBookings", upcomingBookings);
        model.addAttribute("pastBookings", pastBookings);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("canceledCount", canceledCount);
        model.addAttribute("editBlockReasons", editBlockReasons);
        model.addAttribute("cancelBlockReasons", cancelBlockReasons);
        model.addAttribute("hoursLeft", hoursLeft);
        model.addAttribute("reviewedBookingIds", reviewedBookingIds);
        model.addAttribute("payAtCounterCount", bookingService.countActivePayAtCounterBookings(user));
        model.addAttribute("maxPayAtCounterBookings", BookingService.MAX_PAY_AT_COUNTER_BOOKINGS);
        List<Allergy> allergies = allergyService.findForUser(user.getId());
        Map<Long, String> allergyBlockReasons = new HashMap<>();
        for (Allergy allergy : allergies) {
            allergyBlockReasons.put(allergy.getId(), allergyService.whyCannotDelete(allergy, user));
        }
        model.addAttribute("allergies", allergies);
        model.addAttribute("allergyBlockReasons", allergyBlockReasons);

        // Hồ sơ bệnh án bệnh nhân mang từ nơi khác tới — cùng khuôn với dị ứng ngay trên: template
        // dùng map lý do để ẨN nút xoá, controller xoá thì gọi lại chính hàm đó, nên giao diện và
        // server không bao giờ nói khác nhau.
        List<ExternalMedicalRecord> externalRecords =
                externalMedicalRecordService.findForUser(user.getId());
        Map<Long, String> documentBlockReasons = new HashMap<>();
        for (ExternalMedicalRecord record : externalRecords) {
            documentBlockReasons.put(record.getId(),
                    externalMedicalRecordService.whyCannotDelete(record, user));
        }
        model.addAttribute("externalRecords", externalRecords);
        model.addAttribute("documentBlockReasons", documentBlockReasons);

        return "user/profile";
    }

    @PostMapping("/update-profile")
    public String updateProfile(Authentication authentication, @ModelAttribute UpdateProfileDTO dto, RedirectAttributes ra) {
        try {
            User currentUser = getCurrentUser(authentication);
            profileService.updateProfile(currentUser.getUsername(), dto);
            ra.addFlashAttribute("successMessage", "Cập nhật hồ sơ thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/user/profile";
    }

    @PostMapping("/upload-avatar")
    public String uploadAvatar(Authentication authentication, @RequestParam("avatar") MultipartFile file, RedirectAttributes ra) {
        if (!file.isEmpty()) {
            try {
                User currentUser = getCurrentUser(authentication);
                String fileName = fileStorageService.storeImage(file, null);
                profileService.updateAvatar(currentUser.getUsername(), fileName);
                User updatedUser = userService.findByUsername(currentUser.getUsername()).orElse(currentUser);
                CustomUserDetails newUserDetails = new CustomUserDetails(updatedUser);
                Authentication newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        newUserDetails, authentication.getCredentials(), newUserDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(newAuth);
                ra.addFlashAttribute("successMessage", "Cập nhật ảnh thành công!");
            } catch (RuntimeException e) {
                ra.addFlashAttribute("errorMessage", "Lỗi tải ảnh: " + e.getMessage());
            }
        }
        return "redirect:/user/profile";
    }

    @GetMapping("/change-password")
    public String showChangePasswordForm() {
        return "user/change-password";
    }

    @PostMapping("/change-password")
    public String processChangePassword(@RequestParam("currentPassword") String currentPassword,
                                        @RequestParam("newPassword") String newPassword,
                                        @RequestParam("confirmPassword") String confirmPassword,
                                        Authentication authentication, RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "Mật khẩu xác nhận không khớp!");
            return "redirect:/user/change-password";
        }
        try {
            User currentUser = getCurrentUser(authentication);
            profileService.changePassword(currentUser.getUsername(), currentPassword, newPassword);
            ra.addFlashAttribute("successMessage", "Đổi mật khẩu thành công!");
            return "redirect:/user/profile";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/user/change-password";
        }
    }

    @PostMapping("/cancel-booking/{id}")
    public String cancelMyBooking(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes ra) {
        try {
            User currentUser = getCurrentUser(authentication);
            Booking booking = bookingService.findById(id).orElseThrow(() -> new Exception("Không tìm thấy lịch"));

            if (!booking.getUser().getId().equals(currentUser.getId())) {
                ra.addFlashAttribute("errorMessage", "Bạn không có quyền hủy lịch này.");
                return "redirect:/user/profile#booking-history";
            }
            String blockedReason = bookingService.whyCannotCancel(booking);
            if (blockedReason != null) {
                ra.addFlashAttribute("errorMessage", blockedReason);
                return "redirect:/user/profile#booking-history";
            }
            // Đi qua cancelWithRefund thay vì tự viết lại: đây từng là đường hủy DUY NHẤT
            // không dùng hàm chung, nên nó vừa lặp lại logic hoàn ví + email + chuông, vừa
            // thiếu transaction bao ngoài — refundToWallet commit xong mà save() hỏng là
            // tiền đã ra khỏi két trong khi lịch vẫn PAID.
            boolean refunded = bookingService.cancelWithRefund(
                    booking.getId(), "Người bệnh tự hủy (Đúng quy định trước 24h).");
            ra.addFlashAttribute("successMessage", refunded
                    ? "Đã hủy lịch. Tiền đã được hoàn lại vào Ví của bạn."
                    : "Đã hủy lịch hẹn thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }

        return "redirect:/user/profile#booking-history";
    }
}