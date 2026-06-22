package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.repository.ReviewRepository;
import com.bookinghealthy.service.ReviewService;
import com.bookinghealthy.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.security.core.Authentication; // <-- THÊM IMPORT NÀY
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet; // <-- THÊM IMPORT
import java.util.List;
import java.util.Optional;
import java.util.Set; // <-- THÊM IMPORT

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired private UserService userService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private ReviewService reviewService; // <-- Inject Review Service
    @Autowired private ReviewRepository reviewRepository;

    // === THÊM REPO NÀY ĐỂ ĐẾM ===
    @Autowired
    private BookingRepository bookingRepository;

    // === SỬA HÀM NÀY ĐỂ GỬI SỐ LIỆU ===
    // Trong AdminController.java, hàm adminHome

    @GetMapping("/dashboard")
    public String adminHome(Model model) {
        long patientCount = userService.findByRoleName("ROLE_USER").size();
        long doctorCount = userService.findByRoleName("ROLE_DOCTOR").size();
        long bookingCount = bookingRepository.count();

        // === LOGIC MỚI: THỐNG KÊ TRẠNG THÁI ===
        // Đếm số lượng theo từng trạng thái để vẽ biểu đồ tròn
        long countPending = bookingRepository.countByStatus(BookingStatus.PENDING); // Cần thêm hàm này vào Repo nếu chưa có
        long countConfirmed = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        long countCompleted = bookingRepository.countByStatus(BookingStatus.COMPLETED);
        long countCancelled = bookingRepository.countByStatus(BookingStatus.CANCELED);

        // 3. SỐ LIỆU ĐÁNH GIÁ (MỚI)
        // a. Đánh giá mới nhất (Toàn hệ thống)
        List<Review> recentGlobalReviews = reviewService.getRecentGlobalReviews();

        // b. Phân bố sao (5 sao, 4 sao...)
        List<Integer> globalRatingDist = reviewService.getGlobalRatingDistribution();

        // c. Điểm trung bình toàn hệ thống
        Double globalAvgRating = reviewService.getGlobalAverageRating();

        // 4. DANH SÁCH LỊCH HẸN (CŨ)
        List<Booking> allRecentBookings = bookingRepository.findAllByOrderByCreatedAtDesc();

        // === CÁC CHỈ SỐ PHÂN TÍCH TĂNG GIẢM VÀ 2 THẺ MỚI ===
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfThisMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfThisMonth = now;
        LocalDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);
        LocalDateTime endOfLastMonth = now.minusMonths(1);

        // A. Lịch hẹn trend
        long currentBookings = bookingRepository.countByCreatedAtBetween(startOfThisMonth, endOfThisMonth);
        long lastBookings = bookingRepository.countByCreatedAtBetween(startOfLastMonth, endOfLastMonth);
        double bookingDiffPercent = 0.0;
        String bookingTrend = "flat";
        if (lastBookings > 0) {
            bookingDiffPercent = ((double) (currentBookings - lastBookings) / lastBookings) * 100;
            if (bookingDiffPercent > 0) bookingTrend = "up";
            else if (bookingDiffPercent < 0) bookingTrend = "down";
        } else if (currentBookings > 0) {
            bookingDiffPercent = 100.0;
            bookingTrend = "up";
        }
        String bookingDiffPercentStr = String.format("%.1f", Math.abs(bookingDiffPercent));

        // B. Điểm đánh giá trend
        Double currentRating = reviewRepository.getAverageRatingBetween(startOfThisMonth, endOfThisMonth);
        Double lastRating = reviewRepository.getAverageRatingBetween(startOfLastMonth, endOfLastMonth);
        if (currentRating == null) currentRating = 0.0;
        if (lastRating == null) lastRating = 0.0;
        double ratingDiff = currentRating - lastRating;
        String ratingTrend = "flat";
        String ratingDiffStr = "0.0";
        if (ratingDiff > 0) {
            ratingTrend = "up";
            ratingDiffStr = String.format("+%.1f", ratingDiff);
        } else if (ratingDiff < 0) {
            ratingTrend = "down";
            ratingDiffStr = String.format("%.1f", ratingDiff);
        }

        // C. Tổng tiền đặt cọc và trend
        BigDecimal totalDeposit = bookingRepository.sumTotalDeposit();
        BigDecimal currentDeposit = bookingRepository.sumDepositByCreatedAtBetween(startOfThisMonth, endOfThisMonth);
        BigDecimal lastDeposit = bookingRepository.sumDepositByCreatedAtBetween(startOfLastMonth, endOfLastMonth);
        if (currentDeposit == null) currentDeposit = BigDecimal.ZERO;
        if (lastDeposit == null) lastDeposit = BigDecimal.ZERO;
        
        double depositDiffPercent = 0.0;
        String depositTrend = "flat";
        if (lastDeposit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = currentDeposit.subtract(lastDeposit);
            depositDiffPercent = diff.multiply(new BigDecimal(100)).divide(lastDeposit, 1, java.math.RoundingMode.HALF_UP).doubleValue();
            if (depositDiffPercent > 0) depositTrend = "up";
            else if (depositDiffPercent < 0) depositTrend = "down";
        } else if (currentDeposit.compareTo(BigDecimal.ZERO) > 0) {
            depositDiffPercent = 100.0;
            depositTrend = "up";
        }
        String depositDiffPercentStr = String.format("%.1f", Math.abs(depositDiffPercent));

        // D. Tiền hoàn trả và trend
        BigDecimal totalRefund = bookingRepository.sumTotalRefund();
        BigDecimal currentRefund = bookingRepository.sumRefundByCreatedAtBetween(startOfThisMonth, endOfThisMonth);
        BigDecimal lastRefund = bookingRepository.sumRefundByCreatedAtBetween(startOfLastMonth, endOfLastMonth);
        if (currentRefund == null) currentRefund = BigDecimal.ZERO;
        if (lastRefund == null) lastRefund = BigDecimal.ZERO;
        
        double refundDiffPercent = 0.0;
        String refundTrend = "flat";
        if (lastRefund.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = currentRefund.subtract(lastRefund);
            refundDiffPercent = diff.multiply(new BigDecimal(100)).divide(lastRefund, 1, java.math.RoundingMode.HALF_UP).doubleValue();
            if (refundDiffPercent > 0) refundTrend = "up";
            else if (refundDiffPercent < 0) refundTrend = "down";
        } else if (currentRefund.compareTo(BigDecimal.ZERO) > 0) {
            refundDiffPercent = 100.0;
            refundTrend = "up";
        }
        String refundDiffPercentStr = String.format("%.1f", Math.abs(refundDiffPercent));

        // Gửi số liệu ra view
        model.addAttribute("patientCount", patientCount);
        model.addAttribute("doctorCount", doctorCount);
        model.addAttribute("bookingCount", bookingCount);

        model.addAttribute("statPending", countPending);
        model.addAttribute("statConfirmed", countConfirmed);
        model.addAttribute("statCompleted", countCompleted);
        model.addAttribute("statCancelled", countCancelled);

        // Stats cho đánh giá
        model.addAttribute("recentReviews", recentGlobalReviews);
        model.addAttribute("ratingDist", globalRatingDist);
        model.addAttribute("avgRating", globalAvgRating != null ? globalAvgRating : 0.0);

        // Trends
        model.addAttribute("bookingDiffPercent", bookingDiffPercentStr);
        model.addAttribute("bookingTrend", bookingTrend);

        model.addAttribute("ratingDiff", ratingDiffStr);
        model.addAttribute("ratingTrend", ratingTrend);

        model.addAttribute("totalDeposit", totalDeposit);
        model.addAttribute("depositDiffPercent", depositDiffPercentStr);
        model.addAttribute("depositTrend", depositTrend);

        model.addAttribute("totalRefund", totalRefund);
        model.addAttribute("refundDiffPercent", refundDiffPercentStr);
        model.addAttribute("refundTrend", refundTrend);

        model.addAttribute("listBookings", allRecentBookings);

        return "admin/dashboard";
    }

    // (Các hàm manageUsers, showAddUserForm, showEditUserForm, saveUser, deleteUser giữ nguyên)
    // ...

    // 1. SỬA HÀM NÀY: Dùng findAll() để lấy TẤT CẢ user
    @GetMapping("/manage-user")
    public String manageUsers(Model model) {
        model.addAttribute("listUsers", userService.findAll());
        return "admin/manage-user";
    }

    // 2. SỬA HÀM NÀY: Gửi danh sách Role
    @GetMapping("/manage-user/add")
    public String showAddUserForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("allRoles", roleRepository.findAll()); // Gửi Roles ra form
        model.addAttribute("pageTitle", "Thêm mới Người dùng");
        return "admin/user-form";
    }

    // 3. SỬA HÀM NÀY: Gửi danh sách Role
    @GetMapping("/manage-user/edit/{id}")
    public String showEditUserForm(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
        Optional<User> user = userService.findById(id);
        if (user.isPresent()) {
            model.addAttribute("user", user.get());
            model.addAttribute("allRoles", roleRepository.findAll()); // Gửi Roles ra form
            model.addAttribute("pageTitle", "Chỉnh sửa Người dùng");
            return "admin/user-form";
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy User ID: " + id);
            return "redirect:/admin/manage-user";
        }
    }

    // 4. XỬ LÝ LƯU (NÂNG CẤP LỚN)
    @PostMapping("/manage-user/save")
    public String saveUser(@Valid @ModelAttribute("user") User user,
                           BindingResult bindingResult,
                           @RequestParam(name = "password", required = false) String rawPassword,
                           @RequestParam(name = "roleIds", required = false) Set<Long> roleIds, // Lấy Role IDs
                           Model model,
                           RedirectAttributes ra) {

        // Bắt lỗi Validation (Email, NotBlank...)
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", (user.getId() == null) ? "Thêm mới Người dùng" : "Chỉnh sửa Người dùng");
            model.addAttribute("allRoles", roleRepository.findAll());
            return "admin/user-form";
        }

        // Lấy Set<Role> từ Set<Long>
        Set<Role> roles = new HashSet<>();
        if (roleIds != null) {
            roles.addAll(roleRepository.findAllById(roleIds));
        }

        try {
            if (user.getId() == null) {
                // A. TRƯỜNG HỢP THÊM MỚI
                if (rawPassword == null || rawPassword.isEmpty()) {
                    bindingResult.rejectValue("password", "NotBlank", "Mật khẩu là bắt buộc khi tạo mới");
                    model.addAttribute("pageTitle", "Thêm mới Người dùng");
                    model.addAttribute("allRoles", roleRepository.findAll());
                    return "admin/user-form";
                }
                user.setRoles(roles); // Gán vai trò
                user.setPassword(passwordEncoder.encode(rawPassword));

            } else {
                // B. TRƯỜNG HỢP CẬP NHẬT
                User existingUser = userService.findById(user.getId()).orElseThrow();
                existingUser.setFullName(user.getFullName());
                existingUser.setEmail(user.getEmail());
                existingUser.setUsername(user.getUsername());
                existingUser.setPhone(user.getPhone());
                existingUser.setRoles(roles); // Cập nhật vai trò

                // Chỉ cập nhật mật khẩu NẾU admin nhập mật khẩu mới
                if (rawPassword != null && !rawPassword.isEmpty()) {
                    existingUser.setPassword(passwordEncoder.encode(rawPassword));
                }
                user = existingUser;
            }

            userService.save(user);
            ra.addFlashAttribute("successMessage", "Đã lưu Người dùng thành công.");
            return "redirect:/admin/manage-user";

        } catch (DataIntegrityViolationException e) {
            bindingResult.rejectValue("username", "Duplicate", "Username hoặc Email đã tồn tại.");
            model.addAttribute("pageTitle", (user.getId() == null) ? "Thêm mới Người dùng" : "Chỉnh sửa Người dùng");
            model.addAttribute("allRoles", roleRepository.findAll());
            return "admin/user-form";
        }
    }

    // 5. XỬ LÝ XÓA (ĐÃ THÊM NGHIỆP VỤ)
    @GetMapping("/manage-user/delete/{id}")
    public String deleteUser(@PathVariable("id") Long id, RedirectAttributes ra, Authentication authentication) {

        // Nghiệp vụ 1: Không cho Admin tự xóa mình
        String currentAdminUsername = authentication.getName();
        User userToDelete = userService.findById(id).orElse(null);

        if (userToDelete != null && userToDelete.getUsername().equals(currentAdminUsername)) {
            ra.addFlashAttribute("errorMessage", "Không thể xóa tài khoản Admin đang đăng nhập!");
            return "redirect:/admin/manage-user";
        }

        try {
            userService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa Người dùng thành công.");
        } catch (Exception e) {
            // Nghiệp vụ 2: Bắt lỗi Khóa ngoại (đã có lịch hẹn,...)
            ra.addFlashAttribute("errorMessage", "Không thể xóa User (đã có lịch hẹn hoặc liên kết Bác sĩ).");
        }
        return "redirect:/admin/manage-user";
    }
}