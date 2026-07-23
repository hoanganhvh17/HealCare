package com.bookinghealthy.controller.user;

import com.bookinghealthy.dto.UpdateProfileDTO;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class ProfileController {

    @Autowired private ProfileService profileService;
    @Autowired private BookingService bookingService;
    @Autowired private UserService userService;
    @Autowired private EmailService emailService;
    @Autowired private WalletService walletService;

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

    // 1. TRANG HỒ SƠ
    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());

        // Lấy danh sách booking (đã sắp xếp giảm dần theo ngày)
        List<Booking> myBookings = bookingService.findByUser(user);

        // Điều kiện đổi lịch được tính ở service để giao diện và server không nói khác nhau:
        // null = còn đổi được, ngược lại là lý do hiển thị ngay trên nút.
        Map<Long, String> editBlockReasons = new HashMap<>();
        Map<Long, Long> hoursLeft = new HashMap<>();
        for (Booking booking : myBookings) {
            editBlockReasons.put(booking.getId(), bookingService.whyCannotReschedule(booking));
            hoursLeft.put(booking.getId(), bookingService.hoursUntilAppointment(booking));
        }

        model.addAttribute("user", user);
        model.addAttribute("updateProfile", dto);
        model.addAttribute("myBookings", myBookings);
        model.addAttribute("editBlockReasons", editBlockReasons);
        model.addAttribute("hoursLeft", hoursLeft);
        return "user/profile";
    }

    // 2. CẬP NHẬT PROFILE
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

    // 3. UPLOAD AVATAR
    @PostMapping("/upload-avatar")
    public String uploadAvatar(Authentication authentication, @RequestParam("avatar") MultipartFile file, RedirectAttributes ra) {
        if (!file.isEmpty()) {
            try {
                User currentUser = getCurrentUser(authentication);
                String folderPath = "uploads/";
                File dir = new File(folderPath);
                if (!dir.exists()) dir.mkdirs();

                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Path path = Paths.get(folderPath + fileName);
                Files.write(path, file.getBytes());

                profileService.updateAvatar(currentUser.getUsername(), fileName);
                
                // Reload User từ database
                User updatedUser = userService.findByUsername(currentUser.getUsername()).orElse(currentUser);
                
                // Cập nhật lại Authentication context với CustomUserDetails mới
                CustomUserDetails newUserDetails = new CustomUserDetails(updatedUser);
                Authentication newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        newUserDetails, authentication.getCredentials(), newUserDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(newAuth);
                
                ra.addFlashAttribute("successMessage", "Cập nhật ảnh thành công!");
            } catch (IOException e) {
                ra.addFlashAttribute("errorMessage", "Lỗi tải ảnh: " + e.getMessage());
            }
        }
        return "redirect:/user/profile";
    }

    // 4. TRANG ĐỔI MẬT KHẨU
    @GetMapping("/change-password")
    public String showChangePasswordForm() {
        return "user/change-password";
    }

    // 5. XỬ LÝ ĐỔI MẬT KHẨU
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

    // 6. HỦY LỊCH HẸN (ĐÃ NÂNG CẤP CHECK THỜI GIAN & TRẠNG THÁI)
    @GetMapping("/cancel-booking/{id}")
    public String cancelMyBooking(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes ra) {
        try {
            User currentUser = getCurrentUser(authentication);
            Booking booking = bookingService.findById(id).orElseThrow(() -> new Exception("Không tìm thấy lịch"));

            // Check 1: Quyền sở hữu
            if (!booking.getUser().getId().equals(currentUser.getId())) {
                ra.addFlashAttribute("errorMessage", "Bạn không có quyền hủy lịch này.");
                return "redirect:/user/profile";
            }

            // Check 2: Không cho hủy nếu đã Hoàn thành hoặc Đã hủy
            if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELED) {
                ra.addFlashAttribute("errorMessage", "Lịch này đã hoàn thành hoặc đã bị hủy, không thể thao tác.");
                return "redirect:/user/profile";
            }

            // === CHECK 3: KIỂM TRA THỜI GIAN (QUAN TRỌNG) ===
            // Logic: Chỉ cho phép hủy trước giờ khám ít nhất 24 tiếng (hoặc 12 tiếng tùy bạn chỉnh)
            try {
                // Giả sử appointmentTime lưu dạng "08:00" hoặc "08:00 - 08:30"
                // Ta lấy giờ bắt đầu để check
                String startTimeStr = booking.getAppointmentTime().split(" - ")[0].trim(); // Lấy "08:00"
                // Định dạng giờ phút nếu cần, ở đây LocalTime.parse mặc định hiểu HH:mm
                LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));

                LocalDateTime appointmentDateTime = LocalDateTime.of(booking.getAppointmentDate(), startTime);
                LocalDateTime now = LocalDateTime.now();

                // Quy định: Phải hủy trước 24 tiếng
                // Nếu (Hiện tại + 24h) mà lớn hơn (Giờ khám) -> Nghĩa là còn dưới 24h -> CHẶN
                if (now.plusHours(24).isAfter(appointmentDateTime)) {
                    ra.addFlashAttribute("errorMessage", "Rất tiếc, bạn chỉ có thể hủy lịch trước giờ khám ít nhất 24 tiếng. Vui lòng liên hệ hotline để được hỗ trợ.");
                    return "redirect:/user/profile";
                }
            } catch (Exception e) {
                // Nếu lỗi parse giờ (do dữ liệu cũ), ta tạm bỏ qua hoặc log lại
                System.out.println("Lỗi check time: " + e.getMessage());
            }
            // ==================================================

            // Nếu vượt qua hết các bước kiểm tra -> Tiến hành HỦY
            booking.setStatus(BookingStatus.CANCELED);

            // LOGIC HOÀN TIỀN VÀO VÍ
            if ("PAID".equals(booking.getPaymentStatus())) {
                walletService.refundToWallet(currentUser, booking.getBookingPrice(), "Hoàn tiền do hủy lịch khám #" + booking.getId());
                booking.setPaymentStatus("REFUNDED");
                ra.addFlashAttribute("successMessage", "Đã hủy lịch. Tiền đã được hoàn lại vào Ví của bạn.");
            } else {
                booking.setPaymentStatus("FAILED");
                ra.addFlashAttribute("successMessage", "Đã hủy lịch hẹn thành công.");
            }

            bookingService.save(booking);
            emailService.sendBookingCancellation(booking, "Người bệnh tự hủy (Đúng quy định trước 24h).");

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }

        return "redirect:/user/profile";
    }
}