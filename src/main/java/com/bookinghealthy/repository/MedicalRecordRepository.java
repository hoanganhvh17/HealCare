package com.bookinghealthy.repository;

import com.bookinghealthy.model.MedicalRecord;
import com.bookinghealthy.model.MedicalRecordStatus;
import org.springframework.data.jpa.repository.EntityGraph;
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

    // === NHẮC TÁI KHÁM: ứng viên cho FollowUpReminderTask ===
    // Hồ sơ đã hoàn thành, có lời dặn, và job chưa xử lý xong (xem javadoc MedicalRecord.followUpReminderSent).
    // Job này chạy ngoài request HTTP nên không có open-in-view nào giữ session hộ — phải nạp sẵn
    // cả chuỗi quan hệ LAZY (booking -> user/doctor) ngay từ đây, nếu không đụng vào chúng sau khi
    // Hibernate session của lần query này đã đóng là LazyInitializationException.
    @EntityGraph(attributePaths = {"booking", "booking.user", "booking.doctor", "booking.doctor.user", "booking.doctor.department"})
    List<MedicalRecord> findByStatusAndFollowUpReminderSentFalseAndDoctorNotesIsNotNull(MedicalRecordStatus status);
}