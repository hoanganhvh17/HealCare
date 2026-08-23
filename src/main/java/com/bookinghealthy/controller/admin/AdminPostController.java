package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Post;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.FileStorageService;
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

import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/admin/manage-news") // Đường dẫn gốc
public class AdminPostController {

    @Autowired private PostService postService;
    @Autowired private UserService userService;
    @Autowired private NotificationService notificationService;
    @Autowired private com.bookinghealthy.task.MedicalNewsTask medicalNewsTask;
    @Autowired private FileStorageService fileStorageService;

    private static final String PUBLISHED = "PUBLISHED";

    @GetMapping
    public String listPosts(Model model) {
        model.addAttribute("posts", postService.findAll());
        return "admin/post-list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("post", new Post());
        model.addAttribute("pageTitle", "Viết bài mới");
        return "admin/post-form"; // -> Trỏ tới file HTML form
    }

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

    @PostMapping("/save")
    public String savePost(@ModelAttribute("post") Post post,
                           @RequestParam("imageFile") MultipartFile imageFile,
                           @AuthenticationPrincipal UserDetails userDetails,
                           RedirectAttributes ra,
                           Model model) {
        try {
            if (!imageFile.isEmpty()) {
                post.setImage(fileStorageService.storeImage(imageFile, null));
            } else {
                if (post.getId() != null) {
                    Post existingPost = postService.findById(post.getId()).orElse(null);
                    if (existingPost != null) {
                        post.setImage(existingPost.getImage());
                    }
                }
            }

            String previousStatus = (post.getId() == null) ? "DRAFT"
                    : postService.findById(post.getId()).map(Post::getStatus).orElse("DRAFT");

            if (post.getId() != null) {
                Post existingPost = postService.findById(post.getId()).orElse(null);
                if (existingPost != null) {
                    post.setCreatedAt(existingPost.getCreatedAt());
                    post.setAuthor(existingPost.getAuthor());
                }
            } else {
                post.setCreatedAt(LocalDateTime.now());
                User author = userService.findByUsername(userDetails.getUsername()).orElse(null);
                post.setAuthor(author);
            }
            postService.save(post);
            if (PUBLISHED.equals(post.getStatus()) && !PUBLISHED.equals(previousStatus)) {
                notifyPatients(post);
            }
            ra.addFlashAttribute("successMessage", "Đã lưu bài viết thành công.");
            return "redirect:/admin/manage-news";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", "Lỗi tải ảnh: " + e.getMessage());
            model.addAttribute("pageTitle", (post.getId() == null) ? "Viết bài mới" : "Chỉnh sửa bài viết");
            return "admin/post-form";
        }
    }

    @PostMapping("/delete/{id}")
    public String deletePost(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            postService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa bài viết.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi khi xóa bài viết.");
        }
        return "redirect:/admin/manage-news";
    }

    @PostMapping("/publish/{id}")
    public String publishPost(@PathVariable("id") Long id, RedirectAttributes ra) {
        Optional<Post> postOpt = postService.findById(id);
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
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

    private void notifyPatients(Post post) {
        notificationService.pushToAllPatients(
                "bi-newspaper text-info",
                post.getTitle(),
                post.getSummary(),
                "/news/" + post.getId());
    }
}