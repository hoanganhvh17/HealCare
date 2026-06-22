package com.bookinghealthy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email; // <-- THÊM IMPORT
import jakarta.validation.constraints.NotBlank; // <-- THÊM IMPORT
import jakarta.validation.constraints.Size; // <-- THÊM IMPORT
import lombok.*;
import java.math.BigDecimal; // Import BigDecimal
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username không được để trống") // <-- THÊM
    @Size(min = 3, message = "Username phải có ít nhất 3 ký tự") // <-- THÊM
    @Column(nullable = false, unique = true)
    private String username;

    @NotBlank(message = "Email không được để trống") // <-- THÊM
    @Email(message = "Email không đúng định dạng") // <-- THÊM
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Họ tên không được để trống") // <-- THÊM
    private String fullName;

    private String phone;
    private String avatar;
    // === BỔ SUNG TRƯỜNG NÀY NẾU THIẾU ===
    @Column(name = "gender")
    private String gender; // Nam / Nữ

    // === THÊM TRƯỜNG MỚI NÀY ===
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider")
    private AuthProvider authProvider;
    // ============================

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;

    // === TRƯỜNG MỚI: SỐ DƯ VÍ ===
    // Mặc định là 0
    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;
    // ============================

}