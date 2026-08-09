package com.bookinghealthy.controller.user;

import com.bookinghealthy.dto.UpdateProfileDTO;
import com.bookinghealthy.model.Allergy;
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
    @Autowired private ReviewService reviewService;

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

        List<Booking> myBookings = bookingService.findByUser(user);

        // Điều kiện đổi/hủy được tính ở service để giao diện và server không nói khác nhau:
        // null = còn thao tác được, ngược lại là lý do hiển thị ngay dưới nút.
        Map<Long, String> editBlockReasons = new HashMap<>();
        Map<Long, String> cancelBlockReasons = new HashMap<>();
        Map<Long, Long> hoursLeft = new HashMap<>();
        // Ca đã đánh giá rồi thì template thay nút bằng nhãn "Đã đánh giá". Trước đây nút hiện
        // với MỌI ca COMPLETED, nên bệnh nhân mở modal, chấm sao, gõ nhận xét, bấm Gửi và chỉ
        // khi đó mới nhận được câu "Bạn đã đánh giá dịch vụ này rồi." — mất trắng phần đã gõ.
        Set<Long> reviewedBookingIds = new HashSet<>();

        // Repository trả về theo thứ tự chèn nên lịch mới đặt không nằm ở đầu bảng.
        // Tách hẳn hai nhóm: sắp tới (gần nhất lên trước) và đã qua (mới nhất lên trước).
        List<Booking> upcomingBookings = new ArrayList<>();
        List<Booking> pastBookings = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Booking booking : myBookings) {
            editBlockReasons.put(booking.getId(), bookingService.whyCannotReschedule(booking));
            cancelBlockReasons.put(booking.getId(), bookingService.whyCannotCancel(booking));
            hoursLeft.put(booking.getId(), bookingService.hoursUntilAppointment(booking));

            // Chỉ ca đã khám xong mới có nút đánh giá, nên chỉ cần tra đúng nhóm đó.
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

        // Tab "Hồ sơ y tế". Lý do không xoá được tính sẵn ở đây rồi truyền xuống, cùng khuôn
        // cancelBlockReasons ở trên: template ẩn nút theo đúng hàm mà controller xoá cũng gọi,
        // nên giao diện và server không thể nói khác nhau.
        List<Allergy> allergies = allergyService.findForUser(user.getId());
        Map<Long, String> allergyBlockReasons = new HashMap<>();
        for (Allergy allergy : allergies) {
            allergyBlockReasons.put(allergy.getId(), allergyService.whyCannotDelete(allergy, user));
        }
        model.addAttribute("allergies", allergies);
        model.addAttribute("allergyBlockReasons", allergyBlockReasons);

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
                return "redirect:/user/profile#booking-history";
            }

            // Check 2: Trạng thái + mốc 24 tiếng.
            // Dùng chung whyCannotCancel() với giao diện, để nút bị khóa và server
            // luôn nói cùng một lý do (trước đây lỗi parse giờ bị nuốt, lịch cũ vẫn hủy được).
            String blockedReason = bookingService.whyCannotCancel(booking);
            if (blockedReason != null) {
                ra.addFlashAttribute("errorMessage", blockedReason);
                return "redirect:/user/profile#booking-history";
            }

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
            // Đường tự hủy này KHÔNG đi qua cancelWithRefund (nó hoàn tiền tại chỗ), nên phải
            // tự bắn thông báo — nếu không thì hủy từ hồ sơ là lần hủy duy nhất không vào chuông.
            notificationService.pushBookingEvent(booking, "bi-x-circle text-danger", "Lịch hẹn đã bị hủy");

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }

        return "redirect:/user/profile#booking-history";
    }
}