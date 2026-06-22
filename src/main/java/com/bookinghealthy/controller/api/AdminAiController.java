package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.AdminDashboardSummaryDTO;
import com.bookinghealthy.dto.ai.ChatRequest;
import com.bookinghealthy.service.AdminDashboardService;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/chat")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiController {

    @Autowired private AdminDashboardService adminDashboardService;
    @Autowired private AiService aiService;
    @Autowired private PostService postService;

    private static final String ADMIN_SYSTEM_PROMPT_TEMPLATE =
            "Bạn là Trợ lý Điều hành AI của phòng khám MediTrust. Nhiệm vụ của bạn là phân tích dữ liệu và báo cáo tình hình kinh doanh cho Giám đốc (Admin) một cách chuyên nghiệp, súc tích và tập trung vào các con số. Luôn xưng là 'em' và gọi người dùng là 'sếp'.\n\n" +
            "Dưới đây là BÁO CÁO TỔNG HỢP TỰ ĐỘNG được trích xuất từ hệ thống. Dựa vào đây để trả lời câu hỏi của sếp:\n\n" +
            "%s\n\n" + // Placeholder for the summary data
            "--- QUY TẮC TRẢ LỜI ---\n" +
            "1.  **Tập trung vào dữ liệu:** Luôn trích dẫn con số chính xác từ báo cáo.\n" +
            "2.  **So sánh và phân tích:** Khi được hỏi về xu hướng (trend), hãy so sánh các chỉ số và đưa ra nhận định (ví dụ: 'Doanh thu tháng này tăng trưởng 15.2%% so với tháng trước').\n" +
            "3.  **Cảnh báo vấn đề:** Nếu thấy chỉ số tiêu cực (ví dụ: có đánh giá 1 sao, tỷ lệ hủy lịch cao), hãy chủ động nhấn mạnh vấn đề đó.\n" +
            "4.  **Tin tức y tế:** Nếu có bản nháp tin tức y tế khẩn cấp, hãy thông báo cho sếp biết và hướng dẫn sếp vào mục 'Quản lý Tin tức' để kiểm duyệt, sau đó có thể phân tích sơ bộ nếu sếp yêu cầu.\n" +
            "5.  **Tự nhiên:** Trả lời như một người trợ lý thực thụ, không chỉ liệt kê lại con số.\n\n" +
            "Bây giờ, hãy trả lời câu hỏi của sếp.";

    @GetMapping("/welcome")
    public ResponseEntity<Map<String, Object>> getWelcomeMessage() {
        AdminDashboardSummaryDTO summary = adminDashboardService.getDashboardSummary();
        long draftNewsCount = postService.countDrafts();
        
        DecimalFormat currencyFormatter = new DecimalFormat("#,###'đ'");
        String revenueStr = currencyFormatter.format(summary.getFinancialStats().getRevenueThisMonth());
        long newBookings = summary.getOperationalStats().getNewBookingsThisMonth();
        int negativeReviewsCount = summary.getQualityAndHrStats().getRecentNegativeReviews() != null ? summary.getQualityAndHrStats().getRecentNegativeReviews().size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("### 📊 Báo cáo nhanh hôm nay\n\n");
        sb.append(String.format("Chào sếp! Đây là tình hình hoạt động hiện tại:\n\n"));
        sb.append(String.format("- **Doanh thu tháng này:** `%s`\n", revenueStr));
        sb.append(String.format("- **Lịch hẹn mới:** `%d` ca\n", newBookings));
        
        if (negativeReviewsCount > 0) {
            sb.append(String.format("- **⚠️ Chú ý:** Có `%d` đánh giá tiêu cực mới cần xử lý.\n", negativeReviewsCount));
        } else {
            sb.append("- **Tình trạng:** Mọi thứ đều đang vận hành tốt.\n");
        }
        
        sb.append("\nSếp cần em hỗ trợ phân tích thêm thông tin gì không ạ?");

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(Map.of(
                    "message", sb.toString(),
                    "draftNewsCount", draftNewsCount
                ));
    }

    @PostMapping("/ask")
    public ResponseEntity<?> chatWithAdminAssistant(@RequestBody ChatRequest chatRequest) {
        String userPrompt = chatRequest.getPrompt().toLowerCase();
        AdminDashboardSummaryDTO summary = adminDashboardService.getDashboardSummary();
        long draftNewsCount = postService.countDrafts();

        // --- 1. XỬ LÝ NHANH CÁC CÂU LỆNH ĐƠN GIẢN (KHÔNG GỌI AI) ---
        String directResponse = detectSimpleIntent(userPrompt, summary, draftNewsCount);
        if (directResponse != null) {
            return ResponseEntity.ok(Map.of("answer", directResponse));
        }

        // --- 2. NẾU LÀ CÂU HỎI PHỨC TẠP MỚI GỌI AI ---
        // Format bản báo cáo thành một chuỗi text để "nhồi" vào prompt
        String summaryText = formatSummaryToText(summary, draftNewsCount);

        // Xây dựng "siêu prompt" cuối cùng
        String finalSystemPrompt = String.format(ADMIN_SYSTEM_PROMPT_TEMPLATE, summaryText);

        // Gọi đến AI service chung
        String aiResponse = aiService.getConversationalResponse(
                finalSystemPrompt,
                chatRequest.getPrompt(),
                chatRequest.getSessionId() + "_admin"
        );

        return ResponseEntity.ok(Map.of("answer", aiResponse));
    }

    private String detectSimpleIntent(String prompt, AdminDashboardSummaryDTO summary, long draftNewsCount) {
        DecimalFormat currencyFormatter = new DecimalFormat("#,###'đ'");
        DecimalFormat percentFormatter = new DecimalFormat("#,##0.0'%';-#,##0.0'%'");

        if (prompt.contains("tin khẩn cấp") || prompt.contains("bản nháp tin tức")) {
            if (draftNewsCount > 0) {
                return String.format("Dạ sếp, hệ thống vừa tự động thu thập được **%d bản tin y tế khẩn cấp**. Sếp vui lòng truy cập vào mục **Quản lý Tin tức** ở menu bên trái để kiểm duyệt và xuất bản nhé!", draftNewsCount);
            } else {
                return "Dạ, hiện tại không có bản tin khẩn cấp nào cần sếp duyệt ạ.";
            }
        }

        if (prompt.contains("doanh thu")) {
            return String.format("Dạ sếp, **doanh thu tháng này** hiện đạt **%s**. Xu hướng so với tháng trước là **%s** ạ.",
                    currencyFormatter.format(summary.getFinancialStats().getRevenueThisMonth()),
                    percentFormatter.format(summary.getFinancialStats().getRevenueTrend() / 100));
        }
        
        if (prompt.contains("hủy lịch") || prompt.contains("tỷ lệ hủy")) {
            return String.format("Dạ, **tỷ lệ hủy lịch** toàn hệ thống hiện là **%s**. Khoa có tỷ lệ hủy cao nhất đang được theo dõi sát sao sếp nhé.",
                    percentFormatter.format(summary.getOperationalStats().getCancellationRate() / 100));
        }

        if (prompt.contains("bác sĩ") && (prompt.contains("tốt nhất") || prompt.contains("cao nhất") || prompt.contains("top"))) {
            return String.format("Dạ sếp, bác sĩ đang có **chỉ số tốt nhất** là **%s**. Ngược lại, bác sĩ **%s** đang cần được hỗ trợ cải thiện thêm ạ.",
                    summary.getQualityAndHrStats().getTopPerformingDoctor(),
                    summary.getQualityAndHrStats().getLowestPerformingDoctor());
        }

        if (prompt.contains("đánh giá tiêu cực") || prompt.contains("1 sao") || prompt.contains("tệ")) {
            int count = summary.getQualityAndHrStats().getRecentNegativeReviews() != null ? summary.getQualityAndHrStats().getRecentNegativeReviews().size() : 0;
            if (count > 0) {
                return String.format("Dạ sếp, hệ thống ghi nhận **%d đánh giá tiêu cực** gần đây. Em đã liệt kê chi tiết trong danh sách quản lý để sếp xem xét rồi ạ.", count);
            } else {
                return "Dạ tin vui sếp ơi, gần đây **không có đánh giá tiêu cực** nào từ bệnh nhân ạ!";
            }
        }

        if (prompt.contains("lịch hẹn") || prompt.contains("đặt lịch")) {
            return String.format("Dạ, tháng này chúng ta có **%d lịch hẹn mới**. Xu hướng tăng trưởng đạt **%s**, khoa đông nhất là **%s** ạ.",
                    summary.getOperationalStats().getNewBookingsThisMonth(),
                    percentFormatter.format(summary.getOperationalStats().getBookingTrend() / 100),
                    summary.getOperationalStats().getBusiestDepartment());
        }

        return null; // Không bắt được intent đơn giản, chuyển qua AI xử lý
    }

    // Giữ endpoint cũ cho các file JS chưa update
    @PostMapping
    public ResponseEntity<?> oldChatWithAdminAssistant(@RequestBody ChatRequest chatRequest) {
        return chatWithAdminAssistant(chatRequest);
    }

    @DeleteMapping("/clear/{sessionId}")
    public ResponseEntity<String> clearChat(@PathVariable String sessionId) {
        aiService.clearMemory(sessionId + "_admin");
        return ResponseEntity.ok("Đã xóa lịch sử chat của phiên Admin: " + sessionId);
    }

    private String formatSummaryToText(AdminDashboardSummaryDTO summary, long draftNewsCount) {
        DecimalFormat currencyFormatter = new DecimalFormat("#,###'đ'");
        DecimalFormat percentFormatter = new DecimalFormat("#,##0.0'%';-#,##0.0'%'");

        AdminDashboardSummaryDTO.FinancialStats fin = summary.getFinancialStats();
        AdminDashboardSummaryDTO.OperationalStats ops = summary.getOperationalStats();
        AdminDashboardSummaryDTO.QualityAndHRStats qhr = summary.getQualityAndHrStats();

        StringBuilder sb = new StringBuilder();
        sb.append("--- BÁO CÁO TÀI CHÍNH ---\n");
        sb.append(String.format("- Doanh thu tháng này: %s (Xu hướng: %s)\n", currencyFormatter.format(fin.getRevenueThisMonth()), percentFormatter.format(fin.getRevenueTrend() / 100)));
        sb.append(String.format("- Tổng tiền đã hoàn trả: %s\n\n", currencyFormatter.format(fin.getTotalRefunds())));

        sb.append("--- HIỆU SUẤT HOẠT ĐỘNG ---\n");
        sb.append(String.format("- Lịch hẹn mới tháng này: %d (Xu hướng: %s)\n", ops.getNewBookingsThisMonth(), percentFormatter.format(ops.getBookingTrend() / 100)));
        sb.append(String.format("- Tỷ lệ hủy lịch toàn hệ thống: %s\n", percentFormatter.format(ops.getCancellationRate() / 100)));
        sb.append(String.format("- Khoa đông khách nhất: %s\n\n", ops.getBusiestDepartment()));

        sb.append("--- CHẤT LƯỢNG DỊCH VỤ & NHÂN SỰ ---\n");
        sb.append(String.format("- Bác sĩ được đánh giá cao nhất: %s\n", qhr.getTopPerformingDoctor()));
        sb.append(String.format("- Bác sĩ cần chú ý: %s\n", qhr.getLowestPerformingDoctor()));
        if (qhr.getRecentNegativeReviews() != null && !qhr.getRecentNegativeReviews().isEmpty()) {
            sb.append("- Các đánh giá tiêu cực gần đây:\n");
            qhr.getRecentNegativeReviews().stream().limit(3).forEach(review -> {
                sb.append(String.format("  + %d sao - \"%s\" (Bác sĩ: %s)\n",
                        review.getRating(),
                        review.getComment(),
                        review.getBooking().getDoctor().getUser().getFullName()));
            });
        } else {
            sb.append("- Không có đánh giá tiêu cực nào gần đây.\n");
        }
        
        sb.append("\n--- CẢNH BÁO HỆ THỐNG ---\n");
        sb.append(String.format("- Số bản nháp tin tức y tế khẩn cấp đang chờ duyệt: %d\n", draftNewsCount));

        return sb.toString();
    }
}