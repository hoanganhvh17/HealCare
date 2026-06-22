package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Service;
import com.bookinghealthy.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

@Controller
public class ServiceController {

    @Autowired
    private ServiceService serviceService;

    @GetMapping("/services")
    public String showServicePage(Model model) {

        // Lấy dữ liệu cho từng tab
        List<Service> primaryServices = serviceService.findByCategory("Primary");
        List<Service> specialtyServices = serviceService.findByCategory("Specialty");
        List<Service> diagnosticsServices = serviceService.findByCategory("Diagnostics");
        List<Service> emergencyServices = serviceService.findByCategory("Emergency");

        // Gửi 4 danh sách ra view
        model.addAttribute("primaryServices", primaryServices);
        model.addAttribute("specialtyServices", specialtyServices);
        model.addAttribute("diagnosticsServices", diagnosticsServices);
        model.addAttribute("emergencyServices", emergencyServices);

        return "user/services";
    }

    // (Chúng ta sẽ thêm trang /service-details sau)
    // === THÊM HÀM MỚI NÀY ===
    @GetMapping("/service-details/{id}")
    public String showServiceDetails(@PathVariable("id") Long id, Model model) {

        Optional<Service> serviceOpt = serviceService.findById(id);

        if (serviceOpt.isEmpty()) {
            return "redirect:/services"; // Nếu không thấy, về trang danh sách
        }

        model.addAttribute("service", serviceOpt.get());
        return "user/service-details"; // Trỏ đến file HTML chi tiết
    }
}