package com.bookinghealthy.security;

import com.bookinghealthy.model.Role;
import com.bookinghealthy.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class CustomUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;
    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final String avatar;
    private final String email;
    private final List<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.fullName = user.getFullName();
        this.avatar = user.getAvatar();
        this.email = user.getEmail();
        this.authorities = new ArrayList<>();
        Set<Role> roles = user.getRoles();
        if (roles != null) {
            for (Role role : roles) {
                this.authorities.add(new SimpleGrantedAuthority(role.getName()));
            }
        }
    }

    public String getFullName() {
        return fullName;
    }

    // Gọi bằng: ${#authentication.principal.avatar}
    public String getAvatar() {
        return avatar;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
    // ==========================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username; // Hoặc user.getEmail() tùy logic đăng nhập của bạn
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
