package com.bookinghealthy.service.impl;

import com.bookinghealthy.dto.AdminDashboardDTO;
import com.bookinghealthy.dto.AdminDashboardDTO.Alert;
import com.bookinghealthy.dto.AdminDashboardDTO.DayPoint;
import com.bookinghealthy.dto.AdminDashboardDTO.Insight;
import com.bookinghealthy.dto.AdminDashboardDTO.Money;
import com.bookinghealthy.dto.AdminDashboardDTO.NameCount;
import com.bookinghealthy.dto.AdminDashboardDTO.Ops;
import com.bookinghealthy.dto.AdminDashboardDTO.RangeInfo;
import com.bookinghealthy.dto.AdminDashboardDTO.Scale;
import com.bookinghealthy.dto.AdminDashboardDTO.Today;
import com.bookinghealthy.dto.AdminDashboardDTO.Trend;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.CandidateStatus;
import com.bookinghealthy.model.Post;
import com.bookinghealthy.model.TransactionType;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.CandidateRepository;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.PostRepository;
import com.bookinghealthy.repository.ReviewRepository;
import com.bookinghealthy.repository.ServiceRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.repository.WalletTransactionRepository;
import com.bookinghealthy.service.AdminDashboardService;
import com.bookinghealthy.service.ReviewService;
import com.bookinghealthy.service.TimeSlotService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Xem {@link AdminDashboardService}.
 *
 * Cố ý KHÔNG {@code @Transactional}: đây là một loạt truy vấn chỉ-đọc chạy trên luồng request, giữ
 * một transaction mở suốt cả loạt chỉ ghim một kết nối HikariCP (pool 10) mà không đổi lại được gì.
 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /**
     * "Đã thu" phải gồm CẢ {@code REFUNDED}: paymentStatus là trạng thái sống, một lịch đã thu tiền
     * rồi hoàn lại sẽ rời khỏi {@code PAID}. Lọc mỗi {@code PAID} là số tiền đó biến mất khỏi tổng
     * đã thu mà không để lại dấu vết — đúng lỗi của thẻ "Tổng tiền đặt cọc" cũ.
     */
    private static final List<String> GROSS_STATUSES = List.of("PAID", "REFUNDED");
    private static final List<String> REFUNDED_ONLY = List.of("REFUNDED");
    private static final List<String> EXPIRED_ONLY = List.of("EXPIRED");

    private static final List<BookingStatus> OWED_UPCOMING =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    private static final List<BookingStatus> OWED_COMPLETED = List.of(BookingStatus.COMPLETED);

    /** Trần số điểm của biểu đồ theo ngày. Kỳ "Tất cả" cũng chỉ vẽ chừng này ngày gần nhất. */
    private static final int MAX_CHART_DAYS = 90;
    private static final int TOP_DEPARTMENTS = 8;

    /** Mốc dưới cho kỳ "Tất cả" — sớm hơn mọi dòng dữ liệu có thể có. */
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Thứ tự cố định cho biểu đồ tròn: series và màu phải khớp nhau qua mọi lần tải trang. */
    private static final Map<BookingStatus, String> STATUS_LABELS = new LinkedHashMap<>();
    static {
        STATUS_LABELS.put(BookingStatus.PENDING, "Chờ xác nhận");
        STATUS_LABELS.put(BookingStatus.CONFIRMED, "Đã xác nhận");
        STATUS_LABELS.put(BookingStatus.COMPLETED, "Đã hoàn thành");
        STATUS_LABELS.put(BookingStatus.CANCELED, "Đã huỷ");
    }

    private final BookingRepository bookingRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final ServiceRepository serviceRepository;
    private final PostRepository postRepository;
    private final CandidateRepository candidateRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final TimeSlotService timeSlotService;

    public AdminDashboardServiceImpl(BookingRepository bookingRepository,
                                     WalletTransactionRepository walletTransactionRepository,
                                     UserRepository userRepository,
                                     DoctorRepository doctorRepository,
                                     DepartmentRepository departmentRepository,
                                     ServiceRepository serviceRepository,
                                     PostRepository postRepository,
                                     CandidateRepository candidateRepository,
                                     ReviewRepository reviewRepository,
                                     ReviewService reviewService,
                                     TimeSlotService timeSlotService) {
        this.bookingRepository = bookingRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.serviceRepository = serviceRepository;
        this.postRepository = postRepository;
        this.candidateRepository = candidateRepository;
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.timeSlotService = timeSlotService;
    }

    @Override
    public AdminDashboardDTO build(String rangeKey) {
        LocalDate today = LocalDate.now();
        RangeInfo range = resolveRange(rangeKey, today);

        LocalDateTime from = range.getFrom().atStartOfDay();
        LocalDateTime to = range.getTo().atTime(LocalTime.MAX);

        Money money = buildMoney(from, to);
        Ops ops = buildOps(range, from, to, today);
        List<NameCount> byDepartment = buildDepartments(from, to);
        long departmentTotal = departmentRepository.count();
        long departmentsWithNoBooking = Math.max(0, departmentTotal - byDepartment.size());

        List<Booking> overdue = bookingRepository
                .findTop10ByStatusAndAppointmentDateBeforeOrderByAppointmentDateAsc(BookingStatus.CONFIRMED, today);

        return new AdminDashboardDTO(
                range,
                new Today(
                        bookingRepository.countByAppointmentDateAndStatusIn(today, OWED_UPCOMING),
                        bookingRepository.countByAppointmentDateAndStatus(today, BookingStatus.COMPLETED)),
                buildAlerts(today, money),
                money,
                ops,
                buildDaily(range, today),
                buildStatusCounts(from, to),
                trim(byDepartment, TOP_DEPARTMENTS),
                departmentsWithNoBooking,
                departmentTotal,
                buildSlots(from, to),
                overdue,
                reviewService.getRecentGlobalReviews(),
                reviewService.getGlobalRatingDistribution(),
                bookingRepository.findTop20ByOrderByCreatedAtDesc(),
                new Scale(
                        userRepository.countByRoleName("ROLE_USER"),
                        doctorRepository.count(),
                        departmentTotal,
                        serviceRepository.count()),
                buildInsights(money, ops, byDepartment, departmentsWithNoBooking, departmentTotal, overdue, today));
    }

    // ===================================================================== kỳ

    private RangeInfo resolveRange(String key, LocalDate today) {
        String k = (key == null || key.isBlank()) ? DEFAULT_RANGE : key.trim();
        return switch (k) {
            case "7" -> new RangeInfo("7", "7 ngày qua", today.minusDays(6), today);
            case "90" -> new RangeInfo("90", "90 ngày qua", today.minusDays(89), today);
            case "all" -> new RangeInfo("all", "Toàn thời gian", EPOCH, today);
            // Mọi giá trị lạ rơi về mặc định. Dashboard là trang đích sau khi đăng nhập; ném lỗi
            // vì một query param nghĩa là admin bị chặn khỏi trang chủ của chính mình.
            default -> new RangeInfo(DEFAULT_RANGE, "30 ngày qua", today.minusDays(29), today);
        };
    }

    /** Số ngày của kỳ, hoặc rỗng với kỳ "Tất cả" (không có kỳ trước để so sánh). */
    private Optional<Long> lengthInDays(RangeInfo range) {
        if ("all".equals(range.getKey())) return Optional.empty();
        return Optional.of(ChronoUnit.DAYS.between(range.getFrom(), range.getTo()) + 1);
    }

    // =================================================================== tiền

    private Money buildMoney(LocalDateTime from, LocalDateTime to) {
        BigDecimal gross = bookingRepository.sumPriceByPaymentStatusInAndCreatedAtBetween(GROSS_STATUSES, from, to);
        BigDecimal refunded = bookingRepository.sumPriceByPaymentStatusInAndCreatedAtBetween(REFUNDED_ONLY, from, to);
        // Thực thu KHÔNG có truy vấn riêng: trừ ở đây thì ba thẻ trên màn hình luôn khớp phép trừ.
        BigDecimal net = nz(gross).subtract(nz(refunded));

        BigDecimal lost = bookingRepository.sumPriceByPaymentStatusInAndCreatedAtBetween(EXPIRED_ONLY, from, to);
        long lostCount = bookingRepository.countByPaymentStatus("EXPIRED");
        long totalBookings = bookingRepository.count();
        double lostRate = totalBookings > 0 ? (lostCount * 100.0 / totalBookings) : 0.0;

        // Tiền còn phải thu là SỐ DƯ HIỆN TẠI, không phải số phát sinh trong kỳ — cắt nó theo kỳ
        // sẽ trả lời một câu hỏi khác hẳn ("bao nhiêu của tuần này chưa thu"). Thẻ trên giao diện
        // phải ghi rõ "hiện tại" để không đứng lẫn với ba thẻ theo kỳ bên cạnh.
        BigDecimal owedUpcoming = bookingRepository.sumUnpaidByStatusIn(OWED_UPCOMING);
        BigDecimal owedCompleted = bookingRepository.sumUnpaidByStatusIn(OWED_COMPLETED);

        BigDecimal ledgerRefund = walletTransactionRepository.sumAmountByType(TransactionType.REFUND);
        BigDecimal bookingRefundAllTime = bookingRepository.sumPriceByPaymentStatusIn(REFUNDED_ONLY);

        return new Money(
                nz(gross), nz(refunded), net,
                nz(owedUpcoming).add(nz(owedCompleted)), nz(owedUpcoming), nz(owedCompleted),
                nz(lost), lostCount, lostRate,
                nz(ledgerRefund), nz(ledgerRefund).subtract(nz(bookingRefundAllTime)));
    }

    // ================================================================ vận hành

    private Ops buildOps(RangeInfo range, LocalDateTime from, LocalDateTime to, LocalDate today) {
        long newBookings = bookingRepository.countByCreatedAtBetween(from, to);

        Trend trend = lengthInDays(range)
                .map(days -> {
                    LocalDateTime prevTo = from.minusSeconds(1);
                    LocalDateTime prevFrom = from.minusDays(days);
                    return percentTrend(newBookings, bookingRepository.countByCreatedAtBetween(prevFrom, prevTo));
                })
                // Kỳ "Tất cả" KHÔNG có kỳ trước để so sánh. Trả null để thẻ không in mũi tên nào:
                // in "0,0% so với kỳ trước" ở đây là một câu khẳng định sai, không phải một số 0.
                .orElse(null);

        Map<BookingStatus, Long> statuses = rawStatusCounts(from, to);
        long completed = statuses.getOrDefault(BookingStatus.COMPLETED, 0L);
        long canceled = statuses.getOrDefault(BookingStatus.CANCELED, 0L);
        long inPeriod = statuses.values().stream().mapToLong(Long::longValue).sum();

        Double avg = reviewService.getGlobalAverageRating();
        return new Ops(newBookings, trend, completed, canceled,
                inPeriod > 0 ? (canceled * 100.0 / inPeriod) : 0.0,
                avg != null ? avg : 0.0,
                reviewRepository.count());
    }

    private Trend percentTrend(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? new Trend("up", "100,0") : new Trend("flat", "0,0");
        }
        double pct = (current - previous) * 100.0 / previous;
        String dir = pct > 0 ? "up" : (pct < 0 ? "down" : "flat");
        return new Trend(dir, String.format(Locale.forLanguageTag("vi"), "%.1f", Math.abs(pct)));
    }

    // ============================================================== biểu đồ

    private Map<BookingStatus, Long> rawStatusCounts(LocalDateTime from, LocalDateTime to) {
        Map<BookingStatus, Long> counts = new HashMap<>();
        for (Object[] row : bookingRepository.countByStatusInPeriod(from, to)) {
            if (row[0] instanceof BookingStatus s) counts.put(s, ((Number) row[1]).longValue());
        }
        return counts;
    }

    /** Luôn phát đủ 4 trạng thái, kể cả trạng thái có 0 dòng: series và màu của donut phải cố định. */
    private List<NameCount> buildStatusCounts(LocalDateTime from, LocalDateTime to) {
        Map<BookingStatus, Long> counts = rawStatusCounts(from, to);
        List<NameCount> out = new ArrayList<>();
        STATUS_LABELS.forEach((status, label) -> out.add(new NameCount(label, counts.getOrDefault(status, 0L))));
        return out;
    }

    private List<NameCount> buildDepartments(LocalDateTime from, LocalDateTime to) {
        List<NameCount> out = new ArrayList<>();
        for (Object[] row : bookingRepository.countByDepartmentInPeriod(from, to)) {
            String name = row[0] != null ? row[0].toString() : "Chưa gán khoa";
            out.add(new NameCount(name, ((Number) row[1]).longValue()));
        }
        return out;
    }

    /**
     * Mật độ theo khung giờ. Lưới 16 khung ĐỌC LẠI từ {@link TimeSlotService#allSlots()} chứ không
     * liệt kê lại ở đây — dự án đã có 11 nơi khai lưới này và {@code /skills/sync-slot-grid} phải
     * giữ nguyên con số đó.
     *
     * Khung giờ lạ (dữ liệu cũ từ thời còn ca tối) được nối vào cuối chứ không bị bỏ đi: một biểu
     * đồ âm thầm giấu dữ liệu còn tệ hơn một biểu đồ hơi dài.
     */
    private List<NameCount> buildSlots(LocalDateTime from, LocalDateTime to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : bookingRepository.countBySlotInPeriod(from, to)) {
            if (row[0] != null) counts.merge(row[0].toString().trim(), ((Number) row[1]).longValue(), Long::sum);
        }
        List<NameCount> out = new ArrayList<>();
        for (String slot : timeSlotService.allSlots()) {
            out.add(new NameCount(slot, counts.getOrDefault(slot, 0L)));
            counts.remove(slot);
        }
        counts.forEach((slot, n) -> out.add(new NameCount(slot, n)));
        return out;
    }

    /**
     * Chuỗi theo ngày, ĐÃ zero-fill.
     *
     * Truy vấn gộp không trả dòng nào cho ngày không có lịch hẹn, nên nếu vẽ thẳng thì biểu đồ nhảy
     * cóc qua ngày trống — trên DB dev, 7 ngày gần nhất chỉ có đúng 1 điểm và đường biểu đồ trông
     * như một chấm. Đây chính là lỗi của bản {@code getBookingStatsForLast7Days} cũ.
     */
    private List<DayPoint> buildDaily(RangeInfo range, LocalDate today) {
        LocalDate start = range.getFrom();
        long span = ChronoUnit.DAYS.between(start, today) + 1;
        if (span > MAX_CHART_DAYS) start = today.minusDays(MAX_CHART_DAYS - 1L);

        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : bookingRepository.countPerDayInPeriod(start.atStartOfDay(), today.atTime(LocalTime.MAX))) {
            LocalDate d = toLocalDate(row[0]);
            if (d != null) byDay.merge(d, ((Number) row[1]).longValue(), Long::sum);
        }

        List<DayPoint> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            out.add(new DayPoint(d.format(DAY_LABEL), byDay.getOrDefault(d, 0L)));
        }
        return out;
    }

    /** {@code FUNCTION('DATE', ...)} trả về kiểu gì là tuỳ driver — nhận cả ba dạng hay gặp. */
    private LocalDate toLocalDate(Object raw) {
        if (raw instanceof java.sql.Date d) return d.toLocalDate();
        if (raw instanceof LocalDate d) return d;
        if (raw instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (raw != null) return LocalDate.parse(raw.toString().substring(0, 10));
        return null;
    }

    // ============================================================== cảnh báo

    /**
     * Khối "Việc cần xử lý". Mỗi dòng chỉ được dựng khi con số thật sự lớn hơn 0 — một dòng cảnh
     * báo hiển thị số 0 là dòng không bao giờ tắt, và giao diện dạy người dùng bỏ qua nó.
     *
     * Cố ý tính TOÀN THỜI GIAN chứ không theo kỳ: một lịch treo từ tháng 6 không được phép biến mất
     * chỉ vì admin đang xem kỳ 7 ngày.
     */
    private List<Alert> buildAlerts(LocalDate today, Money money) {
        List<Alert> alerts = new ArrayList<>();

        long overdueCount = bookingRepository.countByStatusAndAppointmentDateBefore(BookingStatus.CONFIRMED, today);
        if (overdueCount > 0) {
            BigDecimal amount = nz(bookingRepository
                    .sumPriceByStatusAndAppointmentDateBefore(BookingStatus.CONFIRMED, today));
            List<Booking> oldest = bookingRepository
                    .findTop10ByStatusAndAppointmentDateBeforeOrderByAppointmentDateAsc(BookingStatus.CONFIRMED, today);
            String since = oldest.isEmpty() || oldest.get(0).getAppointmentDate() == null
                    ? "" : " · cũ nhất " + oldest.get(0).getAppointmentDate().format(FULL_DATE);
            alerts.add(new Alert("overdue", "bi-hourglass-bottom", "danger",
                    overdueCount + " lịch đã qua ngày hẹn nhưng chưa đóng",
                    money(amount) + " đang treo ở trạng thái \"Đã xác nhận\"" + since,
                    "/admin/manage-booking?filter=overdue", "Xử lý"));
        }

        if (money.getLostCount() > 0) {
            alerts.add(new Alert("expired", "bi-cart-x", "warning",
                    money.getLostCount() + " lịch bỏ dở khi thanh toán ("
                            + String.format(Locale.forLanguageTag("vi"), "%.1f", money.getLostRate()) + "%)",
                    money(money.getLost()) + " không thành đơn vì khách rời đi giữa lúc trả tiền",
                    "/admin/manage-booking?filter=expired", "Xem"));
        }

        if (money.getOwed().signum() > 0) {
            alerts.add(new Alert("owed", "bi-cash-coin", "warning",
                    money(money.getOwed()) + " chưa thu",
                    money(money.getOwedUpcoming()) + " ở lịch sắp tới · "
                            + money(money.getOwedCompleted()) + " ở ca đã khám xong",
                    "/admin/manage-booking?filter=unpaid", "Đối chiếu"));
        }

        long drafts = postRepository.countByStatus("DRAFT");
        if (drafts > 0) {
            Optional<Post> oldest = postRepository.findFirstByStatusOrderByCreatedAtAsc("DRAFT");
            String age = oldest.filter(p -> p.getCreatedAt() != null)
                    .map(p -> " · cũ nhất " + ChronoUnit.DAYS.between(p.getCreatedAt().toLocalDate(), today) + " ngày")
                    .orElse("");
            alerts.add(new Alert("drafts", "bi-newspaper", "info",
                    drafts + " bài viết nháp chờ duyệt",
                    "Tin tức thu thập tự động không tự lên trang" + age,
                    "/admin/manage-news", "Duyệt"));
        }

        long pendingCandidates = candidateRepository.countByStatus(CandidateStatus.PENDING);
        if (pendingCandidates > 0) {
            alerts.add(new Alert("candidates", "bi-person-lines-fill", "info",
                    pendingCandidates + " ứng viên đang chờ phản hồi",
                    "Mỗi dòng là một người đang đợi câu trả lời",
                    "/admin/candidates", "Xem"));
        }

        // Dữ liệu hỏng mã ký tự: cột appointment_type còn các dòng chứa dấu "?" thật, di chứng của
        // lỗi characterEncoding trong DB_URL. Chúng tự tách thành một nhóm "Dịch vụ" thứ hai trong
        // mọi thống kê theo loại khám, nên phải sửa chứ không phải làm ngơ.
        long mojibake = bookingRepository.countByAppointmentTypeContaining("?");
        if (mojibake > 0) {
            alerts.add(new Alert("mojibake", "bi-exclamation-diamond", "warning",
                    mojibake + " lịch hẹn hỏng mã ký tự",
                    "Trường \"Loại khám\" lưu sai bảng mã (hiện ra dạng \"D?ch v?\") nên bị đếm thành một nhóm riêng",
                    "/admin/manage-booking", "Kiểm tra"));
        }

        return alerts;
    }

    // ============================================================== AI Insight

    /**
     * Bốn ô AI Insight. Câu chữ là LUẬT Java trên chính những con số đã hiển thị phía trên — không
     * gọi mô hình lúc render (8 lượt gọi cho mỗi lần tải dashboard là không trả nổi). AI thật chạy
     * khi admin BẤM vào ô: {@code prompt} được gửi sang khung chat admin.
     */
    private Map<String, Insight> buildInsights(Money money, Ops ops,
                                               List<NameCount> byDepartment,
                                               long deptNoBooking, long deptTotal,
                                               List<Booking> overdue, LocalDate today) {
        Map<String, Insight> out = new LinkedHashMap<>();

        if (money.getLostCount() == 0) {
            out.put("money", new Insight(
                    "Không có lịch nào bị bỏ dở giữa lúc thanh toán. Luồng trả tiền đang trơn tru.",
                    "text-success",
                    "Doanh thu phòng khám đang ổn. Có cách nào tăng tỷ lệ khách hoàn tất thanh toán nữa không?"));
        } else {
            String colour = money.getLostRate() >= 20 ? "text-danger" : "text-warning";
            out.put("money", new Insight(
                    String.format(Locale.forLanguageTag("vi"),
                            "%.1f%% số lịch bị bỏ dở giữa lúc thanh toán, tương đương %s không thành đơn. "
                                    + "Đây là khoản mất lớn nhất, và nó nằm ở bước trả tiền chứ không phải ở khâu khám.",
                            money.getLostRate(), money(money.getLost())),
                    colour,
                    "Có " + money.getLostCount() + " lịch hẹn bị huỷ vì khách bỏ dở khi thanh toán, tương đương "
                            + money(money.getLost()) + ". Phân tích giúp tôi nguyên nhân thường gặp và cách giảm tỷ lệ này."));
        }

        if (byDepartment.isEmpty()) {
            out.put("department", new Insight("Kỳ này chưa có lịch hẹn nào để so sánh giữa các khoa.",
                    "text-muted", "Làm sao để tăng lượng đặt lịch cho phòng khám?"));
        } else {
            NameCount top = byDepartment.get(0);
            long total = byDepartment.stream().mapToLong(NameCount::getCount).sum();
            double share = total > 0 ? (top.getCount() * 100.0 / total) : 0.0;
            String advice = String.format(Locale.forLanguageTag("vi"),
                    "%s chiếm %.1f%% tổng lượt khám.", top.getName(), share);
            if (deptNoBooking > 0) {
                advice += " Còn " + deptNoBooking + "/" + deptTotal + " khoa chưa phát sinh lượt khám nào.";
            }
            out.put("department", new Insight(advice,
                    deptNoBooking > deptTotal / 2 ? "text-warning" : "text-muted",
                    "Khoa " + top.getName() + " đang chiếm phần lớn lượt khám trong khi " + deptNoBooking
                            + " khoa chưa có lượt nào. Nên cân đối lại thế nào?"));
        }

        if (overdue.isEmpty()) {
            out.put("overdue", new Insight("Không có lịch hẹn nào quá hạn mà chưa được đóng.",
                    "text-success", "Quy trình đóng hồ sơ sau khám của phòng khám có điểm nào cải thiện được không?"));
        } else {
            LocalDate oldest = overdue.get(0).getAppointmentDate();
            long days = oldest != null ? ChronoUnit.DAYS.between(oldest, today) : 0;
            out.put("overdue", new Insight(
                    "Lịch quá hạn cũ nhất đã " + days + " ngày chưa được đóng. Mỗi ca treo ở \"Đã xác nhận\" "
                            + "là một lần khám không có hồ sơ bệnh án đi kèm.",
                    days > 30 ? "text-danger" : "text-warning",
                    "Có nhiều lịch hẹn đã qua ngày khám nhưng vẫn ở trạng thái Đã xác nhận, cũ nhất "
                            + days + " ngày. Quy trình nào đang thiếu và nên xử lý số này ra sao?"));
        }

        // Luật bám reviewCount, KHÔNG bám điểm: getGlobalAverageRating trả 0.0 chứ không null khi
        // chưa ai đánh giá, nên xét theo điểm là gán nhãn "điểm thấp" cho một phòng khám chưa có
        // lượt đánh giá nào — đúng con bug đã từng báo đỏ mọi bác sĩ mới.
        if (ops.getReviewCount() == 0) {
            out.put("quality", new Insight("Chưa có lượt đánh giá nào, chưa đủ dữ liệu để kết luận về chất lượng.",
                    "text-muted", "Làm sao khuyến khích bệnh nhân để lại đánh giá sau khi khám?"));
        } else if (ops.getReviewCount() < 10) {
            out.put("quality", new Insight(
                    String.format(Locale.forLanguageTag("vi"),
                            "Điểm %.1f nhưng mới có %d lượt đánh giá — cỡ mẫu còn quá nhỏ để kết luận.",
                            ops.getAvgRating(), ops.getReviewCount()),
                    "text-muted",
                    "Phòng khám mới có " + ops.getReviewCount()
                            + " lượt đánh giá. Làm sao thu thập phản hồi đều đặn hơn sau mỗi ca khám?"));
        } else {
            String colour = ops.getAvgRating() >= 4.5 ? "text-success" : (ops.getAvgRating() >= 3.5 ? "text-warning" : "text-danger");
            out.put("quality", new Insight(
                    String.format(Locale.forLanguageTag("vi"), "Điểm trung bình %.1f trên %d lượt đánh giá.",
                            ops.getAvgRating(), ops.getReviewCount()),
                    colour,
                    "Tổng hợp giúp tôi các đánh giá thấp gần đây và bác sĩ nào đang bị phàn nàn nhiều nhất."));
        }

        return out;
    }

    // ================================================================ tiện ích

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String money(BigDecimal v) {
        return String.format(Locale.forLanguageTag("vi"), "%,d đ", nz(v).setScale(0, RoundingMode.HALF_UP).longValue())
                .replace(',', '.');
    }

    private static <T> List<T> trim(List<T> list, int max) {
        return list.size() <= max ? list : new ArrayList<>(list.subList(0, max));
    }
}
