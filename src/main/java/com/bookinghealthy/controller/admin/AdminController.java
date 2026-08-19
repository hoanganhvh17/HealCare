package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.repository.ReviewRepository;
import com.bookinghealthy.repository.StaffProfileRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet; // <-- THÊM IMPORT
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set; // <-- THÊM IMPORT

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired private UserService userService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Gán khoa cho trưởng khoa: role mở /head/**, còn StaffProfile.headOfDepartment
    // quyết định họ duyệt cho khoa nào (resolveHeadDepartment đọc trường này).
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;

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
    public String manageUsers(Model model, Authentication authentication) {
        List<User> users = userService.findAll();
        String me = authentication != null ? authentication.getName() : null;

        // Cùng khuôn với cancelBlockReasons / actionBlockReasons: template ẩn nút Xoá
        // và in đúng câu mà controller sẽ dùng để từ chối, nên giao diện không bao giờ
        // mời admin bấm một nút chắc chắn lỗi.
        Map<Long, String> deleteBlockReasons = new HashMap<>();
        for (User u : users) {
            String reason = userService.whyCannotDelete(u, me);
            if (reason != null) deleteBlockReasons.put(u.getId(), reason);
        }

        model.addAttribute("listUsers", users);
        model.addAttribute("deleteBlockReasons", deleteBlockReasons);
        return "admin/manage-user";
    }

    // 2. SỬA HÀM NÀY: Gửi danh sách Role
    @GetMapping("/manage-user/add")
    public String showAddUserForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("allRoles", roleRepository.findAll()); // Gửi Roles ra form
        model.addAttribute("allDepartments", departmentRepository.findAll()); // Chọn khoa cho trưởng khoa
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
            model.addAttribute("allDepartments", departmentRepository.findAll());
            // Khoa mà người này đang làm trưởng khoa (nếu có) để chọn sẵn trong dropdown
            model.addAttribute("headDepartmentId", staffProfileRepository.findByUserId(id)
                    .map(sp -> sp.getHeadOfDepartment() != null ? sp.getHeadOfDepartment().getId() : null)
                    .orElse(null));
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
                           @RequestParam(name = "headDepartmentId", required = false) Long headDepartmentId,
                           Model model,
                           RedirectAttributes ra) {

        // Bắt lỗi Validation (Email, NotBlank...)
        if (bindingResult.hasErrors()) {
            reloadFormRefs(model, headDepartmentId);
            model.addAttribute("pageTitle", (user.getId() == null) ? "Thêm mới Người dùng" : "Chỉnh sửa Người dùng");
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
                    reloadFormRefs(model, headDepartmentId);
                    model.addAttribute("pageTitle", "Thêm mới Người dùng");
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
            // Đồng bộ gán/gỡ trưởng khoa (StaffProfile.headOfDepartment) theo vai trò + khoa đã chọn.
            syncHeadOfDepartment(user, roles, headDepartmentId);
            ra.addFlashAttribute("successMessage", "Đã lưu Người dùng thành công.");
            return "redirect:/admin/manage-user";

        } catch (DataIntegrityViolationException e) {
            bindingResult.rejectValue("username", "Duplicate", "Username hoặc Email đã tồn tại.");
            reloadFormRefs(model, headDepartmentId);
            model.addAttribute("pageTitle", (user.getId() == null) ? "Thêm mới Người dùng" : "Chỉnh sửa Người dùng");
            return "admin/user-form";
        }
    }

    /** Nạp lại danh sách vai trò + khoa cho form khi phải render lại vì lỗi. */
    private void reloadFormRefs(Model model, Long headDepartmentId) {
        model.addAttribute("allRoles", roleRepository.findAll());
        model.addAttribute("allDepartments", departmentRepository.findAll());
        model.addAttribute("headDepartmentId", headDepartmentId);
    }

    /**
     * Gán hoặc gỡ vai trò trưởng khoa ở tầng dữ liệu (StaffProfile.headOfDepartment).
     * ROLE_HEAD_DOCTOR chỉ mở cửa /head/**; muốn duyệt đơn cho khoa nào thì phải có
     * bản ghi khoa phụ trách này — nếu thiếu, /head/dashboard báo "chưa được gán khoa".
     */
    private void syncHeadOfDepartment(User user, Set<Role> roles, Long headDepartmentId) {
        boolean isHead = roles.stream().anyMatch(r -> "ROLE_HEAD_DOCTOR".equals(r.getName()));
        Optional<StaffProfile> existing = staffProfileRepository.findByUserId(user.getId());

        if (isHead && headDepartmentId != null) {
            Department dept = departmentRepository.findById(headDepartmentId).orElse(null);
            if (dept == null) {
                return; // Khoa không tồn tại -> bỏ qua, không dựng hồ sơ rác
            }
            StaffProfile profile = existing.orElseGet(() -> {
                StaffProfile fresh = new StaffProfile();
                fresh.setUser(user);
                fresh.setHireDate(LocalDate.now());
                return fresh;
            });
            profile.setHeadOfDepartment(dept);
            staffProfileRepository.save(profile);
        } else {
            // Không còn là trưởng khoa (bỏ vai trò hoặc không chọn khoa) -> gỡ khoa phụ trách.
            existing.ifPresent(profile -> {
                if (profile.getHeadOfDepartment() != null) {
                    profile.setHeadOfDepartment(null);
                    staffProfileRepository.save(profile);
                }
            });
        }
    }

    // 5. XỬ LÝ XÓA (ĐÃ THÊM NGHIỆP VỤ)
    @GetMapping("/manage-user/delete/{id}")
    public String deleteUser(@PathVariable("id") Long id, RedirectAttributes ra, Authentication authentication) {

        User userToDelete = userService.findById(id).orElse(null);

        // Một hàm duy nhất quyết định, dùng chung với template — xem UserService.whyCannotDelete.
        String reason = userService.whyCannotDelete(userToDelete, authentication.getName());
        if (reason != null) {
            ra.addFlashAttribute("errorMessage", reason);
            return "redirect:/admin/manage-user";
        }

        try {
            userService.deleteAccount(id);
            ra.addFlashAttribute("successMessage", "Đã xoá người dùng thành công.");
        } catch (Exception e) {
            e.printStackTrace();
            // Còn ràng buộc nào chưa lường tới thì in ra thay vì đoán hộ nguyên nhân.
            ra.addFlashAttribute("errorMessage", "Không xoá được người dùng: " + e.getMessage());
        }
        return "redirect:/admin/manage-user";
    }

}