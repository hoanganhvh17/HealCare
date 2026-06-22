package com.bookinghealthy.service;

import com.bookinghealthy.dto.AdminDashboardSummaryDTO;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Review;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.ReviewRepository;
import com.bookinghealthy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReviewRepository reviewRepository;

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

        BigDecimal revenueThisMonth = bookingRepository.sumDepositByCreatedAtBetween(startOfThisMonth, now);
        BigDecimal revenueLastMonth = bookingRepository.sumDepositByCreatedAtBetween(startOfLastMonth, startOfThisMonth);
        stats.setRevenueThisMonth(revenueThisMonth != null ? revenueThisMonth : BigDecimal.ZERO);

        if (revenueLastMonth != null && revenueLastMonth.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal trend = stats.getRevenueThisMonth().subtract(revenueLastMonth)
                    .divide(revenueLastMonth, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stats.setRevenueTrend(trend.doubleValue());
        } else if (stats.getRevenueThisMonth().compareTo(BigDecimal.ZERO) > 0) {
            stats.setRevenueTrend(100.0); // Tăng 100% nếu tháng trước không có doanh thu
        } else {
            stats.setRevenueTrend(0.0);
        }

        BigDecimal totalRefunds = bookingRepository.sumTotalRefund();
        stats.setTotalRefunds(totalRefunds != null ? totalRefunds : BigDecimal.ZERO);

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
        if (totalBookings > 0) {
            stats.setCancellationRate(((double) cancelledBookings / totalBookings) * 100);
        } else {
            stats.setCancellationRate(0.0);
        }
        
        // Các nghiệp vụ khác như Khoa đông nhất, Bệnh nhân mới... sẽ được thêm sau để tránh phức tạp hóa ban đầu
        stats.setBusiestDepartment("Chưa có dữ liệu");
        stats.setNewPatientsThisMonth(0); // Cần query từ UserRepository

        return stats;
    }

    private AdminDashboardSummaryDTO.QualityAndHRStats getQualityAndHrStats() {
        AdminDashboardSummaryDTO.QualityAndHRStats stats = new AdminDashboardSummaryDTO.QualityAndHRStats();
        
        // Lấy các đánh giá tiêu cực (1 hoặc 2 sao)
        List<Review> negativeReviews = reviewRepository.findByRatingInOrderByCreatedAtDesc(List.of(1, 2));
        stats.setRecentNegativeReviews(negativeReviews);

        // Các nghiệp vụ xếp hạng bác sĩ sẽ được thêm sau
        stats.setTopPerformingDoctor("Chưa có dữ liệu");
        stats.setLowestPerformingDoctor("Chưa có dữ liệu");

        return stats;
    }
}