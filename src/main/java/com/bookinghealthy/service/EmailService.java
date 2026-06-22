package com.bookinghealthy.service;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Candidate;

public interface EmailService {
    void sendBookingConfirmation(Booking booking);

    // === THÊM HÀM MỚI NÀY ===
    void sendBookingCancellation(Booking booking, String reason); // Thêm "reason" (lý do)

    // === 3 HÀM MỚI CHO TUYỂN DỤNG ===
    void sendCandidateConfirmation(Candidate candidate); // Gửi cho Ứng viên (đã nộp xong)
    void sendNewCandidateNotification(Candidate candidate); // Gửi cho Admin (có người nộp)
    void sendCandidateResult(Candidate candidate, String subject, String content); // Gửi kết quả (Duyệt/Từ chối)
}