package com.bookinghealthy.dto;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Review;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Toàn bộ số liệu của {@code /admin/dashboard} trong MỘT đối tượng.
 *
 * Trước đây 21 thuộc tính rời được nhét thẳng vào model từ 137 dòng số học nội tuyến trong
 * {@code AdminController.adminHome}. Gom về một DTO để phần số học có chỗ ở riêng
 * ({@code AdminDashboardService}), và để template chỉ đọc chứ không tính.
 *
 * <p><b>Dùng Lombok {@code @Getter} chứ KHÔNG dùng {@code record}</b>, dù record gọn hơn hẳn:
 * Thymeleaf đọc thuộc tính qua SpEL {@code ReflectivePropertyAccessor}, thứ tìm {@code getX()} chứ
 * không tìm accessor kiểu record {@code x()}. Cả dự án cũng đang theo khuôn Lombok
 * ({@code DoctorInsightDTO}, {@code AdminDashboardSummaryDTO}...). Đây là trang đích sau khi đăng
 * nhập — không đáng đánh cược nó vào một chi tiết của tầng biểu thức.
 *
 * <p>Quy ước phạm vi thời gian — in luôn trên trang để không ai hiểu nhầm:
 * <ul>
 *   <li><b>Theo kỳ đang chọn</b>: {@code money.gross/refunded/net}, {@link Ops}, {@link #daily},
 *       {@link #statusCounts}, {@link #byDepartment}, {@link #bySlot}.</li>
 *   <li><b>Toàn thời gian</b>: {@link #alerts}, {@code money.owed} và các số đối soát sổ ví,
 *       {@link #overdueTop10}, {@link #recentReviews}, {@link #recentBookings}, {@link #scale}.</li>
 * </ul>
 * Kỳ tính theo <b>ngày TẠO lịch hẹn</b> chứ không phải ngày thu tiền: {@code Booking} không có cột
 * {@code paidAt}, nên không có cách nào khác — và điều đó phải được nói ra trên giao diện.
 */
@Getter
@AllArgsConstructor
public class AdminDashboardDTO {

    private final RangeInfo range;
    private final Today today;
    private final List<Alert> alerts;
    private final Money money;
    private final Ops ops;
    private final List<DayPoint> daily;
    private final List<NameCount> statusCounts;
    private final List<NameCount> byDepartment;
    private final long departmentsWithNoBooking;
    private final long departmentTotal;
    private final List<NameCount> bySlot;
    private final List<Booking> overdueTop10;
    private final List<Review> recentReviews;
    private final List<Integer> ratingDist;
    private final List<Booking> recentBookings;
    private final Scale scale;
    private final Map<String, Insight> insights;

    /** Kỳ đang xem. {@code key} là giá trị của tham số {@code ?range=}: 7 / 30 / 90 / all. */
    @Getter
    @AllArgsConstructor
    public static class RangeInfo {
        private final String key;
        private final String label;
        private final LocalDate from;
        private final LocalDate to;
    }

    /** Dòng nhỏ cạnh tiêu đề. Cố ý KHÔNG phải thẻ số cỡ lớn — xem ghi chú trong dashboard.html. */
    @Getter
    @AllArgsConstructor
    public static class Today {
        private final long booked;
        private final long completed;
    }

    /**
     * Một dòng trong khối "Việc cần xử lý".
     *
     * Chỉ được dựng khi con số thật sự {@code > 0}: một dòng cảnh báo hiển thị số 0 là dòng không
     * bao giờ tắt, và một huy hiệu không bao giờ tắt còn tệ hơn không có huy hiệu.
     */
    @Getter
    @AllArgsConstructor
    public static class Alert {
        private final String id;
        private final String icon;
        /** {@code danger} | {@code warning} | {@code info} — quyết định màu ô icon. */
        private final String severity;
        private final String title;
        private final String detail;
        private final String href;
        private final String actionLabel;
    }

    /**
     * Bộ số tiền tự đối soát.
     *
     * {@link #net} KHÔNG có truy vấn riêng — nó được trừ trong service từ {@link #gross} và
     * {@link #refunded}. Đó là thứ bảo đảm ba thẻ trên màn hình luôn khớp phép trừ.
     *
     * {@link #ledgerRefund} đến từ sổ ví ({@code wallet_transactions}), một nguồn ĐỘC LẬP với bảng
     * {@code bookings}; {@link #ledgerDelta} là khoảng lệch giữa hai bên. Sổ ví không dùng làm
     * nguồn doanh thu được vì {@code payWithWallet} chỉ chạy ở nhánh WALLET.
     */
    @Getter
    @AllArgsConstructor
    public static class Money {
        private final BigDecimal gross;
        private final BigDecimal refunded;
        private final BigDecimal net;
        private final BigDecimal owed;
        private final BigDecimal owedUpcoming;
        private final BigDecimal owedCompleted;
        private final BigDecimal lost;
        private final long lostCount;
        private final double lostRate;
        private final BigDecimal ledgerRefund;
        private final BigDecimal ledgerDelta;

        /** Có lệch giữa sổ ví và bảng lịch hẹn không — quyết định câu chữ của dòng đối soát. */
        public boolean isLedgerGap() {
            return ledgerDelta != null && ledgerDelta.signum() != 0;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Ops {
        private final long newBookings;
        private final Trend bookingTrend;
        private final long completed;
        private final long canceled;
        private final double cancelRate;
        private final double avgRating;
        private final long reviewCount;
    }

    @Getter
    @AllArgsConstructor
    public static class Trend {
        /** {@code up} | {@code down} | {@code flat} */
        private final String direction;
        private final String percent;
    }

    @Getter
    @AllArgsConstructor
    public static class DayPoint {
        private final String label;
        private final long count;
    }

    @Getter
    @AllArgsConstructor
    public static class NameCount {
        private final String name;
        private final long count;
    }

    @Getter
    @AllArgsConstructor
    public static class Scale {
        private final long patients;
        private final long doctors;
        private final long departments;
        private final long services;
    }

    /**
     * Một ô "AI Insight".
     *
     * {@link #advice} là câu do LUẬT Java sinh ra, KHÔNG gọi mô hình lúc render — y hệt
     * {@code DoctorInsightService}. Phần AI thật nằm ở cú bấm: {@link #prompt} đi qua thuộc tính
     * {@code data-ai-prompt} rồi được gửi sang khung chat admin.
     */
    @Getter
    @AllArgsConstructor
    public static class Insight {
        private final String advice;
        /** Class Bootstrap tô màu câu nhận định: text-success / text-warning / text-danger / text-muted. */
        private final String colorClass;
        private final String prompt;
    }
}
