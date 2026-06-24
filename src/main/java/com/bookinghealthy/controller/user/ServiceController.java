package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Service;
import com.bookinghealthy.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class ServiceController {

    @Autowired
    private ServiceService serviceService;

    @GetMapping("/services")
    public String showServicePage(Model model) {
        List<Service> allServices = serviceService.findAll();

        Map<String, List<Service>> servicesByCategory = allServices.stream()
                .collect(Collectors.groupingBy(Service::getCategory));

        model.addAttribute("servicesByCategory", servicesByCategory);

        return "user/services";
    }

    @GetMapping("/service-details/{id}")
    public String showServiceDetails(@PathVariable("id") Long id, Model model) {
        Optional<Service> serviceOpt = serviceService.findById(id);

        if (serviceOpt.isEmpty()) {
            return "redirect:/services";
        }

        model.addAttribute("service", serviceOpt.get());
        return "user/service-details";
    }
}