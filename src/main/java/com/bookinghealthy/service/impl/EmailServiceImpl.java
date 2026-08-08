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
            helper.setSubject("NNL Hospital - Xác nhận đặt lịch khám thành công");

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
    /**
     * Thông báo chung cho nhân viên (lịch làm việc, đơn nghỉ, đổi ca).
     * Gửi bất đồng bộ như các hàm khác nên lỗi chỉ hiện ở log, không làm hỏng request.
     */
    @Async
    @Override
    public void sendStaffNotification(String toEmail, String subject, String title, String bodyHtml) {
        sendGeneral(toEmail, subject, title, bodyHtml, "thông báo nhân viên");
    }

    /**
     * Nhắc bệnh nhân về lịch khám của NGÀY MAI. Gọi bởi {@code AppointmentReminderTask},
     * luôn đi kèm {@code NotificationService.pushBookingEvent}.
     */
    @Async
    @Override
    public void sendAppointmentReminder(Booking booking) {
        if (booking == null || booking.getUser() == null) {
            return;
        }

        java.time.format.DateTimeFormatter dateFormatter =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String doctorName = (booking.getDoctor() != null && booking.getDoctor().getUser() != null)
                ? booking.getDoctor().getUser().getFullName() : "bác sĩ phụ trách";

        // KHÔNG dùng @{...} trong thân thư: EmailServiceImpl render bằng Context thuần chứ
        // không phải IWebContext, @{...} sẽ ném lỗi và bị nuốt lặng trong catch bên dưới.
        String body = "<p>Xin chào <b>" + booking.getUser().getFullName() + "</b>,</p>"
                + "<p>Ngày mai anh/chị có lịch khám tại NNL Hospital:</p>"
                + "<ul>"
                + "<li>Bác sĩ: <b>BS. " + doctorName + "</b></li>"
                + "<li>Thời gian: <b>" + booking.getAppointmentTime() + ", "
                + booking.getAppointmentDate().format(dateFormatter) + "</b></li>"
                + "</ul>"
                + "<p>Anh/chị vui lòng đến trước giờ hẹn 15 phút và mang theo giấy tờ tùy thân. "
                + "Nếu cần đổi hoặc hủy lịch, xin truy cập website và vào mục Hồ sơ cá nhân.</p>";

        sendGeneral(booking.getUser().getEmail(),
                "NNL Hospital - Nhắc lịch khám ngày mai",
                "Nhắc lịch khám ngày mai",
                body,
                "nhắc lịch khám");
    }

    /**
     * Hồ sơ bệnh án + đơn thuốc điện tử, gửi ngay sau khi bác sĩ lưu bệnh án.
     *
     * Chỉ đọc {@link com.bookinghealthy.dto.MedicalRecordMailDTO} — tuyệt đối không chạm vào
     * entity ở đây: hàm này chạy trên luồng khác, mọi quan hệ LAZY sẽ nổ
     * {@code LazyInitializationException} và bị chính khối catch bên dưới nuốt mất.
     */
    @Async
    @Override
    public void sendMedicalRecordReady(com.bookinghealthy.dto.MedicalRecordMailDTO mail,
                                       byte[] prescriptionPdf) {
        if (mail == null || mail.getToEmail() == null || mail.getToEmail().isBlank()) {
            return;
        }
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(mail.getToEmail());
            helper.setSubject("NNL Hospital - Hồ sơ bệnh án & đơn thuốc lần khám #" + mail.getBookingId());

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("mail", mail);

            helper.setText(templateEngine.process("email/medical-record-ready", context), true);

            // Thiếu font in PDF thì buildPrescription không dựng được tệp; thư vẫn phải tới tay
            // bệnh nhân vì toàn bộ đơn thuốc đã nằm trong thân thư.
            if (prescriptionPdf != null && prescriptionPdf.length > 0) {
                helper.addAttachment("don-thuoc-" + mail.getBookingId() + ".pdf",
                        new org.springframework.core.io.ByteArrayResource(prescriptionPdf),
                        "application/pdf");
            }

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail HỒ SƠ BỆNH ÁN: " + e.getMessage());
        }
    }

    /**
     * Gửi một thư dựng từ {@code templates/email/general-notification.html}.
     * Dùng chung cho thông báo nhân viên và nhắc lịch khám — hai chỗ chỉ khác nhau ở nội dung.
     *
     * @param logLabel mô tả ngắn để dòng log nói rõ loại thư nào hỏng
     */
    private void sendGeneral(String toEmail, String subject, String title,
                             String bodyHtml, String logLabel) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject(subject);

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("title", title);
            context.setVariable("bodyContent", bodyHtml);

            helper.setText(templateEngine.process("email/general-notification", context), true);
            mailSender.send(mimeMessage);

        } catch (Exception e) {
            System.err.println("Lỗi gửi mail " + logLabel + ": " + e.getMessage());
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
            helper.setSubject("NNL Hospital - Thông báo hủy lịch hẹn");

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
            helper.setSubject("NNL Hospital - Xin lỗi quý khách: lịch khám #" + booking.getId() + " đã đổi bác sĩ");

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

    // === XÁC NHẬN BỆNH NHÂN TỰ ĐỔI LỊCH ===
    @Async
    @Override
    public void sendBookingRescheduled(Booking booking, String oldDoctorName,
                                       java.time.LocalDate oldDate, String oldTime) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(booking.getUser().getEmail());
            helper.setSubject("NNL Hospital - Đã đổi lịch khám #" + booking.getId() + " thành công");

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("patientName",
                    booking.getPatientName() != null && !booking.getPatientName().isBlank()
                            ? booking.getPatientName()
                            : booking.getUser().getFullName());
            context.setVariable("bookingId", booking.getId());
            context.setVariable("departmentName", booking.getDoctor().getDepartment().getName());

            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

            context.setVariable("oldDoctorName", oldDoctorName);
            context.setVariable("oldDate", oldDate != null ? oldDate.format(dateFormatter) : "");
            context.setVariable("oldTime", oldTime);

            context.setVariable("newDoctorName", "Dr. " + booking.getDoctor().getUser().getFullName());
            context.setVariable("newDate", booking.getAppointmentDate().format(dateFormatter));
            context.setVariable("newTime", booking.getAppointmentTime());

            context.setVariable("type", booking.getAppointmentType());
            context.setVariable("price", booking.getBookingPrice().toString());

            // Cho bệnh nhân biết còn mấy lần đổi nữa, tránh bất ngờ khi bị chặn
            int used = booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount();
            context.setVariable("usedTimes", used);
            context.setVariable("maxTimes", com.bookinghealthy.service.BookingService.MAX_RESCHEDULE_TIMES);

            String htmlContent = templateEngine.process("email/booking-rescheduled", context);
            helper.setText(htmlContent, true);

            // Lịch hẹn giữ nguyên id nên mã QR check-in cũ vẫn dùng được
            byte[] qrCode = com.bookinghealthy.util.QRCodeGenerator.getQRCodeImage("BOOKING_ID:" + booking.getId(), 250, 250);
            if (qrCode != null) {
                helper.addInline("qrCodeImage", new org.springframework.core.io.ByteArrayResource(qrCode), "image/png");
            }

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi khi gửi mail ĐỔI LỊCH: " + e.getMessage());
        }
    }

    // === NHẮC TÁI KHÁM (FollowUpReminderTask) ===
    @Async
    @Override
    public void sendFollowUpReminder(Booking lastBooking, java.time.LocalDate revisitDate) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(lastBooking.getUser().getEmail());
            helper.setSubject("NNL Hospital - Nhắc lịch tái khám");

            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("patientName", lastBooking.getUser().getFullName());
            context.setVariable("doctorName", "Dr. " + lastBooking.getDoctor().getUser().getFullName());
            context.setVariable("departmentName", lastBooking.getDoctor().getDepartment().getName());

            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            context.setVariable("lastVisitDate", lastBooking.getAppointmentDate().format(dateFormatter));
            context.setVariable("revisitDate", revisitDate.format(dateFormatter));

            String htmlContent = templateEngine.process("email/followup-reminder", context);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi khi gửi mail NHẮC TÁI KHÁM: " + e.getMessage());
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
            helper.setSubject("NNL Hospital - Xác nhận ứng tuyển: " + candidate.getJobPosting().getTitle());

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