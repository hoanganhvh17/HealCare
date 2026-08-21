package com.bookinghealthy.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    Path uploadRoot();

    Path privateRoot();

    String storeImage(MultipartFile file, String subdir);

    String storeCv(MultipartFile file);

    Path resolveCv(String storedFileName);

    /**
     * Lưu một hồ sơ bệnh án bệnh nhân mang từ nơi khác tới (ảnh chụp hoặc PDF).
     * Trả về TÊN TỆP đã lưu, giống {@link #storeCv(MultipartFile)}.
     * <p>
     * Nằm ở thư mục RIÊNG TƯ chứ không phải {@code app.upload-dir}: đây là dữ liệu sức khoẻ, mà
     * {@code /uploads/**} là {@code permitAll} và trên production nginx phục vụ thẳng thư mục đó —
     * chỗ Spring Security không hề chạy. Cùng lập luận đã dùng cho CV ứng viên.
     */
    String storeMedicalDocument(MultipartFile file);

    /** Đường dẫn tuyệt đối của một hồ sơ ngoại viện, hoặc null nếu tên tệp không hợp lệ / tệp đã mất. */
    Path resolveMedicalDocument(String storedFileName);

    /** Xoá tệp hồ sơ ngoại viện khỏi đĩa. Trả về true nếu thực sự có tệp bị xoá. */
    boolean deleteMedicalDocument(String storedFileName);
}
