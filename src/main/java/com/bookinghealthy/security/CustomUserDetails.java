package com.bookinghealthy.security;

import com.bookinghealthy.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

public class CustomUserDetails implements UserDetails {

    private User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    // === CÁC HÀM QUAN TRỌNG ĐỂ THYMELEAF GỌI ===
    // Gọi bằng: ${#authentication.principal.fullName}
    public String getFullName() {
        return user.getFullName();
    }

    // Gọi bằng: ${#authentication.principal.avatar}
    public String getAvatar() {
        return user.getAvatar();
    }

    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }
    // ==========================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername(); // Hoặc user.getEmail() tùy logic đăng nhập của bạn
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Có thể check user.isEnabled() nếu bạn có trường đó
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}