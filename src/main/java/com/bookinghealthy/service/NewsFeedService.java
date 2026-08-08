package com.bookinghealthy.service;

import com.bookinghealthy.dto.NewsFeedItem;

import java.util.List;

/**
 * Đi lấy tin y tế THẬT từ các báo trong {@code NewsSourceCatalog.SOURCES}.
 *
 * Tách hẳn khỏi {@code AiService}: chỗ này chỉ tải và bóc dữ liệu, không có một dòng nào gọi
 * model. Nhờ vậy phần "thu thập" kiểm tra được bằng mắt (mở link ra là thấy bài thật) mà không
 * phụ thuộc vào việc OpenRouter còn credit hay không.
 */
public interface NewsFeedService {

    /**
     * Đọc RSS của mọi nguồn đang bật, trả về các bài đăng trong vòng {@code maxAgeDays} ngày,
     * MỚI NHẤT LÊN ĐẦU.
     *
     * Một nguồn hỏng không được làm hỏng cả mẻ: mỗi nguồn nằm trong try/catch riêng và chỉ ghi
     * log tên nguồn bị lỗi.
     */
    List<NewsFeedItem> fetchLatest(int maxAgeDays);

    /**
     * Tải trang bài gốc và bóc lấy phần chữ.
     *
     * Đây là thứ được đưa cho AI tóm tắt. KHÔNG có nó thì AI lại phải bịa: phần description trong
     * RSS chỉ dài 1-2 câu, không đủ để viết nổi 3 gạch đầu dòng.
     *
     * @return toàn văn đã cắt bớt, hoặc chuỗi rỗng nếu không bóc được.
     */
    String fetchArticleText(String url);

    /**
     * Tải ảnh minh họa của bài gốc về thư mục {@code uploads/}.
     *
     * @param refererUrl link bài gốc, gửi kèm làm header Referer. KHÔNG PHẢI tham số trang trí:
     *                   CDN ảnh của báo chặn hotlink, thiếu header này là ăn HTTP 403 và bài nào
     *                   cũng phải lùi về ảnh mặc định.
     * @return TÊN FILE để gán vào {@code Post.image} (template tự ghép tiền tố "/uploads/"),
     *         hoặc null nếu tải hỏng / không phải ảnh / quá nặng.
     */
    String downloadImage(String imageUrl, String refererUrl);
}
