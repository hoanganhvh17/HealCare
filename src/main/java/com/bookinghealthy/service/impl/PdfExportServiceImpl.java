package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.MedicalRecord;
import com.bookinghealthy.model.PrescriptionItem;
import com.bookinghealthy.model.VitalSign;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.MedicalRecordRepository;
import com.bookinghealthy.repository.PrescriptionItemRepository;
import com.bookinghealthy.repository.VitalSignRepository;
import com.bookinghealthy.service.PdfExportService;
import com.bookinghealthy.util.QRCodeGenerator;
import com.bookinghealthy.util.VitalSignFormatter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class PdfExportServiceImpl implements PdfExportService {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private PrescriptionItemRepository prescriptionItemRepository;
    @Autowired private VitalSignRepository vitalSignRepository;

    private static final String CLINIC_NAME = "PHÒNG KHÁM NNL Hospital";
    private static final String CLINIC_ADDRESS = "123 Đường Sức Khỏe, Quận 1, TP. Hồ Chí Minh · Hotline: 1900 1234";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Fonts nhúng (Unicode) — nạp lười ở lần in đầu tiên.
    // Cố tình KHÔNG dùng @PostConstruct: nếu thiếu file font thì chỉ chức năng in
    // báo lỗi, không được phép làm sập toàn bộ ứng dụng lúc khởi động.
    private volatile boolean fontsLoaded = false;
    private Font fontTitle;
    private Font fontHeading;
    private Font fontNormal;
    private Font fontBold;
    private Font fontSmall;
    private Font fontSmallItalic;

    private synchronized void ensureFontsLoaded() {
        if (fontsLoaded) {
            return;
        }
        try {
            byte[] regular = readResource("fonts/DejaVuSans.ttf");
            byte[] bold = readResource("fonts/DejaVuSans-Bold.ttf");

            // IDENTITY_H + EMBEDDED là bắt buộc: 14 font chuẩn của PDF dùng WinAnsi,
            // không render được các ký tự tiếng Việt như "ế", "ộ", "ữ".
            BaseFont bfRegular = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, BaseFont.CACHED, regular, null);
            BaseFont bfBold = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, BaseFont.CACHED, bold, null);

            fontTitle = new Font(bfBold, 15);
            fontHeading = new Font(bfBold, 11);
            fontNormal = new Font(bfRegular, 10);
            fontBold = new Font(bfBold, 10);
            fontSmall = new Font(bfRegular, 8.5f);
            fontSmallItalic = new Font(bfRegular, 8.5f, Font.ITALIC);

            fontsLoaded = true;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Thiếu font in PDF. Cần có DejaVuSans.ttf và DejaVuSans-Bold.ttf trong "
                            + "src/main/resources/fonts (chi tiết: " + e.getMessage() + ")", e);
        }
    }

    // ===================== PHIẾU THU =====================

    @Override
    public byte[] buildReceipt(Long bookingId) {
        ensureFontsLoaded();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        Document document = new Document(PageSize.A5, 32, 32, 28, 28);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addClinicHeader(document);
            addCenteredTitle(document, "PHIẾU THU TIỀN KHÁM");

            Paragraph meta = new Paragraph(
                    "Mã phiếu: #" + booking.getId() + "     ·     Ngày in: " + LocalDateTime.now().format(DATETIME_FMT),
                    fontSmallItalic);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(12f);
            document.add(meta);

            PdfPTable info = new PdfPTable(new float[]{38f, 62f});
            info.setWidthPercentage(100);
            addInfoRow(info, "Bệnh nhân", resolvePatientName(booking));
            addInfoRow(info, "Điện thoại", resolvePatientPhone(booking));
            addInfoRow(info, "Bác sĩ khám", resolveDoctorName(booking));
            addInfoRow(info, "Chuyên khoa", resolveDepartmentName(booking));
            addInfoRow(info, "Ngày khám", booking.getAppointmentDate() != null
                    ? booking.getAppointmentDate().format(DATE_FMT) : "-");
            addInfoRow(info, "Khung giờ", safe(booking.getAppointmentTime()));
            addInfoRow(info, "Loại hình khám", safe(booking.getAppointmentType()));
            document.add(info);

            document.add(buildDivider());

            PdfPTable money = new PdfPTable(new float[]{55f, 45f});
            money.setWidthPercentage(100);
            money.setSpacingBefore(8f);

            PdfPCell label = new PdfPCell(new Phrase("TỔNG TIỀN THANH TOÁN", fontHeading));
            label.setBorder(Rectangle.NO_BORDER);
            label.setPaddingBottom(4f);
            money.addCell(label);

            PdfPCell amount = new PdfPCell(new Phrase(formatMoney(booking.getBookingPrice()), fontTitle));
            amount.setBorder(Rectangle.NO_BORDER);
            amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amount.setPaddingBottom(4f);
            money.addCell(amount);
            document.add(money);

            PdfPTable payInfo = new PdfPTable(new float[]{38f, 62f});
            payInfo.setWidthPercentage(100);
            addInfoRow(payInfo, "Hình thức", describePaymentMethod(booking.getPaymentMethod()));
            addInfoRow(payInfo, "Trạng thái", describePaymentStatus(booking.getPaymentStatus()));
            document.add(payInfo);

            document.add(buildDivider());

            // Chân phiếu: QR tra cứu bên trái, ô ký tên bên phải
            PdfPTable footer = new PdfPTable(new float[]{40f, 60f});
            footer.setWidthPercentage(100);
            footer.setSpacingBefore(10f);

            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.NO_BORDER);
            byte[] qr = QRCodeGenerator.getQRCodeImage("BOOKING_ID:" + booking.getId(), 120, 120);
            if (qr != null) {
                Image qrImage = Image.getInstance(qr);
                qrImage.scaleToFit(80, 80);
                qrCell.addElement(qrImage);
            }
            Paragraph qrNote = new Paragraph("Quét mã để tra cứu lịch hẹn", fontSmallItalic);
            qrCell.addElement(qrNote);
            footer.addCell(qrCell);

            PdfPCell signCell = new PdfPCell();
            signCell.setBorder(Rectangle.NO_BORDER);
            signCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            signCell.addElement(centered("Người lập phiếu", fontBold));
            signCell.addElement(centered("(Ký và ghi rõ họ tên)", fontSmallItalic));
            footer.addCell(signCell);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi khi tạo phiếu thu PDF: " + e.getMessage(), e);
        }
    }

    // ===================== ĐƠN THUỐC =====================

    @Override
    public byte[] buildPrescription(Long bookingId) {
        ensureFontsLoaded();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + bookingId));

        MedicalRecord record = medicalRecordRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalStateException(
                        "Lịch hẹn #" + bookingId + " chưa có hồ sơ bệnh án nên chưa in được đơn thuốc."));

        List<PrescriptionItem> items = prescriptionItemRepository.findByMedicalRecordId(record.getId());
        Optional<VitalSign> vitalSign = vitalSignRepository.findByMedicalRecordId(record.getId());

        Document document = new Document(PageSize.A4, 40, 40, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addClinicHeader(document);
            addCenteredTitle(document, "ĐƠN THUỐC");

            Paragraph meta = new Paragraph(
                    "Mã bệnh án: #" + record.getId() + "     ·     Mã lịch hẹn: #" + booking.getId(),
                    fontSmallItalic);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(12f);
            document.add(meta);

            PdfPTable info = new PdfPTable(new float[]{25f, 75f});
            info.setWidthPercentage(100);
            addInfoRow(info, "Bệnh nhân", resolvePatientName(booking));
            addInfoRow(info, "Giới tính", booking.getUser() != null ? safe(booking.getUser().getGender()) : "-");
            addInfoRow(info, "Điện thoại", resolvePatientPhone(booking));
            addInfoRow(info, "Ngày khám", booking.getAppointmentDate() != null
                    ? booking.getAppointmentDate().format(DATE_FMT) : "-");
            addInfoRow(info, "Bác sĩ khám", resolveDoctorName(booking));
            addInfoRow(info, "Chuyên khoa", resolveDepartmentName(booking));
            document.add(info);

            document.add(buildDivider());

            PdfPTable clinical = new PdfPTable(new float[]{25f, 75f});
            clinical.setWidthPercentage(100);
            clinical.setSpacingBefore(8f);

            String diagnosis = safe(record.getDiagnosis());
            if (record.getDiagnosisCode() != null && !record.getDiagnosisCode().isBlank()) {
                diagnosis = diagnosis + "  (ICD-10: " + record.getDiagnosisCode() + ")";
            }
            addInfoRow(clinical, "Chẩn đoán", diagnosis);
            addInfoRow(clinical, "Triệu chứng", safe(record.getSymptoms()));

            // Dùng chung bộ định dạng với thư "đã có hồ sơ bệnh án": đơn thuốc cầm trên tay và
            // thư trong hộp mail không được ghi chỉ số sinh tồn theo hai kiểu khác nhau.
            String vitals = VitalSignFormatter.describe(vitalSign.orElse(null));
            if (vitals != null) {
                addInfoRow(clinical, "Chỉ số sinh tồn", vitals);
            }
            document.add(clinical);

            Paragraph medHeading = new Paragraph("DANH SÁCH THUỐC", fontHeading);
            medHeading.setSpacingBefore(14f);
            medHeading.setSpacingAfter(6f);
            document.add(medHeading);

            if (!items.isEmpty()) {
                PdfPTable table = new PdfPTable(new float[]{7f, 30f, 14f, 9f, 12f, 28f});
                table.setWidthPercentage(100);
                addTableHeader(table, "STT", "Tên thuốc", "Hàm lượng", "SL", "Đơn vị", "Cách dùng");

                int index = 1;
                for (PrescriptionItem item : items) {
                    addTableCell(table, String.valueOf(index++), Element.ALIGN_CENTER);
                    addTableCell(table, safe(item.getMedicineName()), Element.ALIGN_LEFT);
                    addTableCell(table, safe(item.getDosage()), Element.ALIGN_LEFT);
                    addTableCell(table, item.getQuantity() != null ? String.valueOf(item.getQuantity()) : "-",
                            Element.ALIGN_CENTER);
                    addTableCell(table, safe(item.getUnit()), Element.ALIGN_LEFT);
                    addTableCell(table, safe(item.getInstructions()), Element.ALIGN_LEFT);
                }
                document.add(table);
            } else if (record.getPrescription() != null && !record.getPrescription().isBlank()) {
                // Bác sĩ nhập đơn thuốc dạng văn bản tự do thay vì kê từng dòng
                document.add(new Paragraph(record.getPrescription(), fontNormal));
            } else {
                document.add(new Paragraph("(Không kê thuốc)", fontSmallItalic));
            }

            if (record.getDoctorNotes() != null && !record.getDoctorNotes().isBlank()) {
                Paragraph noteHeading = new Paragraph("LỜI DẶN CỦA BÁC SĨ", fontHeading);
                noteHeading.setSpacingBefore(14f);
                noteHeading.setSpacingAfter(6f);
                document.add(noteHeading);
                document.add(new Paragraph(record.getDoctorNotes(), fontNormal));
            }

            // Ô ký tên bác sĩ ở góc phải
            LocalDate signDate = record.getCreatedAt() != null
                    ? record.getCreatedAt().toLocalDate() : LocalDate.now();

            PdfPTable signTable = new PdfPTable(new float[]{55f, 45f});
            signTable.setWidthPercentage(100);
            signTable.setSpacingBefore(26f);

            PdfPCell blank = new PdfPCell(new Phrase(" ", fontNormal));
            blank.setBorder(Rectangle.NO_BORDER);
            signTable.addCell(blank);

            PdfPCell signCell = new PdfPCell();
            signCell.setBorder(Rectangle.NO_BORDER);
            signCell.addElement(centered(String.format("Ngày %02d tháng %02d năm %d",
                    signDate.getDayOfMonth(), signDate.getMonthValue(), signDate.getYear()), fontSmallItalic));
            signCell.addElement(centered("BÁC SĨ ĐIỀU TRỊ", fontBold));
            signCell.addElement(centered("(Ký và ghi rõ họ tên)", fontSmallItalic));
            signCell.addElement(centered(" ", fontNormal));
            signCell.addElement(centered(" ", fontNormal));
            signCell.addElement(centered(resolveDoctorName(booking), fontBold));
            signTable.addCell(signCell);
            document.add(signTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi khi tạo đơn thuốc PDF: " + e.getMessage(), e);
        }
    }

    // ===================== HELPERS DỰNG PDF =====================

    private void addClinicHeader(Document document) {
        Paragraph name = new Paragraph(CLINIC_NAME, fontHeading);
        name.setAlignment(Element.ALIGN_CENTER);
        document.add(name);

        Paragraph address = new Paragraph(CLINIC_ADDRESS, fontSmall);
        address.setAlignment(Element.ALIGN_CENTER);
        address.setSpacingAfter(10f);
        document.add(address);
    }

    private void addCenteredTitle(Document document, String title) {
        Paragraph heading = new Paragraph(title, fontTitle);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(2f);
        document.add(heading);
    }

    private Paragraph centered(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private PdfPTable buildDivider() {
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        divider.setSpacingBefore(8f);

        PdfPCell cell = new PdfPCell(new Phrase(" ", fontSmall));
        cell.setBorder(Rectangle.TOP);
        cell.setBorderWidthTop(0.7f);
        cell.setFixedHeight(6f);
        divider.addCell(cell);
        return divider;
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, fontBold));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(3f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, fontNormal));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(3f);
        table.addCell(valueCell);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, fontBold));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5f);
            cell.setGrayFill(0.9f);
            table.addCell(cell);
        }
        table.setHeaderRows(1);
    }

    private void addTableCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, fontNormal));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    // ===================== HELPERS DỮ LIỆU =====================

    private byte[] readResource(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String resolvePatientName(Booking booking) {
        if (booking.getPatientName() != null && !booking.getPatientName().isBlank()) {
            return booking.getPatientName();
        }
        return booking.getUser() != null ? safe(booking.getUser().getFullName()) : "-";
    }

    private String resolvePatientPhone(Booking booking) {
        if (booking.getPatientPhone() != null && !booking.getPatientPhone().isBlank()) {
            return booking.getPatientPhone();
        }
        return booking.getUser() != null ? safe(booking.getUser().getPhone()) : "-";
    }

    private String resolveDoctorName(Booking booking) {
        if (booking.getDoctor() != null && booking.getDoctor().getUser() != null) {
            return "BS. " + safe(booking.getDoctor().getUser().getFullName());
        }
        return "-";
    }

    private String resolveDepartmentName(Booking booking) {
        if (booking.getDoctor() != null && booking.getDoctor().getDepartment() != null) {
            return safe(booking.getDoctor().getDepartment().getName());
        }
        return "-";
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0 đ";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols).format(amount) + " đ";
    }

    private String describePaymentMethod(String method) {
        if (method == null) return "-";
        return switch (method) {
            case "WALLET" -> "Ví NNL Hospital";
            case "VNPAY" -> "VNPay";
            case "BANK_TRANSFER" -> "Chuyển khoản ngân hàng";
            case "CASH" -> "Tiền mặt tại quầy";
            case "PAY_AT_COUNTER" -> "Thanh toán tại quầy khi đến khám";
            default -> method;
        };
    }

    private String describePaymentStatus(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "PAID" -> "Đã thanh toán";
            case "UNPAID" -> "Chưa thanh toán";
            case "REFUNDED" -> "Đã hoàn tiền";
            case "FAILED" -> "Thanh toán thất bại";
            default -> status;
        };
    }

    private String safe(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
