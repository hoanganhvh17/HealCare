package com.bookinghealthy.dto;

import com.bookinghealthy.model.Review;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO này đóng vai trò là một "Bản Báo Cáo Tổng Hợp" chứa tất cả các chỉ số
 * quan trọng (KPIs) mà Admin quan tâm.
 * Dữ liệu trong DTO này sẽ được "nhồi" vào prompt để AI có thể đọc và phân tích.
 */
@Data
public class AdminDashboardSummaryDTO {

    // --- 1. Chỉ số tài chính ---
    private FinancialStats financialStats;

    // --- 2. Chỉ số hoạt động ---
    private OperationalStats operationalStats;

    // --- 3. Chỉ số chất lượng & nhân sự ---
    private QualityAndHRStats qualityAndHrStats;


    // --- Lớp con cho các chỉ số tài chính ---
    @Data
    public static class FinancialStats {
        private BigDecimal revenueThisMonth;
        private double revenueTrend; // % tăng/giảm so với tháng trước
        private BigDecimal totalRefunds;
    }

    // --- Lớp con cho các chỉ số hoạt động ---
    @Data
    public static class OperationalStats {
        private long newBookingsThisMonth;
        private double bookingTrend; // % tăng/giảm so với tháng trước
        private long newPatientsThisMonth;
        private double cancellationRate; // Tỷ lệ hủy lịch
        private String busiestDepartment; // Khoa đông khách nhất
    }

    // --- Lớp con cho các chỉ số chất lượng & nhân sự ---
    @Data
    public static class QualityAndHRStats {
        private String topPerformingDoctor; // Bác sĩ có rating cao nhất
        private String lowestPerformingDoctor; // Bác sĩ có rating thấp nhất
        private List<Review> recentNegativeReviews; // Các đánh giá tiêu cực gần đây
    }
}