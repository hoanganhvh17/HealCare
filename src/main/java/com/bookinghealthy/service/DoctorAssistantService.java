package com.bookinghealthy.service;

import com.bookinghealthy.dto.ai.AiMessage;
import com.bookinghealthy.dto.ai.AiRequest;
import com.bookinghealthy.dto.ai.AiResponse;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DoctorAssistantService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    // === AI Integration Dependencies (Reusing from AiService) ===
    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === Base Prompt for Doctor's Assistant ===
    private static final String BASE_PROMPT =
            "Bạn là một Trợ lý AI cá nhân, chuyên nghiệp dành cho bác sĩ của hệ thống y tế MediTrust. " +
            "Nhiệm vụ của bạn là hỗ trợ bác sĩ một cách nhanh chóng và chính xác. " +
            "Bạn phải luôn trả lời bằng giọng văn trang trọng, lịch sự, xưng là 'em' và gọi người dùng là 'bác sĩ'.\n\n" +
            "=== QUY TẮC NHẬN DIỆN Ý ĐỊNH (INTENT RECOGNITION) ===\n" +
            "1. NẾU bác sĩ hỏi về lịch làm việc, lịch hẹn, các ca khám, bệnh nhân trong ngày hôm nay " +
            "(ví dụ: 'hôm nay có ca nào không', 'xem lịch hôm nay', 'lịch làm việc của tôi', 'hôm nay có bao nhiêu bệnh nhân?'), " +
            "BẮT BUỘC phải trả về một chuỗi JSON duy nhất với định dạng: {\"intent\": \"GET_SCHEDULE\"}. " +
            "TUYỆT ĐỐI KHÔNG trả lời bằng văn bản thông thường trong trường hợp này.\n\n" +
            "2. NẾU là câu chào hỏi hoặc các câu hỏi thông thường khác không liên quan đến lịch làm việc, " +
            "hãy trả lời một cách tự nhiên, thân thiện và chuyên nghiệp.\n\n" +
            "Bây giờ, hãy phân tích câu nói từ bác sĩ.";


    /**
     * Helper method to get the current authenticated User
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return null;
        }

        Object principal = auth.getPrincipal();
        String identifier = null;

        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
            identifier = ((org.springframework.security.oauth2.core.user.OAuth2User) principal).getAttribute("email");
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            identifier = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        } else {
            identifier = auth.getName();
        }

        if (identifier == null) return null;

        String finalIdentifier = identifier;
        return userRepository.findByUsername(finalIdentifier)
                .orElseGet(() -> userRepository.findByEmail(finalIdentifier).orElse(null));
    }

    /**
     * This is the core logic method that was created in Step 1.
     * It remains untouched and functions independently.
     */
    @Transactional(readOnly = true)
    public List<Booking> getDailyScheduleForCurrentUser() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return Collections.emptyList();
        }

        Doctor doctor = doctorRepository.findByUser(currentUser);
        if (doctor == null) {
            return Collections.emptyList();
        }

        return bookingRepository.findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(doctor.getId(), LocalDate.now());
    }

    /**
     * This is the new AI orchestration method for Step 2.
     * It handles natural language prompts from the doctor.
     */
    @Transactional(readOnly = true)
    public String handleChat(String doctorPrompt) {
        // 1. Call LLM to understand the doctor's intent
        String intent = getIntentFromLLM(doctorPrompt);

        // 2. Orchestrate based on the intent
        if ("GET_SCHEDULE".equals(intent)) {
            // If intent is to get schedule, call our internal logic
            List<Booking> schedule = getDailyScheduleForCurrentUser();
            return formatScheduleResponse(schedule);
        } else {
            // For any other case (including general conversation), return the AI's direct response
            return intent; // In this case, 'intent' holds the conversational response from the LLM
        }
    }

    private String getIntentFromLLM(String doctorPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<AiMessage> messages = List.of(
                new AiMessage("system", BASE_PROMPT),
                new AiMessage("user", doctorPrompt)
        );

        AiRequest request = new AiRequest();
        request.setModel("openai/gpt-4o-mini"); // Using a fast and smart model for intent detection
        request.setMessages(messages);
        request.setTemperature(0.0); // Set to 0 for deterministic intent classification

        try {
            AiResponse response = restTemplate.postForObject(apiUrl, new HttpEntity<>(request, headers), AiResponse.class);
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String rawContent = response.getChoices().get(0).getMessage().getContent();

                // Try to parse as JSON to check for intent
                try {
                    JsonNode rootNode = objectMapper.readTree(rawContent);
                    if (rootNode.has("intent")) {
                        return rootNode.get("intent").asText();
                    }
                } catch (Exception e) {
                    // If it's not a valid JSON, it's a conversational response. Return it directly.
                    return rawContent;
                }
                return rawContent; // Fallback
            }
        } catch (Exception e) {
            System.err.println("Error calling AI API for doctor assistant: " + e.getMessage());
            return "Dạ, hệ thống AI đang có chút gián đoạn, bác sĩ vui lòng thử lại sau ạ.";
        }
        return "Dạ, em chưa hiểu ý của bác sĩ. Bác sĩ có thể nói rõ hơn được không ạ?";
    }

    private String formatScheduleResponse(List<Booking> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return "Dạ, hôm nay bác sĩ không có lịch hẹn nào ạ. Chúc bác sĩ một ngày làm việc hiệu quả!";
        }

        String scheduleDetails = schedule.stream()
                .map(booking -> String.format("- **%s**: Bệnh nhân **%s** (Lý do: %s)",
                        booking.getAppointmentTime(), // It's already a String
                        booking.getUser().getFullName(),
                        booking.getNotes())) // The field is 'notes', not 'reason'
                .collect(Collectors.joining("\n"));

        return String.format("Dạ, hôm nay bác sĩ có **%d** ca hẹn ạ:\n%s", schedule.size(), scheduleDetails);
    }
}