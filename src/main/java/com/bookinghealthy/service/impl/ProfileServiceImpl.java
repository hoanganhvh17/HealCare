package com.bookinghealthy.service.impl;

import com.bookinghealthy.dto.UpdateProfileDTO;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User getProfile(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    @Transactional
    public void updateProfile(String username, UpdateProfileDTO dto) {
        User user = getProfile(username);
        // Chỉ cập nhật nếu có dữ liệu
        if (dto.getFullName() != null && !dto.getFullName().isEmpty()) {
            user.setFullName(dto.getFullName());
        }
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            user.setPhone(dto.getPhone());
        }
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateAvatar(String username, String avatarPath) {
        User user = getProfile(username);
        user.setAvatar(avatarPath); // Lưu tên file ảnh vào database
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = getProfile(username);

        // 1. Kiểm tra mật khẩu cũ có khớp không
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng!");
        }

        // 2. Mã hóa và lưu mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}