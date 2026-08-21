package com.bookinghealthy.service;

import java.nio.file.Path;

/**
 * Biến một tệp hồ sơ bệnh án ngoại viện thành thứ model đọc được: chữ (PDF) hoặc data URL (ảnh).
 *
 * <p>Tách khỏi {@code ExternalMedicalRecordService} vì đây là việc thuần kỹ thuật tệp tin, không
 * mang luật nghiệp vụ nào — và vì nó là chỗ duy nhất được phép biết PDFBox tồn tại.
 */
public interface DocumentTextExtractor {

    /**
     * Bóc chữ khỏi một tệp PDF.
     *
     * <p><b>Trả về chuỗi RỖNG cho bản scan.</b> PDF chụp/scan chỉ chứa ảnh, không có lớp chữ nào —
     * người gọi BẮT BUỘC phải phân biệt được "rỗng" với "đọc được", nếu không sẽ đưa chuỗi rỗng
     * cho model và in ra một bản "tóm tắt" hoàn toàn bịa đặt dưới tên hồ sơ bệnh án.
     */
    String extractPdfText(Path pdfFile);

    /**
     * Đọc ảnh, thu nhỏ rồi mã hoá thành {@code data:image/jpeg;base64,...} để gửi cho model thị giác.
     *
     * <p><b>Thu nhỏ là bắt buộc.</b> Giới hạn tải lên là 10MB, mà base64 phình thêm ~33% — một ảnh
     * chụp bằng điện thoại sẽ thành request ~13MB và đốt token vô ích. 1600px cạnh dài nhất vẫn
     * thừa nét để đọc chữ trên giấy khám.
     *
     * @return null nếu tệp không phải ảnh đọc được
     */
    String toImageDataUrl(Path imageFile);

    /**
     * Bản làm việc thẳng trên bytes, KHÔNG cần tệp trên đĩa.
     *
     * <p>Đây là thứ giữ cho lời hứa "ảnh triệu chứng không bao giờ được lưu" là sự thật chứ không
     * phải một lời hứa: khung chat phải biết ảnh là gì TRƯỚC khi quyết định có ghi ra đĩa hay
     * không, nên bytes phải đi thẳng từ {@code MultipartFile} tới model. Ghi-rồi-xoá yếu hơn hẳn —
     * mọi nhánh lỗi ở giữa đều để lại một tệp mồ côi trong thư mục riêng tư.
     *
     * @return null nếu bytes không phải ảnh đọc được
     */
    String toImageDataUrl(byte[] imageBytes);
}
