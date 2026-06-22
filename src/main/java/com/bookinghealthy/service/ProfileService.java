package com.bookinghealthy.service;

import com.bookinghealthy.dto.UpdateProfileDTO;
import com.bookinghealthy.model.User;

public interface ProfileService {

    // Lấy thông tin user
    User getProfile(String username);

    // Cập nhật thông tin cơ bản
    void updateProfile(String username, UpdateProfileDTO dto);

    // Cập nhật ảnh đại diện
    void updateAvatar(String username, String avatarPath);

    // Đổi mật khẩu (Quan trọng: Phải có hàm này thì Controller mới gọi được)
    void changePassword(String username, String currentPassword, String newPassword);
}