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

    /**
     * Ca khám bác sĩ đã đăng ký (bảng Schedule). BẮT BUỘC đi qua BookingService — đó là đường
     * duy nhất tới {@code ScheduleRepository.findEffective}, tức là lịch có hiệu lực của ĐÚNG TUẦN
     * chứa ngày đang xét. Tự dựng lại phép so ca ở đây sẽ thành bản sao thứ tư của cùng một luật,
     * và chính kiểu sao chép đó đã đẻ ra bug "trợ lý mời giờ bác sĩ đang nghỉ".
     */
    @Autowired private BookingService bookingService;


    // =========================================================================
    // CƠ CHẾ SOFT-LOCK (MÔ PHỎNG REDIS TTL) - XỬ LÝ RACE CONDITION
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

    // Job tự động dọn dẹp các Lock đã hết hạn (Chạy mỗi 1 phút)
    @Scheduled(fixedRate = 60000)
    public void cleanUpExpiredLocks() {
        long now = System.currentTimeMillis();
        softLockCache.entrySet().removeIf(entry -> now > entry.getValue().expireAtMillis);
    }

    // --------------------------------------------------------
    // 1. CÁC API DÀNH CHO XỬ LÝ NGÔN NGỮ (LLM)
    // --------------------------------------------------------

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askAi(@RequestBody ChatRequest request) {
        String answer = aiService.chatWithMemory(request.getSessionId(), request.getPrompt());
        Map<String, String> result = new HashMap<>();
        result.put("answer", answer);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/clear/{sessionId}")
    public ResponseEntity<String> clearChat(@PathVariable String sessionId) {
        aiService.clearMemory(sessionId);
        return ResponseEntity.ok("Đã xóa lịch sử chat của phiên: " + sessionId);
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
    @GetMapping("/doctors/department/{departmentId}")
    public ResponseEntity<List<DoctorDTO>> getDoctorsByDepartment(@PathVariable Long departmentId,
                                                                  @RequestParam(required = false) String sessionId,
                                                                  @RequestParam(required = false) Long doctorId) {

        List<Doctor> doctors = new java.util.ArrayList<>(doctorService.findByDepartmentId(departmentId));

        // Khi khách chỉ đích danh một bác sĩ ("đổi sang bác sĩ B"), phải đưa người đó lên đầu
        // TRƯỚC khi .limit(3) cắt danh sách. Nếu không, bác sĩ B nằm ngoài top 3 sẽ bị loại
        // và frontend âm thầm rơi về bác sĩ đầu danh sách — tức là đặt nhầm người.
        if (doctorId != null) {
            doctors.sort(java.util.Comparator.comparing((Doctor d) -> !doctorId.equals(d.getId())));
        }
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        long nowMillis = System.currentTimeMillis();

        List<DoctorDTO> doctorDtos = doctors.stream()
                .limit(3)
                .map(doc -> {
                    DoctorDTO dto = new DoctorDTO(doc);
                    List<String> availableSlots = new java.util.ArrayList<>();

                    java.time.LocalDate today = java.time.LocalDate.now();
                    java.time.LocalTime now = java.time.LocalTime.now();
                    java.time.LocalDate endDate = today.plusDays(7); // Quét 7 ngày tới

                    // Quét từng ngày, bắt đầu từ HÔM NAY
                    for (java.time.LocalDate date = today; date.isBefore(endDate); date = date.plusDays(1)) {

                        // 1. Kéo dữ liệu các giờ ĐÃ BỊ ĐẶT (Booking)
                        List<com.bookinghealthy.model.Booking> bookings = bookingRepository
                                .findByDoctorIdAndAppointmentDateAndStatusNot(doc.getId(), date, com.bookinghealthy.model.BookingStatus.CANCELED);
                        List<String> bookedTimes = bookings.stream()
                                .map(com.bookinghealthy.model.Booking::getAppointmentTime)
                                .collect(Collectors.toList());

                        // 2. Kéo dữ liệu các giờ BỊ CHẶN (DoctorBlockTime)
                        List<com.bookinghealthy.model.DoctorBlockTime> blockedTimes = doctorBlockTimeRepository
                                .findByDoctorIdAndBlockDate(doc.getId(), date);

                        // 3. Ca khám bác sĩ đã đăng ký cho tuần chứa ngày này
                        java.util.Set<String> offDuty = offDutySlots(doc.getId(), date);
                        if (offDuty.size() == ALL_SLOTS.length) {
                            continue;   // hôm đó bác sĩ nghỉ hẳn, khỏi duyệt từng khung
                        }

                        // 4. Duyệt mảng ALL_SLOTS để tìm giờ TRỐNG
                        for (String slotStr : ALL_SLOTS) {
                            String[] parts = slotStr.split(" - ");
                            java.time.LocalTime slotStart = java.time.LocalTime.parse(parts[0], timeFormatter);
                            java.time.LocalTime slotEnd = java.time.LocalTime.parse(parts[1], timeFormatter);

                            // Lọc 1: Bỏ qua giờ trong quá khứ (nếu là ngày hôm nay)
                            if (date.isEqual(today) && slotStart.isBefore(now)) {
                                continue;
                            }

                            // Lọc 2: Bỏ qua khung NGOÀI ca khám bác sĩ đã đăng ký.
                            // Phải nằm TRƯỚC bộ đặt soft-lock bên dưới: trước đây thiếu bộ lọc này
                            // nên endpoint vừa mời giờ bác sĩ đang nghỉ, vừa khoá luôn khung đó 3 phút
                            // với các phiên chat khác — một chỗ giữ chẳng bao giờ thành lịch hẹn.
                            if (offDuty.contains(slotStr)) {
                                continue;
                            }

                            // Lọc 3: Bỏ qua giờ đã có khách đặt
                            if (bookedTimes.contains(slotStr)) {
                                continue;
                            }

                            // Lọc 4: Bỏ qua giờ Bác sĩ tự chặn (Overlap logic y hệt BookingApi)
                            boolean isBlocked = false;
                            for (com.bookinghealthy.model.DoctorBlockTime block : blockedTimes) {
                                if (slotStart.isBefore(block.getEndTime()) && slotEnd.isAfter(block.getStartTime())) {
                                    isBlocked = true;
                                    break;
                                }
                            }
                            if (isBlocked) continue;

                            // === BẮT ĐẦU CHÈN LỌC 5: RACE CONDITION SOFT-LOCK CHECK ===
                            String lockKey = doc.getId() + "_" + date.toString() + "_" + slotStr;
                            SlotLock existingLock = softLockCache.get(lockKey);

                            if (existingLock != null) {
                                if (nowMillis > existingLock.expireAtMillis) {
                                    // Lock đã hết hạn -> Xóa rác
                                    softLockCache.remove(lockKey);
                                } else if (sessionId != null && !sessionId.equals(existingLock.sessionId)) {
                                    // Lock còn hạn VÀ đang bị thằng khác giành -> BỎ QUA SLOT NÀY
                                    continue;
                                }
                            }

                            // ĐỦ ĐIỀU KIỆN TRỐNG -> KHÓA LẠI CHO USER NÀY TRONG 3 PHÚT (180,000 ms)
                            if (sessionId != null) {
                                softLockCache.put(lockKey, new SlotLock(sessionId, nowMillis + 180000));
                            }
                            // === KẾT THÚC CHÈN LỌC 5 ===

                            // NẾU VƯỢT QUA CÁC BỘ LỌC TRÊN -> CHÍNH LÀ GIỜ TRỐNG!
                            String displaySlot = translateDay(date.getDayOfWeek()) + " " + date.format(dateFormatter) + " (" + slotStr + ")";
                            availableSlots.add(displaySlot);

                            if (availableSlots.size() >= 4) break; // Lấy 4 slot thôi cho UI gọn
                        }

                        // QUAN TRỌNG: Đã tìm thấy giờ trống của ngày gần nhất thì DỪNG LUÔN, không nhảy ngày hôm sau nữa!
                        if (!availableSlots.isEmpty()) {
                            break;
                        }
                    }

                    dto.setAvailableSlots(availableSlots);
                    return dto;
                })
                .collect(Collectors.toList());
        System.out.println(">>> Đang lấy lịch cho Session: " + sessionId);
        return ResponseEntity.ok(doctorDtos);
    }
    // =========================================================================
    // API GỢI Ý THAY THẾ KHI KHÁCH KHÔNG ĐẶT ĐƯỢC KHUNG GIỜ ĐÃ XIN
    //
    // Trợ lý AI không nhìn thấy lịch làm việc, nên nó KHÔNG được phép tự nói "đã giữ chỗ".
    // Câu trả lời thật về chỗ trống đến từ đây:
    //   - reason / reasonText: VÌ SAO khung giờ đó không đặt được. Bắt buộc phải có — trước đây
    //     cả thẻ chat lẫn câu đọc đều nói cứng "đã kín lịch", nên khách không phân biệt được
    //     "có người đặt trước" với "hôm đó bác sĩ không có ca khám".
    //   - sameTimeDoctors: bác sĩ CÙNG KHOA còn trống ĐÚNG khung giờ khách xin,
    //     xếp theo số ca khám quanh giờ đó (ít ca nhất lên đầu -> khách đỡ ngồi chờ).
    //   - otherTimes: các khung giờ gần nhất của chính bác sĩ khách đang nhắm tới. Nếu hôm đó
    //     bác sĩ nghỉ hẳn thì quét sang NGÀY LÀM VIỆC GẦN NHẤT, nên mỗi mục mang theo `date`
    //     của riêng nó — bên gọi PHẢI dùng `date` đó khi dựng link đặt lịch.
    // Không đặt soft-lock ở đây: đây mới chỉ là bước hỏi ý khách, chưa chốt gì cả.
    // =========================================================================
    private static final int NEARBY_MINUTES = 90;   // phạm vi tính "ca khám quanh giờ đó"
    private static final int FORWARD_SCAN_DAYS = 7; // quét tối đa bấy nhiêu ngày để tìm ngày bác sĩ có ca

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

                if (freeSlot != null) {
                    result.put("reason", "FREE");
                    // Khách xin cả buổi thì khung chốt được là khung sớm nhất còn trống của buổi đó.
                    result.put("slot", freeSlot);
                } else {
                    String reason = day.worstReasonIn(wantedRange, sessionId, nowMillis);
                    result.put("reason", reason);
                    result.put("reasonText", buildReasonText(
                            reason, doctorName, targetDate, wantedSlot, wantedSession, day.workingRanges()));
                }

                otherTimes.addAll(findOtherTimes(doc, doctorName, targetDate, wantedSlot,
                        wantedSession, sessionId, nowMillis));
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
     * Câu giải thích cho khách, dùng CHUNG cho thẻ chat và câu đọc của trợ lý giọng nói.
     * Cố ý viết theo đúng định dạng máy ("T3 28/07", "13:30 - 17:30") để
     * {@code MediTrustVoice.humanizeSchedule()} tự đọc thành lời — đừng thêm một bản riêng cho loa.
     * Xưng hô theo mục 0 của prompt: em / anh-chị.
     */
    private String buildReasonText(String reason, String doctorName, java.time.LocalDate date,
                                   String wantedSlot, String wantedSession, List<String> workingRanges) {
        String when = buildDayLabel(date);
        String who = "bác sĩ " + doctorName;
        String slotPart = (wantedSlot != null) ? "khung giờ " + wantedSlot : sessionLabel(wantedSession);

        if (reason == null) return null;
        switch (reason) {
            case "OFF_DUTY":
                return workingRanges.isEmpty()
                        ? when + " " + who + " không có ca khám ạ."
                        : when + " " + who + " chỉ khám " + String.join(" và ", workingRanges) + " ạ.";
            case "BOOKED":
                return slotPart + " " + when + " của " + who + " đã có người đặt rồi ạ.";
            case "BLOCKED":
                return who + " đã báo bận " + slotPart + " " + when + " ạ.";
            case "HELD":
                return slotPart + " " + when + " đang có người khác giữ chỗ ạ.";
            case "PAST":
                return slotPart + " " + when + " đã trôi qua rồi ạ.";
            case "OUTSIDE_HOURS":
                return OUTSIDE_HOURS_TEXT;
            default:
                return null;
        }
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

    /** "10:30" hoặc "10:30 - 11:00" -> khung giờ chuẩn "10:30 - 11:00". Không khớp khung nào -> null. */
    private String resolveCanonicalSlot(String time) {
        if (time == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(time);
        if (!m.find()) return null;
        String start = String.format("%02d:%s", Integer.parseInt(m.group(1)), m.group(2));
        for (String slot : ALL_SLOTS) {
            if (slot.startsWith(start)) return slot;
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

            SlotLock lock = softLockCache.get(doctorId + "_" + date.toString() + "_" + slotStr);
            if (lock != null && nowMillis <= lock.expireAtMillis
                    && sessionId != null && !sessionId.equals(lock.sessionId)) {
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
         * Lý do đại diện cho cả phạm vi khách xin. Cả buổi đều OFF_DUTY thì nói OFF_DUTY (bác sĩ
         * nghỉ buổi đó); có khung nghỉ có khung bận thì lý do "gần khách" hơn mới là câu đáng nói.
         */
        String worstReasonIn(List<String> pool, String sessionId, long nowMillis) {
            java.util.Set<String> reasons = new java.util.LinkedHashSet<>();
            for (String slot : pool) {
                String reason = blockReason(slot, sessionId, nowMillis);
                if (reason != null) reasons.add(reason);
            }
            if (reasons.isEmpty()) return null;
            if (reasons.size() == 1) return reasons.iterator().next();
            for (String preferred : new String[]{"BOOKED", "BLOCKED", "HELD", "OFF_DUTY", "PAST"}) {
                if (reasons.contains(preferred)) return preferred;
            }
            return reasons.iterator().next();
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
         */
        List<String> workingRanges() {
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

                // FALLBACK: Không có bệnh án hoặc bệnh án rỗng chẩn đoán -> Vẫn chào theo Khoa
                String deptName = booking.getDoctor().getDepartment().getName().toLowerCase();
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