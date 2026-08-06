package com.bookinghealthy.dto;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Review;
import com.bookinghealthy.model.Schedule;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Gói toàn bộ số liệu của một lần mở dashboard bác sĩ, để {@code DoctorInsightService}
 * sinh ra 8 ô "AI Insight" mà không phải truy vấn lại.
 *
 * Cùng vai trò với {@link AdminDashboardSummaryDTO} bên phía admin. Controller đã đọc sẵn
 * bookings / đánh giá / lịch làm việc để render trang, nên truyền thẳng qua đây; để service
 * tự query lại là thêm 4 lượt round-trip DB mỗi lần mở dashboard, mà con số còn có nguy cơ
 * lệch với con số đang in trên chính thẻ đó.
 *
 * CỐ Ý KHÔNG có {@code @AllArgsConstructor}: 14 trường cùng kiểu số/list rất dễ truyền nhầm
 * thứ tự, và thêm trường mới sẽ lặng lẽ làm sai mọi chỗ gọi (đúng cái bẫy mà
 * {@code DataInitializer} đang mắc với User/Doctor/Department).
 */
@Data
@NoArgsConstructor
public class DoctorDashboardStatsDTO {

    /** Bộ lọc đang chọn trên trang: "7" | "30" | "all". "all" thì không so sánh kỳ trước được. */
    private String range;

    // --- Thống kê lịch hẹn trong khoảng đang lọc ---
    private long countPending;
    private long countConfirmed;
    private long countCompleted;
    private long countCancelled;

    /** Số ca CONFIRMED của riêng hôm nay — cố ý KHÔNG theo bộ lọc range. */
    private long countToday;

    /** Số ca đã khám xong nhưng chưa có hồ sơ bệnh án. */
    private long incompleteRecords;

    // --- Đánh giá ---
    private Double avgRating;
    private long reviewCount;

    /** Phân bố sao theo thứ tự GIẢM DẦN: [SL 5 sao, 4 sao, 3 sao, 2 sao, 1 sao]. */
    private List<Integer> ratingDist;

    /** 5 đánh giá mới nhất. */
    private List<Review> recentReviews;

    /** Ca khám đã đăng ký, gom theo tên thứ. CHỈ chứa ngày có ca (ngày trống bị bỏ qua). */
    private Map<String, List<Schedule>> weeklySchedule;

    // --- Dữ liệu thô để tính các chỉ số chi tiết hơn ---
    /** Toàn bộ lịch hẹn của bác sĩ (mọi thời điểm). */
    private List<Booking> allBookings;

    /** Lịch hẹn đã lọc theo range. */
    private List<Booking> filteredBookings;
}
