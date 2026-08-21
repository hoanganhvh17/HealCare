package com.bookinghealthy.service;

import com.bookinghealthy.dto.ImageAnalysis;
import com.bookinghealthy.model.User;

import java.util.Map;

/**
 * Đọc một tấm ảnh khách gửi vào khung chat: vừa PHÂN LOẠI vừa PHÂN TÍCH, trong MỘT lượt gọi model.
 *
 * <p><b>Lớp này không lưu gì cả.</b> Nó nhận bytes, gọi model, trả kết quả. Quyết định có ghi ra
 * đĩa hay không là việc của người gọi, và chỉ được ra quyết định đó SAU khi biết ảnh là gì —
 * đây chính là thứ giữ cho lời hứa "ảnh triệu chứng không bao giờ được lưu" là sự thật.
 *
 * <p>Tách khỏi {@code ExternalMedicalRecordService} có chủ đích: lớp kia có repository và toàn bộ
 * vòng đời của một hồ sơ ĐƯỢC LƯU TRỮ; ảnh triệu chứng cố ý không có gì trong số đó. Gộp vào là
 * mời người sửa sau thêm một {@code save()} cho "tiện".
 */
public interface ChatImageService {

    /** Trần số ảnh mỗi tài khoản được nhờ AI đọc trong một ngày. */
    int MAX_IMAGE_ANALYSES_PER_DAY = 10;

    /**
     * {@code null} = còn lượt. Ngược lại là câu tiếng Việt giải thích, hiển thị nguyên văn.
     *
     * <p>Cùng khuôn {@code BookingService.whyCannotBookWithoutPayment}: một câu COUNT, một nguồn
     * sự thật, controller chặn thật bằng chính câu mà màn hình in ra.
     */
    String whyCannotAnalyzeImage(User user);

    /**
     * Gọi model đọc ảnh. Tăng bộ đếm hạn mức TRƯỚC khi gọi.
     *
     * <p>Không bao giờ ném ra ngoài vì lỗi model: mọi thất bại thành {@code kind = OTHER} kèm
     * {@code message} tiếng Việt, để khung chat luôn có thứ để in.
     *
     * @param imageBytes bytes ảnh, KHÔNG phải đường dẫn — ảnh chưa từng chạm đĩa ở thời điểm này
     */
    ImageAnalysis analyze(User user, byte[] imageBytes, String logTag);

    /**
     * Ghi nhận MỘT lượt phân tích tệp đã dùng.
     *
     * <p>{@link #analyze} tự gọi nó rồi. Cái này để lộ ra ngoài cho nhánh PDF — nhánh đó không đi
     * qua model thị giác nên không chạm {@code analyze}, nhưng nó VẪN là một lượt gọi model trả
     * tiền. Thiếu lời gọi này thì {@link #whyCannotAnalyzeImage} kiểm mãi một con số không bao giờ
     * tăng, và một vòng lặp gửi PDF chạy vô hạn.
     */
    void countOneAnalysis(User user);

    /**
     * Một kết quả SYMPTOM dưới dạng Map cho khung chat, kèm TÊN khoa tra từ DB.
     *
     * <p>Ở cạnh chỗ dựng phân tích chứ không nằm trong controller, cùng lý do với
     * {@code ExternalMedicalRecordService.toCard}: trình duyệt đọc theo TÊN KHOÁ, nên hình dạng
     * này phải có đúng một nơi định nghĩa.
     */
    Map<String, Object> toSymptomCard(ImageAnalysis analysis);
}
