package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List; // <-- THÊM IMPORT NÀY
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // === THÊM 3 PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public User save(User user) {
        // (Chúng ta sẽ thêm logic mã hóa mật khẩu ở đây sau)
        return userRepository.save(user);
    }

    @Override
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public List<User> findByRoleName(String roleName) {
        return userRepository.findByRoles_Name(roleName);
    }
}