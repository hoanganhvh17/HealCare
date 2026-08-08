package com.bookinghealthy.config;

import java.util.List;

/**
 * Bảng NGUỒN tin y tế chính thống mà hệ thống được phép thu thập.
 *
 * Chỉ chứa dữ liệu thuần — phần đi lấy tin nằm ở {@code NewsFeedServiceImpl}, phần biến bài
 * lấy được thành {@code Post} nằm ở {@code MedicalNewsTask}. Cùng khuôn với
 * {@link DoctorSeedData}: dữ liệu tra cứu để riêng, logic để chỗ khác.
 *
 * ĐÂY LÀ DANH SÁCH CHO PHÉP (allow-list), không phải gợi ý. Task tuyệt đối không đi lấy tin ở
 * ngoài danh sách này — đó là toàn bộ ý nghĩa của chữ "chính thống": mình chọn nguồn, chứ không
 * để model tự đi tìm trên internet rồi vớ phải blog rác.
 *
 * Trước khi bật một nguồn mới, PHẢI mở thẳng URL đó bằng trình duyệt từ chính máy chạy app và
 * xác nhận nó trả về RSS chứ không phải trang HTML hay trang chặn — nhiều báo đổi đường dẫn RSS
 * mà không chuyển hướng, và một feed hỏng chỉ hiện ra dưới dạng "không lấy được tin nào".
 */
public final class NewsSourceCatalog {

    private NewsSourceCatalog() {
    }

    /**
     * @param name    tên hiển thị cho bệnh nhân ở khối "Nguồn:" cuối bài
     * @param rssUrl  địa chỉ RSS
     * @param enabled false = tạm tắt, vẫn giữ lại trong bảng để biết đã cân nhắc nguồn này rồi
     */
    public record NewsSource(String name, String rssUrl, boolean enabled) {
    }

    public static final List<NewsSource> SOURCES = List.of(
            // Đã kiểm chứng ngày 2026-08-08: trả về RSS 2.0 hợp lệ, có title/link/pubDate/description.
            // VnExpress còn nhúng sẵn thẻ <img> trong description — đó là ảnh minh họa của bài.
            new NewsSource("VnExpress Sức khỏe", "https://vnexpress.net/rss/suc-khoe.rss", true),
            new NewsSource("Tuổi Trẻ Sức khỏe", "https://tuoitre.vn/rss/suc-khoe.rss", true),

            // Hai nguồn dưới đây là báo của ngành y tế, đáng tin hơn cả hai nguồn trên, nhưng CHƯA
            // kiểm chứng được đường dẫn RSS nên để tắt. Bật lên sau khi tự mở URL và thấy đúng RSS.
            new NewsSource("Sức khỏe & Đời sống", "https://suckhoedoisong.vn/rss/home.rss", false),
            new NewsSource("Báo Sức khỏe & Đời sống - Y tế", "https://suckhoedoisong.vn/rss/y-te.rss", false)
    );

    /**
     * Dấu hiệu một bài là CẢNH BÁO DỊCH BỆNH chứ không phải tin sức khỏe thường ngày.
     *
     * Bài khớp sẽ được gắn tiền tố "[Cảnh báo y tế]" vào tiêu đề, vì đó là thứ
     * {@code PostServiceImpl.findLatestEmergencyAlert()} dò tìm để đổ ra banner cảnh báo trong
     * khung chat AI ({@code GET /api/public/news/latest-alert}). Tiêu đề thật lấy từ báo thì
     * không đời nào chứa sẵn cụm đó, nên nếu bỏ bước gắn tiền tố này, banner sẽ chết lặng lẽ:
     * không lỗi, không log, chỉ là vĩnh viễn không còn cảnh báo nào hiện ra nữa.
     *
     * Viết THƯỜNG toàn bộ; `MedicalNewsTask.looksLikeOutbreak` hạ chữ thường rồi so theo TỪ TRỌN
     * VẸN (đệm dấu cách hai đầu), nên mọi mục ở đây phải là cụm đứng riêng được.
     *
     * KHÔNG thêm từ đơn có thể nằm lọt trong từ khác. Ba cái bẫy đã gặp thật khi chạy với tin thật:
     *  - "dịch"  nằm trong "dịch VỤ" → bài "Điều tra dấu hiệu lừa đảo tại thẩm mỹ viện..." bị gắn
     *            nhãn cảnh báo dịch bệnh. Dùng "dịch bệnh" / "ổ dịch" / "đại dịch" thay thế.
     *  - "tả"    nằm trong "mô tả"  → dùng "dịch tả" / "bệnh tả".
     *  - "dại"   nằm trong "dại dột", "hiện đại" → dùng "bệnh dại" / "chó dại".
     * Đây đúng là cái bẫy đã ghi ở coding-conventions.md cho `extractSessionHint`: một từ khóa
     * cũng là từ thường gặp trong câu tiếng Việt bình thường thì sẽ khớp nhầm liên tục.
     *
     * Cũng CỐ Ý bỏ "cảnh báo", "khuyến cáo", "bộ y tế": quá rộng. "Bộ Y tế hướng dẫn khám sàng lọc
     * ung thư vú" là tin thường, gắn nhãn "[Cảnh báo y tế]" cho nó thì banner cảnh báo trong khung
     * chat mất hết ý nghĩa — nhãn đó để dành cho dịch bệnh đang xảy ra.
     */
    public static final List<String> OUTBREAK_KEYWORDS = List.of(
            "dịch bệnh", "ổ dịch", "đại dịch", "vùng dịch", "chống dịch", "dập dịch",
            "bùng phát", "lây lan", "ca mắc", "ca nhiễm", "ca tử vong", "ca bệnh",
            "cúm a", "cúm b", "cúm gia cầm", "sốt xuất huyết", "sởi", "tay chân miệng",
            "đau mắt đỏ", "bạch hầu", "ho gà", "thủy đậu", "viêm não", "viêm gan",
            "dịch tả", "bệnh tả", "bệnh dại", "chó dại", "ebola", "đậu mùa khỉ",
            "ngộ độc", "ngộ độc thực phẩm"
    );
}
