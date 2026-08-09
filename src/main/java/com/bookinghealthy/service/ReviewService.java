package com.bookinghealthy.service;

import com.bookinghealthy.model.Review;
import java.util.List;

public interface ReviewService {

    // Lưu đánh giá mới
    void saveReview(Long bookingId, Integer rating, String comment);

    /**
     * Lịch hẹn này đã được đánh giá chưa. Trang hồ sơ dùng để ẩn nút "Đánh giá" thay vì để
     * bệnh nhân mở modal, chấm sao, gõ nhận xét rồi mới nhận câu "Bạn đã đánh giá dịch vụ
     * này rồi." — cùng một điều kiện mà {@link #saveReview} kiểm tra khi ghi.
     */
    boolean hasReview(Long bookingId);

    // Lấy danh sách đánh giá của Bác sĩ
    List<Review> getReviewsByDoctor(Long doctorId);

    // Lấy thông số Rating (Trung bình sao, Tổng số lượt)
    // Trả về mảng Object[] hoặc DTO, ở đây tôi dùng DTO nội bộ đơn giản hoặc tính trực tiếp ở Controller
    Double getAverageRating(Long doctorId);

    Long countReviews(Long doctorId);

    // Thêm vào ReviewService
    List<Review> getRecentReviews(Long doctorId);
    List<Integer> getRatingDistribution(Long doctorId); // Trả về list [số lượng 5 sao, 4 sao, ...]

    List<Review> getRecentGlobalReviews();
    List<Integer> getGlobalRatingDistribution();
    Double getGlobalAverageRating();
}