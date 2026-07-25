package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.DoctorDTO;
import com.bookinghealthy.dto.ai.ChatRequest;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.repository.ScheduleRepository;
import com.bookinghealthy.service.AiService;
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

    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private com.bookinghealthy.repository.AiChatSessionRepository sessionRepository;
    @Autowired private com.bookinghealthy.repository.UserRepository userRepository;
    @Autowired private com.bookinghealthy.repository.BookingRepository bookingRepository; // INJECT THÊM REPO NÀY
    // THÊM REPOSITORY NÀY LÊN ĐẦU FILE CÙNG CÁC @Autowired KHÁC
    @Autowired private com.bookinghealthy.repository.DoctorBlockTimeRepository doctorBlockTimeRepository;
    // INJECT SERVICE THẦN THÁNH CỦA BẠN VÀO ĐÂY
    @Autowired private com.bookinghealthy.service.TimeSlotService timeSlotService;
    // === THÊM DÒNG NÀY VÀO ===
    @Autowired private com.bookinghealthy.repository.MedicalRecordRepository medicalRecordRepository;


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
    // =========================================================================
    // API LẤY DATA BÁC SĨ (COPY 100% LOGIC TỪ BOOKING API, BỎ QUA BẢNG SCHEDULE)
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

                        // 3. Duyệt mảng ALL_SLOTS để tìm giờ TRỐNG
                        for (String slotStr : ALL_SLOTS) {
                            String[] parts = slotStr.split(" - ");
                            java.time.LocalTime slotStart = java.time.LocalTime.parse(parts[0], timeFormatter);
                            java.time.LocalTime slotEnd = java.time.LocalTime.parse(parts[1], timeFormatter);

                            // Lọc 1: Bỏ qua giờ trong quá khứ (nếu là ngày hôm nay)
                            if (date.isEqual(today) && slotStart.isBefore(now)) {
                                continue;
                            }

                            // Lọc 2: Bỏ qua giờ đã có khách đặt
                            if (bookedTimes.contains(slotStr)) {
                                continue;
                            }

                            // Lọc 3: Bỏ qua giờ Bác sĩ tự chặn (Overlap logic y hệt BookingApi)
                            boolean isBlocked = false;
                            for (com.bookinghealthy.model.DoctorBlockTime block : blockedTimes) {
                                if (slotStart.isBefore(block.getEndTime()) && slotEnd.isAfter(block.getStartTime())) {
                                    isBlocked = true;
                                    break;
                                }
                            }
                            if (isBlocked) continue;

                            // === BẮT ĐẦU CHÈN LỌC 4: RACE CONDITION SOFT-LOCK CHECK ===
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
                            // === KẾT THÚC CHÈN LỌC 4 ===

                            // NẾU VƯỢ QUA 3 BỘ LỌC TRÊN -> CHÍNH LÀ GIỜ TRỐNG!
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
    // API GỢI Ý THAY THẾ KHI KHUNG GIỜ KHÁCH XIN ĐÃ KÍN
    //
    // Trợ lý AI không nhìn thấy lịch trực, nên nó KHÔNG được phép tự nói "đã giữ chỗ".
    // Câu trả lời thật về chỗ trống đến từ đây:
    //   - sameTimeDoctors: bác sĩ CÙNG KHOA còn trống ĐÚNG khung giờ khách xin,
    //     xếp theo số ca khám quanh giờ đó (ít ca nhất lên đầu -> khách đỡ ngồi chờ).
    //   - otherTimes: các khung giờ gần nhất của chính bác sĩ khách đang nhắm tới.
    // Không đặt soft-lock ở đây: đây mới chỉ là bước hỏi ý khách, chưa chốt gì cả.
    // =========================================================================
    private static final int NEARBY_MINUTES = 90;   // phạm vi tính "ca khám quanh giờ đó"

    @GetMapping("/slot-alternatives")
    public ResponseEntity<Map<String, Object>> getSlotAlternatives(
            @RequestParam Long departmentId,
            @RequestParam String date,
            @RequestParam String time,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String sessionId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sameTimeDoctors = new java.util.ArrayList<>();
        List<Map<String, Object>> otherTimes = new java.util.ArrayList<>();
        result.put("sameTimeDoctors", sameTimeDoctors);
        result.put("otherTimes", otherTimes);
        result.put("requestedDoctorFree", false);

        java.time.LocalDate targetDate;
        try {
            targetDate = java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        String wantedSlot = resolveCanonicalSlot(time);
        result.put("date", date);
        result.put("slot", wantedSlot);
        if (wantedSlot == null) return ResponseEntity.ok(result);

        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        java.time.LocalTime wantedStart = java.time.LocalTime.parse(wantedSlot.split(" - ")[0], timeFormatter);
        long nowMillis = System.currentTimeMillis();

        for (Doctor doc : doctorService.findByDepartmentId(departmentId)) {
            List<com.bookinghealthy.model.Booking> bookings = bookingRepository
                    .findByDoctorIdAndAppointmentDateAndStatusNot(doc.getId(), targetDate,
                            com.bookinghealthy.model.BookingStatus.CANCELED);
            List<String> bookedTimes = bookings.stream()
                    .map(com.bookinghealthy.model.Booking::getAppointmentTime)
                    .collect(Collectors.toList());
            List<com.bookinghealthy.model.DoctorBlockTime> blockedTimes = doctorBlockTimeRepository
                    .findByDoctorIdAndBlockDate(doc.getId(), targetDate);

            boolean freeAtWanted = isSlotFree(doc.getId(), targetDate, wantedSlot,
                    bookedTimes, blockedTimes, sessionId, nowMillis);

            if (freeAtWanted) {
                Map<String, Object> item = new HashMap<>();
                DoctorDTO dto = new DoctorDTO(doc);
                item.put("id", dto.getId());
                item.put("fullName", dto.getFullName());
                item.put("avatar", dto.getAvatar());
                item.put("degree", dto.getDegree());
                item.put("departmentId", dto.getDepartmentId());
                item.put("slot", wantedSlot);
                item.put("slotLabel", buildSlotLabel(targetDate, wantedSlot));
                item.put("nearbyLoad", countNearbyBookings(bookedTimes, wantedStart));
                item.put("dayLoad", bookedTimes.size());
                sameTimeDoctors.add(item);
            }

            // Khung giờ thay thế của CHÍNH bác sĩ khách đang nhắm tới
            if (doctorId != null && doctorId.equals(doc.getId())) {
                result.put("requestedDoctorFree", freeAtWanted);
                result.put("requestedDoctorName", new DoctorDTO(doc).getFullName());

                List<Map<String, Object>> candidates = new java.util.ArrayList<>();
                for (String slotStr : ALL_SLOTS) {
                    if (slotStr.equals(wantedSlot)) continue;
                    if (!isSlotFree(doc.getId(), targetDate, slotStr, bookedTimes, blockedTimes, sessionId, nowMillis)) continue;

                    java.time.LocalTime start = java.time.LocalTime.parse(slotStr.split(" - ")[0], timeFormatter);
                    Map<String, Object> alt = new HashMap<>();
                    alt.put("doctorId", doc.getId());
                    alt.put("fullName", new DoctorDTO(doc).getFullName());
                    alt.put("slot", slotStr);
                    alt.put("slotLabel", buildSlotLabel(targetDate, slotStr));
                    alt.put("distance", Math.abs(java.time.Duration.between(wantedStart, start).toMinutes()));
                    candidates.add(alt);
                }
                candidates.sort(java.util.Comparator.comparingLong(a -> (Long) a.get("distance")));
                otherTimes.addAll(candidates.stream().limit(3).collect(Collectors.toList()));
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

    /** Cùng bộ lọc với API lấy lịch bác sĩ: quá khứ, đã đặt, bác sĩ tự chặn, phiên khác đang giữ. */
    private boolean isSlotFree(Long docId, java.time.LocalDate date, String slotStr,
                               List<String> bookedTimes,
                               List<com.bookinghealthy.model.DoctorBlockTime> blockedTimes,
                               String sessionId, long nowMillis) {
        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        String[] parts = slotStr.split(" - ");
        java.time.LocalTime slotStart = java.time.LocalTime.parse(parts[0], timeFormatter);
        java.time.LocalTime slotEnd = java.time.LocalTime.parse(parts[1], timeFormatter);

        if (date.isEqual(java.time.LocalDate.now()) && slotStart.isBefore(java.time.LocalTime.now())) return false;
        if (bookedTimes.contains(slotStr)) return false;

        for (com.bookinghealthy.model.DoctorBlockTime block : blockedTimes) {
            if (slotStart.isBefore(block.getEndTime()) && slotEnd.isAfter(block.getStartTime())) return false;
        }

        SlotLock lock = softLockCache.get(docId + "_" + date.toString() + "_" + slotStr);
        if (lock != null && nowMillis <= lock.expireAtMillis
                && sessionId != null && !sessionId.equals(lock.sessionId)) {
            return false;
        }
        return true;
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