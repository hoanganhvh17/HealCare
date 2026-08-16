package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.DoctorDTO;
import com.bookinghealthy.dto.ai.ChatRequest;
import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.*;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private DoctorService doctorService;

    @Autowired private AiChatSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private DoctorBlockTimeRepository doctorBlockTimeRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private PrescriptionItemRepository prescriptionItemRepository;
    @Autowired private BookingService bookingService;

    static class SlotLock {
        String sessionId;
        long expireAtMillis;
        public SlotLock(String sessionId, long expireAtMillis) {
            this.sessionId = sessionId;
            this.expireAtMillis = expireAtMillis;
        }
    }

    private final ConcurrentHashMap<String, SlotLock> softLockCache = new ConcurrentHashMap<>();

    private static final long SOFT_LOCK_TTL_MILLIS = 180_000; // 3 phút

    @Scheduled(fixedRate = 60000)
    public void cleanUpExpiredLocks() {
        long now = System.currentTimeMillis();
        softLockCache.entrySet().removeIf(entry -> now > entry.getValue().expireAtMillis);
    }

    private String lockKey(Long doctorId, java.time.LocalDate date, String slotStr) {
        return doctorId + "_" + date.toString() + "_" + slotStr;
    }

    private boolean isHeldByAnotherSession(Long doctorId, java.time.LocalDate date, String slotStr,
                                           String sessionId, long nowMillis) {
        SlotLock lock = softLockCache.get(lockKey(doctorId, date, slotStr));
        if (lock == null || nowMillis > lock.expireAtMillis) return false;
        return sessionId != null && !sessionId.equals(lock.sessionId);
    }

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

        Optional<User> currentUserOpt =
                resolveCurrentUser(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("answer", "Vui lòng đăng nhập để dùng tính năng này."));
        }
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("answer", "Không tìm thấy lịch hẹn."));
        }
        Booking booking = bookingOpt.get();
        if (!booking.getUser().getId().equals(currentUserOpt.get().getId())) {
            return ResponseEntity.status(403).body(Map.of("answer", "Bạn không có quyền xem hồ sơ bệnh án này."));
        }
        MedicalRecord record = medicalRecordRepository.findByBookingId(bookingId).orElse(null);
        if (record == null) {
            return ResponseEntity.status(404).body(Map.of("answer", "Lịch hẹn này chưa có hồ sơ bệnh án."));
        }
        String recordText = formatRecordForAi(record);
        String systemPrompt = String.format(RECORD_EXPLAIN_PROMPT_TEMPLATE, recordText);
        String question = (request == null || request.getPrompt() == null || request.getPrompt().isBlank())
                ? "Hãy giải thích chẩn đoán và đơn thuốc trong hồ sơ này giúp tôi bằng ngôn ngữ dễ hiểu."
                : request.getPrompt().trim();
        if (question.length() > MAX_PROMPT_CHARS) question = question.substring(0, MAX_PROMPT_CHARS);
        String answer = aiService.getConversationalResponse(systemPrompt, question, "record_" + bookingId);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    private String formatRecordForAi(MedicalRecord record) {
        StringBuilder sb = new StringBuilder();
        if (record.getDiagnosisCode() != null && !record.getDiagnosisCode().isBlank()) {
            sb.append("Mã chẩn đoán (ICD-10): ").append(record.getDiagnosisCode()).append("\n");
        }
        sb.append("Chẩn đoán: ").append(nullToEmpty(record.getDiagnosis())).append("\n");
        sb.append("Triệu chứng: ").append(nullToEmpty(record.getSymptoms())).append("\n");

        List<PrescriptionItem> items =
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

    private Optional<com.bookinghealthy.model.User> resolveCurrentUser(
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return java.util.Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        } else if (principal instanceof OAuth2User) {
            String email = ((OAuth2User) principal).getAttribute("email");
            return email != null ? userRepository.findByEmail(email) : java.util.Optional.empty();
        }
        String name = auth.getName();
        Optional<User> byUsername = userRepository.findByUsername(name);
        return byUsername.isPresent() ? byUsername : userRepository.findByEmail(name);
    }

    private final String[] ALL_SLOTS = {
            "07:30 - 08:00", "08:00 - 08:30", "08:30 - 09:00", "09:00 - 09:30",
            "09:30 - 10:00", "10:00 - 10:30", "10:30 - 11:00", "11:00 - 11:30",
            "13:30 - 14:00", "14:00 - 14:30", "14:30 - 15:00", "15:00 - 15:30",
            "15:30 - 16:00", "16:00 - 16:30", "16:30 - 17:00", "17:00 - 17:30"
    };

    private final List<String> ALL_SLOTS_LIST = Arrays.asList(ALL_SLOTS);

    private Set<String> offDutySlots(Long doctorId, java.time.LocalDate date) {
        return new HashSet<>(bookingService.slotsOutsideWorkingHours(doctorId, date, ALL_SLOTS_LIST));
    }

    private static final int MAX_DOCTOR_CARDS = 3;

    private static final class DoctorRank {
        final Doctor doctor;
        final boolean pinned;
        LocalDate rankedOn;
        List<String> preview = new ArrayList<>();
        String matchedSlot;
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

        String wantedSession = normalizeSessionParam(session);
        String wantedSlot = (time == null || time.isBlank()) ? null : resolveCanonicalSlot(time);
        if (wantedSlot != null) {
            wantedSession = sessionOf(wantedSlot);
        }
        List<String> wantedRange = slotsOfSession(wantedSlot, wantedSession);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today;
        if (date != null && !date.isBlank()) {
            try {
                LocalDate parsed = LocalDate.parse(date.trim());
                if (!parsed.isBefore(today) && !parsed.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) {
                    startDate = parsed;
                }
            } catch (Exception ignored) {
            }
        }

        long nowMillis = System.currentTimeMillis();
        List<DoctorRank> rows = new java.util.ArrayList<>();
        for (Doctor doc : doctorService.findByDepartmentId(departmentId)) {
            rows.add(new DoctorRank(doc, doctorId != null && doctorId.equals(doc.getId())));
        }
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
            if (anyoneFree) break;
        }
        rows.sort(java.util.Comparator
                .comparing((DoctorRank r) -> !r.pinned)
                .thenComparing(r -> r.matchedSlot == null)
                .thenComparingInt(r -> r.nearbyLoad)
                .thenComparingInt(r -> r.dayLoad)
                .thenComparingInt(r -> -r.experience())
                .thenComparing(r -> r.doctor.getId()));

        List<DoctorRank> top = rows.size() > MAX_DOCTOR_CARDS
                ? new java.util.ArrayList<>(rows.subList(0, MAX_DOCTOR_CARDS)) : rows;

        for (DoctorRank row : top) {
            if (!row.preview.isEmpty()) continue;
            LocalDate from = (row.rankedOn != null) ? row.rankedOn.plusDays(1) : startDate;
            for (LocalDate d = from; d.isBefore(startDate.plusDays(FORWARD_SCAN_DAYS)); d = d.plusDays(1)) {
                if (d.isAfter(today.plusDays(MAX_BOOKING_AHEAD_DAYS))) break;
                List<String> free = new DaySlots(row.doctor.getId(), d)
                        .freeSlotsIn(ALL_SLOTS_LIST, sessionId, nowMillis);
                if (free.isEmpty()) continue;
                final LocalDate labelDate = d;
                row.preview = free.stream().limit(4)
                        .map(slot -> buildSlotLabel(labelDate, slot))
                        .collect(Collectors.toList());
                break;
            }
        }

        List<DoctorDTO> doctorDtos = new ArrayList<>();
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
        List<Map<String, Object>> sameTimeDoctors = new ArrayList<>();
        List<Map<String, Object>> otherTimes = new ArrayList<>();
        result.put("sameTimeDoctors", sameTimeDoctors);
        result.put("otherTimes", otherTimes);
        result.put("requestedDoctorFree", false);
        result.put("reason", null);
        result.put("reasonText", null);
        result.put("requestedDoctorWorkingRanges", new ArrayList<String>());

        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        result.put("date", date);
        LocalDate today = LocalDate.now();
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
        String wantedSession = normalizeSessionParam(session);
        String wantedSlot = null;
        if (time != null && !time.trim().isEmpty()) {
            wantedSlot = resolveCanonicalSlot(time);
            if (wantedSlot == null) {
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
        List<String> wantedRange = slotsOfSession(wantedSlot, wantedSession);
        result.put("slot", wantedSlot);
        for (Doctor doc : doctorService.findByDepartmentId(departmentId)) {
            DaySlots day = new DaySlots(doc.getId(), targetDate);
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
            if (doctorId != null && doctorId.equals(doc.getId())) {
                String doctorName = new DoctorDTO(doc).getFullName();
                result.put("requestedDoctorFree", freeSlot != null);
                result.put("requestedDoctorName", doctorName);
                result.put("requestedDoctorWorkingRanges", day.workingRanges());
                result.put("scheduleKnown", day.isScheduleKnown());
                if (freeSlot != null) {
                    result.put("reason", "FREE");
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

        LocalDate today = LocalDate.now();
        LocalDate targetDate = today;
        if (date != null && !date.trim().isEmpty()) {
            try {
                targetDate = LocalDate.parse(date.trim());
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

        if (targetDate.isBefore(today)) {
            anchor.put("dayState", "PAST");
            anchor.put("reason", "PAST");
            anchor.put("reasonText", buildDayLabel(targetDate)
                    + " đã qua rồi ạ, em xem giúp anh/chị lịch từ hôm nay trở đi nhé.");
            anchor.put("scheduleKnown", false);
            anchor.put("workingRanges", new java.util.ArrayList<String>());
            anchor.put("freeCount", 0);
            anchor.put("firstFreeSlot", null);
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

    private String buildWeekSummary(String doctorName, List<String> phrases, int span,
                                    boolean anyScheduleKnown) {
        String who = "bác sĩ " + doctorName;
        if (phrases.isEmpty()) {
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

    private List<Map<String, Object>> findOtherTimes(Doctor doc, String doctorName,
                                                     java.time.LocalDate targetDate, String wantedSlot,
                                                     String wantedSession, String sessionId, long nowMillis) {
        for (int offset = 0; offset < FORWARD_SCAN_DAYS; offset++) {
            java.time.LocalDate date = targetDate.plusDays(offset);
            DaySlots day = new DaySlots(doc.getId(), date);
            List<String> pool = (offset == 0 && wantedSession != null)
                    ? day.freeSlotsIn(slotsOfSession(null, wantedSession), sessionId, nowMillis)
                    : new java.util.ArrayList<>();
            if (pool.isEmpty()) {
                pool = day.freeSlotsIn(ALL_SLOTS_LIST, sessionId, nowMillis);
            }
            if (offset == 0 && wantedSlot != null) {
                pool.remove(wantedSlot);
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

    private static final String OUTSIDE_HOURS_TEXT =
            "Phòng khám chỉ nhận đặt khám trong giờ hành chính 07:30 - 11:30 và 13:30 - 17:30 ạ.";

    private static final String[] REASON_TIE_BREAK = {"OFF_DUTY", "BLOCKED", "BOOKED", "HELD", "PAST"};

    private record ReasonSummary(String dominant, java.util.Map<String, Integer> counts,
                                 int free, int total) {

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

    private String buildReasonText(ReasonSummary summary, String doctorName, java.time.LocalDate date,
                                   String wantedSlot, String wantedSession, DaySlots day) {
        if (summary == null || summary.dominant() == null) return null;

        String reason = summary.dominant();
        String when = buildDayLabel(date);
        String who = "bác sĩ " + doctorName;
        String slotPart = (wantedSlot != null) ? "khung giờ " + wantedSlot : sessionLabel(wantedSession);
        String scopeLabel = (wantedSession != null) ? sessionLabel(wantedSession) : "trong ngày";
        String scopeIn = (wantedSession != null) ? "Trong " + sessionLabel(wantedSession) : "Trong ngày";
        List<String> workingRanges = day.workingRanges();

        String main;
        switch (reason) {
            case "OFF_DUTY":
                if (!day.isScheduleKnown()) {
                    main = "Hệ thống chưa có lịch đăng ký của " + who + " cho tuần chứa " + when + " ạ.";
                } else if (workingRanges.isEmpty()) {
                    main = when + " " + who + " không đăng ký ca làm việc nào, nên hôm đó bác sĩ không khám ạ.";
                } else {
                    String offPart = (wantedSlot != null || wantedSession != null)
                            ? slotPart + " hôm đó" : "ngoài khung đó";
                    main = when + " " + who + " chỉ đăng ký ca làm việc "
                            + String.join(" và ", workingRanges) + ", nên " + offPart
                            + " bác sĩ không nhận khám ạ.";
                }
                break;
            case "BOOKED":
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

    private String sessionLabel(String session) {
        if ("morning".equals(session)) return "buổi sáng";
        if ("afternoon".equals(session)) return "buổi chiều";
        return "khung giờ này";
    }

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

    private String sessionOf(String slotStr) {
        return slotStartOf(slotStr).isBefore(java.time.LocalTime.NOON) ? "morning" : "afternoon";
    }

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

    private final class DaySlots {
        private final Long doctorId;
        private final java.time.LocalDate date;
        private final java.util.Set<String> offDuty;
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

        int nearbyLoad(java.time.LocalTime wantedStart) {
            return countNearbyBookings(bookedTimes, wantedStart);
        }

        List<String> workingRanges() {
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

    @GetMapping("/history")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getMyHistory() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }

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

    @GetMapping("/welcome")
    public ResponseEntity<String> getWelcomeMessage() {
        System.out.println("\n========== [DEBUG API WELCOME] START ==========");
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String defaultGreeting = "Xin chào! Em là Trợ lý AI Heal Care. Anh / Chị cần hỗ trợ vấn đề sức khỏe gì hôm nay?";

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            System.out.println("[LOG 1] Thất bại: User là Anonymous (Chưa đăng nhập).");
            return ResponseEntity.ok(defaultGreeting);
        }
        java.util.Optional<com.bookinghealthy.model.User> currentUserOpt = java.util.Optional.empty();
        Object principal = auth.getPrincipal();

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            currentUserOpt = userRepository.findByUsername(username);
        } else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
            String email = ((org.springframework.security.oauth2.core.user.OAuth2User) principal).getAttribute("email");
            if (email != null) {
                currentUserOpt = userRepository.findByEmail(email);
            }
        } else {
            String name = auth.getName();
            currentUserOpt = userRepository.findByUsername(name);
            if(currentUserOpt.isEmpty()) currentUserOpt = userRepository.findByEmail(name);
        }

        if (currentUserOpt.isPresent()) {
            com.bookinghealthy.model.User user = currentUserOpt.get();
            System.out.println("[LOG 2] Đã lấy được User. ID: " + user.getId() + " | Tên: " + user.getFullName());

            System.out.println("[LOG 3] Đang Query bảng Booking với UserID=" + user.getId() + " và Status=COMPLETED...");
            java.util.Optional<com.bookinghealthy.model.Booking> lastBooking = bookingRepository.findFirstByUserIdAndStatusOrderByAppointmentDateDesc(user.getId(), com.bookinghealthy.model.BookingStatus.COMPLETED);

            if (lastBooking.isPresent()) {
                com.bookinghealthy.model.Booking booking = lastBooking.get();
                System.out.println("[LOG 4] Đã tìm thấy Booking COMPLETED! BookingID: " + booking.getId());
                String rawName = (booking.getPatientName() != null && !booking.getPatientName().isEmpty()) ? booking.getPatientName() : user.getFullName();
                String displayName = rawName;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((.*?)\\)").matcher(rawName);
                if (m.find()) {
                    displayName = m.group(1).trim();
                } else {
                    displayName = rawName.trim();
                }
                String pronoun = "anh";
                if (user.getGender() != null) {
                    if (user.getGender().equalsIgnoreCase("FEMALE") || user.getGender().equalsIgnoreCase("Nữ") || user.getGender().equals("0")) {
                        pronoun = "chị";
                    }
                }

                System.out.println("[LOG 5] Đang Query bảng MedicalRecord với BookingID=" + booking.getId() + "...");
                java.util.Optional<com.bookinghealthy.model.MedicalRecord> record = medicalRecordRepository.findByBookingId(booking.getId());

                if (record.isPresent()) {
                    System.out.println("[LOG 6A] ĐÃ TÌM THẤY Medical Record! ID: " + record.get().getId());

                    if (record.get().getDiagnosis() != null && !record.get().getDiagnosis().trim().isEmpty()) {
                        String diagnosis = record.get().getDiagnosis().toLowerCase();
                        System.out.println("[RESULT] Trả về câu chào theo BỆNH LÝ: " + diagnosis);
                        return ResponseEntity.ok("Dạ em chào " + pronoun + " **" + displayName + "**, tình trạng **" + diagnosis + "** của " + pronoun + " sau lần khám trước đã ổn định chưa ạ? Hôm nay em có thể giúp gì thêm cho " + pronoun + " không?");
                    } else {
                        System.out.println("[LOG 6B] Có Medical Record nhưng Diagnosis (Chẩn đoán) bị NULL/TRỐNG.");
                    }
                } else {
                    System.out.println("[LOG 6C] CẢNH BÁO: Bác sĩ set COMPLETED nhưng CHƯA TẠO Medical Record!");
                }
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