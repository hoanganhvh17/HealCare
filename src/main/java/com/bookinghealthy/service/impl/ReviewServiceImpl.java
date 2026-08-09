package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Review;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.ReviewRepository;
import com.bookinghealthy.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Override
    public void saveReview(Long bookingId, Integer rating, String comment) {
        // 1. Tìm Booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn!"));

        // 2. Validate: Phải là COMPLETED mới được đánh giá
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new RuntimeException("Bạn chỉ có thể đánh giá sau khi đã hoàn thành khám.");
        }

        // 3. Validate: Đã đánh giá chưa?
        if (reviewRepository.findByBookingId(bookingId).isPresent()) {
            throw new RuntimeException("Bạn đã đánh giá dịch vụ này rồi.");
        }

        // 4. Lưu
        Review review = new Review();
        review.setBooking(booking);
        review.setRating(rating);
        review.setComment(comment);

        reviewRepository.save(review);
    }

    @Override
    public boolean hasReview(Long bookingId) {
        return reviewRepository.findByBookingId(bookingId).isPresent();
    }

    @Override
    public List<Review> getReviewsByDoctor(Long doctorId) {
        return reviewRepository.findByDoctorId(doctorId);
    }

    @Override
    public Double getAverageRating(Long doctorId) {
        Double avg = reviewRepository.getAverageRating(doctorId);
        // Nếu chưa có đánh giá nào thì trả về 0
        return avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0; // Làm tròn 1 số thập phân
    }

    @Override
    public Long countReviews(Long doctorId) {
        return reviewRepository.countReviews(doctorId);
    }

    @Override
    public List<Review> getRecentReviews(Long doctorId) {
        return reviewRepository.findTop5ByBooking_Doctor_IdOrderByCreatedAtDesc(doctorId);
    }

    @Override
    public List<Integer> getRatingDistribution(Long doctorId) {
        List<Integer> distribution = new ArrayList<>();
        // Đếm từ 5 sao xuống 1 sao
        for (int i = 5; i >= 1; i--) {
            Long count = reviewRepository.countByBooking_Doctor_IdAndRating(doctorId, i);
            distribution.add(count != null ? count.intValue() : 0);
        }
        return distribution; // Kết quả: [SL 5 sao, SL 4 sao, ..., SL 1 sao]
    }

    @Override
    public List<Review> getRecentGlobalReviews() {
        return reviewRepository.findTop5ByOrderByCreatedAtDesc();
    }

    @Override
    public List<Integer> getGlobalRatingDistribution() {
        List<Integer> distribution = new ArrayList<>();
        for (int i = 5; i >= 1; i--) { // Đếm từ 5 sao xuống 1 sao
            Long count = reviewRepository.countByRating(i);
            distribution.add(count != null ? count.intValue() : 0);
        }
        return distribution;
    }

    @Override
    public Double getGlobalAverageRating() {
        Double avg = reviewRepository.getGlobalAverageRating();
        return avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
    }
}