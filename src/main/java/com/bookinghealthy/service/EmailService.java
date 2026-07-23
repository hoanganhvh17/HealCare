package com.bookinghealthy.service;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Candidate;

public interface EmailService {
    void sendBookingConfirmation(Booking booking);

    // === THÊM HÀM MỚI NÀY ===
    void sendBookingCancellation(Booking booking, String reason); // Thêm "reason" (lý do)

    /**
     * Thư xin lỗi kèm thông báo đổi bác sĩ, gửi khi lễ tân dời lịch của bác sĩ bận
     * sang bác sĩ khác cùng chuyên khoa.
     *
     * @param booking       lịch hẹn SAU khi đã gán bác sĩ mới
     * @param oldDoctorName tên bác sĩ ban đầu (phải lấy trước khi đổi)
     * @param reason        lý do lễ tân nhập, hiển thị trong thư
     */
    void sendBookingDoctorChange(Booking booking, String oldDoctorName, String reason);

    // === 3 HÀM MỚI CHO TUYỂN DỤNG ===
    void sendCandidateConfirmation(Candidate candidate); // Gửi cho Ứng viên (đã nộp xong)
    void sendNewCandidateNotification(Candidate candidate); // Gửi cho Admin (có người nộp)
    void sendCandidateResult(Candidate candidate, String subject, String content); // Gửi kết quả (Duyệt/Từ chối)
}