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

import java.util.Optional;

@Controller
@RequestMapping("/admin/manage-service")
public class AdminServiceController {

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private com.bookinghealthy.service.FileStorageService fileStorageService;

    @GetMapping
    public String listServices(Model model) {
        model.addAttribute("services", serviceService.findAll());
        return "admin/service-list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("service", new Service());
        model.addAttribute("pageTitle", "Thêm mới Dịch vụ");
        return "admin/service-form";
    }

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

    @PostMapping("/save")
    public String saveService(@ModelAttribute("service") Service service,
                              @RequestParam("imageFile") MultipartFile imageFile, // <-- Hứng file ảnh
                              RedirectAttributes ra,
                              Model model) {
        try {
            if (!imageFile.isEmpty()) {
                service.setImage(fileStorageService.storeImage(imageFile, "health"));
            } else {
                if (service.getId() != null) {
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
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", "Lỗi khi tải ảnh lên: " + e.getMessage());
            model.addAttribute("pageTitle", (service.getId() == null) ? "Thêm mới Dịch vụ" : "Chỉnh sửa Dịch vụ");
            return "admin/service-form";
        }
    }

    @PostMapping("/delete/{id}")
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