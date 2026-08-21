package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.repository.StaffProfileRepository;
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

import java.time.LocalDate;
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

    @Autowired
    private com.bookinghealthy.service.AdminDashboardService adminDashboardService;

    /**
     * Trang tổng quan của admin.
     *
     * Toàn bộ số học nằm ở {@link com.bookinghealthy.service.AdminDashboardService}. Trước đây 137
     * dòng tính toán nằm thẳng trong hàm này — bên trong một controller vốn đã giữ CRUD người dùng —
     * và đẩy 21 thuộc tính rời rạc ra model, trong đó hai thuộc tính tiền đi thẳng ra view mà không
     * qua một lần kiểm null nào nên in ra đúng chữ "null đ".
     *
     * {@code range} nhận 7 / 30 / 90 / all; giá trị lạ rơi về mặc định chứ không ném lỗi — đây là
     * trang đích sau khi đăng nhập, 500 ở đây là admin bị chặn khỏi trang chủ của chính mình.
     */
    @GetMapping("/dashboard")
    public String adminHome(@RequestParam(name = "range", required = false) String range, Model model) {
        model.addAttribute("dash", adminDashboardService.build(range));
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