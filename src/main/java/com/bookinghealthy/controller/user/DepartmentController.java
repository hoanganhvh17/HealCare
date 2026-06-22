package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.DepartmentService;
import com.bookinghealthy.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

@Controller
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DoctorService doctorService;
    @Autowired
    private BookingRepository bookingRepository;

    // 1. HIỂN THỊ DANH SÁCH KHOA (Trang /departments)
    @GetMapping("/departments")
    public String showDepartmentsPage(Model model) {
        model.addAttribute("departments", departmentService.findAll());
        return "user/departments"; // -> Cần file departments.html
    }

//    // 2. HIỂN THỊ CHI TIẾT KHOA (Trang /department-details/{id})
//    @GetMapping("/department-details/{id}")
//    public String showDepartmentDetails(@PathVariable("id") Long id, Model model) {
//        Optional<Department> dept = departmentService.findById(id);
//
//        if (dept.isPresent()) {
//            Department department = dept.get();
//            model.addAttribute("department", department);
//
//            // Lấy thêm danh sách bác sĩ thuộc khoa này để hiển thị bên dưới
//            List<Doctor> doctors = doctorService.findByDepartmentId(id);
//            model.addAttribute("doctors", doctors);
//
//            return "user/department-details"; // -> Cần file department-details.html
//        } else {
//            return "redirect:/departments";
//        }
//    }   --- v1(12/11 update chỉ số trang chi tiết)
// === CẬP NHẬT HÀM NÀY ===
@GetMapping("/department-details/{id}")
public String showDepartmentDetails(@PathVariable("id") Long id, Model model) {
    Optional<Department> dept = departmentService.findById(id);

    if (dept.isPresent()) {
        Department department = dept.get();
        model.addAttribute("department", department);

        // 1. Lấy danh sách bác sĩ
        List<Doctor> doctors = doctorService.findByDepartmentId(id);
        model.addAttribute("doctors", doctors);

        // 2. Tính toán số liệu thống kê (REAL TIME)
        long doctorCount = doctors.size(); // Số bác sĩ
        long treatedCount = bookingRepository.countByDoctor_Department_IdAndStatus(id, BookingStatus.COMPLETED); // Số ca đã khám

        // (Nếu số liệu quá ít, ta có thể cộng thêm 1 số ảo để demo cho đẹp, ví dụ +100)
        // treatedCount += 100;

        model.addAttribute("doctorCount", doctorCount);
        model.addAttribute("treatedCount", treatedCount);

        return "user/department-details";
    } else {
        return "redirect:/departments";
    }
}

}