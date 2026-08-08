package com.bookinghealthy.service.impl;

import com.bookinghealthy.config.NewsSourceCatalog;
import com.bookinghealthy.config.NewsSourceCatalog.NewsSource;
import com.bookinghealthy.dto.NewsFeedItem;
import com.bookinghealthy.service.NewsFeedService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Thu thập tin y tế bằng Jsoup.
 *
 * CỐ Ý KHÔNG dùng bean {@code RestTemplate} dùng chung của app, dù đó là client HTTP duy nhất
 * đang có. Ba lý do, đều đã cắn thật khi thử:
 *  1. Báo tiếng Việt trả charset trong thẻ meta chứ không phải lúc nào cũng ở header;
 *     {@code SimpleClientHttpRequestFactory} đoán sai là toàn bộ bài về vỡ dấu.
 *  2. Nhiều trang chặn client không có User-Agent (trả 403).
 *  3. Jsoup lo sẵn chuyển hướng, gzip và giới hạn kích thước body — thứ mà dùng RestTemplate
 *     phải tự viết lại từng cái.
 * Đổi lại vẫn phải khai timeout tường minh, y hệt lý do đã ghi ở bean RestTemplate trong
 * BookingHealthyApplication: client không timeout thì chờ vô hạn.
 */
@Service
public class NewsFeedServiceImpl implements NewsFeedService {

    private static final int TIMEOUT_MS = 10_000;

    /**
     * ĐỪNG gắn thêm hậu tố dạng "...NewsBot/1.0" vào chuỗi này.
     *
     * Đã thử và CDN ảnh của VnExpress (i1-suckhoe.vnecdn.net) trả thẳng HTTP 403 khi thấy chữ
     * "Bot" trong User-Agent, trong khi cùng URL đó với chuỗi trình duyệt thuần thì trả 200 —
     * hậu quả là mọi bài của VnExpress đều mất ảnh và phải lùi về ảnh mặc định.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Safari/537.36";

    /** Trần độ dài đoạn văn bản đưa cho AI. Dài hơn chỉ tốn token chứ không thêm ý. */
    private static final int MAX_ARTICLE_CHARS = 4000;

    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private static final String UPLOAD_DIR = "uploads/";
    private static final List<String> ALLOWED_IMAGE_EXT = List.of("jpg", "jpeg", "png", "webp");

    /**
     * Định dạng ngày CÓ múi giờ. VnExpress dùng RFC-1123 ("Sat, 08 Aug 2026 09:46:27 +0700"),
     * là chuẩn của RSS và phủ gần hết; hai dòng sau đỡ cho feed viết lệch chuẩn.
     */
    private static final List<DateTimeFormatter> ZONED_FORMATS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)
    );

    /**
     * Định dạng KHÔNG có múi giờ — hiểu là giờ Việt Nam, tức giờ máy chủ.
     *
     * Dòng đầu là của Tuổi Trẻ: "8/8/2026 9:07:00 AM". Đã xác minh đó là THÁNG/NGÀY chứ không phải
     * ngày/tháng — trong feed xếp mới nhất trước, các bài "8/7/2026" nằm ngay dưới các bài
     * "8/8/2026" của hôm nay, nên 8/7 là ngày 7 tháng 8 chứ không thể là 8 tháng 7.
     */
    private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive()
                    .appendPattern("M/d/yyyy h:mm:ss a").toFormatter(Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH)
    );

    /**
     * Khối nội dung chính của từng báo. Thử lần lượt, trúng cái nào dùng cái đó; trượt hết thì
     * lùi về cách gom mọi thẻ <p> đủ dài.
     */
    private static final List<String> ARTICLE_SELECTORS = List.of(
            "article.fck_detail", ".fck_detail",              // VnExpress
            "#main-detail-body", ".detail-content",           // Tuổi Trẻ
            ".detail-content-body", ".sapo",                  // Sức khỏe & Đời sống
            "article", "[itemprop=articleBody]"               // chung
    );

    @Override
    public List<NewsFeedItem> fetchLatest(int maxAgeDays) {
        List<NewsFeedItem> all = new ArrayList<>();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(maxAgeDays);

        for (NewsSource source : NewsSourceCatalog.SOURCES) {
            if (!source.enabled()) continue;
            try {
                List<NewsFeedItem> items = readFeed(source, cutoff);
                all.addAll(items);
                System.out.println("📰 [NEWS] " + source.name() + ": lấy được " + items.size()
                        + " bài trong " + maxAgeDays + " ngày gần đây.");
            } catch (Exception e) {
                // Một nguồn chết KHÔNG được kéo theo cả mẻ — ghi rõ tên nguồn để còn biết đường sửa.
                System.err.println("⚠️ [NEWS] Không đọc được RSS của " + source.name() + " ("
                        + source.rssUrl() + "): " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }

        all.sort(Comparator.comparing(NewsFeedItem::getPublishedAt).reversed());
        return all;
    }

    private List<NewsFeedItem> readFeed(NewsSource source, LocalDateTime cutoff) throws Exception {
        Document feed = Jsoup.connect(source.rssUrl())
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_PAGE_BYTES)
                .ignoreContentType(true)   // RSS trả application/rss+xml, mặc định Jsoup từ chối
                .parser(Parser.xmlParser())
                .get();

        List<NewsFeedItem> items = new ArrayList<>();
        String unparsedSample = null;

        for (Element item : feed.select("item")) {
            String title = text(item, "title");
            String link = text(item, "link");
            if (title.isEmpty() || link.isEmpty()) continue;

            String rawDate = text(item, "pubDate");
            LocalDateTime published = parseDate(rawDate);
            if (published == null) {
                if (unparsedSample == null) unparsedSample = rawDate;
                continue;
            }
            if (published.isBefore(cutoff)) continue;

            String description = text(item, "description");
            items.add(new NewsFeedItem(title, link, stripHtml(description),
                    extractImage(item, description), published, source.name()));
        }

        if (unparsedSample != null) {
            System.err.println("⚠️ [NEWS] " + source.name()
                    + ": bỏ qua bài không đọc nổi ngày đăng, ví dụ pubDate = \"" + unparsedSample + "\"");
        }
        return items;
    }

    private String text(Element parent, String tag) {
        Element el = parent.selectFirst(tag);
        return el == null ? "" : el.text().trim();
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // Tuổi Trẻ chèn U+202F (narrow no-break space) trước AM/PM, một số feed dùng U+00A0.
        // Mắt thường thấy y hệt dấu cách, nên nếu không thay thì mọi bài của báo đó đều bị loại
        // vì "không đọc nổi ngày đăng" mà nhìn log lại chẳng hiểu vì sao.
        String cleaned = raw.replace('\u202F', ' ').replace('\u00A0', ' ').trim();

        for (DateTimeFormatter fmt : ZONED_FORMATS) {
            try {
                // Quy về múi giờ máy chủ rồi mới bỏ múi, để phép so với LocalDateTime.now() ở
                // fetchLatest luôn cùng một hệ quy chiếu.
                return ZonedDateTime.parse(cleaned, fmt)
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (Exception ignored) {
                // thử định dạng kế tiếp
            }
        }
        for (DateTimeFormatter fmt : LOCAL_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (Exception ignored) {
                // thử định dạng kế tiếp
            }
        }
        return null;
    }

    /** Ảnh có thể nằm ở 3 chỗ tùy báo: <img> trong description, <enclosure>, hoặc <media:content>. */
    private String extractImage(Element item, String description) {
        if (description != null && !description.isBlank()) {
            Element img = Jsoup.parseBodyFragment(description).selectFirst("img[src]");
            if (img != null) return img.attr("src");
        }
        Element enclosure = item.selectFirst("enclosure[url]");
        if (enclosure != null && enclosure.attr("type").startsWith("image")) {
            return enclosure.attr("url");
        }
        Element media = item.selectFirst("media|content[url]");
        if (media != null) return media.attr("url");
        return null;
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parseBodyFragment(html).text().trim();
    }

    @Override
    public String fetchArticleText(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_PAGE_BYTES)
                    .get();

            doc.select("script, style, nav, footer, header, aside, form, iframe, noscript").remove();

            for (String selector : ARTICLE_SELECTORS) {
                Element body = doc.selectFirst(selector);
                if (body != null) {
                    String text = body.text().trim();
                    if (text.length() >= 200) return truncate(text);
                }
            }

            // Không nhận ra khung bài: gom mọi đoạn đủ dài. Câu ngắn thường là chú thích ảnh,
            // nút chia sẻ, tin liên quan — lấy vào chỉ làm nhiễu phần tóm tắt.
            StringBuilder sb = new StringBuilder();
            for (Element p : doc.select("p")) {
                String t = p.text().trim();
                if (t.length() > 60) sb.append(t).append("\n\n");
            }
            return truncate(sb.toString().trim());

        } catch (Exception e) {
            System.err.println("⚠️ [NEWS] Không tải được nội dung bài " + url + ": "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return "";
        }
    }

    private String truncate(String text) {
        if (text.length() <= MAX_ARTICLE_CHARS) return text;
        return text.substring(0, MAX_ARTICLE_CHARS);
    }

    @Override
    public String downloadImage(String imageUrl, String refererUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        try {
            // KHÔNG cắt query string khỏi URL ảnh: báo ký chữ ký vào tham số `s=`, gọi URL trần
            // là ăn HTTP 401. Query chỉ bị bỏ khi ĐOÁN đuôi file (xem extensionOf).
            Connection.Response res = Jsoup.connect(imageUrl)
                    .userAgent(USER_AGENT)
                    // Một số báo chặn hotlink theo Referer. VnExpress thì không, nhưng gửi kèm
                    // không hại gì mà lại đúng với nơi mình thực sự dẫn về.
                    .referrer(refererUrl != null ? refererUrl : imageUrl)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_IMAGE_BYTES)
                    .ignoreContentType(true)
                    .execute();

            String contentType = res.contentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                System.err.println("⚠️ [NEWS] Bỏ ảnh không phải image/* (" + contentType + "): " + imageUrl);
                return null;
            }

            // Đuôi file lấy theo Content-Type THẬT, không theo đuôi trong URL. CDN của VnExpress
            // trả webp cho một URL kết thúc bằng ".jpg"; lưu theo đuôi URL là có file .jpg chứa
            // dữ liệu webp, mà Spring lại đọc đuôi file để đặt Content-Type lúc phục vụ ảnh —
            // tức là mình tự khai sai kiểu ảnh cho trình duyệt của bệnh nhân.
            String ext = extensionOfContentType(contentType);
            if (ext == null) ext = extensionOf(imageUrl);
            if (!ALLOWED_IMAGE_EXT.contains(ext)) {
                System.err.println("⚠️ [NEWS] Bỏ ảnh có định dạng không nhận (" + contentType + "): " + imageUrl);
                return null;
            }

            byte[] bytes = res.bodyAsBytes();
            if (bytes.length == 0) return null;

            // Cùng khuôn đặt tên với AdminPostController: uploads/ nằm cạnh tiến trình chạy và
            // được phục vụ qua /uploads/** (xem WebConfig). Post.image chỉ giữ TÊN FILE.
            String fileName = System.currentTimeMillis() + "_news." + ext;
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();
            Path path = Paths.get(UPLOAD_DIR + fileName);
            Files.write(path, bytes);
            return fileName;

        } catch (Exception e) {
            System.err.println("⚠️ [NEWS] Không tải được ảnh " + imageUrl + ": "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    /** Đuôi file suy từ Content-Type của phản hồi, null nếu không nhận ra kiểu ảnh. */
    private String extensionOfContentType(String contentType) {
        String type = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> null;
        };
    }

    /** Đuôi file lấy từ đường dẫn, bỏ query string (báo hay gắn ?w=380&h=228 phía sau). */
    private String extensionOf(String url) {
        String path = url.split("\\?")[0];
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
