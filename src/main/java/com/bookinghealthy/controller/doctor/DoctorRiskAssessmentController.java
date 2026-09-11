package com.bookinghealthy.controller.doctor;

import com.bookinghealthy.dto.risk.RiskPredictRequest;
import com.bookinghealthy.dto.risk.RiskPredictResponse;
import com.bookinghealthy.dto.risk.RiskTargetResult;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.RiskAssessment;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.RiskAssessmentRepository;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.RiskPredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Màn hình dự đoán nguy cơ bệnh dành cho bác sĩ.
 *
 * <p>Nằm dưới {@code /doctor/**} nên luật {@code hasRole("DOCTOR")} sẵn có trong
 * {@code SecurityConfig} đã che — <b>không cần thêm matcher nào</b>.
 *
 * <p>Bốn model NHANES chạy trong sidecar Python ({@code ml-service/}); lớp này chỉ thu thập
 * đầu vào, quy đổi đơn vị, gọi service rồi lưu kết quả.
 *
 * <p><b>Chỉ ghi bằng POST.</b> {@code CsrfFilter} bỏ qua GET và cookie phiên vẫn được gửi kèm
 * khi điều hướng cấp cao, nên một {@code @GetMapping} có ghi là chỉ cần gửi link đi cũng
 * khai thác được.
 */
@Controller
@RequestMapping("/doctor/risk-assessment")
public class DoctorRiskAssessmentController {

    private static final String ACTIVE_PAGE = "risk-assessment";

    @Autowired
    private RiskPredictionService riskPredictionService;

    @Autowired
    private RiskAssessmentRepository riskAssessmentRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping
    public String showForm(Model model, Authentication authentication) {
        Doctor doctor = resolveDoctor(authentication).orElse(null);
        model.addAttribute("activePage", ACTIVE_PAGE);
        model.addAttribute("blockReason", riskPredictionService.whyCannotPredict());
        model.addAttribute("history", doctor == null
                ? java.util.List.of()
                : riskAssessmentRepository.findTop20ByDoctorIdOrderByCreatedAtDesc(doctor.getId()));
        return "doctor/risk-assessment";
    }

    @PostMapping("/predict")
    public String predict(
            @RequestParam(value = "patientLabel", required = false) String patientLabel,
            @RequestParam(value = "age", required = false) Integer age,
            @RequestParam(value = "sex", required = false) Integer sex,
            @RequestParam(value = "heightCm", required = false) Double heightCm,
            @RequestParam(value = "weightKg", required = false) Double weightKg,
            @RequestParam(value = "waistCm", required = false) Double waistCm,
            @RequestParam(value = "systolicBp", required = false) Double systolicBp,
            @RequestParam(value = "diastolicBp", required = false) Double diastolicBp,
            @RequestParam(value = "pulse", required = false) Double pulse,
            @RequestParam(value = "hba1c", required = false) Double hba1c,
            @RequestParam(value = "totalCholesterol", required = false) Double totalCholesterol,
            @RequestParam(value = "hdl", required = false) Double hdl,
            @RequestParam(value = "cholesterolUnit", required = false) String cholesterolUnit,
            @RequestParam(value = "hsCrp", required = false) Double hsCrp,
            Authentication authentication,
            RedirectAttributes ra) {

        String blockReason = riskPredictionService.whyCannotPredict();
        if (blockReason != null) {
            ra.addFlashAttribute("errorMessage", blockReason);
            return "redirect:/doctor/risk-assessment";
        }

        Optional<Doctor> doctorOpt = resolveDoctor(authentication);
        if (doctorOpt.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Không xác định được bác sĩ đang đăng nhập.");
            return "redirect:/doctor/risk-assessment";
        }

        // Hai phép kiểm này lặp lại ở sidecar. Kiểm ở đây để bác sĩ nhận câu tiếng Việt tử tế
        // thay vì một lỗi HTTP, chứ không phải để thay thế phép kiểm ở đó.
        if (age == null || age < RiskPredictionService.MIN_AGE || age > 120) {
            ra.addFlashAttribute("errorMessage",
                    "Tuổi phải từ " + RiskPredictionService.MIN_AGE
                            + " đến 120. Mô hình được huấn luyện trên nhóm từ "
                            + RiskPredictionService.MIN_AGE + " tuổi trở lên.");
            return "redirect:/doctor/risk-assessment";
        }
        if (sex == null || (sex != 1 && sex != 2)) {
            ra.addFlashAttribute("errorMessage", "Vui lòng chọn giới tính của bệnh nhân.");
            return "redirect:/doctor/risk-assessment";
        }

        // Quy đổi về mg/dL TRƯỚC khi gửi: hợp đồng của sidecar chỉ nhận mg/dL.
        Double cholesterolMgDl = riskPredictionService.toMgPerDl(totalCholesterol, cholesterolUnit);
        Double hdlMgDl = riskPredictionService.toMgPerDl(hdl, cholesterolUnit);

        RiskPredictRequest request = new RiskPredictRequest();
        request.setAge(age);
        request.setSex(sex);
        request.setHeightCm(heightCm);
        request.setWeightKg(weightKg);
        request.setWaistCm(waistCm);
        request.setSystolicBp(systolicBp);
        request.setDiastolicBp(diastolicBp);
        request.setPulse(pulse);
        request.setHba1c(hba1c);
        request.setTotalCholesterol(cholesterolMgDl);
        request.setHdl(hdlMgDl);
        request.setHsCrp(hsCrp);

        RiskPredictResponse response = riskPredictionService.predict(request);
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            ra.addFlashAttribute("errorMessage",
                    "Không kết nối được dịch vụ dự đoán. Vui lòng thử lại sau ít phút.");
            return "redirect:/doctor/risk-assessment";
        }

        // Gọi mạng đã xong mới ghi — service không có transaction bao ngoài, nên lưu ở đây
        // dựa vào transaction riêng của repository.
        RiskAssessment saved = riskAssessmentRepository.save(
                toEntity(doctorOpt.get(), patientLabel, request, response));
        return "redirect:/doctor/risk-assessment/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String showResult(@PathVariable("id") Long id, Model model,
                             Authentication authentication, RedirectAttributes ra) {
        Optional<Doctor> doctorOpt = resolveDoctor(authentication);
        if (doctorOpt.isEmpty()) {
            return "redirect:/doctor/risk-assessment";
        }
        // Cùng một kết quả cho "không phải của bạn" và "không tồn tại" nên không dò được id
        // của bác sĩ khác — đúng khuôn whyCannotView của ExternalMedicalRecordService.
        Optional<RiskAssessment> found =
                riskAssessmentRepository.findByIdAndDoctorId(id, doctorOpt.get().getId());
        if (found.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy kết quả dự đoán này.");
            return "redirect:/doctor/risk-assessment";
        }
        model.addAttribute("activePage", ACTIVE_PAGE);
        model.addAttribute("item", found.get());
        return "doctor/risk-assessment-detail";
    }

    private Optional<Doctor> resolveDoctor(Authentication authentication) {
        // Qua CurrentUserService chứ không ép kiểu (UserDetails): principal có thể là OAuth2User.
        Optional<User> userOpt = currentUserService.find(authentication);
        return userOpt.flatMap(user -> currentUserService.findDoctor(user));
    }

    private RiskAssessment toEntity(Doctor doctor, String patientLabel,
                                    RiskPredictRequest request, RiskPredictResponse response) {
        RiskAssessment entity = new RiskAssessment();
        entity.setDoctor(doctor);
        entity.setPatientLabel(patientLabel == null || patientLabel.isBlank()
                ? null : patientLabel.trim());

        entity.setAge(request.getAge());
        entity.setSexCode(request.getSex());
        entity.setHeightCm(request.getHeightCm());
        entity.setWeightKg(request.getWeightKg());
        entity.setWaistCm(request.getWaistCm());
        entity.setSystolicBp(request.getSystolicBp());
        entity.setDiastolicBp(request.getDiastolicBp());
        entity.setPulse(request.getPulse());
        entity.setHba1c(request.getHba1c());
        entity.setTotalCholesterol(request.getTotalCholesterol());
        entity.setHdl(request.getHdl());
        entity.setHsCrp(request.getHsCrp());

        // BMI lấy từ chính giá trị sidecar đã dùng, không tự tính lại ở đây — hai phép tính
        // là hai chỗ để lệch nhau.
        if (response.getFeaturesUsed() != null) {
            entity.setBmi(response.getFeaturesUsed().get("BMI"));
        }

        apply(entity, response, "DIABETES");
        apply(entity, response, "HYPERTENSION");
        apply(entity, response, "HEART_DISEASE");
        apply(entity, response, "STROKE");

        entity.setModelVersion(response.getModelVersion());
        entity.setMissingCount(response.getMissingFeatures() == null
                ? null : response.getMissingFeatures().size());
        return entity;
    }

    private void apply(RiskAssessment entity, RiskPredictResponse response, String target) {
        RiskTargetResult result = response.find(target);
        if (result == null) {
            return; // Model của target đó không nạp được: để null chứ không ghi 0.
        }
        switch (target) {
            case "DIABETES" -> {
                entity.setDiabetesProbability(result.getProbability());
                entity.setDiabetesPositive(result.getPositive());
            }
            case "HYPERTENSION" -> {
                entity.setHypertensionProbability(result.getProbability());
                entity.setHypertensionPositive(result.getPositive());
            }
            case "HEART_DISEASE" -> {
                entity.setHeartDiseaseProbability(result.getProbability());
                entity.setHeartDiseasePositive(result.getPositive());
            }
            case "STROKE" -> {
                entity.setStrokeProbability(result.getProbability());
                entity.setStrokePositive(result.getPositive());
            }
            default -> { }
        }
    }
}
