package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Candidate;
import com.bookinghealthy.model.JobPosting;
import com.bookinghealthy.repository.CandidateRepository;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.FileStorageService;
import com.bookinghealthy.service.JobPostingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class CareerController {

    @Autowired
    private JobPostingService jobService;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired private EmailService emailService;

    @Autowired private FileStorageService fileStorageService;

    @GetMapping("/careers")
    public String listCareers(Model model) {
        model.addAttribute("jobs", jobService.findActiveJobs());
        model.addAttribute("activePage", "news");
        return "user/careers";
    }

    @GetMapping("/career-details/{id}")
    public String showCareerDetails(@PathVariable("id") Long id, Model model) {
        Optional<JobPosting> job = jobService.findById(id);
        if (job.isPresent() && job.get().isActive()) {
            model.addAttribute("job", job.get());
            return "user/career-details";
        }
        return "redirect:/careers";
    }

    // === HÀM ĐÃ ĐƯỢC SỬA LỖI 400 ===
    @PostMapping("/career-apply")
    public String applyJob(@RequestParam("jobId") Long jobId,
                           @RequestParam("fullName") String fullName,
                           @RequestParam("email") String email,
                           @RequestParam("phone") String phone,
                           @RequestParam(value = "introduction", required = false) String introduction, // <-- Cho phép rỗng
                           @RequestParam("cvFile") MultipartFile cvFile,
                           RedirectAttributes ra) {
        try {
            JobPosting job = jobService.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Tin tuyển dụng không tồn tại"));

            String cvFileName;
            if (cvFile != null && !cvFile.isEmpty()) {
                cvFileName = fileStorageService.storeCv(cvFile);
            } else {
                throw new RuntimeException("Vui lòng tải lên CV (PDF/Word).");
            }

            Candidate candidate = new Candidate();
            candidate.setFullName(fullName);
            candidate.setEmail(email);
            candidate.setPhone(phone);
            candidate.setIntroduction(introduction);
            candidate.setCvFile(cvFileName);
            candidate.setJobPosting(job);

            candidateRepository.save(candidate);

            // === GỬI MAIL TỰ ĐỘNG ===
            emailService.sendCandidateConfirmation(candidate); // Gửi cho Ứng viên
            emailService.sendNewCandidateNotification(candidate); // Gửi cho Admin

            ra.addFlashAttribute("successMessage", "Nộp hồ sơ thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi nộp hồ sơ: " + e.getMessage());
            // Nếu lỗi file quá lớn, Spring có thể văng Exception khác, cần config thêm ở application.properties
        }
        return "redirect:/career-details/" + jobId;
    }
}