package com.bookinghealthy.repository;

import com.bookinghealthy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query; // Thêm import này
import org.springframework.data.repository.query.Param; // Thêm import này

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * === SỬA LẠI HÀM NÀY ===
     * Thêm @Query để ép Spring JOIN FETCH roles (giải quyết LazyInitializationException)
     * khi BookingController hoặc CustomUserDetailsService gọi hàm này.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * === SỬA LẠI HÀM NÀY ===
     * Thêm @Query để ép Spring JOIN FETCH roles (giải quyết LazyInitializationException)
     * khi BookingController hoặc CustomUserDetailsService gọi hàm này.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    // (Giữ nguyên các hàm cũ)
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
//    // === THÊM HÀM MỚI NÀY ===
//    // Tìm tất cả User theo tên Role (ví dụ: "ROLE_USER")
//    List<User> findByRoles_Name(String roleName);           --v1-11/11-ok

    // === SỬA LỖI CÚ PHÁP Ở ĐÂY ===
    // (Đã thêm "r" sau u.roles và dùng r.name)
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles r WHERE r.name = :roleName")
    List<User> findByRoles_Name(@Param("roleName") String roleName);
    // === KẾT THÚC SỬA LỖI ===

    /**
     * Đếm người dùng theo vai trò mà KHÔNG nạp cả bảng.
     *
     * AdminController.adminHome trước đây gọi findByRoles_Name(...).size() HAI lần cho mỗi lần mở
     * dashboard — tức nạp trọn 149 dòng users kèm JOIN FETCH roles, hai lượt, chỉ để lấy hai con số.
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.name = :roleName")
    long countByRoleName(@Param("roleName") String roleName);

    @Override
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles")
    List<User> findAll();

    /* =========================================================================================
     * SỐ DƯ VÍ — phải đổi bằng UPDATE nguyên tử, KHÔNG đọc-tính-ghi trong Java.
     *
     * Bản cũ của WalletServiceImpl làm: đọc getBalance() -> so sánh -> setBalance(...) -> save().
     * Hai request song song cùng đọc 200.000, cả hai cùng qua cửa kiểm số dư, cả hai cùng ghi
     * 0 — bệnh nhân đặt được HAI lịch mà ví chỉ bị trừ MỘT lần. Đây là lost update kinh điển,
     * và nó không hiếm: bấm đúp nút Đặt lịch là đủ.
     *
     * Điều kiện `balance >= :amount` nằm ngay trong WHERE nên phép kiểm và phép trừ là MỘT
     * thao tác; số dòng bị ảnh hưởng (0 hoặc 1) chính là câu trả lời "có đủ tiền không".
     *
     * Chọn cách này thay vì @Version trên User: optimistic locking ở đó sẽ bắt MỌI đường ghi
     * User khác trong dự án (đổi hồ sơ, đổi avatar, admin sửa vai trò) phải xử lý xung đột
     * phiên bản, đổi một vùng rộng để vá một chỗ hẹp.
     * ========================================================================================= */

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.balance = u.balance - :amount "
         + "WHERE u.id = :userId AND u.balance >= :amount")
    int debitBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.balance = u.balance + :amount WHERE u.id = :userId")
    int creditBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);


}