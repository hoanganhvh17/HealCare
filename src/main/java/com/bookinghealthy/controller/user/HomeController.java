package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Service;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.DepartmentService;
import com.bookinghealthy.service.DoctorService;
import com.bookinghealthy.service.ServiceService; // Giả sử bạn đã có ServiceService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    @Autowired private DepartmentService departmentService;
    @Autowired private DoctorService doctorService;
    @Autowired private ServiceService serviceService; // Cần Service này (đã tạo ở Module 7)
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JavaMailSender mailSender;

    @GetMapping("/")
    public String home(Model model) {
        return showHomePage(model);
    }

    @GetMapping("/home")
    public String homeAlias(Model model) {
        return showHomePage(model);
    }

    private String showHomePage(Model model) {

        // (Tối 12/11 - update) Lấy TẤT CẢ Khoa (để dùng cho Dropdown tìm kiếm)
        List<Department> allDepartments = departmentService.findAll();

        // 1. Lấy 6 Khoa đầu tiên
        List<Department> departments = departmentService.findAll().stream()
                .limit(6)
                .collect(Collectors.toList());

        // 2. Lấy 6 Bác sĩ đầu tiên
        List<Doctor> doctors = doctorService.findAll().stream()
                .limit(6)
                .collect(Collectors.toList());

        // 3. Lấy 4 Dịch vụ đầu tiên
        List<Service> services = serviceService.findAll().stream()
                .limit(4)
                .collect(Collectors.toList());
        // Gửi dữ liệu ra View
        model.addAttribute("allDepartments", allDepartments); // <-- DÙNG CÁI NÀY CHO DROPDOWN
        model.addAttribute("featuredDepartments", departments);
        model.addAttribute("featuredDoctors", doctors);
        model.addAttribute("featuredServices", services);

        // === THÊM DÒNG NÀY ===
        model.addAttribute("activePage", "home"); // Báo hiệu đây là trang HOME
        return "user/index"; // Trỏ đến file templates/user/index.html
    }
    // === TRANG GIỚI THIỆU ===
    @GetMapping("/about")
    public String about(Model model) {
        long doctorCount = doctorService.findAll().size();
        long treatedCount = bookingRepository.count(); // Đếm tổng số lịch hẹn

        model.addAttribute("doctorCount", doctorCount);
        model.addAttribute("treatedCount", treatedCount);

        // === THÊM DÒNG NÀY ===
        model.addAttribute("activePage", "about"); // Báo hiệu đây là trang ABOUT
        return "user/about";
    }

    // === TRANG LIÊN HỆ (GET) ===
    @GetMapping("/contact")
    public String contact(Model model) {


        // === THÊM DÒNG NÀY ===
        model.addAttribute("activePage", "contact"); // Báo hiệu đây là trang CONTACT
        return "user/contact";
    }

    // === XỬ LÝ GỬI LIÊN HỆ (POST) ===
    @PostMapping("/contact")
    public String processContact(@RequestParam("name") String name,
                                 @RequestParam("email") String email,
                                 @RequestParam("message") String message,
                                 RedirectAttributes ra) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo("vuhoanganh1706@gmail.com");
            mailMessage.setSubject("Liên hệ mới từ: " + name);
            mailMessage.setText("Người gửi: " + name + "\nEmail: " + email + "\n\nNội dung:\n" + message);

            mailSender.send(mailMessage);

            ra.addFlashAttribute("successMessage", "Cảm ơn bạn! Tin nhắn đã được gửi thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi gửi tin nhắn: " + e.getMessage());
        }
        return "redirect:/contact";
    }
}