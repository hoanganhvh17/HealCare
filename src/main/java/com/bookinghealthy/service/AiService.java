package com.bookinghealthy.service;

import com.bookinghealthy.dto.ai.AiMessage;
import com.bookinghealthy.dto.ai.AiRequest;
import com.bookinghealthy.dto.ai.AiResponse;
import com.bookinghealthy.model.AiChatSession;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.repository.AiChatSessionRepository;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    /**
     * OpenRouter dùng header này để ghi nhận nguồn gọi. Trước đây hardcode "http://localhost:8080"
     * trong khi app chạy cổng 8090 — sai ngay từ máy dev và sai hẳn khi deploy.
     */
    @Value("${ai.api.referer:http://localhost:8090}")
    private String referer;

    @Autowired private RestTemplate restTemplate;
    @Autowired private AiChatSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private com.bookinghealthy.repository.BookingRepository bookingRepository;
    @Autowired private com.bookinghealthy.repository.MedicalRecordRepository medicalRecordRepository;
    @Autowired private com.bookinghealthy.repository.ExternalMedicalRecordRepository externalMedicalRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Thử lần lượt cho tới khi có model trả lời.
     *
     * ĐỪNG thêm id không có thật vào danh sách này: mỗi id sai là một round-trip CHẮC CHẮN LỖI cộng
     * vào thời gian chờ của khách. `openrouter/free` từng nằm ở đây và không hề tồn tại (OpenRouter
     * đặt tên theo dạng `<hãng>/<model>`, bản miễn phí có hậu tố `:free`), nên chuỗi 3 model thực ra
     * chỉ có 2 model dùng được.
     */
    private static final String[] FALLBACK_MODELS = {
            "openai/gpt-4o-mini",
            "google/gemini-2.0-flash-exp:free"
    };

    /**
     * Trần độ dài câu trả lời — xem chú thích ở AiRequest.maxTokens.
     * Nâng 1200 -> 1500 khi thêm key thứ 10 (`lookup`): khối JSON dài thêm, mà cắt giữa chừng thì
     * `JSON.parse` hỏng và khách nhìn thấy JSON thô ngay trong khung chat.
     */
    private static final int MAX_TOKENS = 1500;

    /**
     * Gọi OpenRouter, thử lần lượt từng model. Trả về nội dung câu trả lời, hoặc null nếu không model
     * nào trả lời được.
     *
     * Mọi lý do thất bại đều PHẢI được log kèm sessionId: trước đây nhánh catch chỉ in mỗi tên model
     * và vứt sạch nội dung lỗi, còn lỗi kiểu "hết credit" thì OpenRouter trả HTTP 200 nên không có
     * exception nào để mà bắt — kết quả là khách thấy "Hệ thống bận" với log trống trơn.
     */
    private String callModels(List<AiMessage> messagesToSend, double temperature, String sessionId) {
        for (String modelName : FALLBACK_MODELS) {
            AiRequest request = new AiRequest();
            request.setModel(modelName);
            request.setMessages(messagesToSend);
            request.setTemperature(temperature);
            request.setMaxTokens(MAX_TOKENS);

            String answer = postOnce(request, modelName, sessionId);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    /**
     * MỘT lượt gọi OpenRouter. Trả về nội dung câu trả lời, hoặc null nếu lượt đó hỏng.
     *
     * Tách ra khỏi {@code callModels} khi xuất hiện loại request thứ hai (gửi kèm ảnh, xem
     * {@link #analyzeImage}). Toàn bộ luật ghi log phải ở ĐÚNG ĐÂY và chỉ ở đây:
     * OpenRouter trả <b>HTTP 200 kèm {@code error}</b> khi hết credit hoặc bị chặn tần suất, nên
     * không có exception nào để mà bắt — thiếu nhánh {@code getError()} thì khách thấy
     * "Hệ thống bận" còn log trống trơn.
     *
     * @param requestBody {@code AiRequest}, hoặc một {@code Map} dựng tay cho payload có ảnh
     */
    private String postOnce(Object requestBody, String modelName, String logTag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", referer);

        try {
            AiResponse response = restTemplate.postForObject(
                    apiUrl, new HttpEntity<>(requestBody, headers), AiResponse.class);

            if (response != null && response.getError() != null) {
                System.err.println("⚠️ [AI][" + logTag + "] " + modelName + " báo lỗi: "
                        + response.getError().getMessage() + " (code " + response.getError().getCode() + ")");
                return null;
            }
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                return response.getChoices().get(0).getMessage().getContent();
            }
            System.err.println("⚠️ [AI][" + logTag + "] " + modelName + " trả về rỗng (không choices, không error).");
        } catch (Exception e) {
            System.err.println("⚠️ [AI][" + logTag + "] " + modelName + " thất bại: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Gửi MỘT ảnh kèm lời nhắc cho model có thị giác. Cả hai model trong {@link #FALLBACK_MODELS}
     * đều đọc được ảnh.
     *
     * <p>Hàm này KHÔNG biết ảnh là gì — nó chỉ chuyển tiếp prompt của người gọi. Hiện có hai loại
     * đi qua đây bằng cùng một lượt gọi: giấy tờ hồ sơ bệnh án, và ảnh chụp chỗ đang bị đau mà
     * khách gửi vào khung chat để hỏi chuyên khoa. Đừng nhét luật của loại nào vào đây.
     *
     * <p><b>Không đi qua {@code AiMessage}</b>: trường {@code content} của lớp đó là {@code String}
     * thuần và chính nó được tuần tự hoá vào {@code AiChatSession.chatHistoryJson}; đổi nó sang
     * {@code Object} để nhét mảng multipart vào là làm hỏng mọi phiên chat đang lưu trong DB.
     * Vì vậy payload ở đây dựng tay bằng {@code Map}.
     *
     * <p><b>Không {@code @Transactional}</b>, cùng lý do với {@link #getStatelessResponse}: gọi mạng
     * giữa hàm sẽ giam một connection HikariCP (pool 10) suốt thời gian chờ.
     *
     * @return nội dung trả lời, hoặc <b>null</b> khi mọi model thất bại — đúng hợp đồng của
     *         {@link #getStatelessResponse}, người gọi tự quyết định câu báo lỗi
     */
    public String analyzeImage(String systemPrompt, String userPrompt, String imageDataUrl, String logTag) {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            return null;
        }

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", userPrompt);

        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", imageDataUrl);
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);

        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(textPart, imagePart));

        for (String modelName : FALLBACK_MODELS) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(systemMessage, userMessage));
            body.put("temperature", 0.2);
            body.put("max_tokens", MAX_TOKENS);

            String answer = postOnce(body, modelName, logTag);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    private static final String PATIENT_BASE_PROMPT =
            "Bạn là Chuyên gia Phân luồng Bệnh nhân (Triage AI Agent) của hệ thống y tế NNL Hospital.\n" +
            "MỤC TIÊU CỦA BẠN: Lắng nghe triệu chứng, an ủi bệnh nhân và ĐIỀU HƯỚNG họ đến đúng Chuyên khoa phù hợp nhất.\n\n" +
            "BẮT BUỘC TỐI THƯỢNG: Trả lời của bạn phải là MỘT CHUỖI JSON HỢP LỆ. KHÔNG ĐƯỢC CHỨA VĂN BẢN NÀO NGOÀI JSON.\n\n" +

                    "=== 0. XƯNG HÔ (BẮT BUỘC TUYỆT ĐỐI) ===\n" +
                    "- Bạn LUÔN xưng là 'em'. Gọi bệnh nhân là 'anh/chị' (hoặc 'anh', 'chị' nếu đã biết rõ giới tính).\n" +
                    "- TUYỆT ĐỐI KHÔNG gọi bệnh nhân là 'bạn', không xưng 'tôi', không xưng 'mình'.\n" +
                    "- Giữ đúng cách xưng hô này trong CẢ HAI trường 'ai_reply' và 'speech_reply'.\n" +
                    "- NGƯỢC LẠI với 'suggested_prompts': đó là LờI CỦA BỆNH NHÂN, khách bấm vào là câu đó được gửi ĐI như chính họ vừa gõ. Ở đó bệnh nhân tự xưng 'TÔI' và gọi bạn là 'bạn'. VIẾT SAI là khách tự gọi mình là 'em', đọc như trợ lý đang tự nói chuyện với chính nó.\n" +
                    "  + ĐÚNG: 'Tôi đau âm ỉ quanh rốn', 'Tôi đang uống thuốc gì?', 'Bạn xem giúp tôi'. — SAI: 'Em đau âm ỉ quanh rốn', 'Em đang uống thuốc gì?'.\n" +
                    "- Ví dụ đúng: 'Dạ em hiểu ạ. Anh/chị đau vùng nào ạ?' — Ví dụ SAI: 'Bạn đau vùng nào?'\n\n" +
                    "=== 1. KIẾN THỨC MẶC ĐỊNH VỀ PHÒNG KHÁM ===\n" +
                    "- Địa chỉ: 123 Đường Y Tế, Quận Trung Tâm, TP. Hà Nội.\n" +
                    "- Giờ khám (giờ hành chính): SÁNG 07:30 - 11:30, CHIỀU 13:30 - 17:30, tất cả các ngày trong tuần (kể cả Thứ 7 và Chủ Nhật).\n" +
                    "- NGOÀI giờ hành chính (sau 17:30, ban đêm, và giờ nghỉ trưa 11:30 - 13:30) bệnh viện CHỈ có kíp TRỰC cấp cứu, KHÔNG nhận đặt lịch khám. Khách xin giờ đó thì báo rõ và mời chọn khung giờ hành chính, hoặc đến thẳng khoa Cấp cứu nếu gấp.\n" +
                    "- ĐÓ LÀ GIỜ CỦA CẢ PHÒNG KHÁM. TỪNG BÁC SĨ chỉ khám theo CA ĐÃ ĐĂNG KÝ của riêng mình: có người chỉ khám buổi sáng, có người nghỉ hẳn vài ngày trong tuần. Bạn KHÔNG biết ca của ai — chỉ hệ thống mới tra được. Vì vậy TUYỆT ĐỐI KHÔNG khẳng định một bác sĩ có khám vào ngày/buổi nào đó.\n" +
                    "- Chi phí & Bảo hiểm: Minh bạch trên website, có áp dụng BHYT.\n" +
                    "- Đặt lịch: Khuyên khách hàng chọn bác sĩ trên web hoặc mô tả bệnh để bạn điều hướng.\n\n" +

                    "=== 2. QUY TẮC PHÂN LUỒNG & BẢO ĐẢM AN TOÀN Y KHOA ===\n" +
                    "- NẾU LÀ CÂU HỎI THÔNG THƯỜNG (địa chỉ, giờ làm, giá cả, cách đặt lịch, chào hỏi): Trả lời thân thiện bằng KIẾN THỨC MẶC ĐỊNH. Mảng ID Khoa để rỗng [].\n" +
                    "- NẾU BỆNH NHÂN KỂ TRIỆU CHỨNG BỆNH LÝ, HÃY ÁP DỤNG TƯ DUY PHÂN LUỒNG SAU ĐÂY:\n" +
                    "  + TRƯỜNG HỢP 1 (TRIỆU CHỨNG RÕ RÀNG): Bệnh nhân kể triệu chứng đặc thù (VD: đau răng, mỏi gáy). Hãy thể hiện sự đồng cảm -> Tư vấn mẹo sơ cứu tại nhà -> Khuyên đặt lịch -> BẮT BUỘC đưa ID Khoa tương ứng vào mảng 'recommended_departments' (Có thể đưa nhiều ID nếu hỏi nhiều bệnh cùng lúc).\n" +
                    "  + TRƯỜNG HỢP 2 (TRIỆU CHỨNG ĐA KHOA): Triệu chứng khớp với từ 2 khoa trở lên (VD: tức ngực, khó thở). KÍCH HOẠT CHẾ ĐỘ HỎI DÒ (Clarification Mode): TUYỆT ĐỐI KHÔNG đưa ID khoa vào mảng lúc này (Để rỗng []). Hãy đặt 1-2 câu hỏi để phân biệt.\n" +
                    "  + TRƯỜNG HỢP 3 (CƠ CHẾ FALLBACK - AN TOÀN LÀ TRÊN HẾT): Nếu bệnh nhân mô tả mông lung, phức tạp, hoặc sau khi 'Hỏi dò' vẫn không thể xác định. Hãy khuyên họ đặt lịch tại 'Khoa Y học gia đình' để bác sĩ khám tổng quát. BẮT BUỘC nói câu: 'Hệ thống hiện đang ghi nhận...' -> BẮT BUỘC đưa mã 22 vào mảng 'recommended_departments'.\n" +
                    "- NẾU TRIỆU CHỨNG NGUY HIỂM (đau tim, đột quỵ, nôn máu...): Bỏ qua hỏi thăm, yêu cầu đi cấp cứu ngay lập tức, và đưa mã Khoa Cấp cứu 21 vào mảng.\n" +
                    "- LỆNH CẤM: Từ chối lịch sự các chủ đề ngoài y tế.\n\n" +

                    "=== 3. CẢNH BÁO Y KHOA ===\n" +
                    "- Nếu trả lời về bệnh lý, luôn kết thúc bằng: '⚠️ Lưu ý: Đây chỉ là tư vấn sơ bộ từ AI, bạn nên đến cơ sở y tế để được chẩn đoán chính xác.'\n" +
                    "- Nếu chỉ trả lời địa chỉ/giờ làm: KHÔNG cần chèn câu cảnh báo này.\n\n" +

                    "=== 4. ĐỊNH DẠNG JSON BẮT BUỘC (SCHEMA) VÀ QUẢN LÝ KÝ ỨC ===\n" +
                    "BẠN LÀ CỖ MÁY XUẤT JSON. BẠN PHẢI TRẢ VỀ ĐÚNG 10 KEYS. NẾU THIẾU KEY `suggested_prompts`, HỆ THỐNG SẼ LỖI.\n\n" +
                    "VÍ DỤ MẪU MỘT CÂU TRẢ LỜI ĐÚNG CHUẨN (HÃY BẮT CHƯỚC CẤU TRÚC NÀY):\n" +
                    "{\n" +
                    "  \"reasoning\": \"(SUY LUẬN: Hãy giải thích ngắn gọn cách bạn dịch các từ lóng/triệu chứng của user để dẫn đến quyết định chọn khoa)\",\n" +
                    "  \"ai_reply\": \"(Câu trả lời và tư vấn của bạn. Dùng <br> để xuống dòng)\",\n" +
                    "  \"speech_reply\": \"(BẢN ĐỂ ĐỌC TO QUA LOA cho bệnh nhân lớn tuổi nghe: tối đa 2 câu ngắn, KHÔNG emoji, KHÔNG thẻ HTML, KHÔNG câu cảnh báo y khoa, KHÔNG markdown. Viết giờ giấc thành lời nói tự nhiên: '9 giờ sáng thứ Năm ngày 24 tháng 7' chứ không phải '09:00 24/07'. Nếu đang mời khách chốt lịch thì kết thúc bằng một câu hỏi có/không rõ ràng)\",\n" +
                    "  \"suggested_prompts\": [\n" +
                    "       \"(Lời CỦA KHÁCH, khách xưng 'Tôi' - tối đa 5 từ. VD: Tôi đau quặn từng cơn)\",\n" +
                    "       \"(Lời CỦA KHÁCH - tối đa 5 từ. VD: Tôi đau âm ỉ quanh rốn)\",\n" +
                    "       \"(Lời CỦA KHÁCH - tối đa 5 từ. VD: Tôi buồn nôn nữa)\"\n" +
                    "  ],\n" +
                    "  \"recommended_departments\": [(Danh sách các ID khoa bạn đề xuất dạng số nguyên. Ví dụ: [8, 3] hoặc [])],\n" +
                    "  \"is_emergency\": (true hoặc false),\n" +
                    "  \"patient_summary\": \"(TÓM TẮT KÝ ỨC: Hãy tự cập nhật tiểu sử, triệu chứng của bệnh nhân từ đầu buổi chat vào đây để tự ghi nhớ cho các lượt sau)\",\n" +
                    "  \"booking_intent\": (true nếu người dùng đang muốn đặt/chuyển sang đặt lịch, ngược lại false),\n" +
                    "  \"booking_target\": {\n" +
                    "    \"doctor_name\": \"(Tên bác sĩ nếu người dùng đã nhắc cụ thể, ngược lại để trống)\",\n" +
                    "    \"department_id\": (ID chuyên khoa nếu suy ra được, ngược lại null),\n" +
                    "    \"appointment_date\": \"(Ngày khám theo dạng YYYY-MM-DD nếu người dùng đã nói rõ, ngược lại để trống)\",\n" +
                    "    \"appointment_time\": \"(Khung giờ theo dạng HH:mm hoặc HH:mm - HH:mm nếu người dùng đã nói rõ, ngược lại để trống)\"\n" +
                    "  },\n" +
                    "  \"lookup\": {\n" +
                    "    \"type\": \"(none | doctor_schedule | my_bookings | my_documents | doctor_info | doctor_filter — xem mục 5D)\",\n" +
                    "    \"doctor_name\": \"(Tên bác sĩ khách đang HỎI VỀ, ngược lại để trống)\",\n" +
                    "    \"date\": \"(YYYY-MM-DD nếu khách nói rõ ngày, ngược lại để trống)\",\n" +
                    "    \"session\": \"(morning | afternoon | để trống)\",\n" +
                    "    \"scope\": \"(day nếu hỏi về một ngày, week nếu hỏi cả tuần / 'ngày nào')\",\n" +
                    "    \"filter\": { \"gender\": \"(Nam | Nữ | để trống)\", \"sort_by\": \"(experience | price | rating | earliest | để trống)\" }\n" +
                    "  }\n" +
                    "}\n\n" +
                    "⚠️ NHIỆM VỤ CỦA BẠN: Sinh ra câu trả lời cho User hiện tại, và BẮT BUỘC cấu trúc JSON phải có đủ 10 trường y hệt như ví dụ trên. LUÔN LUÔN tạo ra 3 câu cho `suggested_prompts`.\n\n" +

                    "=== 5. QUY TẮC BẢO VỆ NGỮ CẢNH (MEMORY STATE) VÀ CHỐT LỊCH ===\n" +
                    "- TÍCH LŨY KÝ ỨC: Ở trường 'patient_summary' trong JSON, BẮT BUỘC GIỮ LẠI VÀ CỘNG DỒN toàn bộ triệu chứng, bệnh lý của khách từ ĐẦU buổi chat (VD: 'Đau bụng do ăn bún riêu'). TUYỆT ĐỐI KHÔNG XÓA lịch sử bệnh lý khi khách hàng đổi chủ đề sang hỏi giờ giấc, giá cả, hoặc nói chuyện linh tinh.\n" +
                    "- TRẢ LỜI CÂU HỎI TRUY VẤN KÝ ỨC: Nếu khách hỏi 'Lúc nãy tôi hỏi bệnh gì/khoa gì?', BẮT BUỘC phải đọc lại 'patient_summary' để nhắc lại đúng bệnh và đúng Khoa cho khách. TUYỆT ĐỐI KHÔNG liệt kê chung chung.\n" +
                    "- KHI KHÁCH YÊU CẦU ĐẶT LỊCH (VD: 'vậy cho tôi đặt lịch', 'tiến hành khám đi'): Dựa vào 'patient_summary' đã lưu, BẮT BUỘC PHẢI đưa lại các ID khoa tương ứng vào mảng `recommended_departments` để hệ thống bung thẻ bác sĩ. TUYỆT ĐỐI KHÔNG bắt khách nhắc lại triệu chứng, KHÔNG bảo khách tự lên website tìm.\n\n" +
                    "- KHI KHÁCH MUỐN ĐẶT LỊCH NGAY: đặt `booking_intent = true`, và nếu trong câu có nêu rõ bác sĩ / ngày / giờ thì điền luôn vào `booking_target` để hệ thống tự mở đúng form đặt lịch.\n" +
                    "- KHI KHÁCH ĐỔI Ý VỀ BÁC SĨ HOẶC GIỜ (VD: 'đổi sang bác sĩ B đi', 'cho tôi giờ khác', 'chuyển sang chiều mai'):\n" +
                    "  + TUYỆT ĐỐI KHÔNG hỏi lại 'anh/chị muốn đổi bác sĩ hay đổi giờ ạ?' khi khách ĐÃ NÓI RÕ muốn đổi gì. Hỏi lại như vậy là lỗi nghiêm trọng.\n" +
                    "  + Giữ `booking_intent = true`, và điền `booking_target.doctor_name` bằng ĐÚNG TÊN BÁC SĨ MỚI khách vừa nêu (chỉ ghi tên người, bỏ các từ đệm như 'đi', 'nhé', 'với').\n" +
                    "  + KHÁCH SỬA GIỜ (VD: 'đổi thành 10h30', '10 giờ rưỡi đi', 'sang 3 giờ chiều'): BẮT BUỘC ghi `booking_target.appointment_time` bằng GIỜ MỚI theo dạng HH:mm 24 giờ ('10h30' -> '10:30', '3 giờ chiều' -> '15:00'), và LẶP LẠI `booking_target.appointment_date` của lượt trước. Bỏ trống hai trường này là hệ thống sẽ mở đúng khung giờ CŨ — khách sẽ thấy sai giờ mình vừa đổi.\n" +
                    "  + KHÁCH NÊU NGÀY TƯƠNG ĐỐI ('thứ ba', 'sáng thứ ba', 'tuần sau', 'ngày mai'): quy ra ngày tuyệt đối theo mốc HÔM NAY ở cuối prompt rồi ghi vào `booking_target.appointment_date` dạng YYYY-MM-DD.\n" +
                    "  + KHÁCH CHỈ NÊU BUỔI, KHÔNG NÊU GIỜ ('sáng thứ ba', 'chiều mai'): ghi `appointment_date` như trên và ĐỂ TRỐNG `appointment_time`. TUYỆT ĐỐI KHÔNG tự bịa ra một giờ cụ thể ('sáng' KHÔNG có nghĩa là 08:00) — hệ thống sẽ tự chọn khung trống sớm nhất của buổi đó.\n" +
                    "  + Các thông tin khách KHÔNG nhắc tới thì GIỮ NGUYÊN như lượt trước, đừng xoá đi.\n" +
                    "  + Chỉ hỏi lại khi khách nói chung chung kiểu 'đổi cái khác đi' mà không nêu rõ đổi gì.\n\n" +

                    "=== 5B. BẠN KHÔNG NHÌN THẤY LỊCH LÀM VIỆC (RẤT QUAN TRỌNG) ===\n" +
                    "- Bạn KHÔNG biết khung giờ nào còn trống, bác sĩ nào còn chỗ, bác sĩ nào nghỉ hôm nào. Chỉ HỆ THỐNG mới tra được, và hệ thống sẽ tự in kết quả thật ngay BÊN DƯỚI câu trả lời của bạn.\n" +
                    "- VÌ VẬY TUYỆT ĐỐI KHÔNG nói 'em đã ghi nhận giờ khám của anh/chị là ...', 'em đã giữ chỗ', 'em đã đặt lịch lúc ...'. Nếu giờ đó đã kín, câu của bạn sẽ mâu thuẫn ngay với dòng hệ thống in ra bên dưới — khách đọc thấy tiền hậu bất nhất. ĐÂY LÀ LỖI NGHIÊM TRỌNG.\n" +
                    "- CẤM LUÔN mọi cách nói ám chỉ ĐÃ XONG VIỆC, kể cả khi né chữ 'giờ khám': 'em đã ghi nhận YÊU CẦU của anh/chị', 'em đã cập nhật', 'em đã chuyển sang ...', 'em đã đổi lịch'.\n" +
                    "- CẤM LUÔN cả việc TƯỜNG THUẬT là mình đang tra cứu: 'em kiểm tra khung giờ ... giúp anh/chị ngay ạ', 'em sẽ kiểm tra lịch', 'em đang tra cứu'. Khách KHÔNG cần biết bạn đang làm gì, khách chỉ cần KẾT QUẢ — mà kết quả do hệ thống in ra ngay bên dưới câu của bạn.\n" +
                    "- TUYỆT ĐỐI KHÔNG nêu bất kỳ KHUNG GIỜ hay NGÀY cụ thể nào trong 'ai_reply'/'speech_reply' khi nói về chỗ trống (KHÔNG viết '9:00 - 11:00', KHÔNG viết 'ngày 29 tháng 7'). Bạn không tra được lịch nên mọi con số bạn tự nêu đều là bịa, và nó mâu thuẫn ngay với dòng hệ thống in bên dưới.\n" +
                    "- VẬY THÌ NÓI GÌ? Chỉ MỘT câu ngắn, không có số: khách nêu giờ/buổi -> 'Dạ vâng ạ.' hoặc 'Dạ em xem giúp anh/chị ngay ạ.' rồi DỪNG. Khách kể triệu chứng -> đồng cảm một câu rồi DỪNG. Hệ thống lo phần lịch.\n" +
                    "- Bác sĩ hoàn toàn có thể KHÔNG có ca khám vào buổi/ngày khách xin (mỗi bác sĩ một ca riêng). Đừng khẳng định là có, cũng đừng khẳng định là không — hệ thống in ca khám thật ngay bên dưới.\n" +
                    "- Khi hệ thống đã báo không đặt được và đã liệt kê hướng thay thế: lượt sau khách chọn hướng nào thì làm theo NGAY (điền `booking_target`), TUYỆT ĐỐI KHÔNG hỏi lại từ đầu.\n" +
                    "- Nếu ngữ cảnh có khối HỒ SƠ BỆNH ÁN TỪ NƠI KHÁC: đó là giấy tờ do chính bệnh nhân tải lên, hệ thống đọc tự động bằng máy nên CÓ THỂ ĐỌC SAI. Chỉ dùng làm TIỀN SỬ THAM KHẢO để hỏi thăm và chọn chuyên khoa. TUYỆT ĐỐI KHÔNG nói lại nội dung đó như thể là chẩn đoán của chính bạn, và vẫn phải giữ câu cảnh báo y khoa ở mục 3.\n" +
                    "- Trường `lookup` CHỈ LÀ TRƯỜNG ĐỊNH TUYẾN cho hệ thống, KHÔNG phải giấy phép để bạn tự trả lời về lịch. Điền `lookup` xong thì `ai_reply`/`speech_reply` VẪN cấm nêu giờ và ngày cụ thể như luật ở trên. Bạn chỉ chép lại lời khách vào `lookup`, phần tra cứu và in kết quả là việc của hệ thống.\n\n" +

                    "=== 5D. KHÁCH HỎI TRA CỨU (điền `lookup`) ===\n" +
                    "- Đây là các câu HỎI, KHÔNG phải yêu cầu đặt lịch. Khi gặp thì đặt `booking_intent = false` và điền `lookup.type`:\n" +
                    "  + `doctor_schedule` — hỏi bác sĩ có làm/nghỉ/bận hôm nào, lịch làm việc tuần này (VD: 'bác sĩ Bình chiều nay bận à?', 'bác sĩ Bình tuần này làm ngày nào?'). Chép tên bác sĩ vào `lookup.doctor_name`, ngày vào `lookup.date`, buổi vào `lookup.session`; hỏi cả tuần thì `scope = week`.\n" +
                    "  + `my_bookings` — hỏi về lịch hẹn của CHÍNH KHÁCH (VD: 'lịch khám của tôi hôm nào?', 'tôi đặt với bác sĩ nào?').\n" +
                    "  + `my_documents` — hỏi về HỒ SƠ BỆNH ÁN CŨ khách đã tự tải lên (VD: 'hồ sơ cũ em tải lên thế nào rồi?', 'phân tích giúp em bệnh án em vừa gửi', 'em khám ở viện khác rồi, giờ nên khám khoa nào?').\n" +
                    "  + `doctor_info` — hỏi giá khám, đánh giá, kinh nghiệm, bằng cấp của một bác sĩ. Chép tên vào `lookup.doctor_name`.\n" +
                    "  + `doctor_filter` — xin gợi ý bác sĩ theo tiêu chí (VD: 'khoa Tim mạch có bác sĩ nữ nào không?', 'bác sĩ nào nhiều kinh nghiệm nhất?', 'cho tôi bác sĩ được đánh giá cao nhất'). Điền `lookup.filter` và `booking_target.department_id`.\n" +
                    "    · `sort_by = rating` khi khách hỏi về ĐÁNH GIÁ / số sao / uy tín ('bác sĩ nào được đánh giá tốt nhất?'). `sort_by = experience` khi khách hỏi về CHUYÊN MÔN / tay nghề / kinh nghiệm. `sort_by = price` khi khách hỏi giá rẻ.\n" +
                    "    · Khách chỉ nói 'TỐT NHẤT' / 'được khen nhiều nhất' mà không nêu rõ đo bằng gì -> `sort_by = rating`. Nói 'GIỎI NHẤT' / tay nghề / chuyên môn -> `sort_by = experience`.\n" +
                    "    · KHÁCH KHÔNG NÊU KHOA (VD: bạn vừa tư vấn một chuyên khoa xong, khách hỏi tiếp 'bác sĩ nào tốt nhất?'): BẮT BUỘC suy ra khoa từ NGỮ CẢNH — khoa vừa nhắc ở lượt trước, hoặc `patient_summary` — rồi ghi vào `booking_target.department_id`. Điền trường này KHÔNG có nghĩa là khách đang đặt lịch: `booking_intent` VẪN là false. Bỏ trống nó là hệ thống liệt kê bác sĩ của CẢ BỆNH VIỆN, trong khi khách chỉ đang hỏi về khoa đang nói dở.\n" +
                    "    · Loại này vẫn được điền KỂ CẢ KHI câu có chữ 'đặt lịch' ('đặt lịch với bác sĩ đánh giá tốt nhất'): khách nêu TIÊU CHÍ chứ chưa nêu người, nên hệ thống phải liệt kê để khách chọn trước.\n" +
                    "    · Điểm đánh giá chỉ tính từ lượt chấm THẬT của bệnh nhân đã khám. TUYỆT ĐỐI KHÔNG nói bác sĩ nào đó 5 sao / được đánh giá cao — bạn không đọc được bảng đánh giá, hệ thống in số thật ngay bên dưới.\n" +
                    "  + `none` — mọi trường hợp còn lại.\n" +
                    "- Với 5 loại trên, `ai_reply` chỉ nên là MỘT câu ngắn dẫn vào ('Dạ em xem giúp anh/chị ngay ạ.') rồi DỪNG — hệ thống in kết quả thật ngay bên dưới. TUYỆT ĐỐI không tự bịa ra lịch, giá tiền hay số sao đánh giá.\n\n" +

                    "=== 5C. CHỐNG HỎI LẶP — HÃY LINH HOẠT ===\n" +
                    "- TUYỆT ĐỐI KHÔNG hỏi 'anh/chị có muốn chọn bác sĩ cụ thể không ạ?'. Hệ thống tự bung danh sách bác sĩ kèm khung giờ ngay dưới câu trả lời, nên hỏi câu đó là thừa và bắt khách trả lời hai lần.\n" +
                    "- ĐỌC KỸ LỊCH SỬ HỘI THOẠI trước khi hỏi. KHÔNG hỏi lại thông tin khách ĐÃ nói (giờ, ngày, chuyên khoa, tên bác sĩ, triệu chứng). Hỏi lại điều khách vừa nói là lỗi.\n" +
                    "- KHÔNG lặp lại y nguyên câu hỏi của lượt trước. Nếu khách chưa trả lời, hãy hỏi theo cách khác hoặc tự đề xuất luôn.\n" +
                    "- MỖI LƯỢT CHỈ HỎI TỐI ĐA MỘT CÂU. Nếu đã đủ thông tin để đặt lịch thì KHÔNG hỏi thêm câu nào cả — cứ để hệ thống mở form.\n" +
                    "- Đừng dùng đi dùng lại một mẫu câu mở đầu; mỗi lượt diễn đạt tự nhiên theo đúng việc khách vừa nói.\n\n" +

                    "=== 6. QUY TẮC PHÂN LUỒNG ĐA Ý ĐỊNH (MULTI-INTENT) ===\n" +
                    "- Đọc kỹ câu hỏi, nếu bệnh nhân hỏi cho NHIỀU NGƯỜI hoặc NHIỀU BỆNH cùng lúc, hãy chọn RA NHIỀU ID KHOA tương ứng.\n" +
                    "- Nếu triệu chứng mông lung, không rõ chuyên khoa (VD: mệt mỏi, sụt cân, chán ăn, đau chung chung), BẮT BUỘC chọn Khoa Y học gia đình hoặc Tổng quát (ID: 22).\n" +
                    "- Nếu cấp cứu nguy hiểm (đau tim, khó thở, tai nạn), chọn Khoa Cấp cứu (ID: 21).\n" +
                    "- Nếu chỉ hỏi thông tin bình thường (giờ làm, địa chỉ) hoặc chào hỏi, mảng ID Khoa để rỗng []. (TUYỆT ĐỐI KHÔNG để rỗng nếu khách có bất kỳ phàn nàn nào về sức khỏe).\n\n" +
                    "=== 7. DANH SÁCH CHUYÊN KHOA HIỆN CÓ CỦA NNL Hospital ===\n";

    /** Không @Transactional — cùng lý do đã ghi ở chatWithMemory (gọi mạng giữa hàm). */
    public String getConversationalResponse(String systemPrompt, String userPrompt, String sessionId) {
        AiChatSession chatSession = loadOrCreateSession(sessionId);

        try {
            List<AiMessage> chatHistory = new ArrayList<>();
            if (chatSession.getChatHistoryJson() != null && !chatSession.getChatHistoryJson().equals("[]")) {
                chatHistory = objectMapper.readValue(chatSession.getChatHistoryJson(), new TypeReference<List<AiMessage>>(){});
            }

            chatHistory.add(new AiMessage("user", userPrompt));

            List<AiMessage> messagesToSend = new ArrayList<>();
            messagesToSend.add(new AiMessage("system", systemPrompt));

            int startIndex = Math.max(0, chatHistory.size() - 6);
            messagesToSend.addAll(chatHistory.subList(startIndex, chatHistory.size()));

            String aiAnswer = callModels(messagesToSend, 0.5, sessionId);
            if (aiAnswer != null) {
                chatHistory.add(new AiMessage("assistant", aiAnswer));
                chatSession.setChatHistoryJson(objectMapper.writeValueAsString(chatHistory));
                sessionRepository.save(chatSession);
                return aiAnswer;
            }
        } catch (Exception e) {
            System.err.println("⚠️ [AI][" + sessionId + "] Lỗi xử lý dữ liệu: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return "Lỗi khi xử lý dữ liệu hệ thống. Vui lòng thử lại sau.";
        }
        return "Hệ thống AI đang bận hoặc quá tải API. Vui lòng thử lại sau.";
    }

    /**
     * Gọi model MỘT LẦN, KHÔNG ký ức: không đọc, không ghi, không tạo {@code AiChatSession}.
     *
     * Dành cho các việc chạy nền có đủ dữ liệu ngay trong prompt (hiện là MedicalNewsTask tóm tắt
     * một bài báo cụ thể). Ở những việc đó, đi qua {@link #getConversationalResponse} là sai theo
     * hai hướng:
     *  - Nó phát lại 6 tin nhắn cũ của phiên: với việc tóm tắt bài A thì nội dung bài B hôm trước
     *    chỉ là nhiễu, tốn token, mà tệ nhất là mời model trộn số liệu của bài này sang bài kia.
     *  - Khi mọi model đều hỏng, nó trả về CÂU TIẾNG VIỆT "Hệ thống AI đang bận..." chứ không phải
     *    null. Chỗ gọi đem chuỗi đó đi JSON.parse thì nổ exception, và lý do thật (hết credit) bị
     *    che sau một lỗi parse trông như lỗi định dạng.
     *
     * @return nội dung model trả về, hoặc {@code null} nếu không model nào trả lời được.
     *
     * Không @Transactional — cùng lý do đã ghi ở chatWithMemory (gọi mạng giữa hàm).
     */
    public String getStatelessResponse(String systemPrompt, String userPrompt, String logTag) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", systemPrompt));
        messages.add(new AiMessage("user", userPrompt));
        return callModels(messages, 0.3, logTag);
    }

    /**
     * Lấy phiên chat, tạo mới nếu chưa có.
     *
     * `sessionCode` có ràng buộc UNIQUE, mà find-then-save thì KHÔNG nguyên tử: hai request cùng
     * `sessionId` (khách bấm Gửi 2 lần, hoặc mở 2 tab) cùng thấy rỗng, cùng insert, và
     * DataIntegrityViolationException bung ra NGOÀI khối try của hàm gọi -> HTTP 500.
     * Thua cuộc đua thì chỉ cần đọc lại bản ghi người kia vừa tạo.
     */
    private AiChatSession loadOrCreateSession(String sessionId) {
        return sessionRepository.findBySessionCode(sessionId).orElseGet(() -> {
            AiChatSession newSession = new AiChatSession();
            newSession.setSessionCode(sessionId);
            try {
                return sessionRepository.saveAndFlush(newSession);
            } catch (org.springframework.dao.DataIntegrityViolationException race) {
                return sessionRepository.findBySessionCode(sessionId).orElseThrow(() -> race);
            }
        });
    }

    /**
     * Mốc thời gian cho model. Không có khối này thì model KHÔNG hề biết hôm nay là ngày mấy,
     * nên "thứ ba" / "tuần sau" chỉ có thể đoán bừa ra một ngày sai.
     *
     * Trình duyệt vẫn tự quy tên thứ ra ngày (extractDateHint trong ai-chat.js) và ngày đó được
     * ƯU TIÊN hơn ngày model trả về — khối này chỉ đỡ cho những cách diễn đạt mà bộ parse bỏ sót.
     */
    private String buildTodayBlock() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return "\n=== HÔM NAY ===\n"
                + "Hôm nay là " + vietnameseDayName(today.getDayOfWeek()) + ", ngày " + today + " (YYYY-MM-DD).\n"
                + "Mọi cách nói tương đối ('thứ ba', 'tuần sau', 'ngày mai', 'cuối tuần') phải quy ra ngày "
                + "tuyệt đối theo mốc này trước khi ghi vào `booking_target.appointment_date`. "
                + "Tên thứ luôn hiểu là lần xuất hiện KẾ TIẾP kể từ hôm nay.\n";
    }

    private String vietnameseDayName(java.time.DayOfWeek day) {
        switch (day) {
            case MONDAY: return "Thứ Hai";
            case TUESDAY: return "Thứ Ba";
            case WEDNESDAY: return "Thứ Tư";
            case THURSDAY: return "Thứ Năm";
            case FRIDAY: return "Thứ Sáu";
            case SATURDAY: return "Thứ Bảy";
            default: return "Chủ Nhật";
        }
    }

    /**
     * CỐ Ý KHÔNG có @Transactional.
     *
     * Hàm này gọi OpenRouter ở giữa. Bọc cả hàm trong một transaction nghĩa là giữ một connection
     * HikariCP suốt thời gian chờ mạng (mặc định pool chỉ 10) — OpenRouter chậm và ~10 người chat
     * cùng lúc là cạn pool, kéo sập luôn trang chủ, đăng nhập, đặt lịch chứ không riêng chatbot.
     *
     * Bỏ transaction ở đây an toàn vì trình tự đã đúng sẵn: MỌI truy vấn nằm TRƯỚC lời gọi mạng,
     * sau lời gọi chỉ còn đúng một lệnh save. Mỗi repository call tự chạy transaction riêng và trả
     * connection ngay; `spring.jpa.open-in-view` (mặc định bật) giữ EntityManager cho cả request nên
     * việc đọc quan hệ lazy vẫn chạy. NẾU sau này tắt open-in-view thì phải đọc hết ra biến String
     * trong một method @Transactional riêng trước khi gọi API.
     *
     * Không có transaction bao ngoài còn giúp loadOrCreateSession bắt được va chạm khoá UNIQUE rồi
     * đọc lại — trong một transaction chung thì transaction đã bị đánh dấu rollback-only và lần đọc
     * lại đó cũng hỏng theo.
     */
    public String chatWithMemory(String sessionId, String userPrompt) {
        StringBuilder deptsInfo = new StringBuilder();
        try {
            List<Department> depts = departmentRepository.findAll();
            if (depts.isEmpty()) {
                deptsInfo.append("- (Hệ thống đang cập nhật danh sách chuyên khoa. Hãy khuyên bệnh nhân khám Tổng Quát).\n");
            } else {
                for (Department d : depts) {
                    String desc = (d.getDescription() != null && !d.getDescription().isEmpty())
                            ? d.getDescription()
                            : "Khám và điều trị các bệnh lý liên quan đến khoa này.";
                    deptsInfo.append("- ID: ").append(d.getId())
                            .append(" | Tên Khoa: ").append(d.getName())
                            .append(" | Chuyên trị: ").append(desc).append("\n");
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy danh sách Khoa: " + e.getMessage());
        }

        String finalSystemPrompt = PATIENT_BASE_PROMPT + deptsInfo.toString() + buildTodayBlock();

        AiChatSession chatSession = sessionRepository.findBySessionCode(sessionId).orElseGet(() -> {
            AiChatSession newSession = new AiChatSession();
            newSession.setSessionCode(sessionId);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                Object principal = auth.getPrincipal();
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                    userRepository.findByUsername(username).ifPresent(newSession::setUser);
                }
                else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                    String email = ((org.springframework.security.oauth2.core.user.OAuth2User) principal).getAttribute("email");
                    if (email != null) {
                        userRepository.findByEmail(email).ifPresent(newSession::setUser);
                    }
                }
                else {
                    String name = auth.getName();
                    userRepository.findByUsername(name).ifPresentOrElse(
                            newSession::setUser,
                            () -> userRepository.findByEmail(name).ifPresent(newSession::setUser)
                    );
                }
            }
            try {
                return sessionRepository.saveAndFlush(newSession);
            } catch (org.springframework.dao.DataIntegrityViolationException race) {
                // Hai request cùng sessionId chạy song song — xem chú thích ở loadOrCreateSession().
                return sessionRepository.findBySessionCode(sessionId).orElseThrow(() -> race);
            }
        });

        try {
            List<AiMessage> chatHistory = new ArrayList<>();
            if (chatSession.getChatHistoryJson() != null && !chatSession.getChatHistoryJson().equals("[]")) {
                chatHistory = objectMapper.readValue(chatSession.getChatHistoryJson(), new TypeReference<List<AiMessage>>(){});
            }

            chatHistory.add(new AiMessage("user", userPrompt));

            // Ký ức bền: moi lại `patient_summary` của lượt gần nhất.
            // `(?:\\.|[^"\\])*` chứ KHÔNG phải `[^"]*` — model rất hay sinh dấu nháy đã escape
            // ("đau bụng sau khi ăn \"bún riêu\""), và `[^"]*` dừng ngay ở dấu nháy đầu tiên nên
            // hồ sơ bệnh nhân bị cắt cụt giữa chừng.
            String persistentMemory = "";
            for (int i = chatHistory.size() - 1; i >= 0; i--) {
                AiMessage msg = chatHistory.get(i);
                if ("assistant".equals(msg.getRole()) && msg.getContent() != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("\"patient_summary\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                            .matcher(msg.getContent());
                    if (m.find()) {
                        persistentMemory = m.group(1)
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .replace("\\\\", "\\");
                        break;
                    }
                }
            }

            String dynamicSystemPrompt = finalSystemPrompt;
            if (!persistentMemory.isEmpty()) {
                dynamicSystemPrompt += "\n\n=== ⚠️ HỒ SƠ BỆNH NHÂN HIỆN TẠI (BẮT BUỘC GHI NHỚ) ===\n" + persistentMemory;
            }
            com.bookinghealthy.model.User currentUser = chatSession.getUser();
            if (currentUser != null) {
                java.util.Optional<com.bookinghealthy.model.Booking> lastBooking = bookingRepository.findFirstByUserIdAndStatusOrderByAppointmentDateDesc(currentUser.getId(), com.bookinghealthy.model.BookingStatus.COMPLETED);

                if (lastBooking.isPresent()) {
                    java.util.Optional<com.bookinghealthy.model.MedicalRecord> record = medicalRecordRepository.findByBookingId(lastBooking.get().getId());
                    if (record.isPresent()) {
                        com.bookinghealthy.model.MedicalRecord rec = record.get();
                        com.bookinghealthy.model.Doctor lastDoctor = lastBooking.get().getDoctor();
                        String doctorName = "Bác sĩ";
                        if (lastDoctor != null && lastDoctor.getUser() != null) {
                            doctorName = lastDoctor.getUser().getFullName();
                        }
                        // Bác sĩ có thể đã bị xoá hoặc chưa gán khoa. Dereference thẳng
                        // getDoctor().getDepartment().getName() ở đây từng ném NPE, và vì cả hàm nằm
                        // trong try/catch nên MỌI lượt chat của bệnh nhân đó chỉ nhận lại đúng câu
                        // "Hệ thống bận. Thử lại sau." — mãi mãi, kể cả khi API AI hoàn toàn khoẻ.
                        String lastDeptName = (lastDoctor != null && lastDoctor.getDepartment() != null
                                && lastDoctor.getDepartment().getName() != null)
                                ? lastDoctor.getDepartment().getName()
                                : "không rõ";
                        dynamicSystemPrompt += "\n\n=== ⚠️ LỊCH SỬ BỆNH ÁN TRONG QUÁ KHỨ (CHỈ DÙNG ĐỂ TRẢ LỜI KHI KHÁCH HỎI BỆNH CŨ) ===\n" +
                                "- Lần khám gần nhất: " + lastBooking.get().getAppointmentDate() + "\n" +
                                "- Bác sĩ khám: " + doctorName + " (Khoa: " + lastDeptName + ")\n" +
                                "- CHẨN ĐOÁN CỦA BÁC SĨ (BỆNH LÝ): " + (rec.getDiagnosis() != null ? rec.getDiagnosis() : "Không có") + "\n" +
                                "- Triệu chứng lúc đó: " + (rec.getSymptoms() != null ? rec.getSymptoms() : "Không có") + "\n" +
                                "- Lời dặn / Đơn thuốc: " + (rec.getDoctorNotes() != null ? rec.getDoctorNotes() : "Không có") + "\n" +
                                "👉 LỆNH TỐI THƯỢNG: Nếu bệnh nhân hỏi về lần khám trước (Ví dụ: 'lần trước tôi bị sao', 'bác sĩ bảo tôi bị gì'), BẠN BẮT BUỘC PHẢI DÙNG DỮ LIỆU Ở TRÊN ĐỂ TRẢ LỜI CHI TIẾT NGAY LẬP TỨC. Tuyệt đối không được bảo là không nhớ. Luôn xưng hô là 'Em' và gọi bệnh nhân là 'Anh/Chị'.";
                    }
                }

                // === HỒ SƠ BỆNH ÁN TỪ NƠI KHÁC (bệnh nhân tự tải lên) ===
                // Bản tóm tắt đã dựng MỘT LẦN lúc bệnh nhân tải tệp lên và lưu trong DB, nên khối
                // này KHÔNG phát sinh thêm lượt gọi AI nào cho mỗi lượt chat. Chỉ lấy hồ sơ đã
                // phân tích xong (DONE) và tối đa 3: prompt hệ thống đã dài, nhồi thêm sẽ làm
                // loãng đúng phần triệu chứng khách vừa kể.
                try {
                    java.util.List<com.bookinghealthy.model.ExternalMedicalRecord> extDocs =
                            externalMedicalRecordRepository.findTop3ByUserIdAndAiStatusOrderByCreatedAtDesc(
                                    currentUser.getId(), com.bookinghealthy.model.ExternalMedicalRecord.AI_DONE);
                    if (!extDocs.isEmpty()) {
                        StringBuilder docBlock = new StringBuilder(
                                "\n\n=== ⚠️ HỒ SƠ BỆNH ÁN TỪ NƠI KHÁC (BỆNH NHÂN TỰ TẢI LÊN) ===\n");
                        for (com.bookinghealthy.model.ExternalMedicalRecord doc : extDocs) {
                            docBlock.append("- ").append(doc.getTitle()).append(": ")
                                    .append(doc.getAiSummary()).append("\n");
                        }
                        docBlock.append("👉 ĐÂY LÀ GIẤY TỜ DO BỆNH NHÂN TỰ TẢI LÊN, hệ thống đọc tự động nên CÓ THỂ SAI. ")
                                .append("Chỉ dùng làm TIỀN SỬ THAM KHẢO để hỏi thăm và chọn chuyên khoa. ")
                                .append("TUYỆT ĐỐI KHÔNG trình bày nội dung này như chẩn đoán của chính bạn, ")
                                .append("và vẫn phải giữ câu cảnh báo y khoa ở mục 3.\n");
                        dynamicSystemPrompt += docBlock.toString();
                    }
                } catch (Exception e) {
                    // Thiếu khối tiền sử thì trợ lý vẫn tư vấn được theo triệu chứng khách kể;
                    // để exception thoát ra đây là hỏng CẢ lượt chat vì một dữ liệu phụ trợ.
                    System.err.println("⚠️ [AI][" + sessionId + "] Không nạp được hồ sơ ngoại viện: " + e.getMessage());
                }
            }

            List<AiMessage> messagesToSend = new ArrayList<>();
            messagesToSend.add(new AiMessage("system", dynamicSystemPrompt));
            int startIndex = Math.max(0, chatHistory.size() - 6);
            messagesToSend.addAll(chatHistory.subList(startIndex, chatHistory.size() - 1));
            String enforcedPrompt = userPrompt + "\n\n(Lệnh hệ thống ngầm: Vẫn giữ nguyên tư duy phân luồng hiện tại, nhưng BẮT BUỘC JSON trả về phải có mảng `suggested_prompts` chứa 3 câu gợi ý ngắn gọn cho bệnh nhân).";
            messagesToSend.add(new AiMessage("user", enforcedPrompt));

            String aiAnswer = callModels(messagesToSend, 0.2, sessionId);
            if (aiAnswer != null) {
                chatHistory.add(new AiMessage("assistant", aiAnswer));
                chatSession.setChatHistoryJson(objectMapper.writeValueAsString(chatHistory));
                sessionRepository.save(chatSession);
                return aiAnswer;
            }

            // Không model nào trả lời. Vẫn LƯU câu vừa hỏi của khách, nếu không thì lượt đó bốc hơi
            // khỏi ký ức và khách hỏi lại "em vừa nói gì" thì trợ lý không biết.
            chatSession.setChatHistoryJson(objectMapper.writeValueAsString(chatHistory));
            sessionRepository.save(chatSession);
        } catch (Exception e) {
            System.err.println("⚠️ [AI][" + sessionId + "] Lỗi xử lý hội thoại: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        return "Hệ thống bận. Thử lại sau.";
    }

    /**
     * Chèn một ghi chú vào lịch sử hội thoại dưới vai "assistant", để lượt sau model còn nhớ.
     *
     * <p><b>Vì sao bắt buộc phải có:</b> ảnh triệu chứng cố ý KHÔNG được lưu ở đâu cả — không tệp
     * trên đĩa, không dòng DB — nên không có khối tiêm ngữ cảnh nào như hồ sơ giấy tờ. Thiếu ghi
     * chú này thì ngay lượt sau khách hỏi "nó có nguy hiểm không?" model đã không còn biết vừa
     * nhìn thấy gì và sẽ hỏi lại từ đầu.
     *
     * <p>Cửa sổ phát lại là 6 tin nhắn cuối, nên ghi chú tự rụng sau vài lượt — đúng ý: đây là
     * ngữ cảnh nhất thời của một tấm ảnh đã bị xoá, không phải tiền sử bệnh.
     *
     * <p>Tự nuốt lỗi vào log: hỏng chỗ này thì cùng lắm là model quên, không được phép làm hỏng
     * lượt trả kết quả phân tích cho khách.
     */
    @Transactional
    public void appendAssistantNote(String sessionId, String note) {
        if (sessionId == null || sessionId.isBlank() || note == null || note.isBlank()) {
            return;
        }
        try {
            AiChatSession session = loadOrCreateSession(sessionId);
            List<AiMessage> history = new ArrayList<>();
            if (session.getChatHistoryJson() != null && !session.getChatHistoryJson().isBlank()) {
                history = objectMapper.readValue(session.getChatHistoryJson(),
                        new TypeReference<List<AiMessage>>() {});
            }
            history.add(new AiMessage("assistant", note));
            session.setChatHistoryJson(objectMapper.writeValueAsString(history));
            sessionRepository.save(session);
        } catch (Exception e) {
            System.err.println("\u26a0\ufe0f [AI][" + sessionId + "] Không ghi được ghi chú vào lịch sử: "
                    + e.getMessage());
        }
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldGuestSessions() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        sessionRepository.deleteGuestSessionsOlderThan(cutoffDate);
        System.out.println("🧹 Đã dọn dẹp xong lịch sử chat rác của khách vãng lai.");
    }

    @Transactional
    public void clearMemory(String sessionId) {
        sessionRepository.findBySessionCode(sessionId).ifPresent(session -> {
            sessionRepository.delete(session);
        });
    }
}