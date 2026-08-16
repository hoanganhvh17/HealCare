package com.bookinghealthy.task;

import com.bookinghealthy.config.NewsSourceCatalog;
import com.bookinghealthy.dto.NewsFeedItem;
import com.bookinghealthy.model.Post;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.PostRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.NewsFeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class MedicalNewsTask {

    @Autowired private AiService aiService;
    @Autowired private NewsFeedService newsFeedService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;

    @Value("${news.fetch.enabled:true}") private boolean enabled;
    @Value("${news.fetch.max-per-run:2}") private int maxPerRun;
    @Value("${news.fetch.max-age-days:3}") private int maxAgeDays;

    private static final String FALLBACK_IMAGE = "doctor-3.jpg";

    private static final String ALERT_PREFIX = "[Cảnh báo y tế] ";

    private static final int MAX_TITLE = 255;    // posts.title  là VARCHAR(255)
    private static final int MAX_SUMMARY = 1000; // posts.summary là VARCHAR(1000)

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SUMMARY_PROMPT =
            "Bạn là biên tập viên y tế của hệ thống NNL Hospital.\n"
            + "Người dùng sẽ gửi cho bạn TOÀN VĂN một bài báo y tế mà hệ thống đã tải về từ một tờ báo chính thống.\n"
            + "Nhiệm vụ DUY NHẤT của bạn là TÓM TẮT LẠI bài đó bằng tiếng Việt.\n\n"

            + "=== LUẬT TUYỆT ĐỐI ===\n"
            + "1. CHỈ được dùng thông tin CÓ TRONG bài gốc. TUYỆT ĐỐI KHÔNG thêm bất kỳ con số, ngày tháng, "
            + "địa danh, tên người, tên bệnh viện, tên thuốc nào mà bài gốc không nhắc tới.\n"
            + "2. Bài gốc không nêu số liệu cụ thể thì KHÔNG được tự suy ra hay ước lượng số liệu.\n"
            + "3. KHÔNG tự chèn link, không tự ghi 'Nguồn: ...' — hệ thống tự gắn phần dẫn nguồn.\n"
            + "4. Nếu nội dung nhận được không phải bài báo y tế (trang lỗi, trang trắng), trả về JSON có "
            + "title rỗng để hệ thống bỏ qua.\n"
            + "5. Xưng hô trung tính của báo chí, KHÔNG xưng 'em', KHÔNG gọi người đọc là 'bạn'.\n\n"

            + "=== ĐỊNH DẠNG TRẢ VỀ (raw JSON, KHÔNG markdown, KHÔNG ```) ===\n"
            + "{\n"
            + "  \"title\": \"Tiêu đề ngắn gọn, bám sát bài gốc, tối đa 150 ký tự\",\n"
            + "  \"summary\": \"Tóm tắt 1-2 câu, tối đa 300 ký tự\",\n"
            + "  \"content\": \"3-5 gạch đầu dòng ý chính bằng <ul><li>, sau đó MỘT đoạn <p> ngắn "
            + "mở đầu bằng <b>Lời khuyên từ NNL Hospital:</b> khuyên người đọc đi khám khi có dấu hiệu bất thường. "
            + "CHỈ dùng các thẻ <p> <b> <ul> <li>.\"\n"
            + "}";

    @Scheduled(cron = "${news.fetch.cron:0 0 6,18 * * ?}")
    @SchedulerLock(name = "medicalNewsFetch", lockAtLeastFor = "PT10M", lockAtMostFor = "PT30M")
    public void fetchAndDraftMedicalNews() {
        if (!enabled) {
            System.out.println("⏸️ [MEDICAL NEWS TASK] Đang tắt (news.fetch.enabled=false).");
            return;
        }

        System.out.println("🔄 [MEDICAL NEWS TASK] Bắt đầu thu thập tin y tế từ các báo chính thống...");

        List<NewsFeedItem> feed = newsFeedService.fetchLatest(maxAgeDays);
        if (feed.isEmpty()) {
            System.out.println("⚠️ [MEDICAL NEWS TASK] Không nguồn nào trả về bài mới. Bỏ qua lượt này.");
            return;
        }

        User author = resolveAuthor();
        int saved = 0;
        int skippedDuplicate = 0;

        for (NewsFeedItem item : feed) {
            if (saved >= maxPerRun) break;

            if (postRepository.existsBySourceUrl(item.getLink())) {
                skippedDuplicate++;
                continue;
            }

            try {
                if (draftOne(item, author)) saved++;
            } catch (Exception e) {
                // Một bài hỏng không được chặn các bài còn lại trong cùng lượt chạy.
                System.err.println("❌ [MEDICAL NEWS TASK] Lỗi khi xử lý bài " + item.getLink() + ": "
                        + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }

        System.out.println("✅ [MEDICAL NEWS TASK] Xong: lưu " + saved + " bản nháp, bỏ qua "
                + skippedDuplicate + " bài đã thu thập trước đó (tổng " + feed.size() + " bài đọc được).");
    }

    private boolean draftOne(NewsFeedItem item, User author) throws Exception {
        String articleText = newsFeedService.fetchArticleText(item.getLink());
        if (articleText.isBlank()) {
            System.out.println("⏭️ [MEDICAL NEWS TASK] Không bóc được nội dung, bỏ qua: " + item.getTitle());
            return false;
        }

        String userPrompt = "TIÊU ĐỀ BÀI GỐC: " + item.getTitle() + "\n\nTOÀN VĂN BÀI GỐC:\n" + articleText;
        String raw = aiService.getStatelessResponse(SUMMARY_PROMPT, userPrompt, "news");
        if (raw == null) {
            System.err.println("❌ [MEDICAL NEWS TASK] AI không phản hồi, bỏ qua: " + item.getTitle());
            return false;
        }

        JsonNode root = objectMapper.readTree(stripCodeFence(raw));
        String title = root.path("title").asText("").trim();
        String summary = root.path("summary").asText("").trim();
        String content = root.path("content").asText("").trim();

        if (title.isEmpty() || content.isEmpty()) {
            System.out.println("⏭️ [MEDICAL NEWS TASK] AI trả về thiếu nội dung, bỏ qua: " + item.getTitle());
            return false;
        }
        content = Jsoup.clean(content, Safelist.basic());

        boolean isOutbreak = looksLikeOutbreak(item.getTitle() + " " + title + " " + summary);
        if (isOutbreak && !title.startsWith(ALERT_PREFIX)) {
            title = ALERT_PREFIX + title;
        }

        String image = newsFeedService.downloadImage(item.getImageUrl(), item.getLink());

        Post post = new Post();
        post.setTitle(cut(title, MAX_TITLE));
        post.setSummary(cut(summary.isEmpty() ? item.getDescription() : summary, MAX_SUMMARY));
        post.setContent(content);
        post.setCategory(isOutbreak ? "NEWS" : "KNOWLEDGE");
        post.setStatus("DRAFT"); // luôn chờ admin duyệt: nội dung lấy từ báo ngoài, phải có người thật đọc
        post.setImage(image != null ? image : FALLBACK_IMAGE);
        post.setAuthor(author);
        post.setSourceUrl(item.getLink());
        post.setSourceName(item.getSourceName());

        postRepository.save(post);
        System.out.println("📝 [MEDICAL NEWS TASK] Đã lưu nháp từ " + item.getSourceName() + ": " + post.getTitle());
        return true;
    }

    private boolean looksLikeOutbreak(String text) {
        String padded = " " + text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim() + " ";
        return NewsSourceCatalog.OUTBREAK_KEYWORDS.stream()
                .anyMatch(kw -> padded.contains(" " + kw + " "));
    }

    private String stripCodeFence(String raw) {
        return raw.replace("```json", "").replace("```", "").trim();
    }

    private String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    private User resolveAuthor() {
        return userRepository.findByUsername("admin")
                .orElseGet(() -> userRepository.findByRoles_Name("ROLE_ADMIN")
                        .stream().findFirst().orElse(null));
    }
}
