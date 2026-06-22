package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Candidate;
import com.bookinghealthy.model.CandidateStatus;
import com.bookinghealthy.repository.CandidateRepository;
import com.bookinghealthy.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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

    // 1. HIỂN THỊ DANH SÁCH ỨNG VIÊN
    @GetMapping
    public String listCandidates(Model model) {
        // Lấy danh sách ứng viên, mới nhất lên đầu
        List<Candidate> candidates = candidateRepository.findAllByOrderBySubmittedAtDesc();
        model.addAttribute("candidates", candidates);
        return "admin/candidate-list";
    }

    // 2. DUYỆT HỒ SƠ (MỜI PHỎNG VẤN)
    @GetMapping("/approve/{id}")
    public String approveCandidate(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Candidate candidate = candidateRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ứng viên"));

            candidate.setStatus(CandidateStatus.APPROVED);
            candidateRepository.save(candidate);

            // Gửi mail mời phỏng vấn
            String subject = "MediTrust - Thư mời phỏng vấn: " + candidate.getJobPosting().getTitle();
            String content = "Chào " + candidate.getFullName() + ",\n\n" +
                    "Chúc mừng bạn! Hồ sơ ứng tuyển của bạn cho vị trí " + candidate.getJobPosting().getTitle() + " đã được thông qua.\n" +
                    "Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn trực tiếp.\n\n" +
                    "Bộ phận Nhân sự sẽ liên hệ qua điện thoại để sắp xếp lịch cụ thể.\n\n" +
                    "Trân trọng,\nMediTrust HR Team";

            emailService.sendCandidateResult(candidate, subject, content);

            ra.addFlashAttribute("successMessage", "Đã duyệt hồ sơ và gửi mail mời phỏng vấn.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/candidates";
    }

    // 3. TỪ CHỐI HỒ SƠ
    @GetMapping("/reject/{id}")
    public String rejectCandidate(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Candidate candidate = candidateRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ứng viên"));

            candidate.setStatus(CandidateStatus.REJECTED);
            candidateRepository.save(candidate);

            // Gửi mail từ chối khéo
            String subject = "MediTrust - Thông báo kết quả ứng tuyển: " + candidate.getJobPosting().getTitle();
            String content = "Chào " + candidate.getFullName() + ",\n\n" +
                    "Cảm ơn bạn đã quan tâm đến vị trí " + candidate.getJobPosting().getTitle() + " tại MediTrust.\n" +
                    "Sau khi xem xét kỹ lưỡng, chúng tôi rất tiếc phải thông báo rằng hồ sơ của bạn chưa phù hợp với yêu cầu hiện tại của chúng tôi.\n\n" +
                    "Chúng tôi sẽ lưu hồ sơ của bạn và liên hệ lại nếu có vị trí phù hợp trong tương lai.\n\n" +
                    "Chúc bạn sớm tìm được công việc như ý.\n\n" +
                    "Trân trọng,\nMediTrust HR Team";

            emailService.sendCandidateResult(candidate, subject, content);

            ra.addFlashAttribute("successMessage", "Đã từ chối hồ sơ và gửi mail thông báo.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/candidates";
    }

    // 4. XÓA HỒ SƠ
    @GetMapping("/delete/{id}")
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