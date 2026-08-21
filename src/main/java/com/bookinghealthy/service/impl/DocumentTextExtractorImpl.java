package com.bookinghealthy.service.impl;

import com.bookinghealthy.service.DocumentTextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Base64;

@Service
public class DocumentTextExtractorImpl implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractorImpl.class);

    /**
     * Trần độ dài chữ bóc ra khỏi PDF. Một tập hồ sơ vài chục trang vượt xa cửa sổ ngữ cảnh có ích
     * và đốt token; phần đầu là chỗ luôn có chẩn đoán và đơn thuốc.
     */
    private static final int MAX_PDF_CHARS = 12_000;

    /**
     * Dưới ngưỡng này coi như PDF KHÔNG có lớp chữ. Không dùng {@code isBlank()}: một bản scan vẫn
     * hay còn vài ký tự rác của tiêu đề trang hoặc watermark, đủ để qua phép thử rỗng nhưng không
     * đủ để tóm tắt bất cứ điều gì.
     */
    private static final int MIN_MEANINGFUL_CHARS = 60;

    /** Cạnh dài nhất sau khi thu nhỏ, xem javadoc của {@code toImageDataUrl}. */
    private static final int MAX_IMAGE_EDGE_PX = 1600;

    @Override
    public String extractPdfText(Path pdfFile) {
        if (pdfFile == null) {
            return "";
        }
        try (PDDocument doc = Loader.loadPDF(pdfFile.toFile())) {
            // PDF có mật khẩu / bị hạn chế trích xuất: cứ thử, PDFBox tự ném nếu không được.
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            if (text == null) {
                return "";
            }
            String cleaned = text.replaceAll("[ \t]+", " ").replaceAll("\n{3,}", "\n\n").trim();
            if (cleaned.length() < MIN_MEANINGFUL_CHARS) {
                log.info("[HoSoNgoaiVien] PDF {} chỉ có {} ký tự — coi như bản scan.",
                        pdfFile.getFileName(), cleaned.length());
                return "";
            }
            return cleaned.length() > MAX_PDF_CHARS ? cleaned.substring(0, MAX_PDF_CHARS) : cleaned;
        } catch (Exception e) {
            log.warn("[HoSoNgoaiVien] Không đọc được PDF {}: {} - {}",
                    pdfFile.getFileName(), e.getClass().getSimpleName(), e.getMessage());
            return "";
        }
    }

    @Override
    public String toImageDataUrl(Path imageFile) {
        if (imageFile == null) {
            return null;
        }
        try {
            return encode(ImageIO.read(imageFile.toFile()));
        } catch (Exception e) {
            log.warn("[AnhYTe] Không xử lý được ảnh {}: {} - {}",
                    imageFile.getFileName(), e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    @Override
    public String toImageDataUrl(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            return encode(ImageIO.read(new ByteArrayInputStream(imageBytes)));
        } catch (Exception e) {
            log.warn("[AnhYTe] Không xử lý được ảnh trong bộ nhớ: {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** Phần dùng chung của hai bản trên: thu nhỏ, ép RGB, mã hoá JPEG rồi base64. */
    private String encode(BufferedImage original) throws java.io.IOException {
        if (original == null) {
            log.warn("[AnhYTe] Dữ liệu đưa vào không phải ảnh đọc được.");
            return null;
        }

        BufferedImage scaled = original;
        int longestEdge = Math.max(original.getWidth(), original.getHeight());
        if (longestEdge > MAX_IMAGE_EDGE_PX) {
            scaled = Scalr.resize(original, Scalr.Method.QUALITY, MAX_IMAGE_EDGE_PX);
        }

        // Ép về RGB trước khi ghi JPEG: ảnh PNG/WebP có kênh alpha sẽ ra ảnh ám hồng hoặc
        // ImageIO ném thẳng, và một tờ giấy khám bị ám màu là một tờ giấy model đọc sai.
        BufferedImage rgb = new BufferedImage(scaled.getWidth(), scaled.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.createGraphics().drawImage(scaled, 0, 0, java.awt.Color.WHITE, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "jpg", out)) {
            log.warn("[AnhYTe] Không mã hoá được JPEG.");
            return null;
        }
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
