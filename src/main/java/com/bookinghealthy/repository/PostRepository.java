package com.bookinghealthy.repository;

import com.bookinghealthy.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // Lấy tất cả bài viết đã XUẤT BẢN, bài mới nhất lên đầu (Cho trang User)
    List<Post> findAllByStatusOrderByCreatedAtDesc(String status);

    // Lấy tất cả bài viết (Bao gồm nháp), bài mới nhất lên đầu (Cho trang Admin)
    List<Post> findAllByOrderByCreatedAtDesc();

    // Tìm bài viết theo tiêu đề (cho ô search sau này)
    List<Post> findByTitleContainingIgnoreCase(String title);

    // Lấy bài viết theo danh mục và CHỈ lấy bài đã XUẤT BẢN (Mới nhất lên đầu)
    List<Post> findByCategoryAndStatusOrderByCreatedAtDesc(String category, String status);

    // Đếm số lượng bài viết theo trạng thái
    long countByStatus(String status);

    // Lấy bài viết mới nhất theo trạng thái
    Optional<Post> findFirstByStatusOrderByCreatedAtDesc(String status);

    // Lấy bài viết mới nhất theo trạng thái và tiêu đề chứa từ khóa
    Optional<Post> findFirstByStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(String status, String keyword);

    /**
     * Đã thu thập bài này từ báo chưa? Dùng ở MedicalNewsTask để không lấy trùng.
     *
     * URL bài gốc là khóa tự nhiên duy nhất đáng tin: cùng một bài báo có thể xuất hiện lại trong
     * RSS nhiều lần trong ngày, và tiêu đề thì tòa soạn sửa được sau khi đăng.
     */
    boolean existsBySourceUrl(String sourceUrl);
}