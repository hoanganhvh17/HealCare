package com.bookinghealthy.service.impl;

import com.bookinghealthy.dto.MedicalRecordMailDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.MedicalRecord;
import com.bookinghealthy.model.PrescriptionItem;
import com.bookinghealthy.model.User;
import com.bookinghealthy.model.VitalSign;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.MedicalRecordRepository;
import com.bookinghealthy.repository.PrescriptionItemRepository;
import com.bookinghealthy.repository.VitalSignRepository;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.MedicalRecordDeliveryService;
import com.bookinghealthy.service.NotificationService;
import com.bookinghealthy.service.PdfExportService;
import com.bookinghealthy.util.VitalSignFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MedicalRecordDeliveryServiceImpl implements MedicalRecordDeliveryService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Chuông trỏ thẳng vào trang xem bệnh án của CHÍNH lần khám đó, nên ở đây dùng
     * {@code NotificationService.push} chứ không dùng {@code pushBookingEvent} — hàm kia luôn
     * gắn link về mục lịch sử đặt lịch, bắt bệnh nhân tự dò lại đúng ca vừa khám.
     */
    private static final String RECORD_LINK = "/user/medical-record/view/";

    @Autowired private BookingRepository bookingRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private PrescriptionItemRepository prescriptionItemRepository;
    @Autowired private VitalSignRepository vitalSignRepository;
    @Autowired private PdfExportService pdfExportService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    /**
     * CỐ Ý không {@code @Transactional} và không {@code @Async}.
     *
     * Không transaction: bệnh án đã commit ở {@code createAdvancedMedicalRecord} trước khi vào
     * đây, và dựng PDF là việc nặng — mở thêm transaction chỉ để giữ một kết nối HikariCP
     * (pool 10) suốt thời gian đó, cùng lý do với các hàm gọi mạng trong {@code AiService}.
     *
     * Không async: hàm phải chạy trên luồng phục vụ request, nơi open-in-view còn mở session —
     * cả {@code buildPrescription} lẫn việc đọc {@code booking.getDoctor().getUser()} đều cần
     * nạp quan hệ LAZY. Phần thật sự tốn thời gian (gửi SMTP) đã {@code @Async} sẵn bên
     * {@code EmailServiceImpl}.
     */
    @Override
    public void deliver(Long bookingId) {
        try {
            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            if (booking == null || booking.getUser() == null) {
                return;
            }

            MedicalRecord record = medicalRecordRepository.findByBookingId(bookingId).orElse(null);
            if (record == null) {
                System.err.println("[EMR] Lịch hẹn #" + bookingId + " chưa có bệnh án, không gửi được cho bệnh nhân.");
                return;
            }

            List<PrescriptionItem> items = prescriptionItemRepository.findByMedicalRecordId(record.getId());
            VitalSign vitals = vitalSignRepository.findByMedicalRecordId(record.getId()).orElse(null);

            emailService.sendMedicalRecordReady(buildMail(booking, record, items, vitals),
                    buildPrescriptionPdf(bookingId));
            pushBell(booking, record, items.size());

        } catch (Exception e) {
            // Ca khám đã xong và bệnh án đã lưu — không có gì để bác sĩ thao tác lại, nên lỗi
            // ở đây chỉ được phép nằm trong log.
            System.err.println("[EMR] Lỗi gửi hồ sơ bệnh án của lịch hẹn #" + bookingId + ": " + e.getMessage());
        }
    }

    /** Đọc hết dữ liệu ra chuỗi TẠI ĐÂY, vì thư được gửi ở luồng khác — xem {@link MedicalRecordMailDTO}. */
    private MedicalRecordMailDTO buildMail(Booking booking, MedicalRecord record,
                                           List<PrescriptionItem> items, VitalSign vitals) {
        MedicalRecordMailDTO mail = new MedicalRecordMailDTO();

        mail.setToEmail(booking.getUser().getEmail());
        mail.setPatientName(resolvePatientName(booking));
        mail.setBookingId(booking.getId());
        mail.setRecordId(record.getId());
        mail.setDoctorName(resolveDoctorName(booking));
        mail.setDepartmentName(booking.getDoctor() != null && booking.getDoctor().getDepartment() != null
                ? booking.getDoctor().getDepartment().getName() : null);
        mail.setVisitDate(booking.getAppointmentDate() != null
                ? booking.getAppointmentDate().format(DATE_FORMAT) : "");
        mail.setVisitTime(blankToNull(booking.getAppointmentTime()));

        mail.setSymptoms(blankToNull(record.getSymptoms()));
        mail.setDiagnosis(record.getDiagnosis() == null || record.getDiagnosis().isBlank()
                ? "(Chưa ghi nhận)" : record.getDiagnosis());
        mail.setDiagnosisCode(blankToNull(record.getDiagnosisCode()));
        mail.setDoctorNotes(blankToNull(record.getDoctorNotes()));
        mail.setVitals(VitalSignFormatter.describe(vitals));
        mail.setPrescriptionText(blankToNull(record.getPrescription()));

        for (PrescriptionItem item : items) {
            mail.getMedicines().add(new MedicalRecordMailDTO.MedicineLine(
                    item.getMedicineName(),
                    blankToNull(item.getDosage()),
                    describeAmount(item),
                    blankToNull(item.getInstructions())));
        }
        return mail;
    }

    /**
     * Dựng PDF đơn thuốc để đính kèm. Bọc riêng try/catch vì {@code buildPrescription} ném
     * {@code IllegalStateException} khi thiếu font Unicode trong {@code resources/fonts} —
     * thiếu tệp đính kèm thì chấp nhận được, mất luôn cả thư thì không.
     */
    private byte[] buildPrescriptionPdf(Long bookingId) {
        try {
            return pdfExportService.buildPrescription(bookingId);
        } catch (Exception e) {
            System.err.println("[EMR] Không dựng được PDF đơn thuốc cho lịch hẹn #" + bookingId
                    + " (thư vẫn gửi, chỉ thiếu tệp đính kèm): " + e.getMessage());
            return null;
        }
    }

    /** Chuông trong ứng dụng, đi kèm email vì email có thể vào spam hoặc hỏng âm thầm. */
    private void pushBell(Booking booking, MedicalRecord record, int medicineCount) {
        StringBuilder message = new StringBuilder(resolveDoctorName(booking));
        if (record.getDiagnosis() != null && !record.getDiagnosis().isBlank()) {
            message.append(" — Chẩn đoán: ").append(record.getDiagnosis());
        }
        message.append(medicineCount > 0
                ? " · Đơn thuốc " + medicineCount + " loại"
                : " · Không kê thuốc");

        User patient = booking.getUser();
        notificationService.push(patient,
                "bi-file-earmark-medical text-primary",
                "Đã có hồ sơ bệnh án & đơn thuốc điện tử",
                message.toString(),
                RECORD_LINK + booking.getId());
    }

    /** "10 Viên", hoặc chỉ số lượng / chỉ đơn vị nếu bác sĩ bỏ trống một trong hai. */
    private String describeAmount(PrescriptionItem item) {
        String quantity = item.getQuantity() != null ? String.valueOf(item.getQuantity()) : "";
        String unit = item.getUnit() != null ? item.getUnit().trim() : "";
        return blankToNull((quantity + " " + unit).trim());
    }

    private String resolvePatientName(Booking booking) {
        if (booking.getPatientName() != null && !booking.getPatientName().isBlank()) {
            return booking.getPatientName();
        }
        return booking.getUser().getFullName();
    }

    private String resolveDoctorName(Booking booking) {
        if (booking.getDoctor() != null && booking.getDoctor().getUser() != null) {
            return "BS. " + booking.getDoctor().getUser().getFullName();
        }
        return "Bác sĩ phụ trách";
    }

    /** Template dùng {@code th:if} để ẩn mục trống, nên chuỗi rỗng phải thành null. */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
