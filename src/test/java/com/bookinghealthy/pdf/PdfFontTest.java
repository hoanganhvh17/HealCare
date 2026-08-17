package com.bookinghealthy.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canh hai tệp font trong {@code src/main/resources/fonts/}.
 *
 * Thư mục này đã từng RỖNG trong repo: {@code PdfExportServiceImpl} nạp font lười ở lần in đầu
 * tiên (cố ý, để thiếu font không làm sập ứng dụng lúc khởi động), nên hậu quả chỉ lộ ra khi
 * lễ tân bấm in phiếu thu / đơn thuốc — và trong thư gửi bệnh nhân thì còn êm hơn nữa: mất tệp
 * đính kèm mà thư vẫn gửi bình thường. Bài test này biến "quên commit font" thành lỗi build.
 */
class PdfFontTest {

    /** Đúng hai tệp mà PdfExportServiceImpl.ensureFontsLoaded() đọc. */
    private static final String REGULAR = "fonts/DejaVuSans.ttf";
    private static final String BOLD = "fonts/DejaVuSans-Bold.ttf";

    private byte[] read(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Nạp y hệt cách chạy thật: IDENTITY_H + EMBEDDED, đọc từ mảng byte trên classpath. */
    private BaseFont load(String path, String name) throws Exception {
        return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                BaseFont.CACHED, read(path), null);
    }

    @Test
    void nhungDuocCaHaiFont() throws Exception {
        assertTrue(load(REGULAR, "DejaVuSans.ttf").isEmbedded(), "font thường không nhúng được");
        assertTrue(load(BOLD, "DejaVuSans-Bold.ttf").isEmbedded(), "font đậm không nhúng được");
    }

    /**
     * 14 font chuẩn của PDF dùng WinAnsi nên không vẽ nổi chữ tiếng Việt — đây chính là lý do
     * dự án phải nhúng font riêng. Kiểm đúng những ký tự hay xuất hiện trong bệnh án.
     */
    @Test
    void coDuGlyphTiengViet() throws Exception {
        BaseFont regular = load(REGULAR, "DejaVuSans.ttf");
        BaseFont bold = load(BOLD, "DejaVuSans-Bold.ttf");

        for (char c : "ếộữạđĐồướẫỹ".toCharArray()) {
            assertTrue(regular.charExists(c), "font thường thiếu glyph '" + c + "'");
            assertTrue(bold.charExists(c), "font đậm thiếu glyph '" + c + "'");
        }
    }

    /** Dựng thật một trang PDF có tiếng Việt — bắt cả lỗi font hỏng mà vẫn nạp được. */
    @Test
    void inDuocMotTrangTiengViet() throws Exception {
        Font font = new Font(load(REGULAR, "DejaVuSans.ttf"), 12);

        Document document = new Document(PageSize.A5);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph("Đơn thuốc — chẩn đoán: viêm họng cấp, hẹn tái khám sau 7 ngày", font));
        document.close();

        byte[] pdf = out.toByteArray();
        assertTrue(pdf.length > 500, "PDF rỗng bất thường");
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"), "không phải tệp PDF");
    }
}
