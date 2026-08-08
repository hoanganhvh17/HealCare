package com.bookinghealthy.controller.doctor;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.AllergyService;
import com.bookinghealthy.service.DoctorService;
import com.bookinghealthy.service.MedicalRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.AllergyRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/doctor/medical-record")
public class DoctorMedicalRecordController {

    @Autowired
    private MedicalRecordService medicalRecordService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private com.bookinghealthy.service.MedicalRecordDeliveryService medicalRecordDeliveryService;
    // Thêm Dependency cho phần Dị ứng
    @Autowired
    private AllergyRepository allergyRepository;
    @Autowired
    private AllergyService allergyService;
    @Autowired
    private com.bookinghealthy.repository.MedicalAddendumRepository medicalAddendumRepository;

    @Autowired
    private com.bookinghealthy.repository.VitalSignRepository vitalSignRepository;

    @Autowired
    private com.bookinghealthy.repository.PrescriptionItemRepository prescriptionItemRepository;
    @Autowired
    private com.bookinghealthy.repository.UserRepository userRepository;

    // Helper: Lấy bác sĩ đang đăng nhập
    private Doctor getLoggedInDoctor(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return doctorService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }

    // 1. HIỂN THỊ FORM KHÁM BỆNH
    /*@GetMapping("/create/{bookingId}")
    public String showCreateForm(@PathVariable("bookingId") Long bookingId,
                                 Model model,
                                 Authentication authentication,
                                 RedirectAttributes ra) {
        Doctor currentDoctor = getLoggedInDoctor(authentication);

        // Tìm Booking và validate quyền
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check 1: Lịch này có phải của Bác sĩ này không?
        if (!booking.getDoctor().getId().equals(currentDoctor.getId())) {
            ra.addFlashAttribute("errorMessage", "Bạn không có quyền khám ca này!");
            return "redirect:/doctor/examinations";
        }

        // Check 2: Lịch này có phải trạng thái CONFIRMED không? (Chỉ Confirmed mới được khám)
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            ra.addFlashAttribute("errorMessage", "Lịch hẹn không hợp lệ để khám (Phải ở trạng thái Đã xác nhận).");
            return "redirect:/doctor/examinations";
        }

        model.addAttribute("booking", booking);
        return "doctor/medical-record-form";
    }*/

    // 2. XỬ LÝ LƯU BỆNH ÁN (POST)
    /*@PostMapping("/save")
    public String saveMedicalRecord(
            @RequestParam("bookingId") Long bookingId,
            @RequestParam("symptoms") String symptoms,
            @RequestParam("diagnosis") String diagnosis,
            @RequestParam("prescription") String prescription,
            @RequestParam("doctorNotes") String doctorNotes,
            Authentication authentication,
            RedirectAttributes ra) {

        try {
            // Gọi Service để lưu bệnh án + Update status Booking -> COMPLETED
            medicalRecordService.createMedicalRecord(bookingId, symptoms, diagnosis, prescription, doctorNotes);

            ra.addFlashAttribute("successMessage", "Đã hoàn tất ca khám và lưu hồ sơ bệnh án thành công!");
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Lỗi khi lưu bệnh án: " + e.getMessage());
            return "redirect:/doctor/medical-record/create/" + bookingId; // Quay lại form nếu lỗi
        }

        return "redirect:/doctor/examinations"; // Quay về danh sách khám
    }*/
    // 1. HIỂN THỊ FORM KHÁM BỆNH (NÂNG CẤP EMR)
    @GetMapping("/create/{bookingId}")
    public String showCreateForm(@PathVariable("bookingId") Long bookingId,
                                 Model model,
                                 Authentication authentication,
                                 RedirectAttributes ra) {
        Doctor currentDoctor = getLoggedInDoctor(authentication);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getDoctor().getId().equals(currentDoctor.getId())) {
            ra.addFlashAttribute("errorMessage", "Bạn không có quyền khám ca này!");
            return "redirect:/doctor/examinations";
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            ra.addFlashAttribute("errorMessage", "Lịch hẹn không hợp lệ để khám (Phải ở trạng thái Đã xác nhận).");
            return "redirect:/doctor/examinations";
        }

        // Luật "chỉ khám đúng ngày hẹn" trước đây chỉ nằm trong template, nên một URL gõ tay
        // vẫn mở được form khám cho ca của tuần trước hoặc tuần sau.
        if (!LocalDate.now().equals(booking.getAppointmentDate())) {
            ra.addFlashAttribute("errorMessage", "Chỉ được khám vào đúng ngày hẹn ("
                    + booking.getAppointmentDate() + ").");
            return "redirect:/doctor/examinations";
        }

        model.addAttribute("booking", booking);

        // === LOGIC MỚI: Kéo dữ liệu EMR của Bệnh nhân xuống giao diện ===
        Long patientUserId = booking.getUser().getId();

        // Lấy danh sách dị ứng để cảnh báo đỏ
        List<Allergy> allergies = allergyRepository.findByUserId(patientUserId);
        model.addAttribute("allergies", allergies);

        // Lấy lịch sử các lần khám trước (Loại trừ lần khám hiện tại)
        List<MedicalRecord> historyRecords = medicalRecordService.getPatientMedicalHistory(patientUserId);
        model.addAttribute("historyRecords", historyRecords);

        return "doctor/medical-record-form";
    }

    /**
     * Bác sĩ ghi nhận một dị ứng mới ngay giữa ca khám.
     *
     * Trả JSON chứ không redirect: form khám là MỘT thẻ &lt;form&gt; lớn (triệu chứng, chẩn đoán,
     * bảng thuốc, lời dặn), một lần tải lại trang là xoá sạch mọi thứ bác sĩ đang gõ dở.
     * Trình duyệt gọi bằng fetch rồi tự chèn dòng mới vào thẻ cảnh báo.
     */
    @PostMapping("/allergy/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addAllergyDuringExam(
            @RequestParam("bookingId") Long bookingId,
            @RequestParam("allergen") String allergen,
            @RequestParam(value = "reaction", required = false) String reaction,
            @RequestParam(value = "severity", required = false) String severity,
            Authentication authentication) {

        Doctor currentDoctor = getLoggedInDoctor(authentication);
        Booking booking = bookingRepository.findById(bookingId).orElse(null);

        // Cùng phép kiểm quyền sở hữu ca khám mà showCreateForm dùng — bác sĩ chỉ ghi được vào
        // hồ sơ của bệnh nhân mình đang khám.
        if (booking == null || booking.getDoctor() == null
                || !booking.getDoctor().getId().equals(currentDoctor.getId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Bác sĩ không có quyền ghi vào hồ sơ của ca khám này."));
        }

        try {
            Allergy saved = allergyService.add(booking.getUser().getId(),
                    allergen, reaction, severity, Allergy.SOURCE_DOCTOR);

            Map<String, Object> body = new HashMap<>();
            body.put("allergen", saved.getAllergen());
            body.put("reaction", saved.getReaction());
            body.put("severity", saved.getSeverity());
            return ResponseEntity.ok(body);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Không lưu được. Vui lòng thử lại."));
        }
    }

    // 2. XỬ LÝ LƯU BỆNH ÁN EMR (POST NÂNG CẤP)
    @PostMapping("/save")
    public String saveAdvancedMedicalRecord(
            @RequestParam("bookingId") Long bookingId,
            @RequestParam("symptoms") String symptoms,
            @RequestParam("diagnosis") String diagnosis,
            @RequestParam(value = "diagnosisCode", required = false) String diagnosisCode, // Mã ICD-10
            @RequestParam(value = "prescriptionText", required = false) String prescriptionText, // Ghi chú thêm
            @RequestParam("doctorNotes") String doctorNotes,

            // Tham số Vitals (Cho phép null nếu bác sĩ không đo)
            @RequestParam(value = "vitalHeight", required = false) Double height,
            @RequestParam(value = "vitalWeight", required = false) Double weight,
            @RequestParam(value = "vitalSystolic", required = false) Double systolic,
            @RequestParam(value = "vitalDiastolic", required = false) Double diastolic,
            @RequestParam(value = "vitalTemp", required = false) Double temperature,
            @RequestParam(value = "vitalHeartRate", required = false) Integer heartRate,

            // Tham số Mảng cho Đơn thuốc (Nhận từ Table)
            @RequestParam(value = "medName[]", required = false) String[] medNames,
            @RequestParam(value = "medDosage[]", required = false) String[] medDosages,
            @RequestParam(value = "medQuantity[]", required = false) Integer[] medQuantities,
            @RequestParam(value = "medUnit[]", required = false) String[] medUnits,
            @RequestParam(value = "medInstruction[]", required = false) String[] medInstructions,

            Authentication authentication,
            RedirectAttributes ra) {

        try {
            // 1. Tạo đối tượng Vitals
            VitalSign vitals = null;
            if (height != null || weight != null || systolic != null || temperature != null || heartRate != null) {
                vitals = new VitalSign(null, null, height, weight, systolic, diastolic, temperature, heartRate, null, null);
            }

            // 2. Tạo danh sách thuốc từ các Array gửi lên
            List<PrescriptionItem> prescriptionItems = new ArrayList<>();
            if (medNames != null && medNames.length > 0) {
                for (int i = 0; i < medNames.length; i++) {
                    if (medNames[i] != null && !medNames[i].trim().isEmpty()) {
                        PrescriptionItem item = new PrescriptionItem();
                        item.setMedicineName(medNames[i]);
                        item.setDosage(medDosages != null && i < medDosages.length ? medDosages[i] : "");
                        item.setQuantity(medQuantities != null && i < medQuantities.length ? medQuantities[i] : 0);
                        item.setUnit(medUnits != null && i < medUnits.length ? medUnits[i] : "");
                        item.setInstructions(medInstructions != null && i < medInstructions.length ? medInstructions[i] : "");
                        prescriptionItems.add(item);
                    }
                }
            }

            // 3. Gọi Service hàm Advanced
            // (Phần attachments File tạm truyền null, ta sẽ tích hợp Upload File ở giai đoạn sau nếu cần)
            medicalRecordService.createAdvancedMedicalRecord(
                    bookingId, symptoms, diagnosis, diagnosisCode, prescriptionText, doctorNotes,
                    vitals, prescriptionItems, null
            );

            ra.addFlashAttribute("successMessage",
                    "Đã hoàn tất ca khám chuẩn EMR! Hồ sơ bệnh án & đơn thuốc điện tử đang được gửi tới bệnh nhân.");
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Lỗi khi lưu bệnh án EMR: " + e.getMessage());
            return "redirect:/doctor/medical-record/create/" + bookingId;
        }

        // Gửi hồ sơ + đơn thuốc cho bệnh nhân (email kèm PDF + chuông thông báo).
        //
        // Nằm NGOÀI khối try ở trên là có chủ đích: bệnh án đã commit xong rồi, nếu một lỗi gửi
        // thư rơi vào nhánh catch kia thì bác sĩ bị đá ngược về form khám — và lần bấm Lưu tiếp
        // theo chắc chắn báo "Lịch hẹn này đã có hồ sơ bệnh án!", tức ca khám coi như kẹt.
        // Bản thân deliver() cũng đã tự nuốt lỗi vào log, đây là lớp chặn thứ hai.
        medicalRecordDeliveryService.deliver(bookingId);

        return "redirect:/doctor/examinations";
    }
    // 3. XEM LẠI BỆNH ÁN (Dành cho Bác sĩ)
    @GetMapping("/view/{bookingId}")
    public String viewRecordForDoctor(@PathVariable("bookingId") Long bookingId,
                                      Model model,
                                      Authentication authentication,
                                      RedirectAttributes ra) {
        Doctor currentDoctor = getLoggedInDoctor(authentication);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check quyền: Bác sĩ xem phải là người khám ca này
        //if (!booking.getDoctor().getId().equals(currentDoctor.getId())) {
         //   ra.addFlashAttribute("errorMessage", "Bạn không có quyền xem hồ sơ này.");
         //   return "redirect:/doctor/examinations";
      //  }

        // Tìm bệnh án
        // Lưu ý: Cần Inject MedicalRecordRepository vào Controller này nếu chưa có
        // @Autowired private MedicalRecordRepository medicalRecordRepository;
        var record = medicalRecordService.findByBookingId(bookingId).orElse(null);

        if (record == null) {
            ra.addFlashAttribute("errorMessage", "Hồ sơ bệnh án chưa tồn tại.");
            return "redirect:/doctor/examinations";
        }

        model.addAttribute("record", record);
        // Kéo danh sách phụ lục EMR (nếu có)
        model.addAttribute("addendums", medicalAddendumRepository.findByMedicalRecordIdOrderByCreatedAtAsc(record.getId()));
        model.addAttribute("booking", booking);

        // Kéo dữ liệu EMR (Sinh hiệu + Bảng thuốc) xuống cho Bác sĩ xem
        model.addAttribute("vitals", vitalSignRepository.findByMedicalRecordId(record.getId()).orElse(null));
        model.addAttribute("prescriptionItems", prescriptionItemRepository.findByMedicalRecordId(record.getId()));

        // === THÊM 2 DÒNG NÀY ===
        model.addAttribute("role", "DOCTOR"); // Đánh dấu người xem là Bác sĩ
        model.addAttribute("backLink", "/doctor/examinations"); // Quay lại trang danh sách khám

        // Tái sử dụng giao diện xem chi tiết của User (nhưng cần chỉnh sửa chút xíu ở View để ẩn nút quay lại của User)
        return "user/medical-record-detail";
    }
    // === THÊM MỚI GIAI ĐOẠN 3: API LƯU GHI CHÚ BỔ SUNG (ADDENDUM) ===
    @PostMapping("/addendum/add")
    public String addMedicalAddendum(
            @RequestParam("recordId") Long recordId,
            @RequestParam("bookingId") Long bookingId, // Truyền theo để lát redirect về đúng trang
            @RequestParam("notes") String notes,
            RedirectAttributes ra) {
        try {
            medicalRecordService.addMedicalAddendum(recordId, notes);
            ra.addFlashAttribute("successMessage", "Đã bổ sung ghi chú vào hồ sơ bệnh án thành công!");
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Lỗi khi bổ sung ghi chú: " + e.getMessage());
        }
        // Lưu xong thì load lại trang xem chi tiết của chính ca đó
        return "redirect:/doctor/medical-record/view/" + bookingId;
    }
    // === THÊM MỚI: API XEM DANH SÁCH LỊCH SỬ BỆNH ÁN CỦA 1 BỆNH NHÂN ĐANG KHÁM ===
    @GetMapping("/patient-records/{userId}")
    public String viewPatientHistoryRecords(@PathVariable("userId") Long userId,
                                            Model model,
                                            Authentication authentication) {
        // Kiểm tra quyền (Bác sĩ phải đang đăng nhập)
        getLoggedInDoctor(authentication);

        // Lấy thông tin bệnh nhân
        User patient = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân"));

        // Lấy toàn bộ bệnh án của bệnh nhân này
        List<MedicalRecord> listRecords = medicalRecordService.getPatientMedicalHistory(userId);

        model.addAttribute("patient", patient);
        model.addAttribute("listRecords", listRecords);

        // Trả về file HTML mới
        return "doctor/patient-records";
    }
}