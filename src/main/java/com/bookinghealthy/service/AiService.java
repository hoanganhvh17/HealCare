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
import java.util.List;

@Service
public class AiService {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Autowired private RestTemplate restTemplate;
    @Autowired private AiChatSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private com.bookinghealthy.repository.BookingRepository bookingRepository;
    @Autowired private com.bookinghealthy.repository.MedicalRecordRepository medicalRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PATIENT_BASE_PROMPT =
            "Bạn là Chuyên gia Phân luồng Bệnh nhân (Triage AI Agent) của hệ thống y tế MediTrust.\n" +
            "MỤC TIÊU CỦA BẠN: Lắng nghe triệu chứng, an ủi bệnh nhân và ĐIỀU HƯỚNG họ đến đúng Chuyên khoa phù hợp nhất.\n\n" +
            "BẮT BUỘC TỐI THƯỢNG: Trả lời của bạn phải là MỘT CHUỖI JSON HỢP LỆ. KHÔNG ĐƯỢC CHỨA VĂN BẢN NÀO NGOÀI JSON.\n\n" +
            // ... (rest of the patient prompt)
            "=== 7. DANH SÁCH CHUYÊN KHOA HIỆN CÓ CỦA MEDITRUST ===\n";

    @Transactional
    public String getConversationalResponse(String systemPrompt, String userPrompt, String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "http://localhost:8080");

        AiChatSession chatSession = sessionRepository.findBySessionCode(sessionId).orElseGet(() -> {
            AiChatSession newSession = new AiChatSession();
            newSession.setSessionCode(sessionId);
            return sessionRepository.save(newSession);
        });

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

            String[] fallbackModels = { "google/gemini-2.0-flash-exp:free", "openrouter/free", "openai/gpt-4o-mini" };
            
            for (String modelName : fallbackModels) {
                try {
                    AiRequest request = new AiRequest();
                    request.setModel(modelName);
                    request.setMessages(messagesToSend);
                    request.setTemperature(0.5);

                    AiResponse response = restTemplate.postForObject(apiUrl, new HttpEntity<>(request, headers), AiResponse.class);

                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        String aiAnswer = response.getChoices().get(0).getMessage().getContent();

                        chatHistory.add(new AiMessage("assistant", aiAnswer));
                        chatSession.setChatHistoryJson(objectMapper.writeValueAsString(chatHistory));
                        sessionRepository.save(chatSession);

                        return aiAnswer;
                    }
                } catch (Exception modelEx) {
                    System.err.println("---");
                    System.err.println("⚠️ Lỗi khi gọi model: " + modelName);
                    modelEx.printStackTrace(); // In chi tiết lỗi ra console
                    System.err.println("---");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Lỗi khi xử lý dữ liệu hệ thống. Vui lòng thử lại sau.";
        }
        return "Hệ thống AI đang bận hoặc quá tải API. Vui lòng thử lại sau.";
    }

    @Transactional
    public String chatWithMemory(String sessionId, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "http://localhost:8080");

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

        String finalSystemPrompt = PATIENT_BASE_PROMPT + deptsInfo.toString();

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
            return sessionRepository.save(newSession);
        });

        try {
            List<AiMessage> chatHistory = new ArrayList<>();
            if (chatSession.getChatHistoryJson() != null && !chatSession.getChatHistoryJson().equals("[]")) {
                chatHistory = objectMapper.readValue(chatSession.getChatHistoryJson(), new TypeReference<List<AiMessage>>(){});
            }

            chatHistory.add(new AiMessage("user", userPrompt));

            String persistentMemory = "";
            for (int i = chatHistory.size() - 1; i >= 0; i--) {
                AiMessage msg = chatHistory.get(i);
                if ("assistant".equals(msg.getRole())) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"patient_summary\"\\s*:\\s*\"([^\"]*)\"").matcher(msg.getContent());
                    if (m.find()) {
                        persistentMemory = m.group(1);
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
                        String doctorName = "Bác sĩ";
                        if (lastBooking.get().getDoctor() != null && lastBooking.get().getDoctor().getUser() != null) {
                            doctorName = lastBooking.get().getDoctor().getUser().getFullName();
                        }
                        dynamicSystemPrompt += "\n\n=== ⚠️ LỊCH SỬ BỆNH ÁN TRONG QUÁ KHỨ (CHỈ DÙNG ĐỂ TRẢ LỜI KHI KHÁCH HỎI BỆNH CŨ) ===\n" +
                                "- Lần khám gần nhất: " + lastBooking.get().getAppointmentDate() + "\n" +
                                "- Bác sĩ khám: " + doctorName + " (Khoa: " + lastBooking.get().getDoctor().getDepartment().getName() + ")\n" +
                                "- CHẨN ĐOÁN CỦA BÁC SĨ (BỆNH LÝ): " + (rec.getDiagnosis() != null ? rec.getDiagnosis() : "Không có") + "\n" +
                                "- Triệu chứng lúc đó: " + (rec.getSymptoms() != null ? rec.getSymptoms() : "Không có") + "\n" +
                                "- Lời dặn / Đơn thuốc: " + (rec.getDoctorNotes() != null ? rec.getDoctorNotes() : "Không có") + "\n" +
                                "👉 LỆNH TỐI THƯỢNG: Nếu bệnh nhân hỏi về lần khám trước (Ví dụ: 'lần trước tôi bị sao', 'bác sĩ bảo tôi bị gì'), BẠN BẮT BUỘC PHẢI DÙNG DỮ LIỆU Ở TRÊN ĐỂ TRẢ LỜI CHI TIẾT NGAY LẬP TỨC. Tuyệt đối không được bảo là không nhớ. Luôn xưng hô là 'Em' và gọi bệnh nhân là 'Anh/Chị'.";
                    }
                }
            }

            List<AiMessage> messagesToSend = new ArrayList<>();
            messagesToSend.add(new AiMessage("system", dynamicSystemPrompt));
            int startIndex = Math.max(0, chatHistory.size() - 6);
            messagesToSend.addAll(chatHistory.subList(startIndex, chatHistory.size() - 1));
            String enforcedPrompt = userPrompt + "\n\n(Lệnh hệ thống ngầm: Vẫn giữ nguyên tư duy phân luồng hiện tại, nhưng BẮT BUỘC JSON trả về phải có mảng `suggested_prompts` chứa 3 câu gợi ý ngắn gọn cho bệnh nhân).";
            messagesToSend.add(new AiMessage("user", enforcedPrompt));

            String[] fallbackModels = { "google/gemini-2.0-flash-exp:free", "openrouter/free", "openai/gpt-4o-mini" };
            for (String modelName : fallbackModels) {
                try {
                    AiRequest request = new AiRequest();
                    request.setModel(modelName);
                    request.setMessages(messagesToSend);
                    request.setTemperature(0.2);

                    AiResponse response = restTemplate.postForObject(apiUrl, new HttpEntity<>(request, headers), AiResponse.class);

                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        String aiAnswer = response.getChoices().get(0).getMessage().getContent();
                        chatHistory.add(new AiMessage("assistant", aiAnswer));
                        chatSession.setChatHistoryJson(objectMapper.writeValueAsString(chatHistory));
                        sessionRepository.save(chatSession);
                        return aiAnswer;
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Lỗi model: " + modelName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Hệ thống bận. Thử lại sau.";
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