package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.MedicalRecord;
import com.bookinghealthy.model.MedicalRecordStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.MedicalRecordRepository;
import com.bookinghealthy.service.MedicalRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bookinghealthy.model.VitalSign;
import com.bookinghealthy.model.PrescriptionItem;
import com.bookinghealthy.model.MedicalAttachment;
import com.bookinghealthy.model.Allergy;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.VitalSignRepository;
import com.bookinghealthy.repository.PrescriptionItemRepository;
import com.bookinghealthy.repository.MedicalAttachmentRepository;
import com.bookinghealthy.repository.AllergyRepository;
import com.bookinghealthy.repository.UserRepository;
import java.util.List;
import com.bookinghealthy.model.MedicalAddendum;
import com.bookinghealthy.repository.MedicalAddendumRepository;

import java.util.Optional;

@Service
public class MedicalRecordServiceImpl implements MedicalRecordService {

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private BookingRepository bookingRepository;
    // === CÁC REPOSITORY MỚI PHỤC VỤ EMR ===
    @Autowired private VitalSignRepository vitalSignRepository;
    @Autowired private PrescriptionItemRepository prescriptionItemRepository;
    @Autowired private MedicalAttachmentRepository medicalAttachmentRepository;
    @Autowired private AllergyRepository allergyRepository;
    // Thêm khai báo Autowired (Dán ngay dưới dòng @Autowired private AllergyRepository...)
    @Autowired
    private MedicalAddendumRepository medicalAddendumRepository;
    @Autowired private UserRepository userRepository;



    @Override
    @Transactional // Đảm bảo tính toàn vẹn dữ liệu (Lưu bệnh án + Update status phải cùng thành công)
    public void createMedicalRecord(Long bookingId, String symptoms, String diagnosis, String prescription, String doctorNotes) {

        // 1. Tìm Booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn với ID: " + bookingId));

        // 2. Kiểm tra nếu đã có bệnh án rồi thì không tạo mới (tránh duplicate)
        if (medicalRecordRepository.findByBookingId(bookingId).isPresent()) {
            throw new RuntimeException("Lịch hẹn này đã có hồ sơ bệnh án!");
        }

        // 3. Tạo MedicalRecord mới
        MedicalRecord record = new MedicalRecord();
        record.setBooking(booking);
        record.setSymptoms(symptoms);
        record.setDiagnosis(diagnosis);
        record.setPrescription(prescription);
        record.setDoctorNotes(doctorNotes);
        record.setStatus(MedicalRecordStatus.COMPLETED);

        medicalRecordRepository.save(record);

        // 4. Cập nhật trạng thái Booking -> COMPLETED (Đã hoàn thành khám)
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);
    }

    @Override
    public Optional<MedicalRecord> findByBookingId(Long bookingId) {
        return medicalRecordRepository.findByBookingId(bookingId);
    }
    // =====================================================================
    // ==================== CÁC HÀM NÂNG CẤP EMR ===========================
    // =====================================================================

    @Override
    public List<MedicalRecord> getPatientMedicalHistory(Long userId) {
        // Trả về toàn bộ bệnh án của user, sắp xếp mới nhất lên đầu để vẽ Timeline
        return medicalRecordRepository.findByBooking_UserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional // Rất quan trọng: Rollback toàn bộ nếu 1 trong các bảng lỗi
    public void createAdvancedMedicalRecord(Long bookingId, String symptoms, String diagnosis, String diagnosisCode,
                                            String prescriptionText, String doctorNotes,
                                            VitalSign vitalSign,
                                            List<PrescriptionItem> prescriptionItems,
                                            List<MedicalAttachment> attachments) {

        // 1. Tìm Booking và check trùng lặp (Logic cũ giữ nguyên cho an toàn)
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn với ID: " + bookingId));

        if (medicalRecordRepository.findByBookingId(bookingId).isPresent()) {
            throw new RuntimeException("Lịch hẹn này đã có hồ sơ bệnh án!");
        }

        // 2. Lưu Entity gốc (Medical Record) kèm mã ICD-10
        MedicalRecord record = new MedicalRecord();
        record.setBooking(booking);
        record.setSymptoms(symptoms);
        record.setDiagnosis(diagnosis);
        record.setDiagnosisCode(diagnosisCode); // Mã ICD-10
        record.setPrescription(prescriptionText); // Đơn thuốc dạng text (nếu bác sĩ vẫn thích nhập tay)
        record.setDoctorNotes(doctorNotes);
        record.setStatus(MedicalRecordStatus.COMPLETED);

        // Lưu để lấy ID (Các bảng con cần ID này)
        MedicalRecord savedRecord = medicalRecordRepository.save(record);

        // 3. Lưu Chỉ số sinh tồn (Vital Signs) nếu có
        if (vitalSign != null) {
            vitalSign.setMedicalRecord(savedRecord);
            vitalSignRepository.save(vitalSign);
        }

        // 4. Lưu Đơn thuốc chi tiết (Prescription Items) nếu có
        if (prescriptionItems != null && !prescriptionItems.isEmpty()) {
            for (PrescriptionItem item : prescriptionItems) {
                item.setMedicalRecord(savedRecord);
            }
            prescriptionItemRepository.saveAll(prescriptionItems);
        }

        // 5. Lưu File đính kèm (Attachments) nếu có
        if (attachments != null && !attachments.isEmpty()) {
            for (MedicalAttachment attachment : attachments) {
                attachment.setMedicalRecord(savedRecord);
            }
            medicalAttachmentRepository.saveAll(attachments);
        }

        // 6. Chốt trạng thái Booking
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);
    }

    @Override
    public void addPatientAllergy(Long userId, Allergy allergy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân."));
        allergy.setUser(user);
        allergyRepository.save(allergy);
    }
    @Override
    public void addMedicalAddendum(Long recordId, String notes) {
        // Tìm bệnh án gốc
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh án!"));

        // Tạo bản ghi Phụ lục mới
        MedicalAddendum addendum = new MedicalAddendum();
        addendum.setMedicalRecord(record);
        addendum.setNotes(notes);

        medicalAddendumRepository.save(addendum);
    }

}