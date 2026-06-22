package com.bookinghealthy.controller;

import com.bookinghealthy.dto.ai.ChatRequest;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.service.DoctorAssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/doctor/assistant")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorAssistantController {

    @Autowired
    private DoctorAssistantService doctorAssistantService;

    // API cũ: Lấy raw data (Vẫn giữ nguyên, không ảnh hưởng hệ thống)
    @GetMapping("/schedule")
    public ResponseEntity<List<Booking>> getDailySchedule() {
        List<Booking> schedule = doctorAssistantService.getDailyScheduleForCurrentUser();
        return ResponseEntity.ok(schedule);
    }

    // API mới: Trò chuyện với AI (Chat interface)
    @PostMapping("/chat")
    public ResponseEntity<?> chatWithAssistant(@RequestBody ChatRequest chatRequest) {
        try {
            if (chatRequest.getPrompt() == null || chatRequest.getPrompt().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Prompt cannot be empty"));
            }

            String aiResponse = doctorAssistantService.handleChat(chatRequest.getPrompt());
            
            // Trả về JSON để Frontend dễ xử lý
            return ResponseEntity.ok(Map.of("ai_reply", aiResponse));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal Server Error: " + e.getMessage()));
        }
    }
}