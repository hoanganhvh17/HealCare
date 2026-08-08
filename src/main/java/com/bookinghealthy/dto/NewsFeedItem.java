package com.bookinghealthy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một bài báo lấy được từ RSS của nguồn chính thống, TRƯỚC khi AI tóm tắt.
 *
 * Đây là dữ liệu THẬT: link và ngày đăng đều do tòa soạn phát ra, không phải do model sinh.
 * Chính {@link #link} về sau trở thành {@code Post.sourceUrl} và là khóa chống trùng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsFeedItem {

    /** Tiêu đề gốc trên báo. */
    private String title;

    /** URL bài gốc — vừa để dẫn nguồn cho bệnh nhân, vừa là khóa chống lấy trùng. */
    private String link;

    /** Tóm tắt ngắn kèm trong RSS (1-2 câu). Quá mỏng để viết bài, chỉ dùng khi bóc toàn văn thất bại. */
    private String description;

    /** Ảnh minh họa trích từ RSS, có thể null. */
    private String imageUrl;

    /** Ngày đăng trên báo (KHÁC ngày mình lưu bài), dùng để loại tin quá cũ và sắp mới nhất lên đầu. */
    private LocalDateTime publishedAt;

    /** Tên hiển thị của tòa soạn, lấy từ NewsSourceCatalog. */
    private String sourceName;
}
