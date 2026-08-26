package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Service;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.DepartmentService;
import com.bookinghealthy.service.DoctorService;
import com.bookinghealthy.service.ServiceService; // Giả sử bạn đã có ServiceService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    @Autowired private DepartmentService departmentService;
    @Autowired private DoctorService doctorService;
    @Autowired private ServiceService serviceService; // Cần Service này (đã tạo ở Module 7)
    @Autowired private BookingRepository bookingRepository;
    @Autowired private com.bookinghealthy.repository.DoctorRepository doctorRepository;
    @Autowired private com.bookinghealthy.service.EmailService emailService;

    /** Hộp thư nhận liên hệ từ khách. Trước đây là địa chỉ Gmail cá nhân hardcode trong mã. */
    @org.springframework.beans.factory.annotation.Value("${app.contact-email:${spring.mail.username}}")
    private String contactEmail;

    @GetMapping("/")
    public String home(Model model) {
        return showHomePage(model);
    }

    @GetMapping("/home")
    public String homeAlias(Model model) {
        return showHomePage(model);
    }

    private String showHomePage(Model model) {

        // Lấy TẤT CẢ Khoa (dùng cho Dropdown tìm kiếm) — MỘT lần thôi. Bản cũ gọi
        // departmentService.findAll() hai lần trong cùng một lần tải trang chủ rồi vứt đi
        // một nửa kết quả.
        List<Department> allDepartments = departmentService.findAll();

        // 1. Lấy 6 Khoa đầu tiên, cắt từ danh sách vừa lấy ở trên
        List<Department> departments = allDepartments.stream()
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
        // countAll() thay cho findAll().size(): bản cũ nạp trọn 132 dòng bác sĩ kèm quan hệ
        // chỉ để lấy đúng một con số.
        long doctorCount = doctorRepository.count();
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
            // Đi qua EmailService (@Async) thay vì gọi thẳng mailSender: bản cũ gửi ĐỒNG BỘ
            // nên khách phải ngồi chờ trọn vòng bắt tay SMTP mới thấy trang phản hồi, và
            // một sự cố ở máy chủ mail thì hiện thẳng ra mặt khách. Người nhận nay lấy từ
            // cấu hình chứ không còn là địa chỉ Gmail cá nhân hardcode trong mã nguồn.
            String safeName = HtmlUtils.htmlEscape(name == null ? "" : name);
            String safeEmail = HtmlUtils.htmlEscape(email == null ? "" : email);
            String safeMessage = HtmlUtils.htmlEscape(message == null ? "" : message);

            emailService.sendStaffNotification(
                    contactEmail,
                    "Liên hệ mới từ: " + safeName,
                    "Tin nhắn liên hệ từ website",
                    "<p><strong>Người gửi:</strong> " + safeName + "</p>"
                            + "<p><strong>Email:</strong> " + safeEmail + "</p>"
                            + "<p><strong>Nội dung:</strong></p><p>"
                            + safeMessage.replace("\n", "<br>") + "</p>");

            ra.addFlashAttribute("successMessage", "Cảm ơn bạn! Tin nhắn đã được gửi thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi gửi tin nhắn: " + e.getMessage());
        }
        return "redirect:/contact";
    }
}