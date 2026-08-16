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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class NewsFeedServiceImpl implements NewsFeedService {

    private final com.bookinghealthy.service.FileStorageService fileStorageService;

    public NewsFeedServiceImpl(com.bookinghealthy.service.FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    private static final int TIMEOUT_MS = 10_000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Safari/537.36";

    private static final int MAX_ARTICLE_CHARS = 4000;

    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private static final List<String> ALLOWED_IMAGE_EXT = List.of("jpg", "jpeg", "png", "webp");

    private static final List<DateTimeFormatter> ZONED_FORMATS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)
    );

    private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive()
                    .appendPattern("M/d/yyyy h:mm:ss a").toFormatter(Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH)
    );

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
        String cleaned = raw.replace('\u202F', ' ').replace('\u00A0', ' ').trim();
        for (DateTimeFormatter fmt : ZONED_FORMATS) {
            try {
                return ZonedDateTime.parse(cleaned, fmt)
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (Exception ignored) {
            }
        }
        for (DateTimeFormatter fmt : LOCAL_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

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
            Connection.Response res = Jsoup.connect(imageUrl)
                    .userAgent(USER_AGENT)
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
            String ext = extensionOfContentType(contentType);
            if (ext == null) ext = extensionOf(imageUrl);
            if (!ALLOWED_IMAGE_EXT.contains(ext)) {
                System.err.println("⚠️ [NEWS] Bỏ ảnh có định dạng không nhận (" + contentType + "): " + imageUrl);
                return null;
            }

            byte[] bytes = res.bodyAsBytes();
            if (bytes.length == 0) return null;
            String fileName = System.currentTimeMillis() + "_"
                    + java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000)
                    + "_news." + ext;
            Path dir = fileStorageService.uploadRoot();
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), bytes);
            return fileName;

        } catch (Exception e) {
            System.err.println("⚠️ [NEWS] Không tải được ảnh " + imageUrl + ": "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    private String extensionOfContentType(String contentType) {
        String type = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> null;
        };
    }

    private String extensionOf(String url) {
        String path = url.split("\\?")[0];
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
