package com.bookinghealthy.service;

import com.bookinghealthy.model.User;
import java.util.List; // <-- THÊM IMPORT NÀY
import java.util.Optional;

public interface UserService {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findAll(); // <-- THÊM HÀM MỚI NÀY

    // === THÊM 3 HÀM MỚI ===
    Optional<User> findById(Long id);
    User save(User user);
    void deleteById(Long id);

    // === THÊM HÀM MỚI NÀY ===
    List<User> findByRoleName(String roleName);

    /**
     * Nguồn sự thật duy nhất cho "có còn xoá được tài khoản này không".
     * Trả về null nếu xoá được; ngược lại là câu tiếng Việt giải thích vì sao không.
     * Controller dùng để chặn thật, template dùng để ẩn nút — cùng một khuôn với
     * BookingService.whyCannotCancel.
     */
    String whyCannotDelete(User user, String currentUsername);

    /**
     * Xoá tài khoản cùng các bản ghi PHỤ THUỘC không mang giá trị độc lập
     * (hồ sơ nhân sự, thông báo, phiên chat, dị ứng, ca trực, đơn nghỉ phép).
     * Chỉ gọi khi whyCannotDelete trả về null.
     */
    void deleteAccount(Long id);
}