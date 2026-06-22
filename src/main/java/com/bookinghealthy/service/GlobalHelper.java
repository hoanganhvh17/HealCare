package com.bookinghealthy.service;

import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;

@Service("globalHelper") // Đặt tên bean để gọi từ HTML
public class GlobalHelper {

    @Autowired
    private UserRepository userRepository;

    public String getUserBalance(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "0 đ";
        }

        Object principal = authentication.getPrincipal();
        String tempId = null; // Dùng biến tạm

        // 1. Lấy định danh (Email hoặc Username)
        if (principal instanceof OAuth2User) {
            tempId = ((OAuth2User) principal).getAttribute("email");
        } else if (principal instanceof UserDetails) {
            tempId = ((UserDetails) principal).getUsername();
        }

        if (tempId == null) return "0 đ";

        // === FIX LỖI: TẠO BIẾN FINAL ĐỂ DÙNG TRONG LAMBDA ===
        // Biến này chỉ được gán 1 lần duy nhất tại đây -> Java coi là Final
        String finalId = tempId;
        // ====================================================

        // 2. Query trực tiếp từ DB để lấy số dư mới nhất
        // Sử dụng finalId thay vì tempId trong lambda
        User user = userRepository.findByUsername(finalId)
                .orElseGet(() -> userRepository.findByEmail(finalId).orElse(null));

        if (user != null && user.getBalance() != null) {
            // Format tiền tệ (Ví dụ: 500,000 đ)
            DecimalFormat formatter = new DecimalFormat("#,###");
            return formatter.format(user.getBalance()) + " đ";
        }

        return "0 đ";
    }
}