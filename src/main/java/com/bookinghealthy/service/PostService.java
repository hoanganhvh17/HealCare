package com.bookinghealthy.service;

import com.bookinghealthy.model.Post;
import java.util.List;
import java.util.Optional;

public interface PostService {
    // Cho Admin (Lấy tất cả)
    List<Post> findAll();
    
    // Cho User (Chỉ lấy bài đã xuất bản)
    List<Post> findAllPublished();
    
    // Cho User (Lấy theo danh mục và đã xuất bản)
    List<Post> findPublishedByCategory(String category);

    // Đếm số lượng bài nháp
    long countDrafts();

    // Lấy bài viết xuất bản mới nhất
    Optional<Post> findLatestPublishedPost();

    // Lấy bản tin cảnh báo khẩn cấp mới nhất (dựa vào từ khóa trong title)
    Optional<Post> findLatestEmergencyAlert();

    Optional<Post> findById(Long id);
    Post save(Post post);
    void deleteById(Long id);
}