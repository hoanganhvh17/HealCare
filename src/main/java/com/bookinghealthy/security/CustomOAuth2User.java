package com.bookinghealthy.security;

import com.bookinghealthy.security.userinfo.OAuth2UserInfo;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User, Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> attributes;
    private final List<GrantedAuthority> authorities;
    private final String name;
    private final String email;
    private final String avatar;
    public CustomOAuth2User(OAuth2User oauth2User, OAuth2UserInfo userInfo) {
        this.attributes = new LinkedHashMap<>(oauth2User.getAttributes());
        this.authorities = new ArrayList<>(oauth2User.getAuthorities());
        this.name = userInfo.getName();
        this.email = userInfo.getEmail();
        this.avatar = userInfo.getImageUrl();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return name; // Lấy từ userInfo chuẩn hóa
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return name;
    }

    public String getAvatar() {
        return avatar;
    }
}
