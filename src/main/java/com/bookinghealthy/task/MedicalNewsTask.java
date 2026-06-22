package com.bookinghealthy.task;

import com.bookinghealthy.model.Post;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.PostRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class MedicalNewsTask {

    @Autowired private AiService aiService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;

    // Chạy mỗi 12 tiếng một lần để lấy tin tức mới (3600000 * 12)
    @Scheduled(fixedDelay = 43200000) 
    public void fetchAndDraftMedicalNews() {
        System.out.println("🔄 [MEDICAL NEWS TASK] Đang kết nối hệ thống ngoài để lấy bản nháp tin tức y tế...");

        // 1. Dùng AI đóng vai trò hệ thống thu thập tin tức bên ngoài
        String prompt = "Bạn là một hệ thống thu thập tin tức y tế uy tín. " +
                "Hãy viết một bản tin cảnh báo NGẮN về một dịch bệnh đang bùng phát (VD: Cúm A, Sốt xuất huyết, Đau mắt đỏ...). " +
                "YÊU CẦU TRẢ VỀ ĐÚNG ĐỊNH DẠNG JSON SAU (chỉ trả về raw JSON, không markdown):\n" +
                "{\n" +
                "  \"title\": \"[Cảnh báo y tế] Tiêu đề cảnh báo...\",\n" +
                "  \"summary\": \"Tóm tắt ngắn gọn 1-2 câu\",\n" +
                "  \"content\": \"Nội dung chi tiết cảnh báo và cách phòng tránh (hãy dùng thẻ HTML như <b>, <ul>, <li> để trình bày đẹp).\"\n" +
                "}";

        try {
            // Gọi AI
            String jsonResponse = aiService.getConversationalResponse(prompt, "Bắt đầu lấy tin", "system_news_fetcher");

            // Làm sạch chuỗi (xóa markdown nếu có)
            jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();

            // 2. Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);

            String title = root.get("title").asText();
            String summary = root.get("summary").asText();
            String content = root.get("content").asText();

            // Kiểm tra trùng lặp
            List<Post> existing = postRepository.findByTitleContainingIgnoreCase(title);
            if (!existing.isEmpty()) {
                System.out.println("⚠️ [MEDICAL NEWS TASK] Tin tức này đã được lấy trước đó. Bỏ qua.");
                return;
            }

            // 3. Tìm Admin
            User adminAuthor = userRepository.findByUsername("admin")
                    .orElse(userRepository.findAll().stream()
                            .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN")))
                            .findFirst().orElse(null));

            // 4. LƯU THÀNH BẢN NHÁP (DRAFT)
            Post newPost = new Post();
            newPost.setTitle(title);
            newPost.setSummary(summary);
            newPost.setContent(content);
            newPost.setCategory("KNOWLEDGE");
            newPost.setStatus("DRAFT"); // QUAN TRỌNG: Lưu dạng Nháp
            newPost.setCreatedAt(LocalDateTime.now());
            newPost.setImage("doctor-3.jpg"); // Ảnh minh họa mặc định
            newPost.setAuthor(adminAuthor);

            postRepository.save(newPost);
            System.out.println("✅ [MEDICAL NEWS TASK] Đã lưu bản NHÁP thành công. Chờ Admin duyệt: " + title);

        } catch (Exception e) {
            System.out.println("❌ [MEDICAL NEWS TASK] Lỗi khi lấy tin tức: " + e.getMessage());
        }
    }
}
