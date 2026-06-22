package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Post;
import com.bookinghealthy.repository.PostRepository;
import com.bookinghealthy.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;

    @Override
    public List<Post> findAll() {
        // Cho Admin: Lấy tất cả bài viết (Bao gồm Nháp), mới nhất lên đầu
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Post> findAllPublished() {
        // Cho User: Chỉ lấy bài đã xuất bản
        return postRepository.findAllByStatusOrderByCreatedAtDesc("PUBLISHED");
    }

    @Override
    public List<Post> findPublishedByCategory(String category) {
        // Cho User: Lọc theo danh mục và chỉ lấy bài đã xuất bản
        return postRepository.findByCategoryAndStatusOrderByCreatedAtDesc(category, "PUBLISHED");
    }

    @Override
    public long countDrafts() {
        return postRepository.countByStatus("DRAFT");
    }

    @Override
    public Optional<Post> findLatestPublishedPost() {
        return postRepository.findFirstByStatusOrderByCreatedAtDesc("PUBLISHED");
    }

    @Override
    public Optional<Post> findLatestEmergencyAlert() {
        // Tìm bài viết PUBLISHED mới nhất có chứa chữ "[Cảnh báo y tế]" trong tiêu đề
        return postRepository.findFirstByStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc("PUBLISHED", "[Cảnh báo y tế]");
    }

    @Override
    public Optional<Post> findById(Long id) {
        return postRepository.findById(id);
    }

    @Override
    public Post save(Post post) {
        return postRepository.save(post);
    }

    @Override
    public void deleteById(Long id) {
        postRepository.deleteById(id);
    }
}