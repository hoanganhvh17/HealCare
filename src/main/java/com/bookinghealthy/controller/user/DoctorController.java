//package com.bookinghealthy.controller.user;
//
//import com.bookinghealthy.model.Department;
//import com.bookinghealthy.model.Doctor;
//import com.bookinghealthy.service.DepartmentService;
//import com.bookinghealthy.service.DoctorService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable; // Thêm import này
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//
//import java.util.List;
//import java.util.Optional; // Thêm import này
//
//@Controller
//@RequestMapping("/")
//public class DoctorController {
//
//    @Autowired
//    private DoctorService doctorService;
//    // === ĐÂY LÀ DÒNG BẠN ĐANG THIẾU ===
//    @Autowired
//    private DepartmentService departmentService;
//    // ==================================
//
////    // Endpoint hiển thị danh sách bác sĩ (bạn đã có)
////    @GetMapping("/doctors")
////    public String showDoctorList(Model model) {
////        List<Doctor> doctors = doctorService.findAll();
////        model.addAttribute("doctors", doctors);
////        return "user/doctors";
////    }    --v1-12/11/ok
//
//    // === SỬA HÀM NÀY ===
//    @GetMapping("/doctors")
//    public String listDoctors(
//            @RequestParam(value = "keyword", required = false) String keyword,
//            @RequestParam(value = "departmentId", required = false) Long departmentId,
//            Model model) {
//
//        // 1. Gọi hàm tìm kiếm (nếu không nhập gì, nó sẽ trả về tất cả)
//        List<Doctor> doctors = doctorService.searchDoctors(keyword, departmentId);
//
//        // 2. Lấy danh sách khoa (để hiển thị lại vào dropdown lọc ở trang doctors)
//        List<Department> departments = departmentService.findAll();
//
//        model.addAttribute("doctors", doctors);
//        model.addAttribute("departments", departments);
//
//        // Giữ lại giá trị đã tìm kiếm để hiển thị trên form
//        model.addAttribute("currentKeyword", keyword);
//        model.addAttribute("currentDepartmentId", departmentId);
//
//        return "user/doctors";
//    }
//
//    // === PHƯƠNG THỨC MỚI CẦN THÊM ===
//    /**
//     * Xử lý request xem chi tiết bác sĩ
//     * URL sẽ có dạng: /doctors/1, /doctors/2, v.v.
//     */
//    @GetMapping("/doctors/{id}")
//    public String showDoctorDetails(@PathVariable("id") Long id, Model model) {
//
//        Optional<Doctor> doctorOpt = doctorService.findById(id);
//
//        // Nếu không tìm thấy bác sĩ với ID này,
//        // chuyển hướng người dùng về trang danh sách bác sĩ
//        if (doctorOpt.isEmpty()) {
//            return "redirect:/doctors";
//        }
//
//        // Nếu tìm thấy, gửi đối tượng doctor qua Model
//        model.addAttribute("doctor", doctorOpt.get());
//
//        // Trả về file HTML chi tiết
//        return "user/doctor-details";
//    }
//
//
//}
package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Review; // <--- 1. Import Review
import com.bookinghealthy.service.DepartmentService;
import com.bookinghealthy.service.DoctorService;
import com.bookinghealthy.service.ReviewService; // <--- 2. Import ReviewService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/")
public class DoctorController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private DepartmentService departmentService;

    // === 3. INJECT SERVICE ĐÁNH GIÁ ===
    @Autowired
    private ReviewService reviewService;
    // ==================================

    // Endpoint hiển thị danh sách bác sĩ (Tìm kiếm & Lọc)
    @GetMapping("/doctors")
    public String listDoctors(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            Model model) {

        // 1. Gọi hàm tìm kiếm
        List<Doctor> doctors = doctorService.searchDoctors(keyword, departmentId);

        // 2. Lấy danh sách khoa
        List<Department> departments = departmentService.findAll();

        model.addAttribute("doctors", doctors);
        model.addAttribute("departments", departments);

        // Giữ lại giá trị đã tìm kiếm
        model.addAttribute("currentKeyword", keyword);
        model.addAttribute("currentDepartmentId", departmentId);

        return "user/doctors";
    }

    /**
     * Xử lý request xem chi tiết bác sĩ
     * URL sẽ có dạng: /doctors/1, /doctors/2...
     */
    @GetMapping("/doctors/{id}")
    public String showDoctorDetails(@PathVariable("id") Long id, Model model) {

        Optional<Doctor> doctorOpt = doctorService.findById(id);

        if (doctorOpt.isEmpty()) {
            return "redirect:/doctors";
        }

        Doctor doctor = doctorOpt.get();
        model.addAttribute("doctor", doctor);

        // === 4. LOGIC MỚI: LẤY DỮ LIỆU ĐÁNH GIÁ ===
        // Lấy danh sách bình luận
        List<Review> reviews = reviewService.getReviewsByDoctor(id);

        // Lấy điểm trung bình (VD: 4.8)
        Double averageRating = reviewService.getAverageRating(id);

        // Lấy tổng số lượt đánh giá
        Long reviewCount = reviewService.countReviews(id);

        // Gửi sang View (doctor-details.html)
        model.addAttribute("reviews", reviews);
        model.addAttribute("averageRating", averageRating);
        model.addAttribute("reviewCount", reviewCount);
        // ==========================================

        return "user/doctor-details";
    }
}