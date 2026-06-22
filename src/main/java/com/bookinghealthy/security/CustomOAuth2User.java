package com.bookinghealthy.security;

import com.bookinghealthy.security.userinfo.OAuth2UserInfo;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private OAuth2User oauth2User;
    private OAuth2UserInfo userInfo; // <-- Thêm biến này

    public CustomOAuth2User(OAuth2User oauth2User, OAuth2UserInfo userInfo) {
        this.oauth2User = oauth2User;
        this.userInfo = userInfo;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oauth2User.getAuthorities();
    }

    @Override
    public String getName() {
        return userInfo.getName(); // Lấy từ userInfo chuẩn hóa
    }

    public String getEmail() {
        return userInfo.getEmail();
    }

    public String getFullName() {
        return userInfo.getName();
    }

    public String getAvatar() {
        return userInfo.getImageUrl();
    }
}