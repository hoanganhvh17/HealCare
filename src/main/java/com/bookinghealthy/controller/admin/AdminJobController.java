package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.JobPosting;
import com.bookinghealthy.service.JobPostingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/recruitment")
public class AdminJobController {

    @Autowired
    private JobPostingService jobService;

    // 1. DANH SÁCH TIN TUYỂN DỤNG
    @GetMapping
    public String listJobs(Model model) {
        model.addAttribute("jobs", jobService.findAll());
        return "admin/job-list";
    }

    // 2. FORM THÊM MỚI
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("job", new JobPosting());
        model.addAttribute("pageTitle", "Đăng tin tuyển dụng mới");
        return "admin/job-form";
    }

    // 3. FORM SỬA
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
        Optional<JobPosting> job = jobService.findById(id);
        if (job.isPresent()) {
            model.addAttribute("job", job.get());
            model.addAttribute("pageTitle", "Chỉnh sửa tin tuyển dụng");
            return "admin/job-form";
        }
        ra.addFlashAttribute("errorMessage", "Không tìm thấy tin tuyển dụng ID: " + id);
        return "redirect:/admin/recruitment";
    }

    // 4. LƯU
    @PostMapping("/save")
    public String saveJob(@ModelAttribute("job") JobPosting job, RedirectAttributes ra) {
        jobService.save(job);
        ra.addFlashAttribute("successMessage", "Đã lưu tin tuyển dụng thành công.");
        return "redirect:/admin/recruitment";
    }

    // 5. XÓA
    @PostMapping("/delete/{id}")
    public String deleteJob(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            jobService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa tin tuyển dụng.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi khi xóa: " + e.getMessage());
        }
        return "redirect:/admin/recruitment";
    }

    // 6. BẬT/TẮT TRẠNG THÁI (Quick Toggle)
    @PostMapping("/toggle/{id}")
    public String toggleStatus(@PathVariable("id") Long id, RedirectAttributes ra) {
        Optional<JobPosting> jobOpt = jobService.findById(id);
        if (jobOpt.isPresent()) {
            JobPosting job = jobOpt.get();
            job.setActive(!job.isActive()); // Đảo ngược trạng thái
            jobService.save(job);
            ra.addFlashAttribute("successMessage", "Đã cập nhật trạng thái tin tuyển dụng.");
        }
        return "redirect:/admin/recruitment";
    }
}