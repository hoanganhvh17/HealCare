package com.bookinghealthy.service.impl;

import com.bookinghealthy.config.LeavePolicy;
import com.bookinghealthy.dto.DoctorDashboardStatsDTO;
import com.bookinghealthy.dto.DoctorInsightDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Review;
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.service.DoctorInsightService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Luật sinh 8 ô insight của dashboard bác sĩ. Không gọi mạng, không đụng DB — chỉ tính trên
 * số liệu controller truyền sang, nên gọi bao nhiêu lần cũng rẻ.
 *
 * Thứ tự các nhánh trong mỗi hàm là THEO MỨC ĐỘ CẦN HÀNH ĐỘNG GIẢM DẦN: việc gấp phải thắng
 * lời khen chung chung, nếu không bác sĩ sẽ đọc thấy "Hiệu suất tốt!" trong khi còn 5 hồ sơ
 * bệnh án chưa hoàn thành.
 */
@Service
public class DoctorInsightServiceImpl implements DoctorInsightService {

    private static final String OK = "text-success fw-bold";
    private static final String WARN = "text-warning text-dark fw-bold";
    private static final String DANGER = "text-danger fw-bold";
    private static final String MUTED = "text-muted";

    /** Số ca còn lại trong ngày bị coi là "lịch dày". */
    private static final int HEAVY_DAY_THRESHOLD = 10;
    /** Yêu cầu chờ duyệt quá số ngày này thì cảnh báo khách dễ huỷ. */
    private static final int STALE_REQUEST_DAYS = 3;
    /** Ngưỡng tỷ lệ huỷ (%) bị coi là cao / cần theo dõi. */
    private static final int CANCEL_RATE_HIGH = 20;
    private static final int CANCEL_RATE_WATCH = 10;
    /** Chênh lệch (%) so với kỳ trước mới đáng gọi là tăng/giảm. */
    private static final int TREND_THRESHOLD = 10;

    @Override
    public Map<String, DoctorInsightDTO> buildDashboardInsights(DoctorDashboardStatsDTO stats) {
        LocalDate today = LocalDate.now();
        Map<String, DoctorInsightDTO> insights = new LinkedHashMap<>();

        insights.put(KEY_TODAY, buildToday(stats, today));
        insights.put(KEY_PENDING, buildPending(stats, today));
        insights.put(KEY_COMPLETED, buildCompleted(stats, today));
        insights.put(KEY_RATING, buildRating(stats));
        insights.put(KEY_STATUS, buildStatus(stats));
        insights.put(KEY_RATING_DIST, buildRatingDist(stats));
        insights.put(KEY_SCHEDULE, buildSchedule(stats));
        insights.put(KEY_FEEDBACK, buildFeedback(stats));

        return insights;
    }

    // ===================== 1. CẦN KHÁM HÔM NAY =====================

    private DoctorInsightDTO buildToday(DoctorDashboardStatsDTO stats, LocalDate today) {
        String prompt = "Hôm nay tôi có những ca khám nào, khung giờ nào đông nhất và còn giờ trống nào không?";

        // Thẻ đếm ca ĐÃ XÁC NHẬN của hôm nay, nên "còn lại" ở đây phải đúng bằng con số đó —
        // gộp thêm ca PENDING vào là câu nhận định nói "còn 3 ca" trong khi thẻ ngay trên nó
        // ghi 2. Ca chờ duyệt đã có thẻ "Yêu cầu mới" lo.
        List<Booking> confirmedToday = new ArrayList<>();
        long done = 0;
        for (Booking b : safe(stats.getAllBookings())) {
            if (!today.equals(b.getAppointmentDate())) continue;
            if (b.getStatus() == BookingStatus.CONFIRMED) confirmedToday.add(b);
            else if (b.getStatus() == BookingStatus.COMPLETED) done++;
        }

        long remaining = stats.getCountToday();
        long total = remaining + done;

        if (total == 0) {
            String advice = stats.getIncompleteRecords() > 0
                    ? "Hôm nay chưa có ca khám nào. Đây là lúc hợp lý để hoàn thiện "
                        + stats.getIncompleteRecords() + " hồ sơ bệnh án còn tồn."
                    : "Hôm nay chưa có ca khám nào được đặt.";
            return new DoctorInsightDTO(advice, MUTED, prompt);
        }

        if (remaining == 0) {
            return new DoctorInsightDTO(
                    "Đã khám xong toàn bộ " + total + " ca hôm nay. Bác sĩ nghỉ ngơi nhé!",
                    OK, prompt);
        }

        if (remaining >= HEAVY_DAY_THRESHOLD) {
            return new DoctorInsightDTO(
                    "Lịch hôm nay khá dày: " + total + " ca, còn " + remaining
                            + " ca chưa khám. Cân nhắc phân bổ thời gian mỗi ca.",
                    DANGER, prompt);
        }

        String nextSlot = findNextAppointment(confirmedToday);
        String advice = "Còn " + remaining + "/" + total + " ca trong hôm nay"
                + (nextSlot != null ? ", ca gần nhất lúc " + nextSlot + "." : ".");
        return new DoctorInsightDTO(advice, remaining >= 6 ? WARN : OK, prompt);
    }

    /** Giờ bắt đầu của ca chưa khám gần nhất còn lại trong ngày, null nếu không xác định được. */
    private String findNextAppointment(List<Booking> confirmedToday) {
        LocalTime now = LocalTime.now();
        LocalTime next = null;
        for (Booking b : confirmedToday) {
            LocalTime start = parseSlotStart(b.getAppointmentTime());
            if (start == null || !start.isAfter(now)) continue;
            if (next == null || start.isBefore(next)) next = start;
        }
        return next != null ? next.toString() : null;
    }

    // ===================== 2. YÊU CẦU MỚI =====================

    private DoctorInsightDTO buildPending(DoctorDashboardStatsDTO stats, LocalDate today) {
        String prompt = "Liệt kê các yêu cầu đặt lịch đang chờ tôi duyệt và cho biết yêu cầu nào cần xử lý gấp nhất.";

        // Con số trên thẻ đến từ filteredBookings (chỉ tính tới hôm nay), nên mọi chỉ số phụ
        // của nhánh này cũng phải lấy từ ĐÚNG danh sách đó, bằng không câu nhận định sẽ nói
        // một đằng còn con số ngay phía trên nói một nẻo.
        List<Booking> pending = new ArrayList<>();
        for (Booking b : safe(stats.getFilteredBookings())) {
            if (b.getStatus() == BookingStatus.PENDING) pending.add(b);
        }

        // Yêu cầu cho những ngày TỚI nằm ngoài khoảng lọc nên không có trên thẻ — nhắc riêng
        // để bác sĩ không tưởng là đã hết việc.
        long upcoming = safe(stats.getAllBookings()).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                .filter(b -> b.getAppointmentDate() != null && b.getAppointmentDate().isAfter(today))
                .count();

        long total = stats.getCountPending();

        if (total == 0) {
            return upcoming == 0
                    ? new DoctorInsightDTO("Không có yêu cầu nào chờ duyệt. Bác sĩ đang theo kịp tiến độ.", MUTED, prompt)
                    : new DoctorInsightDTO("Không còn yêu cầu tồn đọng; có " + upcoming
                            + " yêu cầu cho những ngày tới đang chờ duyệt.", OK, prompt);
        }

        long overdue = pending.stream()
                .filter(b -> b.getAppointmentDate() != null && b.getAppointmentDate().isBefore(today))
                .count();
        if (overdue > 0) {
            return new DoctorInsightDTO(
                    total + " yêu cầu chờ duyệt, trong đó " + overdue
                            + " đã QUÁ ngày khám mà chưa được duyệt — cần xử lý ngay.",
                    DANGER, prompt);
        }

        long dueToday = pending.stream()
                .filter(b -> today.equals(b.getAppointmentDate()))
                .count();
        if (dueToday > 0) {
            return new DoctorInsightDTO(
                    total + " yêu cầu chờ duyệt, " + dueToday + " ca khám ngay trong hôm nay — nên duyệt gấp.",
                    DANGER, prompt);
        }

        long oldestDays = oldestPendingDays(pending, today);
        if (oldestDays >= STALE_REQUEST_DAYS) {
            return new DoctorInsightDTO(
                    total + " yêu cầu đang chờ, cũ nhất đã " + oldestDays
                            + " ngày. Bệnh nhân chờ lâu rất dễ huỷ lịch.",
                    WARN, prompt);
        }

        String advice = total + " yêu cầu mới chờ duyệt, chưa có yêu cầu nào gấp";
        advice += upcoming > 0 ? " (và " + upcoming + " yêu cầu cho những ngày tới)." : ".";
        return new DoctorInsightDTO(advice, OK, prompt);
    }

    private long oldestPendingDays(List<Booking> pending, LocalDate today) {
        return pending.stream()
                .filter(b -> b.getCreatedAt() != null)
                .map(b -> Duration.between(b.getCreatedAt().toLocalDate().atStartOfDay(), today.atStartOfDay()).toDays())
                .max(Comparator.naturalOrder())
                .orElse(0L);
    }

    // ===================== 3. ĐÃ HOÀN THÀNH =====================

    private DoctorInsightDTO buildCompleted(DoctorDashboardStatsDTO stats, LocalDate today) {
        String prompt = "Đánh giá hiệu suất khám của tôi trong kỳ này và kiểm tra xem còn hồ sơ bệnh án nào chưa hoàn thành không.";

        long completed = stats.getCountCompleted();
        long owed = stats.getIncompleteRecords();

        // Hồ sơ bệnh án còn nợ được ưu tiên trước mọi thứ: đó là việc bác sĩ làm được NGAY.
        if (owed > 0) {
            return new DoctorInsightDTO(
                    "Đã khám " + completed + " ca nhưng còn " + owed
                            + " hồ sơ bệnh án chưa hoàn thành — nên bổ sung sớm.",
                    DANGER, prompt);
        }

        if (completed == 0) {
            return new DoctorInsightDTO("Chưa có ca khám nào hoàn thành trong kỳ này.", MUTED, prompt);
        }

        Integer windowDays = rangeToDays(stats.getRange());
        if (windowDays != null) {
            // Kỳ trước cùng độ dài, nằm sát ngay trước khoảng đang xem.
            LocalDate prevEnd = today.minusDays(windowDays + 1L);
            LocalDate prevStart = today.minusDays(2L * windowDays + 1L);
            long prevCompleted = safe(stats.getAllBookings()).stream()
                    .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                    .filter(b -> b.getAppointmentDate() != null
                            && !b.getAppointmentDate().isBefore(prevStart)
                            && !b.getAppointmentDate().isAfter(prevEnd))
                    .count();

            if (prevCompleted > 0) {
                long changePct = Math.round((completed - prevCompleted) * 100.0 / prevCompleted);
                if (changePct >= TREND_THRESHOLD) {
                    return new DoctorInsightDTO(
                            "Hoàn thành " + completed + " ca, tăng " + changePct
                                    + "% so với kỳ trước. Hiệu suất đang đi lên.",
                            OK, prompt);
                }
                if (changePct <= -TREND_THRESHOLD) {
                    return new DoctorInsightDTO(
                            "Hoàn thành " + completed + " ca, giảm " + Math.abs(changePct)
                                    + "% so với kỳ trước.",
                            WARN, prompt);
                }
                return new DoctorInsightDTO(
                        "Hoàn thành " + completed + " ca, tương đương kỳ trước. Hồ sơ bệnh án đã đầy đủ.",
                        OK, prompt);
            }
        }

        // range = "Tất cả" (không có kỳ trước để so) hoặc kỳ trước chưa có dữ liệu.
        return new DoctorInsightDTO(
                "Hoàn thành " + completed + " ca khám. Hồ sơ bệnh án đã đầy đủ.",
                OK, prompt);
    }

    /** Độ dài khoảng lọc theo ngày; null nghĩa là "Tất cả" — không có kỳ trước để so sánh. */
    private Integer rangeToDays(String range) {
        if ("30".equals(range)) return 30;
        if ("7".equals(range)) return 7;
        return null;
    }

    // ===================== 4. ĐÁNH GIÁ TB =====================

    private DoctorInsightDTO buildRating(DoctorDashboardStatsDTO stats) {
        String prompt = "Hãy phân tích các đánh giá bằng chữ gần đây của bệnh nhân và chỉ ra điểm tôi làm tốt, điểm cần cải thiện.";

        // Xét SỐ LƯỢNG đánh giá chứ không xét null: getAverageRating trả về 0.0 (không phải null)
        // khi bác sĩ chưa có đánh giá nào, nên nhánh "chưa đủ đánh giá" cũ không bao giờ chạy và
        // bác sĩ mới nhận ngay cảnh báo đỏ "điểm đánh giá đang thấp".
        if (stats.getReviewCount() == 0) {
            return new DoctorInsightDTO("Chưa có đánh giá nào từ bệnh nhân để phân tích.", MUTED, prompt);
        }

        double avg = stats.getAvgRating() != null ? stats.getAvgRating() : 0.0;
        if (avg >= 4.5) {
            return new DoctorInsightDTO("Tuyệt vời! Hãy tiếp tục duy trì thái độ tích cực nhé.", OK, prompt);
        }
        if (avg >= 3.5) {
            return new DoctorInsightDTO("Tốt! Nhưng có vài điểm nhỏ cần cải thiện để đạt 5 sao.", WARN, prompt);
        }
        return new DoctorInsightDTO("Cảnh báo! Điểm đánh giá đang thấp, cần khắc phục ngay.", DANGER, prompt);
    }

    // ===================== 5. TỶ LỆ TRẠNG THÁI =====================

    private DoctorInsightDTO buildStatus(DoctorDashboardStatsDTO stats) {
        String prompt = "Phân tích tỷ lệ các trạng thái lịch hẹn của tôi và cho biết vì sao lịch bị huỷ nhiều hay ít.";

        long total = stats.getCountPending() + stats.getCountConfirmed()
                + stats.getCountCompleted() + stats.getCountCancelled();

        if (total == 0) {
            return new DoctorInsightDTO("Chưa có dữ liệu lịch hẹn trong kỳ này.", MUTED, prompt);
        }

        long cancelPct = Math.round(stats.getCountCancelled() * 100.0 / total);
        if (cancelPct >= CANCEL_RATE_HIGH) {
            return new DoctorInsightDTO(
                    "Tỷ lệ huỷ " + cancelPct + "% (" + stats.getCountCancelled() + "/" + total
                            + " ca) — cao hơn mức bình thường, nên xem lại nguyên nhân.",
                    DANGER, prompt);
        }
        if (cancelPct >= CANCEL_RATE_WATCH) {
            return new DoctorInsightDTO(
                    "Tỷ lệ huỷ " + cancelPct + "%, ở mức chấp nhận được nhưng nên theo dõi thêm.",
                    WARN, prompt);
        }

        long pendingPct = Math.round(stats.getCountPending() * 100.0 / total);
        if (pendingPct > 30) {
            return new DoctorInsightDTO(
                    stats.getCountPending() + " ca đang chờ duyệt, chiếm " + pendingPct
                            + "% tổng số — nên xử lý sớm để lịch không bị dồn.",
                    WARN, prompt);
        }

        return new DoctorInsightDTO(
                "Tỷ lệ huỷ chỉ " + cancelPct + "%, phần lớn lịch hẹn diễn ra đúng kế hoạch.",
                OK, prompt);
    }

    // ===================== 6. PHÂN BỐ ĐÁNH GIÁ =====================

    private DoctorInsightDTO buildRatingDist(DoctorDashboardStatsDTO stats) {
        String prompt = "Phân bố sao đánh giá của tôi đang nói lên điều gì, tôi nên cải thiện ở đâu để tăng số lượt 5 sao?";

        List<Integer> dist = stats.getRatingDist();
        if (dist == null || dist.size() < 5) {
            return new DoctorInsightDTO("Chưa có đánh giá nào để phân tích.", MUTED, prompt);
        }

        // ReviewServiceImpl.getRatingDistribution trả về theo thứ tự GIẢM DẦN: index 0 = 5 sao.
        int five = dist.get(0);
        int four = dist.get(1);
        int two = dist.get(3);
        int one = dist.get(4);
        int total = dist.stream().mapToInt(Integer::intValue).sum();

        if (total == 0) {
            return new DoctorInsightDTO("Chưa có đánh giá nào để phân tích.", MUTED, prompt);
        }

        int low = one + two;
        long lowPct = Math.round(low * 100.0 / total);
        if (lowPct >= 20) {
            return new DoctorInsightDTO(
                    "Có " + low + " lượt 1–2 sao (" + lowPct
                            + "% tổng số), nên đọc kỹ nội dung góp ý để cải thiện.",
                    DANGER, prompt);
        }

        long fivePct = Math.round(five * 100.0 / total);
        if (fivePct >= 70) {
            return new DoctorInsightDTO(
                    fivePct + "% đánh giá đạt 5 sao (" + five + "/" + total
                            + " lượt) — bệnh nhân rất hài lòng.",
                    OK, prompt);
        }

        int belowFour = total - five - four;
        return new DoctorInsightDTO(
                "Phần lớn đánh giá ở mức 4–5 sao (" + (five + four) + "/" + total
                        + " lượt), còn " + belowFour + " lượt dưới 4 sao cần lưu ý.",
                WARN, prompt);
    }

    // ===================== 7. LỊCH LÀM VIỆC TRONG TUẦN =====================

    private DoctorInsightDTO buildSchedule(DoctorDashboardStatsDTO stats) {
        String prompt = "Lịch làm việc tuần này của tôi thế nào, ngày nào đang trống và giờ trống gần nhất là khi nào?";

        Map<String, List<Schedule>> weekly = stats.getWeeklySchedule();
        if (weekly == null || weekly.isEmpty()) {
            // Hạn chốt lấy từ LeavePolicy, KHÔNG viết cứng ở đây — đó là nguồn duy nhất của mọi mốc.
            return new DoctorInsightDTO(
                    "Bác sĩ chưa đăng ký ca khám nào nên bệnh nhân không đặt được lịch. "
                            + "Hạn đăng ký tuần sau là " + deadlineLabel() + ".",
                    DANGER, prompt);
        }

        // Map chỉ chứa ngày CÓ ca (controller bỏ qua ngày rỗng), nên size() chính là số ngày phủ.
        int daysCovered = weekly.size();
        long totalMinutes = 0;
        for (List<Schedule> shifts : weekly.values()) {
            for (Schedule s : shifts) {
                if (s.getStartTime() != null && s.getEndTime() != null) {
                    totalMinutes += Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
                }
            }
        }
        long hours = Math.round(totalMinutes / 60.0);

        if (daysCovered < 5) {
            return new DoctorInsightDTO(
                    "Mới có " + daysCovered + "/7 ngày trong tuần có ca khám. "
                            + "Bệnh nhân sẽ không đặt được vào những ngày còn trống.",
                    WARN, prompt);
        }

        return new DoctorInsightDTO(
                "Đã phủ " + daysCovered + "/7 ngày trong tuần, tổng khoảng " + hours + " giờ khám.",
                OK, prompt);
    }

    private String deadlineLabel() {
        String day;
        switch (LeavePolicy.CLINIC_DEADLINE_DAY) {
            case MONDAY: day = "Thứ Hai"; break;
            case TUESDAY: day = "Thứ Ba"; break;
            case WEDNESDAY: day = "Thứ Tư"; break;
            case THURSDAY: day = "Thứ Năm"; break;
            case FRIDAY: day = "Thứ Sáu"; break;
            case SATURDAY: day = "Thứ Bảy"; break;
            default: day = "Chủ Nhật"; break;
        }
        return LeavePolicy.CLINIC_DEADLINE_TIME + " " + day;
    }

    // ===================== 8. PHẢN HỒI MỚI NHẤT =====================

    private DoctorInsightDTO buildFeedback(DoctorDashboardStatsDTO stats) {
        String prompt = "Tóm tắt các phản hồi mới nhất của bệnh nhân, nêu rõ điểm được khen và điểm bị phàn nàn.";

        List<Review> recent = stats.getRecentReviews();
        if (recent == null || recent.isEmpty()) {
            return new DoctorInsightDTO("Chưa có phản hồi nào từ bệnh nhân.", MUTED, prompt);
        }

        long lowCount = recent.stream()
                .filter(r -> r.getRating() != null && r.getRating() <= 2)
                .count();
        if (lowCount > 0) {
            return new DoctorInsightDTO(
                    "Có " + lowCount + " phản hồi thấp điểm trong " + recent.size()
                            + " lượt gần nhất — nên đọc kỹ để phản hồi lại bệnh nhân.",
                    DANGER, prompt);
        }

        double avg = recent.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
        String avgText = String.format("%.1f", avg);

        if (avg >= 4.5) {
            return new DoctorInsightDTO(
                    recent.size() + " phản hồi gần nhất trung bình " + avgText
                            + " sao — bệnh nhân đang rất hài lòng.",
                    OK, prompt);
        }
        return new DoctorInsightDTO(
                "Phản hồi gần đây trung bình " + avgText + " sao, có vài góp ý đáng lưu tâm.",
                WARN, prompt);
    }

    // ===================== TIỆN ÍCH =====================

    /**
     * Giờ bắt đầu của một khung giờ dạng "08:00 - 08:30".
     *
     * {@code Booking.appointmentTime} là String TỰ DO và dữ liệu cũ có những dòng không parse
     * được ("Sáng", "8h30"). Trả về null để chỗ gọi bỏ qua dòng đó — một dòng lạ từng làm sập
     * cả trợ lý AI của bác sĩ, đừng để nó làm sập luôn dashboard.
     */
    private LocalTime parseSlotStart(String appointmentTime) {
        if (appointmentTime == null) return null;
        String raw = appointmentTime.trim();
        int dash = raw.indexOf('-');
        if (dash > 0) raw = raw.substring(0, dash).trim();
        try {
            return LocalTime.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }
}
