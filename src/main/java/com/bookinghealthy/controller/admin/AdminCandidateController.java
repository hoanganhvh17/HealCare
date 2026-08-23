package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Candidate;
import com.bookinghealthy.model.CandidateStatus;
import com.bookinghealthy.repository.CandidateRepository;
import com.bookinghealthy.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/candidates")
public class AdminCandidateController {

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private com.bookinghealthy.service.FileStorageService fileStorageService;

    @GetMapping("/{id}/cv")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadCv(
            @PathVariable("id") Long id) {

        Candidate candidate = candidateRepository.findById(id).orElse(null);
        if (candidate == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        java.nio.file.Path file = fileStorageService.resolveCv(candidate.getCvFile());
        if (file == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        String downloadName = "CV-" + id + "-" + file.getFileName();
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + downloadName + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(new org.springframework.core.io.FileSystemResource(file));
    }

    // 1. HIỂN THỊ DANH SÁCH ỨNG VIÊN
    @GetMapping
    public String listCandidates(Model model) {
        // Lấy danh sách ứng viên, mới nhất lên đầu
        List<Candidate> candidates = candidateRepository.findAllByOrderBySubmittedAtDesc();
        model.addAttribute("candidates", candidates);
        return "admin/candidate-list";
    }

    // 2. DUYỆT HỒ SƠ (MỜI PHỎNG VẤN)
    @PostMapping("/approve/{id}")
    public String approveCandidate(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Candidate candidate = candidateRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ứng viên"));

            candidate.setStatus(CandidateStatus.APPROVED);
            candidateRepository.save(candidate);

            // Gửi mail mời phỏng vấn
            String subject = "NNL Hospital - Thư mời phỏng vấn: " + candidate.getJobPosting().getTitle();
            String content = "Chào " + candidate.getFullName() + ",\n\n" +
                    "Chúc mừng bạn! Hồ sơ ứng tuyển của bạn cho vị trí " + candidate.getJobPosting().getTitle() + " đã được thông qua.\n" +
                    "Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn trực tiếp.\n\n" +
                    "Bộ phận Nhân sự sẽ liên hệ qua điện thoại để sắp xếp lịch cụ thể.\n\n" +
                    "Trân trọng,\nNNL Hospital HR Team";

            emailService.sendCandidateResult(candidate, subject, content);

            ra.addFlashAttribute("successMessage", "Đã duyệt hồ sơ và gửi mail mời phỏng vấn.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/candidates";
    }

    // 3. TỪ CHỐI HỒ SƠ
    @PostMapping("/reject/{id}")
    public String rejectCandidate(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Candidate candidate = candidateRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ứng viên"));

            candidate.setStatus(CandidateStatus.REJECTED);
            candidateRepository.save(candidate);

            // Gửi mail từ chối khéo
            String subject = "NNL Hospital - Thông báo kết quả ứng tuyển: " + candidate.getJobPosting().getTitle();
            String content = "Chào " + candidate.getFullName() + ",\n\n" +
                    "Cảm ơn bạn đã quan tâm đến vị trí " + candidate.getJobPosting().getTitle() + " tại NNL Hospital.\n" +
                    "Sau khi xem xét kỹ lưỡng, chúng tôi rất tiếc phải thông báo rằng hồ sơ của bạn chưa phù hợp với yêu cầu hiện tại của chúng tôi.\n\n" +
                    "Chúng tôi sẽ lưu hồ sơ của bạn và liên hệ lại nếu có vị trí phù hợp trong tương lai.\n\n" +
                    "Chúc bạn sớm tìm được công việc như ý.\n\n" +
                    "Trân trọng,\nNNL Hospital HR Team";

            emailService.sendCandidateResult(candidate, subject, content);

            ra.addFlashAttribute("successMessage", "Đã từ chối hồ sơ và gửi mail thông báo.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/candidates";
    }

    // 4. XÓA HỒ SƠ
    @PostMapping("/delete/{id}")
    public String deleteCandidate(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            candidateRepository.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa hồ sơ ứng viên.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi khi xóa: " + e.getMessage());
        }
        return "redirect:/admin/candidates";
    }
}