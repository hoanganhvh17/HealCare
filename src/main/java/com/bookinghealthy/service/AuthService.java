package com.bookinghealthy.service;

import com.bookinghealthy.dto.RegisterDTO;
import com.bookinghealthy.model.AuthProvider;
import com.bookinghealthy.model.Role;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public User registerUser(RegisterDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername()))
            throw new RuntimeException("Username already exists");
        if (userRepository.existsByEmail(dto.getEmail()))
            throw new RuntimeException("Email already exists");

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        // Không đặt trường này thì mọi tài khoản đăng ký qua web mang auth_provider = NULL,
        // trong khi tài khoản seed, tài khoản lễ tân tạo tại quầy và tài khoản Google/Facebook
        // đều có giá trị — tức cột dùng để phân biệt đăng nhập nội bộ với đăng nhập mạng xã
        // hội đang bỏ trống ở đúng nhóm người dùng đông nhất.
        user.setAuthProvider(AuthProvider.LOCAL);

        Role roleUser = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_USER")));
        user.setRoles(Collections.singleton(roleUser));

        return userRepository.save(user);
    }
}

