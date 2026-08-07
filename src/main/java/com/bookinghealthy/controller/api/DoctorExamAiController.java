package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.ai.ExamAssistRequest;
import com.bookinghealthy.model.Allergy;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.MedicalRecord;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.AllergyRepository;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.MedicalRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bốn trợ lý AI đặt ngay tại form khám ({@code doctor/medical-record-form.html}), giúp bác sĩ
 * bớt thao tác tay: đối chiếu dị ứng/tương tác thuốc, soạn nháp lời dặn, gợi ý mã ICD-10,
 * tóm tắt bệnh sử.
 *
 * TÁCH KHỎI {@code DoctorAiController} có chủ đích: bên đó phục vụ khung chat nổi (welcome /
 * ask / clear) và đã dài sẵn; gộp thêm 4 màn hình không liên quan là đúng cái đã tránh khi
 * tách {@code controller/head} thành hai lớp. Đường dẫn vẫn nằm dưới {@code /api/doctor/chat/}
 * nên luật {@code /api/doctor/chat/**} → {@code hasRole("DOCTOR")} ở KHỐI 0 của
 * {@code SecurityConfig} phủ luôn, không phải thêm luật phân quyền nào.
 *
 * Ba quy tắc chung, cả bốn endpoint đều theo:
 * <ol>
 *   <li><b>KHÔNG {@code @Transactional}</b> — mỗi hàm đều gọi mạng giữa chừng; mở transaction
 *       ở đây là giữ cứng một connection HikariCP (pool 10) suốt thời gian chờ OpenRouter,
 *       đúng lý do {@code AiService.getConversationalResponse} cũng không có nó.</li>
 *   <li><b>Kiểm quyền sở hữu ca khám</b> trước khi đọc bất cứ dữ liệu bệnh nhân nào.</li>
 *   <li><b>Hỏng thì nói ra</b> — trả câu tiếng Việt rõ ràng, tuyệt đối không trả chuỗi rỗng
 *       để giao diện tự bịa, và không bao giờ tự ghi vào {@code MedicalRecord}: tất cả chỉ là
 *       GỢI Ý để bác sĩ sửa rồi mới bấm lưu.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/doctor/chat/exam")
public class DoctorExamAiController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Cắt dữ liệu người dùng gõ trước khi đưa vào prompt, tránh một ô dán 50KB thành một request khổng lồ. */
    private static final int MAX_FIELD_CHARS = 1000;

    /** Chỉ đưa vài lần khám gần nhất vào prompt — bệnh sử 30 lần khám làm prompt phình vô ích. */
    private static final int MAX_HISTORY_RECORDS = 5;

    private static final String AI_FAILED =
            "Trợ lý AI đang không phản hồi. Bác sĩ vui lòng thử lại sau ít phút.";

    @Autowired private AiService aiService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AllergyRepository allergyRepository;
    @Autowired private MedicalRecordService medicalRecordService;

    // ===================== C1. ĐỐI CHIẾU DỊ ỨNG & TƯƠNG TÁC THUỐC =====================

    /**
     * Đối chiếu đơn thuốc đang kê với danh sách dị ứng CÓ THẬT của bệnh nhân trong hồ sơ,
     * rồi mới xét tương tác thuốc.
     *
     * Form đã hiện sẵn thẻ đỏ liệt kê dị ứng, nhưng đó là danh sách tĩnh — bác sĩ vẫn phải tự
     * so từng tên thuốc với nó. Việc so đó là việc ở đây.
     */
    @PostMapping("/check-prescription")
    public ResponseEntity<Map<String, String>> checkPrescription(@RequestBody ExamAssistRequest request) {
        Optional<Booking> bookingOpt = requireOwnedBooking(request.getBookingId());
        if (bookingOpt.isEmpty()) {
            return forbidden();
        }

        String medicines = formatMedicines(request.getMedicines());
        if (medicines.isBlank()) {
            return ResponseEntity.ok(Map.of("answer", "Chưa có thuốc nào trong đơn để kiểm tra."));
        }

        // Dị ứng đọc THẲNG TỪ DB chứ không nhận từ client: đây là dữ liệu an toàn người bệnh,
        // một request tự chế mà bỏ trống danh sách này sẽ biến cảnh báo thành "không có gì".
        List<Allergy> allergies = allergyRepository.findByUserId(bookingOpt.get().getUser().getId());
        StringBuilder allergyText = new StringBuilder();
        if (allergies.isEmpty()) {
            allergyText.append("(Hồ sơ không ghi nhận dị ứng nào)");
        } else {
            for (Allergy a : allergies) {
                allergyText.append("- ").append(a.getAllergen())
                        .append(" | phản ứng: ").append(a.getReaction())
                        .append(" | mức độ: ").append(a.getSeverity()).append("\n");
            }
        }

        String systemPrompt =
                "Bạn là dược sĩ lâm sàng hỗ trợ bác sĩ tại phòng khám MediTrust. Trả lời bằng tiếng Việt, ngắn gọn.\n\n"
                + "DỊ ỨNG ĐÃ GHI NHẬN CỦA BỆNH NHÂN:\n" + allergyText + "\n"
                + "ĐƠN THUỐC BÁC SĨ ĐANG KÊ:\n" + medicines + "\n\n"
                + "--- QUY TẮC ---\n"
                + "1. Ưu tiên số một: đối chiếu từng thuốc với danh sách dị ứng ở trên. Nếu có thuốc trùng hoặc cùng nhóm với tác nhân gây dị ứng, cảnh báo NGAY ở dòng đầu tiên, ghi rõ tên thuốc và tác nhân.\n"
                + "2. Sau đó mới xét tương tác giữa các thuốc trong đơn, và liều dùng bất thường nếu thấy rõ.\n"
                + "3. Mỗi cảnh báo một gạch đầu dòng, tối đa 5 gạch. Không có vấn đề gì thì trả lời đúng một câu: 'Không phát hiện xung đột với dị ứng đã ghi nhận và không thấy tương tác đáng lưu ý.'\n"
                + "4. TUYỆT ĐỐI không bịa dị ứng không có trong danh sách trên.\n"
                + "5. Không kê thay đơn, không đề nghị thuốc khác — chỉ cảnh báo. Bác sĩ là người quyết định.";

        return respond(systemPrompt,
                "Kiểm tra đơn thuốc này giúp tôi.",
                "exam-rx-" + request.getBookingId());
    }

    // ===================== C2. SOẠN NHÁP LỜI DẶN DÒ =====================

    /**
     * Sinh nháp cho ô "Lời dặn của Bác sĩ".
     *
     * Prompt BẮT BUỘC model viết đúng cụm "tái khám sau N ngày/tuần/tháng": đó chính là mẫu mà
     * {@code FollowUpReminderTask.REVISIT_PATTERN} bóc ra khỏi {@code doctorNotes} để nhắc bệnh
     * nhân. Regex ấy cố ý hẹp và KHÔNG hiểu ngày tuyệt đối ("hẹn ngày 20/8"), nên lời dặn viết
     * tự do sẽ khiến tính năng nhắc tái khám im lặng không chạy — viết đúng cú pháp thì thứ vốn
     * đã có sẵn mới thực sự sống.
     */
    @PostMapping("/draft-notes")
    public ResponseEntity<Map<String, String>> draftNotes(@RequestBody ExamAssistRequest request) {
        if (requireOwnedBooking(request.getBookingId()).isEmpty()) {
            return forbidden();
        }

        String diagnosis = trim(request.getDiagnosis());
        if (diagnosis.isBlank()) {
            return ResponseEntity.ok(Map.of("answer", "Bác sĩ vui lòng nhập Kết luận bệnh trước khi soạn lời dặn."));
        }

        String systemPrompt =
                "Bạn là bác sĩ đang viết phần 'Lời dặn của bác sĩ' trong bệnh án điện tử. Viết bằng tiếng Việt.\n\n"
                + "--- QUY TẮC ---\n"
                + "1. Chỉ trả về ĐÚNG nội dung lời dặn, không mở đầu, không giải thích, không markdown.\n"
                + "2. Độ dài 2-4 câu: chế độ nghỉ ngơi, ăn uống, cách dùng thuốc cần lưu ý, dấu hiệu phải quay lại khám ngay.\n"
                + "3. BẮT BUỘC kết thúc bằng một câu hẹn tái khám viết CHÍNH XÁC theo mẫu: 'Tái khám sau N ngày' (hoặc 'Tái khám sau N tuần', 'Tái khám sau N tháng'), với N là số. "
                + "Hệ thống tự động bóc đúng cụm chữ này để nhắc bệnh nhân, nên TUYỆT ĐỐI không viết ngày tháng cụ thể như 'tái khám ngày 20/8' và không diễn đạt kiểu khác.\n"
                + "4. Không chẩn đoán thêm bệnh mới, không kê thêm thuốc ngoài đơn đã có.";

        String userPrompt = "Triệu chứng: " + orNone(request.getSymptoms()) + "\n"
                + "Kết luận bệnh: " + diagnosis + "\n"
                + "Đơn thuốc:\n" + orNone(formatMedicines(request.getMedicines()));

        return respond(systemPrompt, userPrompt, "exam-notes-" + request.getBookingId());
    }

    // ===================== C3. GỢI Ý MÃ ICD-10 =====================

    /**
     * Gợi ý mã ICD-10 từ kết luận bệnh bác sĩ vừa gõ.
     *
     * KHÔNG cần bookingId: đầu vào chỉ là một cụm từ chuyên môn, không đụng tới dữ liệu bệnh
     * nhân nào. Vẫn phải là bác sĩ mới gọi được — luật /api/doctor/chat/** lo việc đó.
     */
    @PostMapping("/suggest-icd")
    public ResponseEntity<Map<String, String>> suggestIcd(@RequestBody ExamAssistRequest request) {
        if (getCurrentDoctor().isEmpty()) {
            return forbidden();
        }

        String diagnosis = trim(request.getDiagnosis());
        if (diagnosis.isBlank()) {
            return ResponseEntity.ok(Map.of("answer", "Bác sĩ vui lòng nhập Kết luận bệnh trước."));
        }

        String systemPrompt =
                "Bạn là chuyên viên mã hoá bệnh tật theo ICD-10, hỗ trợ bác sĩ Việt Nam.\n\n"
                + "--- QUY TẮC ---\n"
                + "1. Trả về tối đa 3 dòng, mỗi dòng đúng dạng: MÃ - Tên bệnh tiếng Việt\n"
                + "2. Dòng đầu là mã khớp nhất, hai dòng sau là phương án gần đúng để bác sĩ cân nhắc.\n"
                + "3. Không thêm bất kỳ câu dẫn, lời khuyên hay giải thích nào.\n"
                + "4. Nếu cụm từ quá mơ hồ để mã hoá, trả lời đúng một câu: 'Chưa đủ thông tin để gợi ý mã ICD-10.'";

        return respond(systemPrompt, "Kết luận bệnh: " + diagnosis, "exam-icd");
    }

    // ===================== C4. TÓM TẮT BỆNH SỬ =====================

    /**
     * Tóm tắt các lần khám trước của CHÍNH bệnh nhân này.
     *
     * Form đã có timeline lịch sử ở cột trái, nhưng đó là danh sách thô — bác sĩ phải đọc từng
     * mục. Ở đây gói lại thành vài gạch đầu dòng để nắm ngay trước khi khám.
     */
    @GetMapping("/patient-summary/{bookingId}")
    public ResponseEntity<Map<String, String>> patientSummary(@PathVariable Long bookingId) {
        Optional<Booking> bookingOpt = requireOwnedBooking(bookingId);
        if (bookingOpt.isEmpty()) {
            return forbidden();
        }

        List<MedicalRecord> history =
                medicalRecordService.getPatientMedicalHistory(bookingOpt.get().getUser().getId());
        if (history == null || history.isEmpty()) {
            return ResponseEntity.ok(Map.of("answer", "Bệnh nhân chưa có lần khám nào trước đây."));
        }

        StringBuilder historyText = new StringBuilder();
        int count = 0;
        for (MedicalRecord record : history) {
            if (count++ >= MAX_HISTORY_RECORDS) break;
            historyText.append("--- Lần khám ")
                    .append(record.getCreatedAt() != null ? record.getCreatedAt().format(DATE_FORMAT) : "(không rõ ngày)")
                    .append(" ---\n")
                    .append("Chẩn đoán: ").append(orNone(record.getDiagnosis())).append("\n")
                    .append("Triệu chứng: ").append(orNone(record.getSymptoms())).append("\n")
                    .append("Đơn thuốc: ").append(orNone(record.getPrescription())).append("\n")
                    .append("Lời dặn: ").append(orNone(record.getDoctorNotes())).append("\n\n");
        }

        String systemPrompt =
                "Bạn là trợ lý của bác sĩ, tóm tắt bệnh sử để bác sĩ nắm nhanh trước khi khám. Viết bằng tiếng Việt.\n\n"
                + "BỆNH SỬ CÁC LẦN KHÁM TRƯỚC:\n" + historyText + "\n"
                + "--- QUY TẮC ---\n"
                + "1. Trả về ĐÚNG 3 gạch đầu dòng, theo thứ tự: Vấn đề sức khoẻ nổi bật / Thuốc đã dùng đáng lưu ý / Lần khám gần nhất và diễn tiến.\n"
                + "2. Mỗi gạch tối đa một câu.\n"
                + "3. CHỈ dựa vào nội dung trên, TUYỆT ĐỐI không suy diễn chẩn đoán mới và không đề xuất điều trị.\n"
                + "4. Mục nào bệnh sử không có dữ liệu thì ghi 'Không ghi nhận'.";

        return respond(systemPrompt, "Tóm tắt bệnh sử này giúp tôi.", "exam-history-" + bookingId);
    }

    // ===================== DÙNG CHUNG =====================

    /**
     * Gọi LLM và bọc kết quả. Nuốt lỗi thành câu tiếng Việt thay vì để 500 dội lên giao diện:
     * credit OpenRouter có thể cạn giữa chừng, và một nút trợ lý hỏng không được làm bác sĩ
     * tưởng cả form khám bị lỗi.
     */
    private ResponseEntity<Map<String, String>> respond(String systemPrompt, String userPrompt, String sessionId) {
        try {
            String answer = aiService.getConversationalResponse(systemPrompt, userPrompt, sessionId);
            if (answer == null || answer.isBlank()) {
                return ResponseEntity.ok(Map.of("answer", AI_FAILED));
            }
            return ResponseEntity.ok(Map.of("answer", answer.trim()));
        } catch (Exception e) {
            System.err.println("[ExamAi][" + sessionId + "] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ResponseEntity.ok(Map.of("answer", AI_FAILED));
        }
    }

    /**
     * Ca khám phải tồn tại VÀ thuộc về bác sĩ đang đăng nhập.
     * Cùng khuôn kiểm quyền của {@code DoctorMedicalRecordController.showCreateForm}, chỉ khác
     * là ở đây trả rỗng để controller đổi thành 403 thay vì redirect.
     */
    private Optional<Booking> requireOwnedBooking(Long bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        Optional<Doctor> doctorOpt = getCurrentDoctor();
        if (doctorOpt.isEmpty()) {
            return Optional.empty();
        }
        return bookingRepository.findById(bookingId)
                .filter(b -> b.getDoctor() != null
                        && b.getDoctor().getId().equals(doctorOpt.get().getId()));
    }

    private ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(403)
                .body(Map.of("answer", "Bác sĩ không có quyền truy cập ca khám này."));
    }

    private String formatMedicines(List<ExamAssistRequest.Medicine> medicines) {
        if (medicines == null || medicines.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ExamAssistRequest.Medicine m : medicines) {
            if (m == null || m.getName() == null || m.getName().isBlank()) continue;
            text.append("- ").append(trim(m.getName()));
            if (m.getDosage() != null && !m.getDosage().isBlank()) {
                text.append(" | hàm lượng: ").append(trim(m.getDosage()));
            }
            if (m.getInstructions() != null && !m.getInstructions().isBlank()) {
                text.append(" | cách dùng: ").append(trim(m.getInstructions()));
            }
            text.append("\n");
        }
        return text.toString();
    }

    private String trim(String value) {
        if (value == null) return "";
        String cleaned = value.trim();
        return cleaned.length() <= MAX_FIELD_CHARS ? cleaned : cleaned.substring(0, MAX_FIELD_CHARS);
    }

    private String orNone(String value) {
        String cleaned = trim(value);
        return cleaned.isBlank() ? "(không có)" : cleaned;
    }

    private Optional<Doctor> getCurrentDoctor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();
        Optional<User> userOpt;
        if (principal instanceof UserDetails userDetails) {
            userOpt = userRepository.findByUsername(userDetails.getUsername());
        } else if (principal instanceof OAuth2User oauthUser) {
            String email = oauthUser.getAttribute("email");
            userOpt = (email == null) ? Optional.empty() : userRepository.findByEmail(email);
        } else {
            userOpt = userRepository.findByUsername(auth.getName())
                    .or(() -> userRepository.findByEmail(auth.getName()));
        }

        return userOpt.flatMap(user -> doctorRepository.findByUserId(user.getId()));
    }
}
