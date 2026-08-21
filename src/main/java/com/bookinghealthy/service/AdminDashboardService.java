package com.bookinghealthy.service;

import com.bookinghealthy.dto.AdminDashboardDTO;

/**
 * Dựng toàn bộ số liệu cho {@code /admin/dashboard}.
 *
 * Tồn tại để {@code AdminController.adminHome} không còn là 137 dòng số học nội tuyến nằm trong một
 * class 600+ dòng vốn đã giữ CRUD người dùng. Controller giờ chỉ đọc tham số {@code range}, gọi
 * {@link #build(String)} và đẩy một đối tượng ra model.
 *
 * <p><b>Đừng nhầm với {@code AdminAiReportService}</b> (trước đây mang đúng cái tên này). Lớp kia
 * phục vụ khung chat AI của admin và trả về {@code AdminDashboardSummaryDTO}; nó chưa từng nuôi
 * trang dashboard. Nay nó lấy các con số dùng chung từ đây, để hai bề mặt không thể nói khác nhau.
 */
public interface AdminDashboardService {

    /** Giá trị mặc định của {@code ?range=} khi không truyền hoặc truyền giá trị lạ. */
    String DEFAULT_RANGE = "30";

    /**
     * @param rangeKey một trong {@code "7"}, {@code "30"}, {@code "90"}, {@code "all"};
     *                 giá trị không hợp lệ rơi về {@link #DEFAULT_RANGE} thay vì ném lỗi —
     *                 dashboard là trang đích sau khi đăng nhập, không được 500 vì một query param.
     */
    AdminDashboardDTO build(String rangeKey);
}
