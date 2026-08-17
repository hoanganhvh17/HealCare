package com.bookinghealthy.email;

import com.bookinghealthy.dto.MedicalRecordMailDTO;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dựng thật {@code email/medical-record-ready.html} bằng Thymeleaf.
 *
 * Có bài test này vì lỗi template ở đây KHÔNG bao giờ tự lộ ra: EmailServiceImpl gửi thư trong
 * try/catch nuốt lỗi, nên một biểu thức sai chỉ hiện thành một dòng log trông y hệt lỗi SMTP,
 * còn bệnh nhân thì không nhận được gì. Dùng Context thuần đúng như lúc chạy thật — @{...}
 * hay bất cứ thứ gì cần IWebContext sẽ hỏng ngay tại đây.
 */
class MedicalRecordMailTemplateTest {

    private static final String TEMPLATE = "email/medical-record-ready";

    private TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        
        
        
        
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private MedicalRecordMailDTO fullMail() {
        MedicalRecordMailDTO mail = new MedicalRecordMailDTO();
        mail.setToEmail("benhnhan@example.com");
        mail.setPatientName("Nguyễn Văn A");
        mail.setBookingId(12L);
        mail.setRecordId(34L);
        mail.setDoctorName("BS. Trần Văn Bình");
        mail.setDepartmentName("Nội tổng quát");
        mail.setVisitDate("08/08/2026");
        mail.setVisitTime("09:00 - 09:30");
        mail.setSymptoms("Sốt, ho khan 3 ngày");
        mail.setDiagnosis("Viêm họng cấp");
        mail.setDiagnosisCode("J02.9");
        mail.setDoctorNotes("Uống nhiều nước. Tái khám sau 7 ngày");
        mail.setVitals("Mạch 82 l/p  ·  Nhiệt độ 38 °C");
        mail.getMedicines().add(new MedicalRecordMailDTO.MedicineLine(
                "Paracetamol", "500mg", "10 Viên", "Uống ngày 2 lần sau ăn"));
        return mail;
    }

    @Test
    void dungDuHoSoVaDonThuoc() {
        Context context = new Context();
        context.setVariable("mail", fullMail());

        String html = engine().process(TEMPLATE, context);

        assertTrue(html.contains("Nguyễn Văn A"), "thiếu tên bệnh nhân");
        assertTrue(html.contains("BS. Trần Văn Bình"), "thiếu tên bác sĩ");
        assertTrue(html.contains("09:00 - 09:30, 08/08/2026"), "thiếu thời gian khám");
        assertTrue(html.contains("Viêm họng cấp"), "thiếu chẩn đoán");
        assertTrue(html.contains("ICD-10: J02.9"), "thiếu mã ICD-10");
        assertTrue(html.contains("Mạch 82 l/p"), "thiếu chỉ số sinh tồn");
        assertTrue(html.contains("Paracetamol"), "thiếu dòng thuốc");
        assertTrue(html.contains("Tái khám sau 7 ngày"), "thiếu lời dặn");
        assertFalse(html.contains("Lần khám này bác sĩ không kê thuốc"), "kê thuốc rồi mà vẫn báo không kê");
    }

    /** Ca khám không đo sinh hiệu, không kê thuốc, không mã ICD — mọi mục trống phải tự ẩn. */
    @Test
    void banGonNhatVanDungDuoc() {
        MedicalRecordMailDTO mail = new MedicalRecordMailDTO();
        mail.setToEmail("benhnhan@example.com");
        mail.setPatientName("Nguyễn Văn A");
        mail.setBookingId(12L);
        mail.setRecordId(34L);
        mail.setDoctorName("BS. Trần Văn Bình");
        mail.setVisitDate("08/08/2026");
        mail.setDiagnosis("Theo dõi thêm");

        Context context = new Context();
        context.setVariable("mail", mail);

        String html = engine().process(TEMPLATE, context);

        assertTrue(html.contains("Theo dõi thêm"), "thiếu chẩn đoán");
        assertTrue(html.contains("Lần khám này bác sĩ không kê thuốc"), "thiếu câu thay cho đơn thuốc rỗng");
        assertFalse(html.contains("ICD-10"), "không có mã ICD mà vẫn in nhãn");
        assertFalse(html.contains("Chỉ số sinh tồn"), "không đo sinh hiệu mà vẫn in mục");
        assertFalse(html.contains("Lời dặn của bác sĩ"), "không có lời dặn mà vẫn in mục");
    }
}
