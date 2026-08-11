package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.DoctorDTO;
import com.bookinghealthy.dto.ai.ChatRequest;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class AiController {

    @Autowired
    private AiService aiService;

    // === INJECT THÊM DOCTOR SERVICE ĐỂ LẤY DỮ LIỆU ===
    @Autowired
    private DoctorService doctorService;

    @Autowired private com.bookinghealthy.repository.AiChatSessionRepository sessionRepository;
    @Autowired private com.bookinghealthy.repository.UserRepository userRepository;
    @Autowired private com.bookinghealthy.repository.BookingRepository bookingRepository; // INJECT THÊM REPO NÀY
    // THÊM REPOSITORY NÀY LÊN ĐẦU FILE CÙNG CÁC @Autowired KHÁC
    @Autowired private com.bookinghealthy.repository.DoctorBlockTimeRepository doctorBlockTimeRepository;
    // === THÊM DÒNG NÀY VÀO ===
    @Autowired private com.bookinghealthy.repository.MedicalRecordRepository medicalRecordRepository;
    // Dùng cho endpoint "giải thích hồ sơ bệnh án" — đọc thẳng đơn thuốc có cấu trúc từ DB.
    @Autowired private com.bookinghealthy.repository.PrescriptionItemRepository prescriptionItemRepository;

    /**
     * Ca khám bác sĩ đã đăng ký (bảng Schedule). BẮT BUỘC đi qua BookingService — đó là đường
     * duy nhất tới {@code ScheduleRepository.findEffective}, tức là lịch có hiệu lực của ĐÚNG TUẦN
     * chứa ngày đang xét. Tự dựng lại phép so ca ở đây sẽ thành bản sao thứ tư của cùng một luật,
     * và chính kiểu sao chép đó đã đẻ ra bug "trợ lý mời giờ bác sĩ đang nghỉ".
     */
    @Autowired private BookingService bookingService;


    // =========================================================================
    // CƠ CHẾ SOFT-LOCK (MÔ PHỎNG REDIS TTL)
    //
    // Giữ tạm một khung giờ cho phiên chat vừa CHỐT nó, để hai khách không cùng được điều hướng
    // vào đúng một chỗ. Đây chỉ là lớp giảm va chạm ở tầng gợi ý — chỗ chống trùng thật sự vẫn là
    // BookingServiceImpl.reserve() lúc ghi booking.
    //
    // CHỈ ĐƯỢC ĐẶT KHOÁ KHI KHÁCH THẬT SỰ CHỐT (POST /hold-slot). Trước đây chỉ cần MỞ chat xem
    // danh sách bác sĩ là GET /doctors/department/{id} khoá luôn 12 khung giờ trong 3 phút, và
    // người vào sau bị GIẤU sạch những khung đó — hai khách chat cùng khoa cùng lúc thấy hai lịch
    // khác nhau, kèm câu "đang có người khác giữ chỗ" hoàn toàn sai sự thật (thực ra người kia chỉ
    // đang xem). Endpoint đó lại là permitAll, nên một vòng lặp đổi sessionId là khoá sạch lịch
    // toàn hệ thống.
    //
    // Lưu ý: map này nằm trong bộ nhớ của MỘT tiến trình — chạy nhiều instance là mất tác dụng.
    // =========================================================================
    static class SlotLock {
        String sessionId;
        long expireAtMillis;
        public SlotLock(String sessionId, long expireAtMillis) {
            this.sessionId = sessionId;
            this.expireAtMillis = expireAtMillis;
        }
    }

    // Bộ nhớ đệm lưu trữ các khóa (Khóa tự động mất sau 3 phút)
    private final java.util.concurrent.ConcurrentHashMap<String, SlotLock> softLockCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long SOFT_LOCK_TTL_MILLIS = 180_000; // 3 phút

    // Job tự động dọn dẹp các Lock đã hết hạn (Chạy mỗi 1 phút)
    @Scheduled(fixedRate = 60000)
    public void cleanUpExpiredLocks() {
        long now = System.currentTimeMillis();
        softLockCache.entrySet().removeIf(entry -> now > entry.getValue().expireAtMillis);
    }

    private String lockKey(Long doctorId, java.time.LocalDate date, String slotStr) {
        return doctorId + "_" + date.toString() + "_" + slotStr;
    }

    /** Khung giờ này có đang bị PHIÊN KHÁC giữ không. Chỉ đọc, không ghi. */
    private boolean isHeldByAnotherSession(Long doctorId, java.time.LocalDate date, String slotStr,
                                           String sessionId, long nowMillis) {
        SlotLock lock = softLockCache.get(lockKey(doctorId, date, slotStr));
        if (lock == null || nowMillis > lock.expireAtMillis) return false;
        return sessionId != null && !sessionId.equals(lock.sessionId);
    }

    /**
     * Giữ tạm một khung giờ cho phiên chat vừa chốt nó. Gọi từ finishBookingHandoff ở trình duyệt,
     * tức là chỉ khi khách THẬT SỰ được điều hướng sang trang đặt lịch.
     *
     * Dùng {@code compute()} chứ không get-rồi-put: hai request đồng thời cùng khung giờ đều lọt qua
     * bước kiểm rồi ghi đè nhau, và cả hai khách đều tin mình đang giữ chỗ.
     */
    @PostMapping("/hold-slot")
    public ResponseEntity<Map<String, Object>> holdSlot(@RequestParam Long doctorId,
                                                        @RequestParam String date,
                                                        @RequestParam String slot,
                                                        @RequestParam(required = false) String sessionId) {
        Map<String, Object> result = new HashMap<>();
        java.time.LocalDate targetDate;
        try {
            targetDate = java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            result.put("held", false);
            return ResponseEntity.ok(result);
        }

        final long now = System.currentTimeMillis();
        SlotLock winner = softLockCache.compute(lockKey(doctorId, targetDate, slot), (key, current) -> {
            boolean freeToTake = current == null
                    || now > current.expireAtMillis
                    || sessionId.equals(current.sessionId);
            return freeToTake ? new SlotLock(sessionId, now + SOFT_LOCK_TTL_MILLIS) : current;
        });

        result.put("held", winner != null && sessionId.equals(winner.sessionId));
        return ResponseEntity.ok(result);
    }

    // --------------------------------------------------------
    // 1. CÁC API DÀNH CHO XỬ LÝ NGÔN NGỮ (LLM)
    // --------------------------------------------------------

    /** Prompt dài hơn mức này chắc chắn không phải câu hỏi thật — cắt để khỏi tốn token và phình chatHistoryJson. */
    private static final int MAX_PROMPT_CHARS = 2000;

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askAi(@RequestBody(required = false) ChatRequest request) {
        Map<String, String> result = new HashMap<>();

        String prompt = (request == null || request.getPrompt() == null) ? "" : request.getPrompt().trim();
        if (prompt.isEmpty()) {
            result.put("answer", "Dạ anh/chị nhắn giúp em nội dung cần hỏi với ạ.");
            return ResponseEntity.badRequest().body(result);
        }
        if (prompt.length() > MAX_PROMPT_CHARS) {
            prompt = prompt.substring(0, MAX_PROMPT_CHARS);
        }

        // sessionCode có ràng buộc NOT NULL: nhận null từ client là insert lỗi -> HTTP 500.
        // Tự sinh và trả về để trình duyệt dùng tiếp cho các lượt sau.
        String sessionId = (request == null || request.getSessionId() == null
                || request.getSessionId().trim().isEmpty())
                ? "session_" + java.util.UUID.randomUUID()
                : request.getSessionId().trim();

        result.put("answer", aiService.chatWithMemory(sessionId, prompt));
        result.put("sessionId", sessionId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/clear/{sessionId}")
    public ResponseEntity<String> clearChat(@PathVariable String sessionId) {
        aiService.clearMemory(sessionId);
        return ResponseEntity.ok("Đã xóa lịch sử chat của phiên: " + sessionId);
    }

    // =========================================================================
    // GIẢI THÍCH HỒ SƠ BỆNH ÁN CHO BỆNH NHÂN
    //
    // CỐ Ý đứng ngoài chatWithMemory/PATIENT_BASE_PROMPT: prompt đó ép JSON 9 key cho luồng
    // tam giác, không phải chỗ để nhét ngữ cảnh bệnh án. Dùng getConversationalResponse
    // (đúng cách AdminAiController đang làm) — tự soạn system prompt riêng, trả văn xuôi.
    //
    // Đọc dữ liệu THẲNG TỪ DB (không cào DOM #pdf-content như bên bác sĩ): AiController đã
    // có sẵn medicalRecordRepository + bookingRepository, và dữ liệu gốc đáng tin hơn hẳn text
    // hiển thị. Endpoint này yêu cầu đăng nhập — xem SecurityConfig khối 0
    // (/api/chat/medical-record/** phải đứng TRÊN /api/chat/**.permitAll()).
    // =========================================================================

    private static final String RECORD_EXPLAIN_PROMPT_TEMPLATE =
            "Bạn là trợ lý AI của phòng khám NNL Hospital, đang giúp một bệnh nhân hiểu hồ sơ bệnh án của chính họ. " +
            "Luôn xưng là 'em' và gọi bệnh nhân là 'anh/chị'.\n\n" +
            "Dưới đây là NỘI DUNG HỒ SƠ BỆNH ÁN (trích từ hệ thống, chỉ đúng bệnh nhân đang hỏi mới xem được):\n\n" +
            "%s\n\n" +
            "--- QUY TẮC TRẢ LỜI ---\n" +
            "1. Giải thích bằng ngôn ngữ ĐƠN GIẢN, DỄ HIỂU — mọi thuật ngữ y khoa (tên bệnh, tên thuốc, chỉ số) phải kèm giải thích ngắn gọn, không dùng nguyên xi từ chuyên môn mà không diễn giải.\n" +
            "2. Chỉ dựa vào đúng nội dung hồ sơ ở trên, không suy diễn thêm chẩn đoán hay đơn thuốc khác.\n" +
            "3. Đây KHÔNG phải yêu cầu đặt lịch. TUYỆT ĐỐI không chủ động đề nghị đặt lịch tái khám hay mời đặt lịch mới, kể cả khi hồ sơ có nhắc tái khám — chỉ trả lời nếu bệnh nhân chủ động hỏi về việc đặt lịch.\n" +
            "4. Nếu câu hỏi vượt ngoài phạm vi hồ sơ (ví dụ hỏi bệnh khác), trả lời ngắn gọn là hồ sơ này không có thông tin đó.\n\n" +
            "Bây giờ, hãy trả lời câu hỏi của anh/chị.";

    @PostMapping("/medical-record/{bookingId}/explain")
    public ResponseEntity<Map<String, String>> explainMedicalRecord(
            @PathVariable Long bookingId, @RequestBody(required = false) ChatRequest request) {

        java.util.Optional<com.bookinghealthy.model.User> currentUserOpt =
                resolveCurrentUser(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("answer", "Vui lòng đăng nhập để dùng tính năng này."));
        }

        java.util.Optional<com.bookinghealthy.model.Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("answer", "Không tìm thấy lịch hẹn."));
        }

        // Đúng check bảo mật UserMedicalRecordController.viewMedicalRecord đang dùng: chỉ chủ
        // của lịch hẹn mới được xem/hỏi về hồ sơ bệnh án của lịch hẹn đó.
        com.bookinghealthy.model.Booking booking = bookingOpt.get();
        if (!booking.getUser().getId().equals(currentUserOpt.get().getId())) {
            return ResponseEntity.status(403).body(Map.of("answer", "Bạn không có quyền xem hồ sơ bệnh án này."));
        }

        com.bookinghealthy.model.MedicalRecord record = medicalRecordRepository.findByBookingId(bookingId).orElse(null);
        if (record == null) {
            return ResponseEntity.status(404).body(Map.of("answer", "Lịch hẹn này chưa có hồ sơ bệnh án."));
        }

        String recordText = formatRecordForAi(record);
        String systemPrompt = String.format(RECORD_EXPLAIN_PROMPT_TEMPLATE, recordText);

        String question = (request == null || request.getPrompt() == null || request.getPrompt().isBlank())
                ? "Hãy giải thích chẩn đoán và đơn thuốc trong hồ sơ này giúp tôi bằng ngôn ngữ dễ hiểu."
                : request.getPrompt().trim();
        if (question.length() > MAX_PROMPT_CHARS) question = question.substring(0, MAX_PROMPT_CHARS);

        // Mỗi bệnh án một phiên riêng: bookingId vốn đã là khoá duy nhất, không cần ghép userId.
        String answer = aiService.getConversationalResponse(systemPrompt, question, "record_" + bookingId);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    /** Chuỗi ngữ cảnh cho AI — ưu tiên đơn thuốc có cấu trúc, chỉ dùng text tự do khi bảng rỗng. */
    private String formatRecordForAi(com.bookinghealthy.model.MedicalRecord record) {
        StringBuilder sb = new StringBuilder();
        if (record.getDiagnosisCode() != null && !record.getDiagnosisCode().isBlank()) {
            sb.append("Mã chẩn đoán (ICD-10): ").append(record.getDiagnosisCode()).append("\n");
        }
        sb.append("Chẩn đoán: ").append(nullToEmpty(record.getDiagnosis())).append("\n");
        sb.append("Triệu chứng: ").append(nullToEmpty(record.getSymptoms())).append("\n");

        java.util.List<com.bookinghealthy.model.PrescriptionItem> items =
                prescriptionItemRepository.findByMedicalRecordId(record.getId());
        if (!items.isEmpty()) {
            sb.append("Đơn thuốc:\n");
            for (com.bookinghealthy.model.PrescriptionItem item : items) {
                sb.append("- ").append(item.getMedicineName());
                if (item.getDosage() != null) sb.append(" (").append(item.getDosage()).append(")");
                if (item.getQuantity() != null) sb.append(", số lượng ").append(item.getQuantity());
                if (item.getUnit() != null) sb.append(" ").append(item.getUnit());
                if (item.getInstructions() != null && !item.getInstructions().isBlank()) {
                    sb.append(" — cách dùng: ").append(item.getInstructions());
                }
                sb.append("\n");
            }
        } else {
            sb.append("Đơn thuốc: ").append(nullToEmpty(record.getPrescription())).append("\n");
        }

        sb.append("Lời dặn của bác sĩ: ").append(nullToEmpty(record.getDoctorNotes())).append("\n");
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return (s == null || s.isBlank()) ? "(không có)" : s;
    }

    /** Cùng logic UserDetails/OAuth2User/fallback đã lặp lại 2 lần trong file này (/history, /welcome). */
    private java.util.Optional<com.bookinghealthy.model.User> resolveCurrentUser(
            org.springframework.security.core.Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return java.util.Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        } else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
            String email = ((org.springframework.security.oauth2.core.user.OAuth2User) principal).getAttribute("email");
            return email != null ? userRepository.findByEmail(email) : java.util.Optional.empty();
        }
        String name = auth.getName();
        java.util.Optional<com.bookinghealthy.model.User> byUsername = userRepository.findByUsername(name);
        return byUsername.isPresent() ? byUsername : userRepository.findByEmail(name);
    }

    // BỘ KHUNG GIỜ CHUẨN (Copy y hệt từ BookingApi của mày)
    private final String[] ALL_SLOTS = {
            "07:30 - 08:00", "08:00 - 08:30", "08:30 - 09:00", "09:00 - 09:30",
            "09:30 - 10:00", "10:00 - 10:30", "10:30 - 11:00", "11:00 - 11:30",
            "13:30 - 14:00", "14:00 - 14:30", "14:30 - 15:00", "15:00 - 15:30",
            "15:30 - 16:00", "16:00 - 16:30", "16:30 - 17:00", "17:00 - 17:30"
    };

    /** Cùng bộ khung giờ trên, dạng List để truyền cho BookingService. KHÔNG liệt kê lại 16 chuỗi. */
    private final List<String> ALL_SLOTS_LIST = Arrays.asList(ALL_SLOTS);

    /**
     * Các khung giờ NẰM NGOÀI ca khám bác sĩ đã đăng ký trong ngày đó.
     * Tính MỘT LẦN cho cả ngày rồi tra bằng Set, không hỏi lại theo từng khung giờ.
     * Bác sĩ chưa đăng ký lịch nào -> rỗng -> không gạch khung nào (giữ hành vi cũ cho dữ liệu seed).
     */
    private java.util.Set<String> offDutySlots(Long doctorId, java.time.LocalDate date) {
        return new java.util.HashSet<>(bookingService.slotsOutsideWorkingHours(doctorId, date, ALL_SLOTS_LIST));
    }

    // =========================================================================
    // API LẤY DATA BÁC SĨ (cùng bộ lọc với BookingApi, KỂ CẢ ca khám đã đăng ký)
    // =========================================================================
    /** Tối đa bao nhiêu thẻ bác sĩ trả về. Cắt SAU khi xếp hạng, không bao giờ trước. */
    private static final int MAX_DOCTOR_CARDS = 3;

    /** Một bác sĩ kèm căn cứ xếp hạng, chỉ sống trong một lần gọi endpoint. */
    private static final class DoctorRank {
        final Doctor doctor;
        final boolean pinned;
        java.time.LocalDate rankedOn;
        List<String> preview = new java.util.ArrayList<>();
        String matchedSlot;          // khung trống đầu tiên nằm trong phạm vi khách xin
        int nearbyLoad = Integer.MAX_VALUE;
        int dayLoad = Integer.MAX_VALUE;

        DoctorRank(Doctor doctor, boolean pinned) {
            this.doctor = doctor;
            this.pinned = pinned;
        }

        int experience() {
            return doctor.getExperienceYears() == null ? 0 : doctor.getExperienceYears();
        }
    }

    @GetMapping("/doctors/department/{departmentId}")
    public ResponseEntity<List<DoctorDTO>> getDoctorsByDepartment(@PathVariable Long departmentId,
                                                                  @RequestParam(required = false) String sessionId,
                                                                  @RequestParam(required = false) Long doctorId,
                                                                  @RequestParam(required = false) String date,
                                                                  @RequestParam(required = false) String time,
                                                                  @RequestParam(required = false) String session) {

        // Phạm vi khách xin. Dùng lại y nguyên helper của /slot-alternatives — KHÔNG liệt kê giờ
        // ở đây, nếu không lưới khung giờ có thêm nơi khai báo thứ 12 (xem /skills/sync-slot-grid).
        String wantedSession = normalizeSessionParam(session);
        String wantedSlot = (time == null || time.isBlank()) ? null : resolveCanonicalSlot(time);
        if (wantedSlot != null) {
            wantedSession = sessionOf(wantedSlot);
        }
        // Giờ NGOÀI lưới ("7 giờ tối") ở đây im lặng hạ xuống "không ràng buộc giờ", TUYỆT ĐỐI
        // không trả OUTSIDE_HOURS: câu đó là của /slot-alternatives, mà nhánh A luôn gọi tới nó.
        // Hai nơi cùng trả lời một câu hỏi chính là cách lưới khung giờ đẻ ra bản sao.
        List<String> wantedRange = slotsOfSession(wantedSlot, wantedSession);

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate startDate = today;
        if (date != null && !date.isBlank()) {
            try {
                java.time.LocalDate parsed = java.time.LocalDate.parse(date.trim());
                // Ngày đã qua / quá xa thì lùi về hôm nay. Không kẹp thì bác sĩ không có Schedule
                // cho ngày đó -> slotsOutsideWorkingHours rỗng = "không giới hạn" -> mọi khung đọc
                // ra trống và toàn bộ việc xếp hạng thành vô nghĩa.
                if (!parsed.isBefore(today) && !parsed.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) {
                    startDate = parsed;
                }
            } catch (Exception ignored) {
                // Ngày rác -> hôm nay. Đây là endpoint dựng thẻ gợi ý, không đáng trả 400.
            }
        }

        long nowMillis = System.currentTimeMillis();
        List<DoctorRank> rows = new java.util.ArrayList<>();
        for (Doctor doc : doctorService.findByDepartmentId(departmentId)) {
            rows.add(new DoctorRank(doc, doctorId != null && doctorId.equals(doc.getId())));
        }

        // ===== QUÉT THEO NGÀY, KHÔNG THEO BÁC SĨ =====
        // Bản cũ cho mỗi bác sĩ tự quét 7 ngày rồi dừng ở ngày mở đầu tiên CỦA CHÍNH MÌNH, nên
        // preview của người A có thể là ngày mai còn người B là thứ Sáu tuần sau — hai người
        // không bao giờ được đem ra so với nhau. Vòng ngoài là NGÀY thì cả khoa cùng được chấm
        // trên một ngày, và đó là điều kiện cần để xếp hạng có nghĩa.
        for (int offset = 0; offset < FORWARD_SCAN_DAYS; offset++) {
            java.time.LocalDate day = startDate.plusDays(offset);
            if (day.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) break;

            boolean anyoneFree = false;
            for (DoctorRank row : rows) {
                DaySlots slots = new DaySlots(row.doctor.getId(), day);
                List<String> free = slots.freeSlotsIn(ALL_SLOTS_LIST, sessionId, nowMillis);
                if (free.isEmpty()) continue;

                anyoneFree = true;
                row.rankedOn = day;
                row.preview = free.stream().limit(4)
                        .map(slot -> buildSlotLabel(day, slot))
                        .collect(Collectors.toList());
                row.matchedSlot = free.stream().filter(wantedRange::contains).findFirst().orElse(null);

                java.time.LocalTime anchor = (row.matchedSlot != null) ? slotStartOf(row.matchedSlot)
                        : (wantedSlot != null) ? slotStartOf(wantedSlot)
                        : slotStartOf(free.get(0));
                row.nearbyLoad = slots.nearbyLoad(anchor);
                row.dayLoad = slots.bookedTimes.size();
            }
            if (anyoneFree) break;   // cả khoa đã được chấm trên CÙNG ngày này
        }

        // ===== XẾP HẠNG =====
        // XẾP HẠNG CHỈ ĐỔI THỨ TỰ, TUYỆT ĐỐI KHÔNG LOẠI AI. Lọc bỏ người bận sẽ phá hai thứ:
        // bác sĩ khách nêu đích danh mà đang kín lịch sẽ biến mất -> frontend bắn doctorNotFound
        // ("Em chưa tìm thấy bác sĩ X" về một người có thật); và khoa kín lịch trả [] -> frontend
        // bắn NO_DOCTORS, giết luôn đường giải thích lý do của /slot-alternatives.
        rows.sort(java.util.Comparator
                // 1. Bác sĩ khách gọi đích danh luôn đứng đầu, KỂ CẢ khi bận (false xếp trước true)
                .comparing((DoctorRank r) -> !r.pinned)
                // 2. Trống đúng giờ/buổi khách xin
                .thenComparing(r -> r.matchedSlot == null)
                // 3. Ít ca quanh giờ đó nhất -> khách đỡ phải ngồi chờ
                .thenComparingInt(r -> r.nearbyLoad)
                .thenComparingInt(r -> r.dayLoad)
                .thenComparingInt(r -> -r.experience())
                // 5. Thứ tự TOÀN PHẦN: hoà tuyệt đối cũng không được rơi về thứ tự DB ngẫu nhiên
                .thenComparing(r -> r.doctor.getId()));

        List<DoctorRank> top = rows.size() > MAX_DOCTOR_CARDS
                ? new java.util.ArrayList<>(rows.subList(0, MAX_DOCTOR_CARDS)) : rows;

        // Bù preview cho người lọt top nhưng không rảnh đúng ngày xếp hạng — dải thẻ vẫn cần
        // "ca trống gần nhất" của họ. Thứ hạng đã chốt nên vòng này không xáo lại được gì.
        for (DoctorRank row : top) {
            if (!row.preview.isEmpty()) continue;
            java.time.LocalDate from = (row.rankedOn != null) ? row.rankedOn.plusDays(1) : startDate;
            for (java.time.LocalDate d = from; d.isBefore(startDate.plusDays(FORWARD_SCAN_DAYS)); d = d.plusDays(1)) {
                if (d.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) break;
                List<String> free = new DaySlots(row.doctor.getId(), d)
                        .freeSlotsIn(ALL_SLOTS_LIST, sessionId, nowMillis);
                if (free.isEmpty()) continue;
                final java.time.LocalDate labelDate = d;
                row.preview = free.stream().limit(4)
                        .map(slot -> buildSlotLabel(labelDate, slot))
                        .collect(Collectors.toList());
                break;
            }
        }

        List<DoctorDTO> doctorDtos = new java.util.ArrayList<>();
        for (DoctorRank row : top) {
            DoctorDTO dto = new DoctorDTO(row.doctor);
            dto.setAvailableSlots(row.preview);
            if (row.rankedOn != null) {
                dto.setNearbyLoad(row.nearbyLoad);
                dto.setDayLoad(row.dayLoad);
                dto.setMatchedDate(row.rankedOn.toString());
                dto.setMatchedSlot(row.matchedSlot);
                dto.setMatchesRequest(row.matchedSlot != null);
                if (row.matchedSlot != null) {
                    dto.setMatchedSlotLabel(buildSlotLabel(row.rankedOn, row.matchedSlot));
                }
            }
            doctorDtos.add(dto);
        }
        return ResponseEntity.ok(doctorDtos);
    }
    // =========================================================================
    // API GỢI Ý THAY THẾ KHI KHÁCH KHÔNG ĐẶT ĐƯỢC KHUNG GIỜ ĐÃ XIN
    //
    // Trợ lý AI không nhìn thấy lịch làm việc, nên nó KHÔNG được phép tự nói "đã giữ chỗ".
    // Câu trả lời thật về chỗ trống đến từ đây:
    //   - reason / reasonText: VÌ SAO khung giờ đó không đặt được. Bắt buộc phải có — trước đây
    //     cả thẻ chat lẫn câu đọc đều nói cứng "đã kín lịch", nên khách không phân biệt được
    //     "có người đặt trước" với "hôm đó bác sĩ không đăng ký ca làm việc".
    //   - sameTimeDoctors: bác sĩ CÙNG KHOA còn trống ĐÚNG khung giờ khách xin,
    //     xếp theo số ca khám quanh giờ đó (ít ca nhất lên đầu -> khách đỡ ngồi chờ).
    //   - otherTimes: các khung giờ gần nhất của chính bác sĩ khách đang nhắm tới. Nếu hôm đó
    //     bác sĩ nghỉ hẳn thì quét sang NGÀY LÀM VIỆC GẦN NHẤT, nên mỗi mục mang theo `date`
    //     của riêng nó — bên gọi PHẢI dùng `date` đó khi dựng link đặt lịch.
    // Không đặt soft-lock ở đây: đây mới chỉ là bước hỏi ý khách, chưa chốt gì cả.
    // =========================================================================
    private static final int NEARBY_MINUTES = 90;   // phạm vi tính "ca khám quanh giờ đó"
    private static final int FORWARD_SCAN_DAYS = 7; // quét tối đa bấy nhiêu ngày để tìm ngày bác sĩ có ca
    private static final int MAX_BOOKING_AHEAD_DAYS = 90; // trần đặt trước, chặn ngày vô lý kiểu 2035

    @GetMapping("/slot-alternatives")
    public ResponseEntity<Map<String, Object>> getSlotAlternatives(
            @RequestParam Long departmentId,
            @RequestParam String date,
            @RequestParam(required = false) String time,
            @RequestParam(required = false) String session,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String sessionId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sameTimeDoctors = new java.util.ArrayList<>();
        List<Map<String, Object>> otherTimes = new java.util.ArrayList<>();
        result.put("sameTimeDoctors", sameTimeDoctors);
        result.put("otherTimes", otherTimes);
        result.put("requestedDoctorFree", false);
        result.put("reason", null);
        result.put("reasonText", null);
        result.put("requestedDoctorWorkingRanges", new java.util.ArrayList<String>());

        java.time.LocalDate targetDate;
        try {
            targetDate = java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        result.put("date", date);

        // Ngày trong QUÁ KHỨ phải bị chặn ở đây, không thể trông vào bộ lọc slot: mã "PAST" chỉ được
        // xét khi date == hôm nay, còn bác sĩ thì không có lịch làm việc cho ngày đã qua nên
        // slotsOutsideWorkingHours trả rỗng = "không giới hạn" -> cả 16 khung đều FREE và trợ lý
        // hồn nhiên mời khách đặt lịch một ngày đã trôi qua.
        java.time.LocalDate today = java.time.LocalDate.now();
        if (targetDate.isBefore(today)) {
            result.put("slot", null);
            result.put("session", normalizeSessionParam(session));
            result.put("reason", "PAST");
            result.put("reasonText", buildDayLabel(targetDate)
                    + " đã qua rồi ạ, anh/chị chọn giúp em một ngày từ hôm nay trở đi nhé.");
            return ResponseEntity.ok(result);
        }
        if (targetDate.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) {
            result.put("slot", null);
            result.put("session", normalizeSessionParam(session));
            result.put("reason", "TOO_FAR");
            result.put("reasonText", "Phòng khám chỉ nhận đặt lịch trước tối đa "
                    + MAX_BOOKING_AHEAD_DAYS + " ngày ạ.");
            return ResponseEntity.ok(result);
        }

        // Khách nêu BUỔI ("sáng thứ ba") thay vì giờ cụ thể. Việc quy buổi -> khung giờ nằm HẲN
        // ở server: trình duyệt chỉ gửi lên chữ "morning"/"afternoon" và không bao giờ phải biết
        // buổi sáng kết thúc lúc mấy giờ (nếu biết thì lưới khung giờ có thêm nơi khai báo thứ 12).
        String wantedSession = normalizeSessionParam(session);
        String wantedSlot = null;

        if (time != null && !time.trim().isEmpty()) {
            wantedSlot = resolveCanonicalSlot(time);
            if (wantedSlot == null) {
                // Khách xin giờ ngoài lưới (19h, 12h trưa). Trước đây hàm trả payload rỗng và
                // frontend vứt đi, nên yêu cầu kiểu "7 giờ tối" cụt đường không một lời giải thích.
                result.put("slot", null);
                result.put("session", wantedSession);
                result.put("reason", "OUTSIDE_HOURS");
                result.put("reasonText", OUTSIDE_HOURS_TEXT);
                return ResponseEntity.ok(result);
            }
            wantedSession = sessionOf(wantedSlot);
        } else if ("evening".equals(wantedSession)) {
            result.put("slot", null);
            result.put("session", wantedSession);
            result.put("reason", "OUTSIDE_HOURS");
            result.put("reasonText", OUTSIDE_HOURS_TEXT);
            return ResponseEntity.ok(result);
        }
        result.put("session", wantedSession);

        long nowMillis = System.currentTimeMillis();
        // Khách chỉ nêu buổi (hoặc chỉ nêu ngày) -> chưa có khung giờ đích, xét cả buổi/cả ngày.
        List<String> wantedRange = slotsOfSession(wantedSlot, wantedSession);
        result.put("slot", wantedSlot);

        for (Doctor doc : doctorService.findByDepartmentId(departmentId)) {
            DaySlots day = new DaySlots(doc.getId(), targetDate);

            // Khung sớm nhất bác sĩ này còn nhận trong phạm vi khách xin
            String freeSlot = day.firstFreeIn(wantedRange, sessionId, nowMillis);

            if (freeSlot != null) {
                DoctorDTO dto = new DoctorDTO(doc);
                Map<String, Object> item = new HashMap<>();
                item.put("id", dto.getId());
                item.put("fullName", dto.getFullName());
                item.put("avatar", dto.getAvatar());
                item.put("degree", dto.getDegree());
                item.put("departmentId", dto.getDepartmentId());
                item.put("slot", freeSlot);
                item.put("date", targetDate.toString());
                item.put("session", sessionOf(freeSlot));
                item.put("slotLabel", buildSlotLabel(targetDate, freeSlot));
                item.put("nearbyLoad", day.nearbyLoad(slotStartOf(freeSlot)));
                item.put("dayLoad", day.bookedTimes.size());
                sameTimeDoctors.add(item);
            }

            // Khung giờ thay thế của CHÍNH bác sĩ khách đang nhắm tới
            if (doctorId != null && doctorId.equals(doc.getId())) {
                String doctorName = new DoctorDTO(doc).getFullName();
                result.put("requestedDoctorFree", freeSlot != null);
                result.put("requestedDoctorName", doctorName);
                result.put("requestedDoctorWorkingRanges", day.workingRanges());
                // Rỗng ở requestedDoctorWorkingRanges có hai nghĩa ngược nhau — cờ này phân biệt.
                result.put("scheduleKnown", day.isScheduleKnown());

                if (freeSlot != null) {
                    result.put("reason", "FREE");
                    // Khách xin cả buổi thì khung chốt được là khung sớm nhất còn trống của buổi đó.
                    result.put("slot", freeSlot);
                } else {
                    ReasonSummary summary = day.summarizeReasonsIn(wantedRange, sessionId, nowMillis);
                    result.put("reason", summary.dominant());
                    result.put("reasonBreakdown", summary.counts());
                    result.put("freeCountInRange", summary.free());
                    result.put("reasonText", buildReasonText(
                            summary, doctorName, targetDate, wantedSlot, wantedSession, day));
                }

                otherTimes.addAll(findOtherTimes(doc, doctorName, targetDate, wantedSlot,
                        wantedSession, sessionId, nowMillis));

                // ĐỔI NGÀY LÀ THAY ĐỔI LỚN NHẤT VỚI KHÁCH. findOtherTimes có thể nhảy tới 7 ngày
                // để tìm ngày bác sĩ còn làm việc; nếu chỉ trả danh sách khung giờ thì khách đọc
                // "dời sang khung gần nhất", bấm nút, và tới NHẦM HÔM. Ba key này để giao diện đưa
                // chuyện đổi ngày lên DÒNG TIÊU ĐỀ.
                if (!otherTimes.isEmpty()) {
                    String firstDate = (String) otherTimes.get(0).get("date");
                    boolean moved = firstDate != null && !firstDate.equals(targetDate.toString());
                    result.put("otherTimesDate", firstDate);
                    result.put("otherTimesMovedDay", moved);
                    result.put("otherTimesText", moved
                            ? "Ngày làm việc gần nhất của bác sĩ " + doctorName + " là "
                              + buildDayLabel(java.time.LocalDate.parse(firstDate)) + " ạ."
                            : null);
                }
            }
        }

        // Ít ca quanh giờ đó nhất lên đầu; hoà thì xét tổng ca cả ngày
        sameTimeDoctors.sort(java.util.Comparator
                .comparingInt((Map<String, Object> m) -> (Integer) m.get("nearbyLoad"))
                .thenComparingInt(m -> (Integer) m.get("dayLoad")));
        if (sameTimeDoctors.size() > 3) {
            List<Map<String, Object>> top = new java.util.ArrayList<>(sameTimeDoctors.subList(0, 3));
            sameTimeDoctors.clear();
            sameTimeDoctors.addAll(top);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // KHÁCH **HỎI** VỀ LỊCH LÀM VIỆC, KHÔNG PHẢI XIN ĐẶT LỊCH
    //
    // "bác sĩ X chiều nay bận à?" là một CÂU HỎI. Trước endpoint này, hệ thống chỉ tra được lịch
    // khi khách đang ĐẶT: /slot-alternatives chỉ có đúng một nơi gọi, nằm bên trong
    // resolveBookingHandoff, mà hàm đó thoát ngay khi booking_intent = false. Trong khi đó mục 1
    // và 5B của prompt CẤM model khẳng định bác sĩ có khám hay không, và hứa "hệ thống lo phần
    // lịch" — lời hứa chưa từng được thực hiện ở nhánh câu hỏi. Khách nhận một câu chung chung,
    // bên dưới trống trơn, rồi tự hiểu thành "bác sĩ đang rảnh".
    //
    // CỐ Ý KHÔNG tái dùng /slot-alternatives:
    //   - hàm đó BẮT BUỘC có departmentId và duyệt toàn bộ bác sĩ trong khoa — thừa cho một câu
    //     hỏi về đúng một người;
    //   - về ngữ nghĩa nó là một LỜI MỜI ĐẶT LỊCH (sameTimeDoctors, otherTimes, slot), nhét câu
    //     trả lời vào đó là mời giao diện gán nó vào pendingAlternatives — đúng thứ phải tránh;
    //   - nó chỉ xử lý MỘT ngày, không trả lời được "tuần này bác sĩ làm ngày nào".
    //
    // KHÔNG đặt soft-lock: hỏi thăm không phải là chốt chỗ.
    // =========================================================================
    private static final int AVAILABILITY_MAX_DAYS = 14;

    @GetMapping("/doctor-availability")
    public ResponseEntity<Map<String, Object>> getDoctorAvailability(
            @RequestParam Long doctorId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String sessionId) {

        Doctor doc = doctorService.findById(doctorId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(404).body(Map.of("error", "DOCTOR_NOT_FOUND"));
        }
        DoctorDTO dto = new DoctorDTO(doc);
        String doctorName = dto.getFullName();

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate targetDate = today;
        if (date != null && !date.trim().isEmpty()) {
            try {
                targetDate = java.time.LocalDate.parse(date.trim());
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("doctorId", dto.getId());
        result.put("doctorName", doctorName);
        result.put("departmentId", dto.getDepartmentId());
        result.put("avatar", dto.getAvatar());
        result.put("degree", dto.getDegree());
        result.put("date", targetDate.toString());

        String wantedSession = normalizeSessionParam(session);
        result.put("session", wantedSession);

        long nowMillis = System.currentTimeMillis();
        Map<String, Object> anchor = new HashMap<>();
        result.put("anchor", anchor);
        anchor.put("date", targetDate.toString());
        anchor.put("dayLabel", buildDayLabel(targetDate));

        // Cùng hai lá chắn của /slot-alternatives, và cùng lý do: bác sĩ KHÔNG có lịch làm việc cho
        // ngày đã qua, nên slotsOutsideWorkingHours trả rỗng = "không giới hạn" -> cả 16 khung đọc
        // ra FREE và trợ lý hồn nhiên báo "cả ngày còn trống" về một ngày đã trôi qua.
        if (targetDate.isBefore(today)) {
            anchor.put("dayState", "PAST");
            anchor.put("reason", "PAST");
            anchor.put("reasonText", buildDayLabel(targetDate)
                    + " đã qua rồi ạ, em xem giúp anh/chị lịch từ hôm nay trở đi nhé.");
            anchor.put("scheduleKnown", false);
            anchor.put("workingRanges", new java.util.ArrayList<String>());
            anchor.put("freeCount", 0);
            anchor.put("firstFreeSlot", null);
            // Tuần vẫn bắt đầu từ HÔM NAY, và summaryText nói rõ điều đó — không lặng lẽ đổi mốc.
            targetDate = today;
        } else if (targetDate.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) {
            anchor.put("dayState", "PAST");
            anchor.put("reason", "TOO_FAR");
            anchor.put("reasonText", "Phòng khám chỉ nhận đặt lịch trước tối đa "
                    + MAX_BOOKING_AHEAD_DAYS + " ngày ạ.");
            anchor.put("scheduleKnown", false);
            anchor.put("workingRanges", new java.util.ArrayList<String>());
            anchor.put("freeCount", 0);
            anchor.put("firstFreeSlot", null);
            result.put("week", new java.util.ArrayList<Map<String, Object>>());
            result.put("summaryText", "Phòng khám chỉ nhận đặt lịch trước tối đa "
                    + MAX_BOOKING_AHEAD_DAYS + " ngày ạ.");
            return ResponseEntity.ok(result);
        } else {
            DaySlots day = new DaySlots(doctorId, targetDate);
            List<String> wantedRange = slotsOfSession(null, wantedSession);
            ReasonSummary summary = day.summarizeReasonsIn(wantedRange, sessionId, nowMillis);

            fillDayInfo(anchor, day, targetDate, wantedRange, sessionId, nowMillis);
            anchor.put("reason", summary.free() > 0 ? "FREE" : summary.dominant());
            anchor.put("reasonBreakdown", summary.counts());
            anchor.put("reasonText", summary.free() > 0
                    ? buildFreeText(doctorName, targetDate, day, wantedSession, summary.free())
                    : buildReasonText(summary, doctorName, targetDate, null, wantedSession, day));
        }

        // days kẹp trong [1, 14]: endpoint này permitAll, không để một vòng lặp quét sạch lịch cả
        // năm của bác sĩ.
        int span = (days == null) ? 7 : Math.max(1, Math.min(AVAILABILITY_MAX_DAYS, days));
        List<Map<String, Object>> week = new java.util.ArrayList<>();
        List<String> workingDayPhrases = new java.util.ArrayList<>();
        boolean anyScheduleKnown = false;

        for (int offset = 0; offset < span; offset++) {
            java.time.LocalDate d = targetDate.plusDays(offset);
            if (d.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) break;

            DaySlots day = new DaySlots(doctorId, d);
            Map<String, Object> info = new HashMap<>();
            fillDayInfo(info, day, d, ALL_SLOTS_LIST, sessionId, nowMillis);
            week.add(info);
            anyScheduleKnown |= day.isScheduleKnown();

            @SuppressWarnings("unchecked")
            List<String> ranges = (List<String>) info.get("workingRanges");
            if (!ranges.isEmpty()) {
                workingDayPhrases.add(buildDayLabel(d) + " (" + String.join(" và ", ranges) + ")");
            }
        }
        result.put("week", week);
        result.put("scheduleKnown", anyScheduleKnown);
        result.put("summaryText", buildWeekSummary(doctorName, workingDayPhrases, span, anyScheduleKnown));
        return ResponseEntity.ok(result);
    }

    /** Một ô ngày trong dải lịch: ca đã đăng ký, còn mấy khung, khung sớm nhất. */
    private void fillDayInfo(Map<String, Object> info, DaySlots day, java.time.LocalDate d,
                             List<String> pool, String sessionId, long nowMillis) {
        List<String> ranges = day.workingRanges();
        List<String> free = day.freeSlotsIn(pool, sessionId, nowMillis);

        info.put("date", d.toString());
        info.put("dayLabel", buildDayLabel(d));
        info.put("scheduleKnown", day.isScheduleKnown());
        info.put("workingRanges", ranges);
        info.put("freeCount", free.size());
        info.put("firstFreeSlot", free.isEmpty() ? null : free.get(0));

        String state;
        if (!day.isScheduleKnown()) {
            // Rỗng vì hệ thống chưa biết, KHÔNG phải vì bác sĩ nghỉ. Hai chuyện khác hẳn nhau.
            state = "NO_SCHEDULE";
        } else if (ranges.isEmpty()) {
            state = "OFF_ALL_DAY";
        } else if (free.isEmpty()) {
            state = "FULL";
        } else {
            state = "PARTIAL";
        }
        info.put("dayState", state);
    }

    /** Bác sĩ CÒN chỗ — câu này phải nêu ca làm việc thật, không chỉ nói "còn trống". */
    private String buildFreeText(String doctorName, java.time.LocalDate date, DaySlots day,
                                 String wantedSession, int freeCount) {
        String when = buildDayLabel(date);
        String who = "bác sĩ " + doctorName;
        List<String> ranges = day.workingRanges();
        String scope = (wantedSession != null) ? sessionLabel(wantedSession) : "trong ngày";

        if (!day.isScheduleKnown()) {
            return when + " " + who + " còn " + freeCount + " khung trống " + scope + " ạ.";
        }
        return when + " " + who + " có ca làm việc " + String.join(" và ", ranges)
                + ", còn " + freeCount + " khung trống " + scope + " ạ.";
    }

    /**
     * "Tuần này bác sĩ X có ca khám T2 04/08 (07:30 - 11:30) và T4 06/08 (13:30 - 17:30) ạ."
     * Giữ định dạng máy để {@code MediTrustVoice.humanizeSchedule()} đọc thành lời.
     */
    private String buildWeekSummary(String doctorName, List<String> phrases, int span,
                                    boolean anyScheduleKnown) {
        String who = "bác sĩ " + doctorName;
        if (phrases.isEmpty()) {
            // HAI trường hợp rỗng khác hẳn nhau, và nói nhầm là mâu thuẫn ngay với dòng bên trên:
            // bác sĩ CHƯA đăng ký lịch vẫn đặt khám được (luật "chưa đăng ký = không giới hạn" của
            // BookingService), nên câu "không đăng ký ca làm việc nào" ở đây sẽ đá nhau với câu
            // "còn 8 khung trống" mà chính hàm này in ra ở anchor.
            return anyScheduleKnown
                    ? span + " ngày tới " + who + " không đăng ký ca làm việc nào ạ."
                    : "Hệ thống chưa có lịch đăng ký của " + who
                      + ", nhưng anh/chị vẫn đặt khám trong giờ hành chính được ạ.";
        }
        if (phrases.size() == 1) {
            return span + " ngày tới " + who + " chỉ có ca khám " + phrases.get(0) + " ạ.";
        }
        String last = phrases.get(phrases.size() - 1);
        String head = String.join(", ", phrases.subList(0, phrases.size() - 1));
        return span + " ngày tới " + who + " có ca khám " + head + " và " + last + " ạ.";
    }

    /**
     * Khung giờ thay thế của chính bác sĩ khách nhắm tới: ưu tiên ĐÚNG NGÀY khách xin; hôm đó
     * không còn khung nào (thường vì bác sĩ nghỉ cả ngày) thì lùi sang ngày làm việc gần nhất.
     *
     * KHÔNG trộn nhiều ngày trong một danh sách — `distance` tính bằng phút sẽ vô nghĩa khi khác
     * ngày, và giao diện sẽ xếp một khung của tuần sau lên trước một khung của ngày mai.
     */
    private List<Map<String, Object>> findOtherTimes(Doctor doc, String doctorName,
                                                     java.time.LocalDate targetDate, String wantedSlot,
                                                     String wantedSession, String sessionId, long nowMillis) {
        for (int offset = 0; offset < FORWARD_SCAN_DAYS; offset++) {
            java.time.LocalDate date = targetDate.plusDays(offset);
            DaySlots day = new DaySlots(doc.getId(), date);

            // Ngày khách xin: giữ nguyên buổi khách muốn nếu buổi đó còn chỗ, hết mới mở ra cả ngày.
            // Các ngày sau đó thì xét cả ngày, vì khách đã phải đổi ngày rồi.
            List<String> pool = (offset == 0 && wantedSession != null)
                    ? day.freeSlotsIn(slotsOfSession(null, wantedSession), sessionId, nowMillis)
                    : new java.util.ArrayList<>();
            if (pool.isEmpty()) {
                pool = day.freeSlotsIn(ALL_SLOTS_LIST, sessionId, nowMillis);
            }
            if (offset == 0 && wantedSlot != null) {
                pool.remove(wantedSlot);   // khung khách vừa xin, không gợi ý lại
            }
            if (pool.isEmpty()) continue;

            java.time.LocalTime anchor = (wantedSlot != null)
                    ? slotStartOf(wantedSlot) : slotStartOf(pool.get(0));

            List<Map<String, Object>> candidates = new java.util.ArrayList<>();
            for (String slotStr : pool) {
                Map<String, Object> alt = new HashMap<>();
                alt.put("doctorId", doc.getId());
                alt.put("fullName", doctorName);
                alt.put("slot", slotStr);
                alt.put("date", date.toString());
                alt.put("session", sessionOf(slotStr));
                alt.put("slotLabel", buildSlotLabel(date, slotStr));
                alt.put("distance", Math.abs(
                        java.time.Duration.between(anchor, slotStartOf(slotStr)).toMinutes()));
                candidates.add(alt);
            }
            candidates.sort(java.util.Comparator.comparingLong(a -> (Long) a.get("distance")));
            return candidates.stream().limit(3).collect(Collectors.toList());
        }
        return new java.util.ArrayList<>();
    }

    // ===================== LÝ DO KHÔNG ĐẶT ĐƯỢC =====================

    private static final String OUTSIDE_HOURS_TEXT =
            "Phòng khám chỉ nhận đặt khám trong giờ hành chính 07:30 - 11:30 và 13:30 - 17:30 ạ.";

    /**
     * Lý do nào được chọn làm câu chính khi cả phạm vi có nhiều lý do BẰNG NHAU về số khung.
     *
     * Cùng thứ tự với {@code DaySlots.blockReason}: sự thật về CON NGƯỜI (bác sĩ không đăng ký ca,
     * bác sĩ báo bận) đứng trên sự thật về MỘT KHUNG GIỜ (có người đặt, đang giữ chỗ), vì cái trước
     * còn đúng cả ngày còn cái sau có thể đổi sau một phút.
     */
    private static final String[] REASON_TIE_BREAK = {"OFF_DUTY", "BLOCKED", "BOOKED", "HELD", "PAST"};

    /** Mỗi lý do chiếm bao nhiêu khung trong phạm vi khách xin, kèm lý do chính. */
    private record ReasonSummary(String dominant, java.util.Map<String, Integer> counts,
                                 int free, int total) {
        /**
         * Lý do đứng thứ hai (khác dominant, số khung > 0) — để câu trả lời nói được CẢ HAI sự thật.
         *
         * PAST bị loại khỏi vị trí này: "đã trôi qua" là tính chất của ĐỒNG HỒ chứ không phải của
         * bác sĩ, nên ghép nó vào câu chỉ tạo nhiễu — "hôm nay bác sĩ không đăng ký ca nào, trong
         * ngày có 8 khung ngoài ca làm việc và 8 khung đã trôi qua" khiến khách tưởng 8 khung kia
         * lẽ ra đặt được.
         */
        String runnerUp() {
            String second = null;
            int best = 0;
            for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getKey().equals(dominant) || "PAST".equals(e.getKey())) continue;
                if (e.getValue() > best) {
                    best = e.getValue();
                    second = e.getKey();
                }
            }
            return second;
        }
    }

    /** "6 khung ngoài ca làm việc đã đăng ký" — mệnh đề phụ ghép vào câu lý do chính. */
    private String reasonClause(String reason, int count) {
        switch (reason) {
            case "OFF_DUTY": return count + " khung ngoài ca làm việc bác sĩ đã đăng ký";
            case "BLOCKED":  return count + " khung bác sĩ báo bận";
            case "BOOKED":   return count + " khung đã có bệnh nhân khác đặt";
            case "HELD":     return count + " khung đang chờ người khác xác nhận";
            case "PAST":     return count + " khung đã trôi qua";
            default:         return null;
        }
    }

    /**
     * Câu giải thích cho khách, dùng CHUNG cho thẻ chat và câu đọc của trợ lý giọng nói.
     * Cố ý viết theo đúng định dạng máy ("T3 28/07", "13:30 - 17:30") để
     * {@code MediTrustVoice.humanizeSchedule()} tự đọc thành lời — đừng thêm một bản riêng cho loa.
     * (Tên biến JS vẫn là MediTrustVoice trong {@code meditrust-voice.js}; đừng đổi theo tên
     * hiển thị của bệnh viện, một lần replace nhầm là thành "NNL HospitalVoice" — sai ký hiệu.)
     * Xưng hô theo mục 0 của prompt: em / anh-chị.
     */
    private String buildReasonText(ReasonSummary summary, String doctorName, java.time.LocalDate date,
                                   String wantedSlot, String wantedSession, DaySlots day) {
        if (summary == null || summary.dominant() == null) return null;

        String reason = summary.dominant();
        String when = buildDayLabel(date);
        String who = "bác sĩ " + doctorName;
        String slotPart = (wantedSlot != null) ? "khung giờ " + wantedSlot : sessionLabel(wantedSession);
        // Khách chỉ nêu NGÀY, không nêu buổi -> "buổi sáng/buổi chiều" đều sai, nói "trong ngày".
        String scopeLabel = (wantedSession != null) ? sessionLabel(wantedSession) : "trong ngày";
        // Bản đứng ĐẦU CÂU. Ghép "Trong " + scopeLabel sẽ ra "Trong trong ngày".
        String scopeIn = (wantedSession != null) ? "Trong " + sessionLabel(wantedSession) : "Trong ngày";
        List<String> workingRanges = day.workingRanges();

        String main;
        switch (reason) {
            case "OFF_DUTY":
                if (!day.isScheduleKnown()) {
                    // Rỗng ở đây KHÔNG có nghĩa là bác sĩ nghỉ — hệ thống chưa có lịch nào của
                    // người này. Nói "không đăng ký ca nào" là vu oan, nói giờ làm việc là bịa.
                    main = "Hệ thống chưa có lịch đăng ký của " + who + " cho tuần chứa " + when + " ạ.";
                } else if (workingRanges.isEmpty()) {
                    // "không đăng ký ca làm việc" chứ TUYỆT ĐỐI KHÔNG phải "không có ca khám":
                    // câu cũ đọc được thành "không có ai đặt khám bác sĩ" nên khách hiểu ngược
                    // hoàn toàn — tưởng bác sĩ đang rảnh rồi hỏi "vậy sao không đặt cho tôi?".
                    main = when + " " + who + " không đăng ký ca làm việc nào, nên hôm đó bác sĩ không khám ạ.";
                } else {
                    // Khách không nêu buổi nào -> "khung giờ này" trỏ vào hư không; nói "ngoài
                    // khung đó" mới đúng nghĩa "phần còn lại của ngày".
                    String offPart = (wantedSlot != null || wantedSession != null)
                            ? slotPart + " hôm đó" : "ngoài khung đó";
                    main = when + " " + who + " chỉ đăng ký ca làm việc "
                            + String.join(" và ", workingRanges) + ", nên " + offPart
                            + " bác sĩ không nhận khám ạ.";
                }
                break;
            case "BOOKED":
                // Mở đầu bằng "CÓ khám ... nhưng" là vế đối của OFF_DUTY: khách phải phân biệt
                // được "bác sĩ không làm" với "bác sĩ có làm nhưng hết chỗ".
                // Khách xin cả buổi thì slotPart TRÙNG scopeLabel ("...CÓ khám buổi chiều, nhưng
                // buổi chiều đã có người đặt") — nói "các khung giờ đều" cho gọn và đúng hơn.
                String bookedPart = (wantedSlot != null)
                        ? slotPart + " đã có bệnh nhân khác đặt trước rồi ạ."
                        : "các khung giờ đều đã có bệnh nhân khác đặt trước rồi ạ.";
                main = who + " CÓ khám " + scopeLabel + " " + when + ", nhưng " + bookedPart;
                break;
            case "BLOCKED":
                main = day.isBlockedAllDay()
                        ? when + " " + who + " báo bận cả ngày nên không nhận khám ạ."
                        : when + " " + who + " có ca làm việc nhưng đã báo bận " + slotPart + " ạ.";
                break;
            case "HELD":
                // Người kia đã CHỐT khung này và đang trong bước điền phiếu đặt lịch (khoá 3 phút).
                main = slotPart + " " + when + " vừa có bệnh nhân khác chọn và đang chờ xác nhận ạ.";
                break;
            case "PAST":
                main = slotPart + " " + when + " đã trôi qua rồi ạ.";
                break;
            case "OUTSIDE_HOURS":
                return OUTSIDE_HOURS_TEXT;
            default:
                return null;
        }

        // Phạm vi có NHIỀU lý do -> nói cả hai. Tối đa 2 mệnh đề: lớp giọng nói đọc nguyên văn
        // câu này, chuỗi bốn mệnh đề nghe như đọc báo cáo.
        String second = summary.runnerUp();
        if (second != null && summary.total() > 1) {
            String firstClause = reasonClause(reason, summary.counts().getOrDefault(reason, 0));
            String secondClause = reasonClause(second, summary.counts().getOrDefault(second, 0));
            if (firstClause != null && secondClause != null) {
                main += " " + scopeIn + " có " + firstClause + " và " + secondClause + " ạ.";
            }
        }
        return main;
    }

    /** "morning" -> "buổi sáng". Dùng khi khách nêu buổi chứ không nêu giờ cụ thể. */
    private String sessionLabel(String session) {
        if ("morning".equals(session)) return "buổi sáng";
        if ("afternoon".equals(session)) return "buổi chiều";
        return "khung giờ này";
    }

    /** "T3 28/07" — đúng định dạng translateDay để lớp giọng nói đọc được. */
    private String buildDayLabel(java.time.LocalDate date) {
        return translateDay(date.getDayOfWeek()) + " "
                + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
    }

    private String normalizeSessionParam(String session) {
        if (session == null) return null;
        String s = session.trim().toLowerCase();
        if (s.isEmpty()) return null;
        if (s.equals("morning") || s.equals("afternoon") || s.equals("evening")) return s;
        return null;
    }

    /** Buổi của một khung giờ. Ranh giới nghỉ trưa là mốc duy nhất, không cần biết giờ mở/đóng cửa. */
    private String sessionOf(String slotStr) {
        return slotStartOf(slotStr).isBefore(java.time.LocalTime.NOON) ? "morning" : "afternoon";
    }

    /** Phạm vi khung giờ mà khách đang xin: một khung cụ thể, cả một buổi, hoặc cả ngày. */
    private List<String> slotsOfSession(String wantedSlot, String session) {
        if (wantedSlot != null) return java.util.Collections.singletonList(wantedSlot);
        if (session == null) return ALL_SLOTS_LIST;
        return ALL_SLOTS_LIST.stream()
                .filter(slot -> session.equals(sessionOf(slot)))
                .collect(Collectors.toList());
    }

    private java.time.LocalTime slotStartOf(String slotStr) {
        return java.time.LocalTime.parse(slotStr.split(" - ")[0],
                java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * "10:30" hoặc "10:30 - 11:00" -> khung giờ chuẩn "10:30 - 11:00". Ngoài giờ hành chính -> null.
     *
     * Quy về khung CHỨA giờ đó (start &lt;= t &lt; end), không so `startsWith`: khách nói "9 giờ 15" là
     * giờ hoàn toàn hợp lệ giữa giờ hành chính, nhưng "09:15" không mở đầu khung nào nên cách so cũ
     * trả null và trợ lý đáp lại bằng câu "phòng khám chỉ nhận đặt khám trong giờ hành chính
     * 07:30 - 11:30 và 13:30 - 17:30 ạ" — vô lý với chính giờ khách vừa xin.
     */
    private String resolveCanonicalSlot(String time) {
        if (time == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(time);
        if (!m.find()) return null;

        java.time.LocalTime wanted;
        try {
            wanted = java.time.LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        } catch (Exception e) {
            return null;
        }

        for (String slot : ALL_SLOTS) {
            java.time.LocalTime start = slotStartOf(slot);
            java.time.LocalTime end = start.plusMinutes(30);
            if (!wanted.isBefore(start) && wanted.isBefore(end)) return slot;
        }
        return null;
    }

    /**
     * Tình trạng chỗ trống của MỘT bác sĩ trong MỘT ngày: đã đặt, tự chặn, và ca khám đã đăng ký.
     * Ba thứ này tra một lần rồi dùng lại cho mọi khung giờ trong ngày — nếu hỏi lại theo từng
     * khung thì mỗi ngày tốn 16 lượt truy vấn lịch làm việc.
     */
    private final class DaySlots {
        private final Long doctorId;
        private final java.time.LocalDate date;
        private final java.util.Set<String> offDuty;
        /**
         * Hệ thống có biết ca làm việc của bác sĩ trong tuần này không.
         *
         * BẮT BUỘC phải đọc kèm {@link #offDuty}: offDuty rỗng có HAI nghĩa ngược nhau —
         * "nhận khám cả ngày" (đã đăng ký, ca phủ hết) và "chưa đăng ký gì cả" (không giới hạn).
         * Không có cờ này thì câu trả lời cho bệnh nhân sẽ bịa ra một ngày làm việc đầy đủ.
         *
         * TÍNH LƯỜI (null = chưa hỏi). {@code hasRegisteredSchedule} gọi lại đúng
         * {@code findEffective} mà {@code offDutySlots} vừa gọi — trả tiền hai lần cho cùng một
         * câu hỏi. Chỉ lớp GIẢI THÍCH cần nó ({@link #workingRanges()} và khoá `scheduleKnown`
         * trong payload); đường XẾP HẠNG bác sĩ duyệt cả khoa × nhiều ngày và không hề đụng tới,
         * nên để lười là bớt hẳn một truy vấn cho mỗi (bác sĩ, ngày).
         */
        private Boolean scheduleKnown;
        private final List<String> bookedTimes;
        private final List<com.bookinghealthy.model.DoctorBlockTime> blocked;

        DaySlots(Long doctorId, java.time.LocalDate date) {
            this.doctorId = doctorId;
            this.date = date;
            this.offDuty = offDutySlots(doctorId, date);
            this.bookedTimes = bookingRepository
                    .findByDoctorIdAndAppointmentDateAndStatusNot(doctorId, date,
                            com.bookinghealthy.model.BookingStatus.CANCELED)
                    .stream()
                    .map(com.bookinghealthy.model.Booking::getAppointmentTime)
                    .collect(Collectors.toList());
            this.blocked = doctorBlockTimeRepository.findByDoctorIdAndBlockDate(doctorId, date);
        }

        /**
         * {@code null} = đặt được. Ngược lại là MÃ LÝ DO. Bộ lọc và câu giải thích dùng chung
         * hàm này nên không bao giờ nói vênh nhau.
         *
         * OFF_DUTY cố ý xếp trên BOOKED: "hôm đó bác sĩ không khám buổi sáng" vừa hữu ích hơn
         * vừa bền hơn cho khách so với "khung này có người đặt".
         */
        String blockReason(String slotStr, String sessionId, long nowMillis) {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            String[] parts = slotStr.split(" - ");
            java.time.LocalTime slotStart = java.time.LocalTime.parse(parts[0], fmt);
            java.time.LocalTime slotEnd = java.time.LocalTime.parse(parts[1], fmt);

            if (date.isEqual(java.time.LocalDate.now()) && slotStart.isBefore(java.time.LocalTime.now())) {
                return "PAST";
            }
            if (offDuty.contains(slotStr)) return "OFF_DUTY";
            if (bookedTimes.contains(slotStr)) return "BOOKED";

            for (com.bookinghealthy.model.DoctorBlockTime block : blocked) {
                if (slotStart.isBefore(block.getEndTime()) && slotEnd.isAfter(block.getStartTime())) {
                    return "BLOCKED";
                }
            }

            if (isHeldByAnotherSession(doctorId, date, slotStr, sessionId, nowMillis)) {
                return "HELD";
            }
            return null;
        }

        List<String> freeSlotsIn(List<String> pool, String sessionId, long nowMillis) {
            return pool.stream()
                    .filter(slot -> blockReason(slot, sessionId, nowMillis) == null)
                    .collect(Collectors.toCollection(java.util.ArrayList::new));
        }

        String firstFreeIn(List<String> pool, String sessionId, long nowMillis) {
            for (String slot : pool) {
                if (blockReason(slot, sessionId, nowMillis) == null) return slot;
            }
            return null;
        }

        /**
         * Bức tranh ĐẦY ĐỦ của cả phạm vi khách xin: đếm theo TỪNG lý do, không chọn một lý do
         * đại diện rồi vứt phần còn lại.
         *
         * Nửa buổi ngoài ca làm việc + nửa buổi đã có người đặt là HAI sự thật khác nhau. Bản cũ
         * (worstReasonIn) chỉ nói mỗi "đã có người đặt" nên khách tưởng bác sĩ rảnh cả buổi, chỉ
         * là hết chỗ — đúng cái hiểu lầm mà cả mục này sinh ra để dập.
         *
         * Thứ tự phá hoà nay TRÙNG với {@link #blockReason}: OFF_DUTY/BLOCKED là sự thật về CON
         * NGƯỜI, vẫn còn đúng sau một tiếng nữa; BOOKED/HELD là sự thật về MỘT KHUNG GIỜ, có thể
         * đổi trong một phút. Trước đây hai hàm này xếp NGƯỢC nhau.
         */
        ReasonSummary summarizeReasonsIn(List<String> pool, String sessionId, long nowMillis) {
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            int free = 0;
            for (String slot : pool) {
                String reason = blockReason(slot, sessionId, nowMillis);
                if (reason == null) {
                    free++;
                } else {
                    counts.merge(reason, 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) return new ReasonSummary(null, counts, free, pool.size());

            String dominant = null;
            int best = -1;
            for (String candidate : REASON_TIE_BREAK) {
                int count = counts.getOrDefault(candidate, 0);
                if (count > best) {
                    best = count;
                    dominant = candidate;
                }
            }
            return new ReasonSummary(dominant, counts, free, pool.size());
        }

        /** Khối chặn này có phủ trọn ngày làm việc không — để phân biệt "bận cả ngày" với "bận một buổi". */
        boolean isBlockedAllDay() {
            for (com.bookinghealthy.model.DoctorBlockTime block : blocked) {
                if (!block.getStartTime().isAfter(java.time.LocalTime.of(7, 30))
                        && !block.getEndTime().isBefore(java.time.LocalTime.of(17, 30))) {
                    return true;
                }
            }
            return false;
        }

        boolean isScheduleKnown() {
            if (scheduleKnown == null) {
                scheduleKnown = bookingService.hasRegisteredSchedule(doctorId, date);
            }
            return scheduleKnown;
        }

        /** Số ca khám của bác sĩ trong khoảng ±NEARBY_MINUTES quanh giờ khách xin. */
        int nearbyLoad(java.time.LocalTime wantedStart) {
            return countNearbyBookings(bookedTimes, wantedStart);
        }

        /**
         * Ca khám bác sĩ NHẬN trong ngày, gộp các khung liền nhau: ["13:30 - 17:30"].
         *
         * CHỈ ĐỂ HIỂN THỊ. Tuyệt đối không đảo ngược thành whitelist để lọc khung giờ: bác sĩ
         * chưa đăng ký lịch nào thì {@code offDuty} rỗng, nên danh sách này thành "cả ngày" —
         * đúng cho câu nói, nhưng dùng làm bộ lọc thì mất luôn ý nghĩa "chưa đăng ký = không giới hạn".
         *
         * RỖNG CÓ HAI NGHĨA, phải đọc kèm {@link #isScheduleKnown()}:
         *   scheduleKnown = true  -> có đăng ký tuần đó nhưng KHÔNG có ca vào thứ này (nghỉ hẳn).
         *   scheduleKnown = false -> hệ thống chưa có lịch nào; offDuty rỗng nên vòng lặp dưới sẽ
         *                            dựng ra "07:30 - 11:30 và 13:30 - 17:30" — một ngày làm việc
         *                            HOÀN TOÀN BỊA. Thà không nói gì còn hơn nói bịa, nên chặn ở đây.
         */
        List<String> workingRanges() {
            // Qua accessor, KHÔNG đọc thẳng field: nó tính lười nên còn null lúc này -> unbox là NPE.
            if (!isScheduleKnown()) return java.util.Collections.emptyList();
            List<String> ranges = new java.util.ArrayList<>();
            String openAt = null;
            String previousEnd = null;

            for (String slot : ALL_SLOTS) {
                String[] parts = slot.split(" - ");
                if (offDuty.contains(slot)) {
                    if (openAt != null) ranges.add(openAt + " - " + previousEnd);
                    openAt = null;
                    continue;
                }
                // Nghỉ trưa cắt đôi buổi: khung 11:00-11:30 và 13:30-14:00 không liền nhau.
                if (openAt != null && !parts[0].equals(previousEnd)) {
                    ranges.add(openAt + " - " + previousEnd);
                    openAt = null;
                }
                if (openAt == null) openAt = parts[0];
                previousEnd = parts[1];
            }
            if (openAt != null) ranges.add(openAt + " - " + previousEnd);
            return ranges;
        }
    }

    /** Số ca khám của bác sĩ trong khoảng ±NEARBY_MINUTES quanh giờ khách xin. */
    private int countNearbyBookings(List<String> bookedTimes, java.time.LocalTime wantedStart) {
        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        int count = 0;
        for (String booked : bookedTimes) {
            if (booked == null) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(booked);
            if (!m.find()) continue;
            try {
                java.time.LocalTime start = java.time.LocalTime.parse(
                        String.format("%02d:%s", Integer.parseInt(m.group(1)), m.group(2)), timeFormatter);
                if (Math.abs(java.time.Duration.between(wantedStart, start).toMinutes()) <= NEARBY_MINUTES) count++;
            } catch (Exception ignored) { /* dữ liệu giờ lạ thì bỏ qua */ }
        }
        return count;
    }

    private String buildSlotLabel(java.time.LocalDate date, String slotStr) {
        return translateDay(date.getDayOfWeek()) + " "
                + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                + " (" + slotStr + ")";
    }

    // API: Kéo lịch sử chat của User đang đăng nhập
    @GetMapping("/history")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getMyHistory() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }

        // --- TÌM CHÍNH XÁC USER ĐANG ĐĂNG NHẬP ---
        java.util.Optional<com.bookinghealthy.model.User> currentUserOpt = java.util.Optional.empty();
        Object principal = auth.getPrincipal();

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            currentUserOpt = userRepository.findByUsername(username);
        } else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
            String email = ((org.springframework.security.oauth2.core.user.OAuth2User) principal).getAttribute("email");
            if (email != null) currentUserOpt = userRepository.findByEmail(email);
        } else {
            String name = auth.getName();
            currentUserOpt = userRepository.findByUsername(name);
            if (currentUserOpt.isEmpty()) currentUserOpt = userRepository.findByEmail(name);
        }

        // --- TRẢ VỀ LỊCH SỬ NẾU TÌM THẤY USER ---
        return currentUserOpt.map(user -> {
            java.util.List<com.bookinghealthy.model.AiChatSession> sessions = sessionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
            java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

            for (com.bookinghealthy.model.AiChatSession s : sessions) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("sessionCode", s.getSessionCode());
                map.put("date", s.getUpdatedAt().toString());
                map.put("chatData", s.getChatHistoryJson());
                result.add(map);
            }
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.ok(java.util.Collections.emptyList()));
    }

    // Hàm phụ trợ dịch Ngày sang tiếng Việt
    private String translateDay(DayOfWeek day) {
        switch(day) {
            case MONDAY: return "T2";
            case TUESDAY: return "T3";
            case WEDNESDAY: return "T4";
            case THURSDAY: return "T5";
            case FRIDAY: return "T6";
            case SATURDAY: return "T7";
            case SUNDAY: return "CN";
            default: return "";
        }
    }
    // =========================================================================
    // TẠO CÂU CHÀO CÁ NHÂN HÓA (DỰA VÀO BỆNH ÁN CŨ)
    // =========================================================================
    @GetMapping("/welcome")
    public ResponseEntity<String> getWelcomeMessage() {
        System.out.println("\n========== [DEBUG API WELCOME] START ==========");
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String defaultGreeting = "Xin chào! Em là Trợ lý AI Heal Care. Anh / Chị cần hỗ trợ vấn đề sức khỏe gì hôm nay?";

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            System.out.println("[LOG 1] Thất bại: User là Anonymous (Chưa đăng nhập).");
            return ResponseEntity.ok(defaultGreeting);
        }
        // 1. TÌM USER ĐANG ĐĂNG NHẬP (Đã Fix lỗi đăng nhập bằng Google/OAuth2)
        java.util.Optional<com.bookinghealthy.model.User> currentUserOpt = java.util.Optional.empty();
        Object principal = auth.getPrincipal();

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            // Đăng nhập thường
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            currentUserOpt = userRepository.findByUsername(username);
        } else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
            // NẾU ĐĂNG NHẬP BẰNG GOOGLE (Lấy thẳng email từ token)
            String email = ((org.springframework.security.oauth2.core.user.OAuth2User) principal).getAttribute("email");
            if (email != null) {
                currentUserOpt = userRepository.findByEmail(email);
            }
        } else {
            // Fallback
            String name = auth.getName();
            currentUserOpt = userRepository.findByUsername(name);
            if(currentUserOpt.isEmpty()) currentUserOpt = userRepository.findByEmail(name);
        }

        if (currentUserOpt.isPresent()) {
            com.bookinghealthy.model.User user = currentUserOpt.get();
            System.out.println("[LOG 2] Đã lấy được User. ID: " + user.getId() + " | Tên: " + user.getFullName());

            // 2. TÌM LỊCH KHÁM 'COMPLETED'
            System.out.println("[LOG 3] Đang Query bảng Booking với UserID=" + user.getId() + " và Status=COMPLETED...");
            java.util.Optional<com.bookinghealthy.model.Booking> lastBooking = bookingRepository.findFirstByUserIdAndStatusOrderByAppointmentDateDesc(user.getId(), com.bookinghealthy.model.BookingStatus.COMPLETED);

            if (lastBooking.isPresent()) {
                com.bookinghealthy.model.Booking booking = lastBooking.get();
                System.out.println("[LOG 4] Đã tìm thấy Booking COMPLETED! BookingID: " + booking.getId());

                // === 1. LẤY TRỌN VẸN TÊN VÀ XỬ LÝ SẠCH SẼ ===
                String rawName = (booking.getPatientName() != null && !booking.getPatientName().isEmpty()) ? booking.getPatientName() : user.getFullName();
                String displayName = rawName;

                // Xử lý riêng vụ Google OAuth hay chèn tên vào ngoặc: "Vũ Hữu Hoàng Anh (Hoàng Anh)"
                // Nếu thấy ngoặc, lấy luôn cụm từ TRONG ngoặc làm tên hiển thị
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((.*?)\\)").matcher(rawName);
                if (m.find()) {
                    displayName = m.group(1).trim();
                } else {
                    displayName = rawName.trim();
                }

                // === 2. XỬ LÝ XƯNG HÔ (MẶC ĐỊNH LÀ ANH - EM) ===
                String pronoun = "anh";
                if (user.getGender() != null) {
                    if (user.getGender().equalsIgnoreCase("FEMALE") || user.getGender().equalsIgnoreCase("Nữ") || user.getGender().equals("0")) {
                        pronoun = "chị";
                    }
                }

                // 3. TÌM HỒ SƠ BỆNH ÁN
                System.out.println("[LOG 5] Đang Query bảng MedicalRecord với BookingID=" + booking.getId() + "...");
                java.util.Optional<com.bookinghealthy.model.MedicalRecord> record = medicalRecordRepository.findByBookingId(booking.getId());

                if (record.isPresent()) {
                    System.out.println("[LOG 6A] ĐÃ TÌM THẤY Medical Record! ID: " + record.get().getId());

                    if (record.get().getDiagnosis() != null && !record.get().getDiagnosis().trim().isEmpty()) {
                        String diagnosis = record.get().getDiagnosis().toLowerCase();
                        System.out.println("[RESULT] Trả về câu chào theo BỆNH LÝ: " + diagnosis);

                        // Câu chào bôi đậm cả Tên (displayName) và Bệnh (diagnosis)
                        return ResponseEntity.ok("Dạ em chào " + pronoun + " **" + displayName + "**, tình trạng **" + diagnosis + "** của " + pronoun + " sau lần khám trước đã ổn định chưa ạ? Hôm nay em có thể giúp gì thêm cho " + pronoun + " không?");
                    } else {
                        System.out.println("[LOG 6B] Có Medical Record nhưng Diagnosis (Chẩn đoán) bị NULL/TRỐNG.");
                    }
                } else {
                    System.out.println("[LOG 6C] CẢNH BÁO: Bác sĩ set COMPLETED nhưng CHƯA TẠO Medical Record!");
                }

                // FALLBACK: Không có bệnh án hoặc bệnh án rỗng chẩn đoán -> Vẫn chào theo Khoa.
                // Bác sĩ có thể đã bị xoá hoặc chưa gán khoa: dereference thẳng ở đây từng làm
                // /api/chat/welcome trả 500 và khung chat mở ra TRỐNG TRƠN.
                String deptName = (booking.getDoctor() != null && booking.getDoctor().getDepartment() != null
                        && booking.getDoctor().getDepartment().getName() != null)
                        ? booking.getDoctor().getDepartment().getName().toLowerCase()
                        : null;
                if (deptName == null) {
                    System.out.println("[LOG 6D] Booking COMPLETED nhưng bác sĩ/khoa không còn -> chào mặc định.");
                    return ResponseEntity.ok(defaultGreeting);
                }
                System.out.println("[RESULT] Trả về câu chào theo KHOA (Fallback): " + deptName);

                // Bôi đậm cả Tên và Khoa
                return ResponseEntity.ok("Dạ em chào " + pronoun + " **" + displayName + "**, sức khỏe của " + pronoun + " sau lần khám chuyên khoa **" + deptName + "** trước đây đã ổn định chưa ạ? Hôm nay " + pronoun + " cần em hỗ trợ gì không?");
            }else {
                System.out.println("[LOG 4] Không tìm thấy Booking nào có Status=COMPLETED của UserID=" + user.getId());
            }
        } else {
            System.out.println("[LOG 2] Lỗi: Không thể map Authentication thành User entity.");
        }

        System.out.println("[RESULT] Trả về câu chào MẶC ĐỊNH.");
        System.out.println("========== [DEBUG API WELCOME] END ==========\n");
        return ResponseEntity.ok(defaultGreeting);
    }
}