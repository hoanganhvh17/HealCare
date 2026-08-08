package com.bookinghealthy.service;

import com.bookinghealthy.dto.MedicalRecordMailDTO;
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

    /**
     * Xác nhận bệnh nhân đã tự đổi lịch thành công, trình bày dạng "trước → sau".
     *
     * @param booking       lịch hẹn SAU khi đã đổi
     * @param oldDoctorName tên bác sĩ cũ (phải lấy trước khi đổi)
     * @param oldDate       ngày khám cũ
     * @param oldTime       khung giờ cũ
     */
    void sendBookingRescheduled(Booking booking, String oldDoctorName,
                                java.time.LocalDate oldDate, String oldTime);

    /**
     * Nhắc bệnh nhân tới hạn tái khám theo lời dặn của bác sĩ ở lần khám trước.
     * Gọi bởi {@code FollowUpReminderTask}, luôn đi kèm {@code NotificationService.push}.
     *
     * @param lastBooking lịch hẹn đã hoàn thành mà lời dặn tái khám được trích ra
     * @param revisitDate ngày tái khám ước tính (suy từ lời dặn, không phải mốc chính xác)
     */
    void sendFollowUpReminder(Booking lastBooking, java.time.LocalDate revisitDate);

    /**
     * Nhắc bệnh nhân về lịch khám của NGÀY MAI.
     * Gọi bởi {@code AppointmentReminderTask}, luôn đi kèm
     * {@code NotificationService.pushBookingEvent} — email có thể vào spam hoặc hỏng âm thầm,
     * chuông thì bệnh nhân chắc chắn thấy khi mở web.
     */
    void sendAppointmentReminder(Booking booking);

    /**
     * Gửi hồ sơ bệnh án + đơn thuốc điện tử cho bệnh nhân ngay sau khi bác sĩ khám xong.
     * Gọi bởi {@code MedicalRecordDeliveryService}, luôn đi kèm {@code NotificationService.push}.
     *
     * Nhận DTO chứ KHÔNG nhận entity: hàm chạy {@code @Async} nên ở luồng khác, mà
     * {@code Booking.user} / {@code Booking.doctor} đều LAZY — xem {@link MedicalRecordMailDTO}.
     *
     * @param prescriptionPdf đơn thuốc PDF đính kèm; null thì thư vẫn gửi, chỉ thiếu tệp đính kèm
     */
    void sendMedicalRecordReady(MedicalRecordMailDTO mail, byte[] prescriptionPdf);

    /**
     * Thông báo chung gửi cho nhân viên, dựng từ {@code templates/email/general-notification.html}.
     * Dùng cho các sự kiện của lịch làm việc: đơn nghỉ được duyệt / bị từ chối,
     * lời mời nhận ca, ca trực đã có người thay.
     *
     * @param bodyHtml phần thân, cho phép HTML (template render bằng th:utext)
     */
    void sendStaffNotification(String toEmail, String subject, String title, String bodyHtml);

    // === 3 HÀM MỚI CHO TUYỂN DỤNG ===
    void sendCandidateConfirmation(Candidate candidate); // Gửi cho Ứng viên (đã nộp xong)
    void sendNewCandidateNotification(Candidate candidate); // Gửi cho Admin (có người nộp)
    void sendCandidateResult(Candidate candidate, String subject, String content); // Gửi kết quả (Duyệt/Từ chối)
}