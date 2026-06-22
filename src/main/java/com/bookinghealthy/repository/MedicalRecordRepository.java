package com.bookinghealthy.repository;

import com.bookinghealthy.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    // Tìm hồ sơ bệnh án theo Booking ID (Để xem chi tiết)
    Optional<MedicalRecord> findByBookingId(Long bookingId);
    // === THÊM MỚI (EMR GIAI ĐOẠN 2): Lấy toàn bộ lịch sử khám của 1 bệnh nhân ===
    // Mục đích: Trải phẳng dữ liệu để vẽ Timeline cho bác sĩ xem
    List<MedicalRecord> findByBooking_UserIdOrderByCreatedAtDesc(Long userId);
}