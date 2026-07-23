package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Candidate;
import com.bookinghealthy.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async; // <-- Thêm import
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter; // <-- Thêm import

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private org.thymeleaf.TemplateEngine templateEngine;
    /**
     * @Async: Chạy tác vụ gửi mail trên một luồng (thread) riêng
     * để user không phải chờ.
     * Để @Async hoạt động, bạn cần thêm @EnableAsync vào file Application chính.
     */
    @Async
    @Override
    public void sendBookingConfirmation(Booking booking) {
        try {
            // SỬA Ở ĐÂY: Thay javax bằng jakarta
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(booking.getUser().getEmail());
            helper.setSubject("MediTrust - Xác nhận đặt lịch khám thành công");

            // Đổ dữ liệu vào Template HTML
            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("patientName", booking.getUser().getFullName());
            context.setVariable("bookingId", booking.getId());
            context.setVariable("doctorName", "Dr. " + booking.getDoctor().getUser().getFullName());
            context.setVariable("departmentName", booking.getDoctor().getDepartment().getName());

            // Format ngày
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            context.setVariable("date", booking.getAppointmentDate().format(dateFormatter));

            context.setVariable("time", booking.getAppointmentTime());
            context.setVariable("type", booking.getAppointmentType());
            context.setVariable("price", booking.getBookingPrice().toString());

            String htmlContent = templateEngine.process("email/booking-confirmation", context);
            helper.setText(htmlContent, true); // true = gửi dạng HTML

            // Tạo mã QR (Mã hóa ID lịch hẹn)
            byte[] qrCode = com.bookinghealthy.util.QRCodeGenerator.getQRCodeImage("BOOKING_ID:" + booking.getId(), 250, 250);

            // Nhúng ảnh QR trực tiếp vào mail (inline)
            if (qrCode != null) {
                helper.addInline("qrCodeImage", new org.springframework.core.io.ByteArrayResource(qrCode), "image/png");
            }

            mailSender.send(mimeMessage);

        } catch (Exception e) {
            System.err.println("Lỗi gửi mail HTML + QR: " + e.getMessage());
        }
    }
    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Async
    @Override
    public void sendBookingCancellation(Booking booking, String reason) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(booking.getUser().getEmail());
            helper.setSubject("MediTrust - Thông báo hủy lịch hẹn");

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("patientName", booking.getUser().getFullName());
            context.setVariable("reason", reason);
            context.setVariable("doctorName", "Dr. " + booking.getDoctor().getUser().getFullName());

            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            context.setVariable("date", booking.getAppointmentDate().format(dateFormatter));
            context.setVariable("time", booking.getAppointmentTime());

            String htmlContent = templateEngine.process("email/booking-cancellation", context);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi khi gửi mail HTML HỦY LỊCH: " + e.getMessage());
        }
    }
    // === THƯ XIN LỖI + ĐỔI BÁC SĨ (LỄ TÂN DỜI LỊCH) ===
    @Async
    @Override
    public void sendBookingDoctorChange(Booking booking, String oldDoctorName, String reason) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(booking.getUser().getEmail());
            helper.setSubject("MediTrust - Xin lỗi quý khách: lịch khám #" + booking.getId() + " đã đổi bác sĩ");

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("patientName",
                    booking.getPatientName() != null && !booking.getPatientName().isBlank()
                            ? booking.getPatientName()
                            : booking.getUser().getFullName());
            context.setVariable("reason", reason);
            context.setVariable("oldDoctorName", oldDoctorName);
            context.setVariable("newDoctorName", "Dr. " + booking.getDoctor().getUser().getFullName());
            context.setVariable("bookingId", booking.getId());
            context.setVariable("departmentName", booking.getDoctor().getDepartment().getName());

            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            context.setVariable("date", booking.getAppointmentDate().format(dateFormatter));
            context.setVariable("time", booking.getAppointmentTime());
            context.setVariable("type", booking.getAppointmentType());
            context.setVariable("price", booking.getBookingPrice().toString());

            String htmlContent = templateEngine.process("email/booking-doctor-change", context);
            helper.setText(htmlContent, true);

            // Giữ nguyên mã QR check-in vì lịch hẹn không đổi id
            byte[] qrCode = com.bookinghealthy.util.QRCodeGenerator.getQRCodeImage("BOOKING_ID:" + booking.getId(), 250, 250);
            if (qrCode != null) {
                helper.addInline("qrCodeImage", new org.springframework.core.io.ByteArrayResource(qrCode), "image/png");
            }

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi khi gửi mail ĐỔI BÁC SĨ: " + e.getMessage());
        }
    }

    // === 1. GỬI XÁC NHẬN CHO ỨNG VIÊN ===
    @Async
    @Override
    public void sendCandidateConfirmation(Candidate candidate) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(candidate.getEmail());
            helper.setSubject("MediTrust - Xác nhận ứng tuyển: " + candidate.getJobPosting().getTitle());

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("candidateName", candidate.getFullName());
            context.setVariable("jobTitle", candidate.getJobPosting().getTitle());

            String htmlContent = templateEngine.process("email/candidate-confirmation", context);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail HTML xác nhận ứng viên: " + e.getMessage());
        }
    }
    // === 2. GỬI THÔNG BÁO CHO ADMIN ===
    @Async
    @Override
    public void sendNewCandidateNotification(Candidate candidate) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo("vuhoanganh1706@gmail.com"); // Email Admin nhận tin
            helper.setSubject("[HR] Ứng viên mới: " + candidate.getJobPosting().getTitle());

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("title", "Thông báo hồ sơ mới");

            String body = String.format(
                    "<p>Hệ thống vừa nhận được hồ sơ từ <strong>%s</strong></p>" +
                            "<ul><li>Vị trí: %s</li><li>Email: %s</li><li>SĐT: %s</li></ul>" +
                            "<p>Vui lòng truy cập trang quản trị để xem CV chi tiết.</p>",
                    candidate.getFullName(), candidate.getJobPosting().getTitle(), candidate.getEmail(), candidate.getPhone()
            );
            context.setVariable("bodyContent", body);

            String htmlContent = templateEngine.process("email/general-notification", context);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail HTML cho admin: " + e.getMessage());
        }
    }

    // === 3. GỬI KẾT QUẢ (DUYỆT/TỪ CHỐI) ===
    @Async
    @Override
    public void sendCandidateResult(Candidate candidate, String subject, String content) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(candidate.getEmail());
            helper.setSubject(subject);

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("title", "Thông báo từ Bộ phận Tuyển dụng");
            // Biến \n thành thẻ <br> để HTML hiểu được việc xuống dòng
            context.setVariable("bodyContent", content.replace("\n", "<br>"));

            String htmlContent = templateEngine.process("email/general-notification", context);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail HTML kết quả ứng viên: " + e.getMessage());
        }
    }
}