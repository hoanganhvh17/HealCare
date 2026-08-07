package com.bookinghealthy.service;

import com.bookinghealthy.model.Allergy;
import com.bookinghealthy.model.User;

import java.util.List;

/**
 * Hồ sơ dị ứng của bệnh nhân — nguồn dữ liệu cho thẻ cảnh báo đỏ ở form khám và cho trợ lý AI
 * đối chiếu đơn thuốc.
 *
 * Ghi được từ HAI phía: bệnh nhân tự khai trong hồ sơ cá nhân, và bác sĩ ghi nhận lúc khám.
 * Mỗi bản ghi mang theo {@code source} để bác sĩ biết mục nào đã được đồng nghiệp xác nhận.
 */
public interface AllergyService {

    /** Danh sách dị ứng của một người, mới khai nằm trên cùng. */
    List<Allergy> findForUser(Long userId);

    /**
     * Thêm một mục dị ứng.
     *
     * @param source {@link Allergy#SOURCE_PATIENT} hoặc {@link Allergy#SOURCE_DOCTOR}
     * @return bản ghi vừa lưu
     * @throws IllegalStateException nếu thiếu tác nhân hoặc người này đã khai tác nhân đó rồi
     */
    Allergy add(Long userId, String allergen, String reaction, String severity, String source);

    /**
     * Lý do KHÔNG được xoá mục này, {@code null} nghĩa là xoá được.
     *
     * Nguồn sự thật DUY NHẤT cho quyền xoá: template gọi để ẩn nút và in lý do, controller gọi
     * lại trước khi xoá thật. Nhờ vậy một POST tự chế cũng bị từ chối đúng lý do đang hiển thị
     * — cùng khuôn {@code BookingService.whyCannotCancel} đã dùng cho lịch hẹn.
     */
    String whyCannotDelete(Allergy allergy, User currentUser);

    /**
     * Xoá một mục của chính người dùng đang đăng nhập.
     *
     * @throws IllegalStateException kèm lý do từ {@link #whyCannotDelete} nếu không được phép
     */
    void delete(Long allergyId, User currentUser);
}
