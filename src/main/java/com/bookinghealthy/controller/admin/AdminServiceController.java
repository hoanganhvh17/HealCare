//package com.bookinghealthy.controller.admin;
//
//import com.bookinghealthy.model.Service;
//import com.bookinghealthy.service.ServiceService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.dao.DataIntegrityViolationException;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.servlet.mvc.support.RedirectAttributes;
//
//import java.util.Optional;
//
//@Controller
//@RequestMapping("/admin/manage-service") // Khớp với link trong sidebar
//public class AdminServiceController {
//
//    @Autowired
//    private ServiceService serviceService;
//
//    // 1. HIỂN THỊ DANH SÁCH (READ)
//    @GetMapping
//    public String listServices(Model model) {
//        model.addAttribute("services", serviceService.findAll());
//        return "admin/service-list"; // -> Tới file HTML
//    }
//
//    // 2. HIỂN THỊ FORM THÊM MỚI (CREATE)
//    @GetMapping("/add")
//    public String showAddForm(Model model) {
//        model.addAttribute("service", new Service());
//        model.addAttribute("pageTitle", "Thêm mới Dịch vụ");
//        return "admin/service-form"; // -> Tới file HTML
//    }
//
//    // 3. HIỂN THỊ FORM SỬA (UPDATE)
//    @GetMapping("/edit/{id}")
//    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
//        Optional<Service> service = serviceService.findById(id);
//        if (service.isPresent()) {
//            model.addAttribute("service", service.get());
//            model.addAttribute("pageTitle", "Chỉnh sửa Dịch vụ");
//            return "admin/service-form";
//        } else {
//            ra.addFlashAttribute("errorMessage", "Không tìm thấy Dịch vụ ID: " + id);
//            return "redirect:/admin/manage-service";
//        }
//    }
//
//    // 4. XỬ LÝ LƯU (SAVE)
//    @PostMapping("/save")
//    public String saveService(@ModelAttribute("service") Service service, RedirectAttributes ra, Model model) {
//        try {
//            serviceService.save(service);
//            ra.addFlashAttribute("successMessage", "Đã lưu Dịch vụ thành công.");
//            return "redirect:/admin/manage-service";
//        } catch (DataIntegrityViolationException e) {
//            // Bắt lỗi trùng tên
//            model.addAttribute("errorMessage", "Lỗi: Tên Dịch vụ '" + service.getName() + "' đã tồn tại.");
//            model.addAttribute("pageTitle", (service.getId() == null) ? "Thêm mới Dịch vụ" : "Chỉnh sửa Dịch vụ");
//            return "admin/service-form";
//        }
//    }
//
//    // 5. XỬ LÝ XÓA (DELETE)
//    @GetMapping("/delete/{id}")
//    public String deleteService(@PathVariable("id") Long id, RedirectAttributes ra) {
//        try {
//            serviceService.deleteById(id);
//            ra.addFlashAttribute("successMessage", "Đã xóa Dịch vụ thành công.");
//        } catch (Exception e) {
//            ra.addFlashAttribute("errorMessage", "Không thể xóa Dịch vụ.");
//        }
//        return "redirect:/admin/manage-service";
//    }
//}   (sáng 14/11)
package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Service;
import com.bookinghealthy.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // <-- THÊM IMPORT
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Controller
@RequestMapping("/admin/manage-service")
public class AdminServiceController {

    @Autowired
    private ServiceService serviceService;

    // 1. HIỂN THỊ DANH SÁCH (READ)
    @GetMapping
    public String listServices(Model model) {
        model.addAttribute("services", serviceService.findAll());
        return "admin/service-list";
    }

    // 2. HIỂN THỊ FORM THÊM MỚI (CREATE)
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("service", new Service());
        model.addAttribute("pageTitle", "Thêm mới Dịch vụ");
        return "admin/service-form";
    }

    // 3. HIỂN THỊ FORM SỬA (UPDATE)
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
        Optional<Service> service = serviceService.findById(id);
        if (service.isPresent()) {
            model.addAttribute("service", service.get());
            model.addAttribute("pageTitle", "Chỉnh sửa Dịch vụ");
            return "admin/service-form";
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy Dịch vụ ID: " + id);
            return "redirect:/admin/manage-service";
        }
    }

    // 4. XỬ LÝ LƯU (SAVE) - ĐÃ NÂNG CẤP UPLOAD ẢNH
    @PostMapping("/save")
    public String saveService(@ModelAttribute("service") Service service,
                              @RequestParam("imageFile") MultipartFile imageFile, // <-- Hứng file ảnh
                              RedirectAttributes ra,
                              Model model) {
        try {
            // Logic xử lý ảnh
            if (!imageFile.isEmpty()) {
                // 1. Tạo tên file duy nhất (dùng thời gian hiện tại)
                String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();

                // 2. Đường dẫn lưu file (Thư mục uploads trong static)
                // Lưu ý: Trong môi trường dev, nên lưu vào src/main/resources... để IDE thấy ngay
                String uploadDir = "src/main/resources/static/assets/img/health/";
                // (Hoặc dùng thư mục uploads riêng nếu muốn: "src/main/resources/static/uploads/")

                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();

                // 3. Lưu file vào ổ đĩa
                Path path = Paths.get(uploadDir + fileName);
                Files.write(path, imageFile.getBytes());

                // 4. Gán tên file vào đối tượng Service
                service.setImage(fileName);
            } else {
                // Nếu không chọn ảnh mới, kiểm tra xem có ảnh cũ không
                if (service.getId() != null) {
                    // Lấy lại ảnh cũ từ DB nếu người dùng không upload ảnh mới khi sửa
                    Service existingService = serviceService.findById(service.getId()).orElse(null);
                    if (existingService != null) {
                        service.setImage(existingService.getImage());
                    }
                }
            }

            serviceService.save(service);
            ra.addFlashAttribute("successMessage", "Đã lưu Dịch vụ thành công.");
            return "redirect:/admin/manage-service";

        } catch (DataIntegrityViolationException e) {
            model.addAttribute("errorMessage", "Lỗi: Tên Dịch vụ '" + service.getName() + "' đã tồn tại.");
            model.addAttribute("pageTitle", (service.getId() == null) ? "Thêm mới Dịch vụ" : "Chỉnh sửa Dịch vụ");
            return "admin/service-form";
        } catch (IOException e) {
            model.addAttribute("errorMessage", "Lỗi khi tải ảnh lên: " + e.getMessage());
            model.addAttribute("pageTitle", (service.getId() == null) ? "Thêm mới Dịch vụ" : "Chỉnh sửa Dịch vụ");
            return "admin/service-form";
        }
    }

    // 5. XỬ LÝ XÓA (DELETE)
    @GetMapping("/delete/{id}")
    public String deleteService(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            serviceService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa Dịch vụ thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không thể xóa Dịch vụ.");
        }
        return "redirect:/admin/manage-service";
    }
}