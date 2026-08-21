package com.bookinghealthy.service;

import com.bookinghealthy.dto.ImageAnalysis;
import com.bookinghealthy.model.ExternalMedicalRecord;
import com.bookinghealthy.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Cửa DUY NHẤT cho hồ sơ bệnh án bệnh nhân mang từ nơi khác tới.
 *
 * <p>Theo đúng khuôn {@link AllergyService}: mọi luật "còn làm được hay không" nằm trong một hàm
 * {@code whyCannot…} trả {@code null} khi được phép và câu tiếng Việt khi không — template dùng nó
 * để ẩn nút, controller dùng lại chính nó để chặn thật, nên giao diện và server không bao giờ nói
 * khác nhau.
 */
public interface ExternalMedicalRecordService {

    /** Toàn bộ hồ sơ ngoại viện của một bệnh nhân, mới nhất lên đầu. */
    List<ExternalMedicalRecord> findForUser(Long userId);

    /**
     * Lưu tệp + tạo dòng ở trạng thái {@code PENDING}. KHÔNG gọi AI — người gọi tự quyết định
     * có chạy {@link #analyze(Long)} ngay hay không, vì đó là một lượt gọi mạng chậm.
     *
     * @throws IllegalStateException khi tiêu đề trống hoặc tệp không hợp lệ (câu tiếng Việt sẵn sàng hiển thị)
     */
    ExternalMedicalRecord upload(Long userId, String title, String patientNote, MultipartFile file);

    /**
     * Ghi kết quả phân tích ĐÃ CÓ SẴN vào một hồ sơ, KHÔNG gọi AI lần nào.
     *
     * <p>Dùng cho đường đính kèm trong khung chat: ở đó ảnh đã phải đi qua một lượt gọi thị giác để
     * biết nó là giấy tờ hay ảnh triệu chứng, nên kết quả nằm sẵn trong tay. Gọi lại {@link #analyze}
     * là trả tiền hai lần cho đúng một tấm ảnh.
     */
    void applyAnalysis(Long recordId, ImageAnalysis analysis);

    /**
     * Đọc tệp, gọi AI, ghi lại bản tóm tắt + khoa gợi ý.
     *
     * <p>Tự nuốt mọi lỗi vào {@code aiStatus} chứ không ném ra ngoài: hỏng phân tích không được
     * phép làm hỏng việc tải lên — tệp đã nằm trên đĩa và bác sĩ vẫn mở xem được.
     */
    void analyze(Long recordId);

    /** {@code null} = được xem. Chủ hồ sơ luôn được; bác sĩ chỉ được khi đã có lịch hẹn với bệnh nhân. */
    String whyCannotView(ExternalMedicalRecord record, User viewer);

    /** {@code null} = được xoá. Chỉ chủ hồ sơ. */
    String whyCannotDelete(ExternalMedicalRecord record, User viewer);

    /** Xoá cả dòng DB lẫn tệp trên đĩa. Kiểm lại {@link #whyCannotDelete} trước khi làm. */
    void delete(Long recordId, User viewer);

    /**
     * Một hồ sơ dưới dạng Map cho các API JSON của khung chat.
     *
     * <p>Nằm ở tầng service chứ không phải trong controller vì có <b>HAI</b> endpoint trả cùng hình
     * dạng này — danh sách hồ sơ (<code>/api/chat/my-documents</code>) và kết quả tải lên ngay trong
     * khung chat — mà trình duyệt lại đọc theo TÊN KHOÁ. Hai bản sao là hai chỗ để một bên đổi tên
     * khoá còn bên kia thì không, và thẻ chat sẽ lặng lẽ in ra "undefined".
     */
    Map<String, Object> toCard(ExternalMedicalRecord record);
}
