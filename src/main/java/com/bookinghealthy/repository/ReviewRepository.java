package com.bookinghealthy.repository;

import com.bookinghealthy.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Kiểm tra xem lịch hẹn này đã được đánh giá chưa
    Optional<Review> findByBookingId(Long bookingId);

    // Lấy danh sách đánh giá của 1 bác sĩ (để hiện lên trang chi tiết)
    // Sắp xếp mới nhất lên đầu
    @Query("SELECT r FROM Review r WHERE r.booking.doctor.id = :doctorId ORDER BY r.createdAt DESC")
    List<Review> findByDoctorId(@Param("doctorId") Long doctorId);

    // Tính điểm trung bình sao của bác sĩ (VD: 4.8)
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.booking.doctor.id = :doctorId")
    Double getAverageRating(@Param("doctorId") Long doctorId);

    // Đếm tổng số đánh giá của bác sĩ
    @Query("SELECT COUNT(r) FROM Review r WHERE r.booking.doctor.id = :doctorId")
    Long countReviews(@Param("doctorId") Long doctorId);

    // 1. Lấy 5 đánh giá mới nhất của Bác sĩ (Để hiện Widget góc phải)
    List<Review> findTop5ByBooking_Doctor_IdOrderByCreatedAtDesc(Long doctorId);

    // 2. Đếm số lượng đánh giá theo số sao (Để vẽ biểu đồ)
    // Ví dụ: Đếm xem bác sĩ có bao nhiêu đánh giá 5 sao
    Long countByBooking_Doctor_IdAndRating(Long doctorId, Integer rating);

    // Trong ReviewRepository.java

    // 1. Lấy 5 đánh giá mới nhất của TOÀN HỆ THỐNG
    List<Review> findTop5ByOrderByCreatedAtDesc();

    // 2. Đếm số lượng đánh giá theo số sao (Toàn hệ thống)
    Long countByRating(Integer rating);

    // 3. Tính điểm trung bình sao (Toàn hệ thống)
    @Query("SELECT AVG(r.rating) FROM Review r")
    Double getGlobalAverageRating();

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.createdAt BETWEEN :start AND :end")
    Double getAverageRatingBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Tìm các đánh giá dựa vào danh sách số sao (Ví dụ: 1 sao, 2 sao)
    List<Review> findByRatingInOrderByCreatedAtDesc(List<Integer> ratings);
}