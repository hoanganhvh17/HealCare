package com.bookinghealthy.service;

import com.bookinghealthy.dto.AdminDashboardSummaryDTO;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Review;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.ReviewRepository;
import com.bookinghealthy.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo tổng hợp cho KHUNG CHAT AI của admin ({@code AdminAiController}).
 *
 * <p><b>Trước đây lớp này tên là {@code AdminDashboardService}</b>, nhưng nó chưa từng nuôi trang
 * {@code /admin/dashboard} — nó chỉ nhồi số liệu vào prompt. Cái tên đó nay thuộc về service thật
 * của dashboard; đổi tên ở đây để hai thứ không còn bị nhầm là một.
 *
 * <p>Bốn giá trị từng bị hardcode ({@code "Chưa có dữ liệu"} cho khoa đông nhất và cho hai bác sĩ,
 * {@code 0} cho bệnh nhân mới) nay được tính thật. Chúng nguy hiểm hơn vẻ ngoài: {@code AdminAiController}
 * in thẳng chúng vào báo cáo gửi cho admin <b>như thể là dữ liệu có thật</b>, nên trợ lý AI đang nói
 * dối về chính hoạt động của phòng khám.
 *
 * <p>Riêng "bệnh nhân mới trong tháng" là <b>không tính được</b>: {@code User} không có cột
 * {@code createdAt}. Trường đó đã được thay bằng tổng số bệnh nhân — một con số có thật — thay vì
 * giữ lại một số 0 giả.
 */
@Service
public class AdminAiReportService {

    /** Giống dashboard: "đã thu" phải gồm cả REFUNDED, vì paymentStatus là trạng thái sống. */
    private static final List<String> GROSS_STATUSES = List.of("PAID", "REFUNDED");
    private static final List<String> REFUNDED_ONLY = List.of("REFUNDED");
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;

    public AdminAiReportService(BookingRepository bookingRepository,
                                UserRepository userRepository,
                                DoctorRepository doctorRepository,
                                ReviewRepository reviewRepository,
                                ReviewService reviewService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
    }

    public AdminDashboardSummaryDTO getDashboardSummary() {
        AdminDashboardSummaryDTO summary = new AdminDashboardSummaryDTO();
        summary.setFinancialStats(getFinancialStats());
        summary.setOperationalStats(getOperationalStats());
        summary.setQualityAndHrStats(getQualityAndHrStats());
        return summary;
    }

    private AdminDashboardSummaryDTO.FinancialStats getFinancialStats() {
        AdminDashboardSummaryDTO.FinancialStats stats = new AdminDashboardSummaryDTO.FinancialStats();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfThisMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);

        BigDecimal revenueThisMonth = bookingRepository
                .sumPriceByPaymentStatusInAndCreatedAtBetween(GROSS_STATUSES, startOfThisMonth, now);
        BigDecimal revenueLastMonth = bookingRepository
                .sumPriceByPaymentStatusInAndCreatedAtBetween(GROSS_STATUSES, startOfLastMonth, startOfThisMonth);
        stats.setRevenueThisMonth(nz(revenueThisMonth));

        if (nz(revenueLastMonth).compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal trend = stats.getRevenueThisMonth().subtract(revenueLastMonth)
                    .divide(revenueLastMonth, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stats.setRevenueTrend(trend.doubleValue());
        } else if (stats.getRevenueThisMonth().compareTo(BigDecimal.ZERO) > 0) {
            stats.setRevenueTrend(100.0);
        } else {
            stats.setRevenueTrend(0.0);
        }

        stats.setTotalRefunds(nz(bookingRepository.sumPriceByPaymentStatusIn(REFUNDED_ONLY)));
        return stats;
    }

    private AdminDashboardSummaryDTO.OperationalStats getOperationalStats() {
        AdminDashboardSummaryDTO.OperationalStats stats = new AdminDashboardSummaryDTO.OperationalStats();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfThisMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);

        long bookingsThisMonth = bookingRepository.countByCreatedAtBetween(startOfThisMonth, now);
        long bookingsLastMonth = bookingRepository.countByCreatedAtBetween(startOfLastMonth, startOfThisMonth);
        stats.setNewBookingsThisMonth(bookingsThisMonth);

        if (bookingsLastMonth > 0) {
            stats.setBookingTrend(((double) (bookingsThisMonth - bookingsLastMonth) / bookingsLastMonth) * 100);
        } else if (bookingsThisMonth > 0) {
            stats.setBookingTrend(100.0);
        } else {
            stats.setBookingTrend(0.0);
        }

        long totalBookings = bookingRepository.count();
        long cancelledBookings = bookingRepository.countByStatus(BookingStatus.CANCELED);
        stats.setCancellationRate(totalBookings > 0 ? ((double) cancelledBookings / totalBookings) * 100 : 0.0);

        // Khoa đông khách nhất — MỘT truy vấn gộp, không lặp theo từng khoa.
        List<Object[]> byDept = bookingRepository.countByDepartmentInPeriod(
                EPOCH.atStartOfDay(), LocalDate.now().atTime(LocalTime.MAX));
        stats.setBusiestDepartment(byDept.isEmpty() || byDept.get(0)[0] == null
                ? "Chưa có lịch hẹn nào"
                : byDept.get(0)[0].toString() + " (" + ((Number) byDept.get(0)[1]).longValue() + " lượt)");

        // "Bệnh nhân mới trong tháng" KHÔNG tính được — User không có cột createdAt. Báo tổng số
        // bệnh nhân, một con số có thật, thay vì trả 0 và để AI đọc số 0 đó thành sự thật.
        stats.setTotalPatients(userRepository.countByRoleName("ROLE_USER"));
        return stats;
    }

    private AdminDashboardSummaryDTO.QualityAndHRStats getQualityAndHrStats() {
        AdminDashboardSummaryDTO.QualityAndHRStats stats = new AdminDashboardSummaryDTO.QualityAndHRStats();
        stats.setRecentNegativeReviews(reviewRepository.findByRatingInOrderByCreatedAtDesc(List.of(1, 2)));

        List<Doctor> doctors = doctorRepository.findAll();
        Map<Long, ReviewService.RatingStats> ratings = reviewService.getRatingStats(
                doctors.stream().map(Doctor::getId).toList());

        // CHỈ xếp hạng bác sĩ ĐÃ CÓ đánh giá. getAverageRating trả 0.0 chứ không null khi chưa ai
        // chấm, nên xếp theo điểm trần sẽ dìm bác sĩ mới xuống đáy bảng "cần chú ý" một cách oan uổng
        // — và đẩy điểm 5.0 quảng cáo của người chưa ai chấm lên đầu bảng "tốt nhất".
        List<Doctor> rated = doctors.stream()
                .filter(d -> ratings.getOrDefault(d.getId(), new ReviewService.RatingStats(0, 0)).hasReal())
                .sorted(Comparator.comparingDouble(d -> ratings.get(d.getId()).average()))
                .toList();

        if (rated.isEmpty()) {
            stats.setTopPerformingDoctor("Chưa có bác sĩ nào được đánh giá");
            stats.setLowestPerformingDoctor("Chưa có bác sĩ nào được đánh giá");
        } else {
            stats.setTopPerformingDoctor(describe(rated.get(rated.size() - 1), ratings));
            stats.setLowestPerformingDoctor(describe(rated.get(0), ratings));
        }
        return stats;
    }

    /** Luôn kèm SỐ LƯỢT: "5,0 sao" từ một lượt đánh giá không nói lên điều gì mà không có cỡ mẫu. */
    private String describe(Doctor doctor, Map<Long, ReviewService.RatingStats> ratings) {
        ReviewService.RatingStats s = ratings.get(doctor.getId());
        String name = doctor.getUser() != null ? doctor.getUser().getFullName() : "Bác sĩ #" + doctor.getId();
        return String.format(java.util.Locale.forLanguageTag("vi"),
                "%s (%.1f sao / %d lượt)", name, s.average(), s.count());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
