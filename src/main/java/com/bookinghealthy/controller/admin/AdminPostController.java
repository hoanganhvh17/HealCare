package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Post;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.NotificationService;
import com.bookinghealthy.service.PostService;
import com.bookinghealthy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/admin/manage-news") // Đường dẫn gốc
public class AdminPostController {

    @Autowired private PostService postService;
    @Autowired private UserService userService;
    @Autowired private NotificationService notificationService;
    @Autowired private com.bookinghealthy.task.MedicalNewsTask medicalNewsTask;

    private static final String PUBLISHED = "PUBLISHED";

    // 1. HIỂN THỊ DANH SÁCH TIN TỨC
    @GetMapping
    public String listPosts(Model model) {
        model.addAttribute("posts", postService.findAll());
        return "admin/post-list"; // -> Trỏ tới file HTML danh sách
    }

    // 2. HIỂN THỊ FORM THÊM MỚI
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("post", new Post());
        model.addAttribute("pageTitle", "Viết bài mới");
        return "admin/post-form"; // -> Trỏ tới file HTML form
    }

    // 3. HIỂN THỊ FORM SỬA
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
        Optional<Post> post = postService.findById(id);
        if (post.isPresent()) {
            model.addAttribute("post", post.get());
            model.addAttribute("pageTitle", "Chỉnh sửa bài viết");
            return "admin/post-form";
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy bài viết ID: " + id);
            return "redirect:/admin/manage-news";
        }
    }

    // 4. XỬ LÝ LƯU BÀI VIẾT (KÈM UPLOAD ẢNH) -- sửa chức năng sửa bài viết chiều 19/11
    // 4. XỬ LÝ LƯU BÀI VIẾT (ĐÃ SỬA LỖI MẤT DỮ LIỆU)
    @PostMapping("/save")
    public String savePost(@ModelAttribute("post") Post post,
                           @RequestParam("imageFile") MultipartFile imageFile,
                           @AuthenticationPrincipal UserDetails userDetails,
                           RedirectAttributes ra,
                           Model model) {
        try {
            // --- A. XỬ LÝ ẢNH ---
            if (!imageFile.isEmpty()) {
                // Nếu upload ảnh mới -> Lưu và cập nhật tên
                String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
                String uploadDir = "uploads/";
                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();
                Path path = Paths.get(uploadDir + fileName);
                Files.write(path, imageFile.getBytes());
                post.setImage(fileName);
            } else {
                // Nếu không chọn ảnh mới -> Cần lấy lại tên ảnh cũ (nếu đang sửa)
                if (post.getId() != null) {
                    Post existingPost = postService.findById(post.getId()).orElse(null);
                    if (existingPost != null) {
                        post.setImage(existingPost.getImage());
                    }
                }
            }

            // Trạng thái TRƯỚC khi lưu, để biết bài này có VỪA được xuất bản hay không.
            // Bài mới -> coi như trước đó là nháp; sửa lại một bài đã xuất bản thì không
            // được bắn thông báo lần nữa cho toàn bộ bệnh nhân.
            String previousStatus = (post.getId() == null) ? "DRAFT"
                    : postService.findById(post.getId()).map(Post::getStatus).orElse("DRAFT");

            // --- B. XỬ LÝ NGÀY ĐĂNG & TÁC GIẢ (QUAN TRỌNG) ---
            if (post.getId() != null) {
                // == TRƯỜNG HỢP SỬA BÀI ==
                // Lấy bài cũ từ database
                Post existingPost = postService.findById(post.getId()).orElse(null);

                if (existingPost != null) {
                    // Giữ nguyên Ngày đăng cũ
                    post.setCreatedAt(existingPost.getCreatedAt());

                    // Giữ nguyên Tác giả cũ (người viết bài ban đầu)
                    post.setAuthor(existingPost.getAuthor());

                    // (Nếu bạn muốn đổi người sửa thành người đăng nhập hiện tại thì dùng dòng dưới đây, còn không thì dùng dòng trên)
                    // post.setAuthor(userService.findByUsername(userDetails.getUsername()).orElse(null));
                }
            } else {
                // == TRƯỜNG HỢP THÊM MỚI ==
                // Tự động set ngày giờ hiện tại (nếu Model không tự làm)
                post.setCreatedAt(LocalDateTime.now());

                // Set tác giả là người đang đăng nhập (Admin)
                User author = userService.findByUsername(userDetails.getUsername()).orElse(null);
                post.setAuthor(author);
            }

            // --- C. LƯU ---
            postService.save(post);

            if (PUBLISHED.equals(post.getStatus()) && !PUBLISHED.equals(previousStatus)) {
                notifyPatients(post);
            }

            ra.addFlashAttribute("successMessage", "Đã lưu bài viết thành công.");
            return "redirect:/admin/manage-news";

        } catch (IOException e) {
            model.addAttribute("errorMessage", "Lỗi tải ảnh: " + e.getMessage());
            model.addAttribute("pageTitle", (post.getId() == null) ? "Viết bài mới" : "Chỉnh sửa bài viết");
            return "admin/post-form";
        }
    }

    // 5. XÓA BÀI VIẾT
    @GetMapping("/delete/{id}")
    public String deletePost(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            postService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa bài viết.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi khi xóa bài viết.");
        }
        return "redirect:/admin/manage-news";
    }

    // 6. XUẤT BẢN BẢN NHÁP (DUYỆT BÀI)
    @GetMapping("/publish/{id}")
    public String publishPost(@PathVariable("id") Long id, RedirectAttributes ra) {
        Optional<Post> postOpt = postService.findById(id);
        if (postOpt.isPresent()) {
            Post post = postOpt.get();

            // Đọc trạng thái TRƯỚC khi ghi đè: route này là GET nên bấm lại / F5 / trình duyệt
            // prefetch đều gọi được lần nữa, mà mỗi lần gọi là một lượt fan-out tới TOÀN BỘ
            // bệnh nhân. Bài đã xuất bản rồi thì không thông báo lại.
            boolean firstPublish = !PUBLISHED.equals(post.getStatus());

            post.setStatus(PUBLISHED);
            postService.save(post);

            if (firstPublish) {
                notifyPatients(post);
            }

            ra.addFlashAttribute("successMessage", "Đã xuất bản bài viết thành công: " + post.getTitle());
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy bài viết để duyệt.");
        }
        return "redirect:/admin/manage-news";
    }

    /**
     * 7. THU THẬP TIN NGAY — chạy tay đúng việc mà cron news.fetch.cron vẫn làm.
     *
     * Là POST chứ không GET, khác với /publish/{id}: đây là thao tác GHI (tạo bản nháp mới, gọi AI,
     * tải ảnh về đĩa), mà GET thì F5 hay trình duyệt prefetch đều kích hoạt lại được.
     *
     * Chạy đồng bộ, có thể mất vài chục giây vì phải tải bài và đợi AI. Chấp nhận được cho một nút
     * admin bấm thủ công; việc chống trùng nằm ở existsBySourceUrl nên bấm hai lần cũng không tạo
     * bài lặp. Bài vẫn vào dạng DRAFT — nút này KHÔNG xuất bản gì cả.
     */
    @PostMapping("/fetch-now")
    public String fetchNow(RedirectAttributes ra) {
        try {
            medicalNewsTask.fetchAndDraftMedicalNews();
            ra.addFlashAttribute("successMessage",
                    "Đã chạy thu thập tin tức. Các bài lấy được nằm ở trạng thái Bản nháp bên dưới, "
                            + "hãy đối chiếu bản gốc trước khi duyệt.");
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Lỗi khi thu thập tin tức: " + e.getMessage());
        }
        return "redirect:/admin/manage-news";
    }

    /**
     * Báo tin mới cho toàn bộ bệnh nhân. Chạy bất đồng bộ bên trong service nên không làm
     * chậm redirect của admin. Chỗ gọi chịu trách nhiệm chỉ gọi ĐÚNG MỘT LẦN cho mỗi bài.
     */
    private void notifyPatients(Post post) {
        notificationService.pushToAllPatients(
                "bi-newspaper text-info",
                post.getTitle(),
                post.getSummary(),
                "/news/" + post.getId());
    }
}