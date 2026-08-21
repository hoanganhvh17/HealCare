package com.bookinghealthy.service.impl;

import com.bookinghealthy.dto.ImageAnalysis;
import com.bookinghealthy.model.AiImageUsage;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.AiImageUsageRepository;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.ChatImageService;
import com.bookinghealthy.service.DocumentTextExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatImageServiceImpl implements ChatImageService {

    private static final Logger log = LoggerFactory.getLogger(ChatImageServiceImpl.class);

    private static final int MAX_TEXT_CHARS = 3000;

    @Autowired private AiService aiService;
    @Autowired private DocumentTextExtractor documentTextExtractor;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private AiImageUsageRepository usageRepository;

    /** Cùng công tắc với hồ sơ ngoại viện — tắt AI là tắt cả hai đường đọc ảnh. */
    @Value("${medical-doc.ai-enabled:true}")
    private boolean aiEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String whyCannotAnalyzeImage(User user) {
        if (user == null) {
            return "Anh/chị đăng nhập giúp em trước khi gửi ảnh nhé.";
        }
        int used = usageRepository.findByUserIdAndUsageDate(user.getId(), LocalDate.now())
                .map(AiImageUsage::getCount)
                .orElse(0);
        if (used >= MAX_IMAGE_ANALYSES_PER_DAY) {
            return "Hôm nay anh/chị đã gửi " + MAX_IMAGE_ANALYSES_PER_DAY
                    + " ảnh rồi ạ. Anh/chị mô tả bằng lời giúp em, hoặc quay lại vào ngày mai nhé.";
        }
        return null;
    }

    @Override
    public ImageAnalysis analyze(User user, byte[] imageBytes, String logTag) {
        if (!aiEnabled) {
            log.info("[AnhYTe] medical-doc.ai-enabled=false, bỏ qua phân tích ảnh.");
            return other("Chức năng đọc ảnh đang tạm tắt ạ. Anh/chị mô tả bằng lời giúp em nhé.");
        }

        String dataUrl = documentTextExtractor.toImageDataUrl(imageBytes);
        if (dataUrl == null) {
            return other("Em chưa đọc được ảnh này ạ. Anh/chị thử chụp lại rõ hơn giúp em nhé.");
        }

        // Tăng bộ đếm TRƯỚC khi gọi model: lượt gọi thất bại vẫn tốn tiền và vẫn tốn thời gian của
        // máy chủ, nên nó vẫn phải bị tính. Đếm sau chỉ cần model lỗi là hạn mức không bao giờ chạm.
        countOneAnalysis(user);

        String raw = aiService.analyzeImage(buildPrompt(),
                "Đây là ảnh em vừa gửi. Hãy phân loại và trả lời theo đúng định dạng JSON đã yêu cầu.",
                dataUrl, logTag);
        if (raw == null) {
            return other("Hệ thống AI đang bận ạ. Anh/chị thử lại sau ít phút giúp em nhé.");
        }
        return parse(raw, logTag);
    }

    /**
     * Một dòng cho mỗi (người dùng, ngày).
     *
     * <p><b>Cố ý KHÔNG gắn {@code @Transactional}.</b> Hàm này được gọi từ {@code analyze()} trong
     * CÙNG bean, nên lời gọi không đi qua proxy của Spring và annotation ấy sẽ là một dòng chữ
     * không làm gì cả — tệ hơn là không có, vì người đọc sau sẽ tin là đã có transaction.
     * {@code usageRepository.save} tự chạy transaction của Spring Data, đúng thứ cần ở đây: một
     * lần ghi ngắn, commit xong TRƯỚC khi bắt đầu chờ OpenRouter.
     *
     * <p>Hai lượt cùng lúc có thể cùng thấy "chưa có dòng nào" rồi cùng chèn — UNIQUE (user, ngày)
     * sẽ từ chối cái thứ hai và nhánh catch nuốt nó. Chấp nhận: đây là gờ giảm tốc về chi phí,
     * cùng lập trường mà {@code whyCannotBookWithoutPayment} đã chọn với race check-then-insert.
     */
    @Override
    public void countOneAnalysis(User user) {
        try {
            LocalDate today = LocalDate.now();
            AiImageUsage usage = usageRepository.findByUserIdAndUsageDate(user.getId(), today)
                    .orElseGet(() -> {
                        AiImageUsage fresh = new AiImageUsage();
                        fresh.setUser(user);
                        fresh.setUsageDate(today);
                        return fresh;
                    });
            usage.setCount(usage.getCount() + 1);
            usageRepository.save(usage);
        } catch (Exception e) {
            // Đếm hỏng thì cho lượt này đi tiếp: hạn mức là gờ giảm tốc về chi phí, không phải luật
            // an toàn — chặn khách vì bộ đếm lỗi là đánh đổi sai.
            log.warn("[AnhYTe] Không tăng được bộ đếm hạn mức: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> toSymptomCard(ImageAnalysis analysis) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("bodyPart", analysis.getBodyPart());
        card.put("findings", analysis.getFindings());
        card.put("urgency", analysis.getUrgency());
        card.put("advice", analysis.getAdvice());
        card.put("isEmergency", analysis.isEmergency());

        List<Map<String, Object>> departments = new ArrayList<>();
        if (analysis.getDepartmentIds() != null) {
            for (Long id : analysis.getDepartmentIds()) {
                departmentRepository.findById(id).ifPresent(dept -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", dept.getId());
                    row.put("name", dept.getName());
                    departments.add(row);
                });
            }
        }
        card.put("departments", departments);
        return card;
    }

    // ==================== prompt ====================

    /**
     * MỘT prompt cho cả hai loại ảnh. Model tự phân loại rồi trả đúng phần tương ứng.
     *
     * <p>Danh sách khoa nạp tại thời điểm gọi (giống {@code AiService.chatWithMemory}) nên thêm khoa
     * mới là dùng được ngay, không phải sửa prompt.
     */
    private String buildPrompt() {
        StringBuilder depts = new StringBuilder();
        for (Department d : departmentRepository.findAll()) {
            depts.append("- ID: ").append(d.getId()).append(" | ").append(d.getName());
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                depts.append(" | Chuyên trị: ").append(d.getDescription());
            }
            depts.append("\n");
        }

        return "Bạn là trợ lý phân luồng của bệnh viện NNL Hospital, đang xem MỘT tấm ảnh bệnh nhân gửi lên.\n\n"
                + "BƯỚC 1 — PHÂN LOẠI ảnh vào đúng MỘT trong ba nhóm:\n"
                + "- DOCUMENT: giấy tờ y tế (phiếu khám, kết quả xét nghiệm, đơn thuốc, phim chụp, sổ khám bệnh).\n"
                + "- SYMPTOM: ảnh chụp một phần CƠ THỂ đang có vấn đề (mắt sưng, nốt ban, vết thương, mẩn đỏ, sưng tấy...).\n"
                + "- OTHER: mọi thứ còn lại (phong cảnh, ảnh chụp màn hình, đồ vật, ảnh chân dung bình thường không có dấu hiệu bệnh).\n\n"

                + "BƯỚC 2 — TRẢ VỀ MỘT CHUỖI JSON HỢP LỆ, KHÔNG kèm bất kỳ chữ nào ngoài JSON.\n\n"

                + "Nếu kind = DOCUMENT:\n"
                + "{\n"
                + "  \"kind\": \"DOCUMENT\",\n"
                + "  \"summary\": \"3-5 câu tiếng Việt tóm tắt nội dung giấy tờ\",\n"
                + "  \"conditions\": [\"bệnh hoặc chẩn đoán ĐỌC ĐƯỢC trên giấy\"],\n"
                + "  \"medications\": [\"thuốc ĐỌC ĐƯỢC trên giấy\"],\n"
                + "  \"department_id\": <ID khoa phù hợp nhất, hoặc null>,\n"
                + "  \"confidence\": \"cao|trung bình|thấp\"\n"
                + "}\n"
                + "TUYỆT ĐỐI KHÔNG suy diễn, không bịa số liệu hay tên thuốc không nhìn thấy.\n\n"

                + "Nếu kind = SYMPTOM:\n"
                + "{\n"
                + "  \"kind\": \"SYMPTOM\",\n"
                + "  \"body_part\": \"vùng cơ thể nhìn thấy, VD: mắt trái, cẳng tay phải\",\n"
                + "  \"visible_findings\": [\"dấu hiệu NHÌN THẤY ĐƯỢC, VD: mi mắt sưng nề, kết mạc đỏ\"],\n"
                + "  \"department_ids\": [<ID khoa phù hợp, tối đa 2>],\n"
                + "  \"urgency\": \"EMERGENCY|SOON|ROUTINE\",\n"
                + "  \"advice\": \"1-2 câu chăm sóc tạm thời trong lúc chờ đi khám\"\n"
                + "}\n"
                + "LUẬT AN TOÀN cho nhánh này, quan trọng hơn mọi luật khác:\n"
                + "- Chỉ MÔ TẢ những gì NHÌN THẤY. TUYỆT ĐỐI KHÔNG đặt tên bệnh, không chẩn đoán.\n"
                + "- KHÔNG trấn an kiểu 'không sao đâu', 'chỉ là nhẹ thôi'. Ảnh không đủ để kết luận điều đó.\n"
                + "- Chọn urgency = EMERGENCY nếu thấy BẤT KỲ dấu hiệu nào sau: chảy máu nhiều, vết thương sâu/hở, "
                + "bỏng rộng, sưng nề lan nhanh kèm đỏ tím, tổn thương xuyên thấu nhãn cầu, dấu hiệu nhiễm trùng "
                + "nặng (mủ nhiều, vệt đỏ lan), hoặc bất cứ thứ gì bạn không chắc mà trông nghiêm trọng. "
                + "KHI PHÂN VÂN, LUÔN CHỌN MỨC NẶNG HƠN.\n"
                + "- advice chỉ là chăm sóc tạm thời (chườm, rửa sạch, không dụi, không tự nặn). KHÔNG kê thuốc.\n\n"

                + "Nếu kind = OTHER:\n"
                + "{ \"kind\": \"OTHER\", \"message\": \"một câu tiếng Việt nói rõ đây không phải ảnh y tế\" }\n\n"

                + "Ảnh quá mờ/tối/không rõ để kết luận: trả kind = OTHER kèm message mời chụp lại rõ hơn.\n\n"
                + "=== DANH SÁCH CHUYÊN KHOA ===\n" + depts;
    }

    // ==================== bóc JSON ====================

    private ImageAnalysis parse(String raw, String logTag) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            String kind = text(node, "kind").toUpperCase();

            if (ImageAnalysis.KIND_DOCUMENT.equals(kind)) {
                return buildDocument(node);
            }
            if (ImageAnalysis.KIND_SYMPTOM.equals(kind)) {
                return buildSymptom(node);
            }
            String message = text(node, "message");
            return other(message.isEmpty()
                    ? "Ảnh này có vẻ không phải giấy tờ y tế hay ảnh vùng đang bị đau ạ. "
                            + "Anh/chị mô tả bằng lời giúp em nhé."
                    : message);
        } catch (Exception e) {
            log.warn("[AnhYTe][{}] JSON không đọc được ({}). Thô: {}", logTag, e.getMessage(),
                    raw.length() > 200 ? raw.substring(0, 200) + "..." : raw);
            return other("Em chưa đọc được ảnh này ạ. Anh/chị thử gửi lại giúp em nhé.");
        }
    }

    private ImageAnalysis buildDocument(JsonNode node) {
        ImageAnalysis result = new ImageAnalysis();
        result.setKind(ImageAnalysis.KIND_DOCUMENT);

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

        String text = summary.toString().trim();
        if (text.isEmpty()) {
            return other("Em chưa đọc được nội dung giấy tờ này ạ. Anh/chị chụp lại rõ hơn giúp em nhé.");
        }
        result.setDocumentSummary(text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text);
        result.setDocumentDepartmentId(validDepartment(node.get("department_id")));
        return result;
    }

    private ImageAnalysis buildSymptom(JsonNode node) {
        ImageAnalysis result = new ImageAnalysis();
        result.setKind(ImageAnalysis.KIND_SYMPTOM);
        result.setBodyPart(text(node, "body_part"));
        result.setFindings(arrayOf(node, "visible_findings"));
        result.setAdvice(text(node, "advice"));

        String urgency = text(node, "urgency").toUpperCase();
        // Giá trị lạ thì coi là SOON chứ KHÔNG phải ROUTINE: model trả sai chính tả mà bị hạ xuống
        // mức nhẹ nhất là đúng hướng nguy hiểm.
        if (!ImageAnalysis.URGENCY_EMERGENCY.equals(urgency)
                && !ImageAnalysis.URGENCY_SOON.equals(urgency)
                && !ImageAnalysis.URGENCY_ROUTINE.equals(urgency)) {
            urgency = ImageAnalysis.URGENCY_SOON;
        }
        result.setUrgency(urgency);

        // Đối chiếu từng id với DB. Đường chat hiện KHÔNG kiểm gì, model bịa id nào cũng lọt và
        // khách bấm vào một khoa không tồn tại.
        List<Long> departments = new ArrayList<>();
        JsonNode ids = node.get("department_ids");
        if (ids != null && ids.isArray()) {
            for (JsonNode item : ids) {
                Long valid = validDepartment(item);
                if (valid != null && !departments.contains(valid)) {
                    departments.add(valid);
                }
            }
        }
        result.setDepartmentIds(departments);
        return result;
    }

    private Long validDepartment(JsonNode value) {
        if (value == null || value.isNull() || !value.canConvertToLong()) {
            return null;
        }
        long id = value.asLong();
        return departmentRepository.findById(id).isPresent() ? id : null;
    }

    private static ImageAnalysis other(String message) {
        ImageAnalysis result = new ImageAnalysis();
        result.setKind(ImageAnalysis.KIND_OTHER);
        result.setMessage(message);
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? "" : value.asText("").trim();
    }

    private static List<String> arrayOf(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return out;
        }
        for (JsonNode item : array) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static String joinArray(JsonNode node, String field) {
        return String.join(", ", arrayOf(node, field));
    }
}
