package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Department;
import com.bookinghealthy.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/departments") // Đường dẫn gốc
public class AdminDepartmentController {

    @Autowired
    private DepartmentService departmentService;

    // 1. HIỂN THỊ DANH SÁCH (READ)
    @GetMapping
    public String listDepartments(Model model) {
        model.addAttribute("departments", departmentService.findAll());
        return "admin/department-list"; // -> Tới file HTML
    }

    // 2. HIỂN THỊ FORM THÊM MỚI (CREATE)
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("department", new Department());
        model.addAttribute("pageTitle", "Thêm mới Chuyên khoa");
        return "admin/department-form"; // -> Tới file HTML
    }

    // 3. HIỂN THỊ FORM SỬA (UPDATE)
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
        Optional<Department> department = departmentService.findById(id);
        if (department.isPresent()) {
            model.addAttribute("department", department.get());
            model.addAttribute("pageTitle", "Chỉnh sửa Chuyên khoa");
            return "admin/department-form";
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy Khoa ID: " + id);
            return "redirect:/admin/departments";
        }
    }

    /**
     * === THAY THẾ TOÀN BỘ HÀM NÀY ===
     * XỬ LÝ LƯU (SAVE) - Dùng cho cả Thêm và Sửa
     * Đã thêm try-catch để bắt lỗi TRÙNG TÊN
     */
    @PostMapping("/save")
    public String saveDepartment(@ModelAttribute("department") Department department,
                                 RedirectAttributes ra,
                                 Model model) { // <-- THÊM Model
        try {
            // 1. Thử lưu vào database
            departmentService.save(department);

            // 2. Nếu thành công, báo success và chuyển về trang danh sách
            ra.addFlashAttribute("successMessage", "Đã lưu Chuyên khoa thành công.");
            return "redirect:/admin/departments";

        } catch (DataIntegrityViolationException e) {
            // 3. NẾU LỖI (do trùng tên)
            // Gửi thông báo lỗi
            model.addAttribute("errorMessage", "Lỗi: Tên chuyên khoa '" + department.getName() + "' đã tồn tại. Vui lòng chọn tên khác.");

            // 4. Chuẩn bị Tiêu đề trang (Page Title)
            if (department.getId() == null) {
                model.addAttribute("pageTitle", "Thêm mới Chuyên khoa");
            } else {
                model.addAttribute("pageTitle", "Chỉnh sửa Chuyên khoa");
            }

            // 5. Trả người dùng VỀ LẠI FORM (không redirect)
            // để họ thấy lỗi và sửa lại, không mất dữ liệu đã nhập
            return "admin/department-form";
        }
    }

    // 5. XỬ LÝ XÓA (DELETE)
    @GetMapping("/delete/{id}")
    public String deleteDepartment(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            departmentService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa Khoa thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không thể xóa Khoa (có thể do Bác sĩ đang liên kết).");
        }
        return "redirect:/admin/departments";
    }
}