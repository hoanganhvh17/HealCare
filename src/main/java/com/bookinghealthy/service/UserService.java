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
}