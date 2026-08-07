package com.bookinghealthy.service;

import com.bookinghealthy.dto.DoctorDashboardStatsDTO;
import com.bookinghealthy.dto.DoctorInsightDTO;

import java.util.Map;

/**
 * Sinh các ô "AI Insight" trên dashboard bác sĩ.
 *
 * LUẬT CỐ ĐỊNH, KHÔNG GỌI LLM. Đây là cách ô "Đánh giá TB" vẫn chạy từ trước (if/else trên
 * điểm trung bình), nay áp cho cả 8 ô. AI thật chỉ chạy khi bác sĩ BẤM vào ô: mỗi
 * {@link DoctorInsightDTO} mang theo một câu hỏi soạn sẵn, JS bắn sang {@code /api/doctor/chat/ask}.
 *
 * Nhờ vậy trang không phải chờ mạng, không tốn chi phí API, và câu nhận định luôn khớp
 * chính xác con số in ngay phía trên nó (cùng dùng một biến).
 */
public interface DoctorInsightService {

    /** Khoá của ô "Cần khám hôm nay". */
    String KEY_TODAY = "today";
    /** Khoá của ô "Yêu cầu mới". */
    String KEY_PENDING = "pending";
    /** Khoá của ô "Đã hoàn thành". */
    String KEY_COMPLETED = "completed";
    /** Khoá của ô "Đánh giá TB". */
    String KEY_RATING = "rating";
    /** Khoá của ô "Tỷ lệ Trạng thái". */
    String KEY_STATUS = "status";
    /** Khoá của ô "Phân bố Đánh giá". */
    String KEY_RATING_DIST = "ratingDist";
    /** Khoá của ô "Lịch làm việc trong tuần". */
    String KEY_SCHEDULE = "schedule";
    /** Khoá của ô "Phản hồi mới nhất". */
    String KEY_FEEDBACK = "feedback";

    /**
     * Dựng 8 ô insight cho một lần mở dashboard.
     *
     * @param stats số liệu controller đã đọc sẵn khi render trang
     * @return map key (các hằng {@code KEY_*} ở trên) → nội dung ô; template tra bằng {@code ${insights.today}}
     */
    Map<String, DoctorInsightDTO> buildDashboardInsights(DoctorDashboardStatsDTO stats);
}
