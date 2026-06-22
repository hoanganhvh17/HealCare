package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Post;
import com.bookinghealthy.model.User;
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
            post.setStatus("PUBLISHED");
            postService.save(post);
            ra.addFlashAttribute("successMessage", "Đã xuất bản bài viết thành công: " + post.getTitle());
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy bài viết để duyệt.");
        }
        return "redirect:/admin/manage-news";
    }
}