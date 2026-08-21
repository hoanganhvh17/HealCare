package com.bookinghealthy.service.impl;

import com.bookinghealthy.dto.ImageAnalysis;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.ExternalMedicalRecord;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.ExternalMedicalRecordRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.DocumentTextExtractor;
import com.bookinghealthy.service.ExternalMedicalRecordService;
import com.bookinghealthy.service.FileStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ExternalMedicalRecordServiceImpl implements ExternalMedicalRecordService {

    private static final Logger log = LoggerFactory.getLogger(ExternalMedicalRecordServiceImpl.class);

    private static final int MAX_TITLE = 200;
    private static final int MAX_SUMMARY_CHARS = 3000;
    private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private ExternalMedicalRecordRepository recordRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private DocumentTextExtractor documentTextExtractor;
    @Autowired private AiService aiService;

    /**
     * Công tắc tắt hẳn phần gọi AI, khuôn giống {@code news.fetch.enabled}. Tắt thì tệp vẫn lưu
     * bình thường và hồ sơ nằm ở {@code PENDING} — để lập trình viên làm việc mà không đốt lượt gọi.
     */
    @Value("${medical-doc.ai-enabled:true}")
    private boolean aiEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ExternalMedicalRecord> findForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return recordRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Cố ý KHÔNG {@code @Transactional}: {@code storeMedicalDocument} ghi tệp ra đĩa, thứ mà một
     * lần rollback không thu hồi được. Thay vào đó ghi tệp trước, lưu dòng sau, và tự bù trừ bằng
     * cách xoá tệp nếu lưu hỏng — nếu không mỗi lần lỗi để lại một tệp mồ côi trong thư mục riêng tư.
     */
    @Override
    public ExternalMedicalRecord upload(Long userId, String title, String patientNote, MultipartFile file) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            throw new IllegalStateException("Vui lòng nhập tên hồ sơ (ví dụ: Khám tại BV Bạch Mai 03/2026).");
        }
        if (cleanTitle.length() > MAX_TITLE) {
            cleanTitle = cleanTitle.substring(0, MAX_TITLE);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Vui lòng chọn tệp hồ sơ cần tải lên.");
        }

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy bệnh nhân."));

        String storedName;
        try {
            storedName = fileStorageService.storeMedicalDocument(file);
        } catch (IllegalArgumentException e) {
            // FileStorageService đã viết sẵn câu tiếng Việt về định dạng không nhận — giữ nguyên
            // chứ không đắp thêm một câu chung chung lên trên.
            throw new IllegalStateException(e.getMessage());
        }

        ExternalMedicalRecord record = new ExternalMedicalRecord();
        record.setUser(owner);
        record.setTitle(cleanTitle);
        record.setStoredFileName(storedName);
        record.setOriginalFileName(StringUtils.getFilename(file.getOriginalFilename()));
        record.setContentType(file.getContentType());
        record.setFileSize(file.getSize());
        record.setDocType(isPdf(storedName) ? ExternalMedicalRecord.TYPE_PDF : ExternalMedicalRecord.TYPE_IMAGE);
        record.setPatientNote(patientNote == null || patientNote.isBlank() ? null : patientNote.trim());
        record.setAiStatus(ExternalMedicalRecord.AI_PENDING);

        try {
            return recordRepository.save(record);
        } catch (RuntimeException e) {
            fileStorageService.deleteMedicalDocument(storedName);
            throw e;
        }
    }

    /**
     * KHÔNG {@code @Transactional} — có một lượt gọi mạng ở giữa hàm, và một transaction ở đây sẽ
     * giam một connection HikariCP (pool 10) suốt thời gian chờ OpenRouter. Cùng lý do khiến
     * {@code MedicalRecordDeliveryService} được tách khỏi {@code MedicalRecordServiceImpl}.
     * Việc đọc và việc ghi mỗi cái tự chạy transaction riêng của Spring Data.
     */
    @Override
    public void analyze(Long recordId) {
        ExternalMedicalRecord record = recordRepository.findById(recordId).orElse(null);
        if (record == null) {
            return;
        }
        if (!aiEnabled) {
            log.info("[HoSoNgoaiVien] medical-doc.ai-enabled=false, bỏ qua phân tích hồ sơ #{}.", recordId);
            return;
        }

        Path file = fileStorageService.resolveMedicalDocument(record.getStoredFileName());
        if (file == null) {
            finish(record, ExternalMedicalRecord.AI_FAILED, "Không tìm thấy tệp trên máy chủ.", null);
            return;
        }

        String raw;
        if (ExternalMedicalRecord.TYPE_PDF.equals(record.getDocType())) {
            String text = documentTextExtractor.extractPdfText(file);
            if (text.isEmpty()) {
                // Bản scan: KHÔNG đưa chuỗi rỗng cho model rồi in ra một bản "tóm tắt" bịa đặt
                // dưới tên hồ sơ bệnh án. Nói thẳng, và chỉ đúng việc bệnh nhân cần làm.
                finish(record, ExternalMedicalRecord.AI_UNREADABLE,
                        "Tệp PDF này là bản scan nên hệ thống chưa đọc được chữ. "
                                + "Anh/chị chụp ảnh từng trang rồi tải lên giúp em nhé.", null);
                return;
            }
            raw = aiService.getStatelessResponse(buildSystemPrompt(), text, "extdoc-" + recordId);
        } else {
            String dataUrl = documentTextExtractor.toImageDataUrl(file);
            if (dataUrl == null) {
                finish(record, ExternalMedicalRecord.AI_FAILED,
                        "Hệ thống chưa đọc được ảnh này. Anh/chị thử chụp lại rõ hơn giúp em nhé.", null);
                return;
            }
            raw = aiService.analyzeImage(buildSystemPrompt(),
                    "Đây là ảnh chụp hồ sơ bệnh án của em. Hãy đọc và tóm tắt theo đúng định dạng JSON đã yêu cầu.",
                    dataUrl, "extdoc-" + recordId);
        }

        if (raw == null) {
            finish(record, ExternalMedicalRecord.AI_FAILED,
                    "Hệ thống AI đang bận. Anh/chị bấm nút Phân tích lại giúp em ạ.", null);
            return;
        }
        applyAiResult(record, raw);
    }

    @Override
    public void applyAnalysis(Long recordId, ImageAnalysis analysis) {
        ExternalMedicalRecord record = recordRepository.findById(recordId).orElse(null);
        if (record == null || analysis == null) {
            return;
        }
        if (!analysis.isDocument()) {
            // Không bao giờ để một tấm ảnh KHÔNG phải giấy tờ mang trạng thái DONE: DONE chính là
            // vé để khối tiêm ngữ cảnh của chatbot đọc nó lên như tiền sử bệnh, và để nó hiện trên
            // màn hình bác sĩ dưới mục "hồ sơ bệnh án".
            finish(record, ExternalMedicalRecord.AI_FAILED,
                    "Tệp này không phải giấy tờ y tế nên hệ thống không đọc được nội dung bệnh án.", null);
            return;
        }
        finish(record, ExternalMedicalRecord.AI_DONE,
                analysis.getDocumentSummary(), analysis.getDocumentDepartmentId());
    }

    @Override
    public String whyCannotView(ExternalMedicalRecord record, User viewer) {
        if (record == null || viewer == null) {
            return "Không tìm thấy hồ sơ.";
        }
        if (record.getUser() != null && record.getUser().getId().equals(viewer.getId())) {
            return null;
        }
        // Bác sĩ chỉ đọc được hồ sơ của bệnh nhân mình có lịch hẹn — nguyên tắc need-to-know.
        // Cố ý dò qua bảng doctors thay vì đọc User.roles: roles là @ManyToMany LAZY, một lần
        // chạm nhầm ngoài session là LazyInitializationException ở đúng chỗ đang gác quyền.
        Optional<Doctor> doctor = doctorRepository.findByUserId(viewer.getId());
        if (doctor.isPresent()
                && record.getUser() != null
                && bookingRepository.existsByDoctorIdAndUserId(doctor.get().getId(), record.getUser().getId())) {
            return null;
        }
        return "Anh/chị không có quyền xem hồ sơ này.";
    }

    @Override
    public String whyCannotDelete(ExternalMedicalRecord record, User viewer) {
        if (record == null || viewer == null) {
            return "Không tìm thấy hồ sơ.";
        }
        if (record.getUser() == null || !record.getUser().getId().equals(viewer.getId())) {
            return "Đây không phải hồ sơ của anh/chị.";
        }
        return null;
    }

    @Override
    public void delete(Long recordId, User viewer) {
        ExternalMedicalRecord record = recordRepository.findById(recordId).orElse(null);
        String blocked = whyCannotDelete(record, viewer);
        if (blocked != null) {
            throw new IllegalStateException(blocked);
        }
        String storedName = record.getStoredFileName();
        recordRepository.delete(record);
        // Xoá tệp SAU khi dòng DB đã mất: thứ tự ngược lại để lại một dòng trỏ tới tệp không còn
        // tồn tại, và màn hình sẽ mời bác sĩ bấm vào một liên kết chắc chắn 404.
        fileStorageService.deleteMedicalDocument(storedName);
    }

    @Override
    public Map<String, Object> toCard(ExternalMedicalRecord record) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", record.getId());
        row.put("title", record.getTitle());
        row.put("docType", record.getDocType());
        row.put("aiStatus", record.getAiStatus());
        row.put("aiSummary", record.getAiSummary());
        row.put("patientNote", record.getPatientNote());
        row.put("fileUrl", "/user/medical-document/file/" + record.getId());
        row.put("createdAt", record.getCreatedAt() == null
                ? null
                : record.getCreatedAt().format(CARD_DATE));

        // Tên khoa tra tại đây chứ không lưu vào bảng: khoa có thể bị đổi tên sau khi phân tích,
        // và một cái tên đóng băng trong hồ sơ sẽ chỉ tới một trang không còn khớp.
        row.put("departmentId", record.getAiDepartmentId());
        row.put("departmentName", record.getAiDepartmentId() == null ? null
                : departmentRepository.findById(record.getAiDepartmentId())
                        .map(Department::getName).orElse(null));
        return row;
    }

    // ==================== phần nội bộ ====================

    private static boolean isPdf(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * Prompt phân tích. Danh sách khoa nạp tại thời điểm gọi, giống cách
     * {@code AiService.chatWithMemory} làm — thêm khoa mới là dùng được ngay, không phải sửa prompt.
     */
    private String buildSystemPrompt() {
        StringBuilder depts = new StringBuilder();
        for (Department d : departmentRepository.findAll()) {
            depts.append("- ID: ").append(d.getId()).append(" | ").append(d.getName()).append("\n");
        }
        return "Bạn là trợ lý y tế đọc hồ sơ bệnh án do bệnh nhân mang từ cơ sở y tế KHÁC tới.\n"
                + "NHIỆM VỤ: đọc đúng những gì có trên giấy tờ và tóm tắt lại. TUYỆT ĐỐI KHÔNG suy diễn, "
                + "KHÔNG tự chẩn đoán, KHÔNG bịa thêm số liệu hay tên thuốc không nhìn thấy.\n"
                + "Chỗ nào mờ hoặc không đọc được thì ghi rõ là không đọc được, đừng đoán.\n\n"
                + "TRẢ VỀ MỘT CHUỖI JSON HỢP LỆ, KHÔNG kèm chữ nào ngoài JSON, đúng 5 khoá:\n"
                + "{\n"
                + "  \"summary\": \"3-5 câu tiếng Việt tóm tắt nội dung giấy tờ\",\n"
                + "  \"conditions\": [\"bệnh hoặc chẩn đoán đọc được\"],\n"
                + "  \"medications\": [\"thuốc đọc được\"],\n"
                + "  \"department_id\": <số ID khoa phù hợp nhất trong danh sách dưới, hoặc null nếu không rõ>,\n"
                + "  \"confidence\": \"cao|trung bình|thấp\"\n"
                + "}\n"
                + "Nếu ảnh hoặc tệp không phải giấy tờ y tế: summary ghi rõ điều đó, các mảng để rỗng, "
                + "department_id để null.\n\n"
                + "=== DANH SÁCH CHUYÊN KHOA ===\n" + depts;
    }

    /** Bóc JSON model trả về; hỏng thì ghi {@code FAILED} chứ không ném ra ngoài. */
    private void applyAiResult(ExternalMedicalRecord record, String raw) {
        String json = raw.trim();
        // Model hay bọc trong ```json ... ``` bất chấp lời dặn — gỡ trước khi parse.
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            StringBuilder summary = new StringBuilder(text(node, "summary"));

            String conditions = joinArray(node, "conditions");
            if (!conditions.isEmpty()) {
                summary.append("\nBệnh lý / chẩn đoán ghi nhận: ").append(conditions);
            }
            String medications = joinArray(node, "medications");
            if (!medications.isEmpty()) {
                summary.append("\nThuốc ghi nhận: ").append(medications);
            }
            String confidence = text(node, "confidence");
            if (!confidence.isEmpty()) {
                summary.append("\nĐộ tin cậy khi đọc: ").append(confidence);
            }

            Long departmentId = resolveDepartmentId(node);
            String result = summary.toString().trim();
            if (result.isEmpty()) {
                finish(record, ExternalMedicalRecord.AI_FAILED,
                        "Hệ thống chưa đọc được nội dung tệp này. Anh/chị bấm nút Phân tích lại giúp em ạ.", null);
                return;
            }
            if (result.length() > MAX_SUMMARY_CHARS) {
                result = result.substring(0, MAX_SUMMARY_CHARS);
            }
            finish(record, ExternalMedicalRecord.AI_DONE, result, departmentId);
        } catch (Exception e) {
            log.warn("[HoSoNgoaiVien] Hồ sơ #{}: JSON không đọc được ({}). Nội dung thô: {}",
                    record.getId(), e.getMessage(),
                    raw.length() > 200 ? raw.substring(0, 200) + "..." : raw);
            finish(record, ExternalMedicalRecord.AI_FAILED,
                    "Hệ thống chưa đọc được nội dung tệp này. Anh/chị bấm nút Phân tích lại giúp em ạ.", null);
        }
    }

    /**
     * Khoa model gợi ý phải TỒN TẠI THẬT mới được ghi.
     * Model bịa id là chuyện đã xảy ra nhiều lần trong dự án này (xem các ghi chú về
     * {@code booking_target} ở ai-assistant.md); một id lạ sẽ thành đường dẫn đặt lịch chết.
     */
    private Long resolveDepartmentId(JsonNode node) {
        JsonNode dept = node.get("department_id");
        if (dept == null || dept.isNull() || !dept.canConvertToLong()) {
            return null;
        }
        long id = dept.asLong();
        return departmentRepository.findById(id).isPresent() ? id : null;
    }

    private void finish(ExternalMedicalRecord record, String status, String summary, Long departmentId) {
        record.setAiStatus(status);
        record.setAiSummary(summary);
        record.setAiDepartmentId(departmentId);
        record.setAnalyzedAt(LocalDateTime.now());
        recordRepository.save(record);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? "" : value.asText("").trim();
    }

    private static String joinArray(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray() || array.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : array) {
            String value = item.asText("").trim();
            if (value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
        }
        return sb.toString();
    }
}
