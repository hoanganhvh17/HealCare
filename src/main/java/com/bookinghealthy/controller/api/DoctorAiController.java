package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.ai.ChatRequest;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import com.bookinghealthy.model.Review;
import com.bookinghealthy.repository.ReviewRepository;
import com.bookinghealthy.service.ReviewService;
import org.springframework.http.HttpHeaders;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/doctor/chat")
public class DoctorAiController {

    @Autowired private AiService aiService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewService reviewService;

    /** Nguồn lưới khung giờ dùng chung — xem chú thích ở chỗ tính "giờ trống gần nhất". */
    @Autowired private com.bookinghealthy.service.TimeSlotService timeSlotService;

    /** Đường DUY NHẤT được phép tới ca khám đã đăng ký (ScheduleRepository.findEffective). */
    @Autowired private com.bookinghealthy.service.BookingService bookingService;

    private Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username);
        } else if (principal instanceof OAuth2User) {
            String email = ((OAuth2User) principal).getAttribute("email");
            if (email != null) return userRepository.findByEmail(email);
        } else {
            String name = auth.getName();
            Optional<User> userOpt = userRepository.findByUsername(name);
            if (userOpt.isEmpty()) userOpt = userRepository.findByEmail(name);
            return userOpt;
        }
        return Optional.empty();
    }

    private Optional<Doctor> getCurrentDoctor() {
        return getCurrentUser().flatMap(user -> doctorRepository.findByUserId(user.getId()));
    }

    @GetMapping("/welcome")
    public ResponseEntity<String> getWelcomeMessage() {
        Optional<Doctor> doctorOpt = getCurrentDoctor();
        if (doctorOpt.isEmpty()) {
            return ResponseEntity.ok("Xin chào Bác sĩ! Em là trợ lý AI thống kê lịch khám. Em có thể giúp gì cho bác sĩ hôm nay?");
        }
        Doctor doctor = doctorOpt.get();
        String doctorName = doctor.getUser().getFullName();

        LocalDate today = LocalDate.now();
        long todayTotalNoCancel = bookingRepository.countByDoctorIdAndStatusNotAndDateRange(doctor.getId(), BookingStatus.CANCELED, today, today);
        long todayCompleted = bookingRepository.countByDoctorIdAndStatusAndDateRange(doctor.getId(), BookingStatus.COMPLETED, today, today);
        long todayRemaining = Math.max(0, todayTotalNoCancel - todayCompleted);
        long incompleteRecords = bookingRepository.countIncompleteRecordsByDoctor(doctor.getId(), BookingStatus.COMPLETED, today);

        // Bọc thẻ span có ID để Frontend dễ dàng dùng JS ghi đè dữ liệu mới vào
        String message = String.format("<span id='live-welcome-stats'>Chào Bác sĩ **%s**! Hôm nay bác sĩ có **%d** lịch hẹn. Đã khám **%d**, còn lại **%d**. ",
                doctorName, todayTotalNoCancel, todayCompleted, todayRemaining);

        if (incompleteRecords > 0) {
            message += String.format("🚨 Bác sĩ lưu ý đang có **%d** hồ sơ bệnh án khám xong nhưng chưa hoàn thành nhé!</span>", incompleteRecords);
        } else {
            message += "Tất cả hồ sơ bệnh án của các ca đã khám đều được hoàn thành tốt ạ!</span>";
        }

        // Trả về kèm Header chống lưu cache để đảm bảo luôn lấy số mới nhất
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(message);
    }

    @GetMapping("/quick-review-advice")
    public ResponseEntity<Map<String, String>> getQuickReviewAdvice() {
        Optional<Doctor> doctorOpt = getCurrentDoctor();
        if (doctorOpt.isEmpty()) return ResponseEntity.status(403).build();

        Doctor currentDoctor = doctorOpt.get();
        Double avgRating = reviewService.getAverageRating(currentDoctor.getId());

        String advice = "Chưa có đủ đánh giá.";
        String colorClass = "text-muted";

        if (avgRating != null) {
            if (avgRating >= 4.5) {
                advice = "Tuyệt vời! Hãy tiếp tục duy trì thái độ tích cực nhé.";
                colorClass = "text-success fw-bold";
            } else if (avgRating >= 3.5) {
                advice = "Tốt! Nhưng có vài điểm nhỏ cần cải thiện để đạt 5 sao.";
                colorClass = "text-warning text-dark fw-bold";
            } else {
                advice = "Cảnh báo! Điểm đánh giá đang thấp, cần khắc phục ngay.";
                colorClass = "text-danger fw-bold";
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("advice", advice);
        response.put("colorClass", colorClass);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askAi(@RequestBody ChatRequest request) {
        Optional<Doctor> doctorOpt = getCurrentDoctor();
        if (doctorOpt.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("answer", "Vui lòng đăng nhập với tư cách Bác sĩ."));
        }

        Doctor doctor = doctorOpt.get();

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate startOfWeek = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(java.time.DayOfWeek.SUNDAY);
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        long todayActiveTotal = bookingRepository.countByDoctorIdAndStatusNotAndDateRange(doctor.getId(), BookingStatus.CANCELED, today, today);
        long todayCompleted = bookingRepository.countByDoctorIdAndStatusAndDateRange(doctor.getId(), BookingStatus.COMPLETED, today, today);
        long todayRemaining = Math.max(0, todayActiveTotal - todayCompleted);
        long todayTotal = bookingRepository.countByDoctorIdAndDateRange(doctor.getId(), today, today);
        long weekTotal = bookingRepository.countByDoctorIdAndDateRange(doctor.getId(), startOfWeek, endOfWeek);
        long weekCompleted = bookingRepository.countByDoctorIdAndStatusAndDateRange(doctor.getId(), BookingStatus.COMPLETED, startOfWeek, endOfWeek);
        long weekRemaining = Math.max(0, weekTotal - weekCompleted);
        long monthTotal = bookingRepository.countByDoctorIdAndDateRange(doctor.getId(), startOfMonth, endOfMonth);
        long monthCompleted = bookingRepository.countByDoctorIdAndStatusAndDateRange(doctor.getId(), BookingStatus.COMPLETED, startOfMonth, endOfMonth);
        long incompleteRecords = bookingRepository.countIncompleteRecordsByDoctor(doctor.getId(), BookingStatus.COMPLETED, today);

        List<Booking> upcomingBookings = bookingRepository.findDetailedBookingsForAi(doctor.getId(), today, tomorrow);
        StringBuilder scheduleDetails = new StringBuilder();

        // 1. Logic tìm CA KHÁM (BOOKED) và LỊCH TRỐNG (EMPTY)
        String nextAppointmentStr = "Hiện tại không còn ca khám nào chờ khám.";
        String nextEmptySlotStr = "Không còn lịch trống trong hôm nay và ngày mai.";

        java.time.LocalTime nowTime = java.time.LocalTime.now();
        boolean foundNextBooked = false;
        boolean foundNextEmpty = false;

        Set<String> bookedToday = new HashSet<>();
        Set<String> bookedTomorrow = new HashSet<>();

        if (upcomingBookings.isEmpty()) {
            scheduleDetails.append("Hiện tại không có ca khám nào trong hôm nay và ngày mai.\n");
        } else {
            for (Booking b : upcomingBookings) {
                String patientName = b.getUser() != null ? b.getUser().getFullName() : "Khách vãng lai";
                String fullTimeStr = b.getAppointmentTime();

                // Một dòng booking có appointmentTime lạ (null, "Sáng", "8h30" của dữ liệu cũ) từng
                // làm LocalTime.parse ném ra ngoài controller -> HTTP 500 cho TOÀN BỘ trợ lý bác sĩ,
                // chứ không riêng ca đó. Bỏ qua dòng hỏng, phần còn lại vẫn dùng được.
                java.time.LocalTime appointmentLocalTime = null;
                String startTimeStr = null;
                if (fullTimeStr != null) {
                    startTimeStr = fullTimeStr.split("-")[0].trim();
                    try {
                        appointmentLocalTime = java.time.LocalTime.parse(startTimeStr);
                    } catch (Exception badTime) {
                        System.err.println("[DoctorAi] Bỏ qua booking #" + b.getId()
                                + " vì giờ khám không đọc được: " + fullTimeStr);
                    }
                }
                if (appointmentLocalTime == null) continue;

                String dateStr = b.getAppointmentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                scheduleDetails.append(String.format("- Ngày %s | Giờ: %s | Bệnh nhân: %s | Trạng thái: %s\n",
                        dateStr, fullTimeStr, patientName, b.getStatus()));

                if (b.getStatus() != BookingStatus.CANCELED) {
                    if (b.getAppointmentDate().equals(today)) bookedToday.add(startTimeStr);
                    if (b.getAppointmentDate().equals(tomorrow)) bookedTomorrow.add(startTimeStr);

                    if (!foundNextBooked && b.getStatus() != BookingStatus.COMPLETED) {
                        java.time.LocalDateTime bookingDateTime = java.time.LocalDateTime.of(b.getAppointmentDate(), appointmentLocalTime);
                        if (bookingDateTime.isAfter(java.time.LocalDateTime.now())) {
                            nextAppointmentStr = String.format("Bệnh nhân **%s** vào ca **%s** ngày **%s**", patientName, fullTimeStr, dateStr);
                            foundNextBooked = true;
                        }
                    }
                }
            }
        }

        // 2. THUẬT TOÁN TÌM GIỜ TRỐNG GẦN NHẤT
        //
        // Lưới khung giờ ĐỌC LẠI từ TimeSlotService, tuyệt đối không liệt kê ở đây. Bản sao cũ nằm
        // ngay chỗ này còn sót 17:30/18:00/18:30/19:00 — các khung ca tối đã bị bỏ từ 24/07 — nên
        // trợ lý trả lời bác sĩ "rảnh lúc 18:30", một giờ mà bệnh nhân không đặt được ở đâu cả.
        //
        // Và phải lọc theo CA KHÁM bác sĩ đã đăng ký (BookingService là đường duy nhất được phép tới
        // ScheduleRepository.findEffective), nếu không thì ngày bác sĩ nghỉ vẫn báo "trống lúc 07:30".
        List<String> gridSlots = timeSlotService.allSlots();
        Set<String> offDutyToday = new HashSet<>(
                bookingService.slotsOutsideWorkingHours(doctor.getId(), today, gridSlots));
        Set<String> offDutyTomorrow = new HashSet<>(
                bookingService.slotsOutsideWorkingHours(doctor.getId(), tomorrow, gridSlots));

        for (String slot : gridSlots) {
            if (offDutyToday.contains(slot)) continue;
            String start = slot.split(" - ")[0].trim();
            java.time.LocalTime slotTime = java.time.LocalTime.parse(start);
            if (slotTime.isAfter(nowTime) && !bookedToday.contains(start)) {
                nextEmptySlotStr = String.format("Lúc **%s** Hôm nay (%s)", start, today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                foundNextEmpty = true;
                break;
            }
        }
        if (!foundNextEmpty) {
            for (String slot : gridSlots) {
                if (offDutyTomorrow.contains(slot)) continue;
                String start = slot.split(" - ")[0].trim();
                if (!bookedTomorrow.contains(start)) {
                    nextEmptySlotStr = String.format("Lúc **%s** Ngày mai (%s)", start, tomorrow.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    break;
                }
            }
        }

        // 3. Đọc dữ liệu Đánh giá
        List<Review> recentReviews = reviewRepository.findByDoctorId(doctor.getId());
        StringBuilder reviewTexts = new StringBuilder();
        int reviewCount = 0;
        for (Review r : recentReviews) {
            if (r.getComment() != null && !r.getComment().trim().isEmpty()) {
                reviewTexts.append("- ").append(r.getRating()).append(" sao: ").append(r.getComment()).append("\n");
                if (++reviewCount >= 10) break;
            }
        }
        if (reviewCount == 0) reviewTexts.append("Chưa có đánh giá chữ.\n");

        // 4. Đọc dữ liệu văn bản từ màn hình và ÉP KIỂU NGỮ CẢNH CHO AI
        String pageContextInfo = "";
        if (request.getPageContent() != null && !request.getPageContent().trim().isEmpty()) {
            pageContextInfo = "\n\n======================================================\n"
                    + "📋 THÔNG TIN BỆNH ÁN BÁC SĨ ĐANG MỞ TRÊN MÀN HÌNH:\n"
                    + request.getPageContent() + "\n"
                    + "======================================================\n"
                    + "🚨 CHỈ THỊ ÉP BUỘC ĐỐI VỚI BỆNH ÁN NÀY (HÃY ĐỌC KỸ):\n"
                    + "Nếu Bác sĩ yêu cầu 'tóm tắt', 'đánh giá', 'phân tích', hoặc hỏi bất cứ điều gì về 'bệnh nhân', 'bệnh án', 'toa thuốc', 'đơn thuốc'... BẠN PHẢI MẶC ĐỊNH lấy thông tin từ Bệnh án đang mở ở trên để trả lời mà KHÔNG CẦN Bác sĩ phải nói rõ là 'trên màn hình'.\n\n"
                    + "💡 KỊCH BẢN HỖ TRỢ BÁC SĨ (Hãy linh hoạt áp dụng tùy câu hỏi):\n"
                    + "- KHI ĐƯỢC YÊU CẦU TÓM TẮT: Trình bày cực kỳ súc tích: (1) Tên & Tuổi bệnh nhân, (2) Chẩn đoán chính, (3) Các chỉ số sinh tồn bất thường (nếu có), (4) Tổng quan thuốc đã kê.\n"
                    + "- KHI ĐƯỢC YÊU CẦU ĐÁNH GIÁ/PHÂN TÍCH: Hãy đóng vai trò hội chẩn. Kiểm tra xem các thuốc đã kê có tương tác xấu với nhau không? Có phù hợp với chẩn đoán (Mã ICD) không? Có cần cảnh báo tác dụng phụ gì cho bệnh nhân này không?\n"
                    + "- KHI ĐƯỢC YÊU CẦU LỜI DẶN: Dựa vào chẩn đoán, hãy đưa ra 3-5 gạch đầu dòng về chế độ dinh dưỡng, sinh hoạt, tập luyện để Bác sĩ copy và dặn dò bệnh nhân.\n";
        }

        // 5. NHỒI BIẾN VÀO PROMPT
        String contextMsg = String.format("Bạn là AI Trợ lý của hệ thống MediTrust. ĐỐI TƯỢNG BẠN ĐANG GIAO TIẾP LÀ BÁC SĨ.\n"
                        + "YÊU CẦU XƯNG HÔ TUYỆT ĐỐI NGHIÊM NGẶT: Bạn PHẢI tự xưng bản thân mình là 'em' (hoặc 'trợ lý') và luôn gọi người dùng là 'Bác sĩ'. TUYỆT ĐỐI CẤM tự nhận mình là bác sĩ và cấm gọi người dùng là em.\n\n"
                        + "--- THỐNG KÊ LỊCH KHÁM ---\n"
                        + "- HÔM NAY (%s): Tổng %d ca, đã khám %d ca, còn %d ca chưa khám (Tổng tất cả bao gồm hủy: %d ca)\n"
                        + "- TUẦN NÀY (%s đến %s): Tổng %d ca, đã khám %d ca, còn %d ca\n"
                        + "- THÁNG NÀY (%s): Tổng %d ca, đã khám %d ca\n"
                        + "- HỒ SƠ BỆNH ÁN CÒN NỢ: %d hồ sơ.\n"
                        + "👉 CA KHÁM GẦN NHẤT TIẾP THEO: %s\n"
                        + "👉 LỊCH TRỐNG GẦN NHẤT TIẾP THEO: %s\n\n"
                        + "--- CHI TIẾT LỊCH KHÁM (HÔM NAY & NGÀY MAI) ---\n"
                        + "%s\n\n"
                        + "--- QUY TẮC CHÍNH (TUYỆT ĐỐI TUÂN THỦ) ---\n"
                        + "1. PHÂN BIỆT RẠCH RÒI 'CA KHÁM' VÀ 'LỊCH TRỐNG':\n"
                        + "   - KHI BÁC SĨ HỎI 'LỊCH TIẾP THEO', 'CA TIẾP THEO', 'BỆNH NHÂN TIẾP THEO': Bạn CHỈ ĐƯỢC PHÉP dùng mục '👉 CA KHÁM GẦN NHẤT TIẾP THEO' để trả lời. TUYỆT ĐỐI KHÔNG được lôi lịch trống ra nói.\n"
                        + "   - KHI BÁC SĨ HỎI 'CÒN TRỐNG KHÔNG', 'LỊCH TRỐNG', 'KHI NÀO RẢNH': Bạn MỚI ĐƯỢC PHÉP dùng mục '👉 LỊCH TRỐNG GẦN NHẤT TIẾP THEO'.\n"
                        + "   - KHI BÁC SĨ HỎI 'HÔM NAY CÓ LỊCH GÌ KHÔNG', 'DANH SÁCH BỆNH NHÂN': Bạn PHẢI đọc phần 'CHI TIẾT LỊCH KHÁM' ra để báo cáo.\n"
                        + "2. HỎI VỀ THỐNG KÊ: Lấy dữ liệu ở mục 'THỐNG KÊ LỊCH KHÁM' để báo cáo.\n"
                        + "3. LỆNH ĐỊNH DẠNG TỐI THƯỢNG: TUYỆT ĐỐI CẤM tự chế ra các key JSON như 'total_cases'. NẾU hệ thống bắt buộc trả về JSON, BẮT BUỘC đặt TOÀN BỘ câu trả lời vào key \"ai_reply\".\n\n"
                        + "--- REVIEW ---\n%s\n"
                        + "%s\n"
                        + "Thời gian hiện tại: %s. Câu hỏi của Bác sĩ: %s",
                today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), todayActiveTotal, todayCompleted, todayRemaining, todayTotal,
                startOfWeek.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), endOfWeek.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), weekTotal, weekCompleted, weekRemaining,
                startOfMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")), monthTotal, monthCompleted,
                incompleteRecords,
                nextAppointmentStr,
                nextEmptySlotStr,
                scheduleDetails.toString(),
                reviewTexts.toString(),
                pageContextInfo,
                java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                request.getPrompt()
        );

        String answer = aiService.chatWithMemory(request.getSessionId() + "_doctor", contextMsg);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    @DeleteMapping("/clear/{sessionId}")
    public ResponseEntity<String> clearChat(@PathVariable String sessionId) {
        aiService.clearMemory(sessionId + "_doctor");
        return ResponseEntity.ok("Đã xóa lịch sử chat của phiên: " + sessionId);
    }
}