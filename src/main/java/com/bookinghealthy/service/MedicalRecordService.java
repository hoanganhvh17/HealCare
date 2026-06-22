package com.bookinghealthy.service;

import com.bookinghealthy.model.MedicalRecord;

import java.util.Optional;

public interface MedicalRecordService {

    // Lưu bệnh án mới (Đồng thời set Booking thành COMPLETED)
    void createMedicalRecord(Long bookingId, String symptoms, String diagnosis, String prescription, String doctorNotes);

    // Tìm kiếm bệnh án theo ID lịch hẹn
    Optional<MedicalRecord> findByBookingId(Long bookingId);
    // === THÊM MỚI (GIAI ĐOẠN 2 EMR) ===
    // 1. Lấy lịch sử khám bệnh của bệnh nhân (Vẽ Timeline)
    java.util.List<MedicalRecord> getPatientMedicalHistory(Long userId);

    // 2. Tạo bệnh án chuẩn EMR (Có Sinh hiệu, Đơn thuốc chi tiết, File đính kèm, ICD-10)
    void createAdvancedMedicalRecord(Long bookingId, String symptoms, String diagnosis, String diagnosisCode,
                                     String prescriptionText, String doctorNotes,
                                     com.bookinghealthy.model.VitalSign vitalSign,
                                     java.util.List<com.bookinghealthy.model.PrescriptionItem> prescriptionItems,
                                     java.util.List<com.bookinghealthy.model.MedicalAttachment> attachments);

    // 3. Thêm cảnh báo dị ứng cho bệnh nhân
    void addPatientAllergy(Long userId, com.bookinghealthy.model.Allergy allergy);

    // 4. Bổ sung ghi chú vào bệnh án đã đóng (Cơ chế Addendum)
    void addMedicalAddendum(Long recordId, String notes);
}