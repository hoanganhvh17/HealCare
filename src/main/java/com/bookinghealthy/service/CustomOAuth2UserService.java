package com.bookinghealthy.service;

import com.bookinghealthy.model.AuthProvider;
import com.bookinghealthy.model.Role;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.security.CustomOAuth2User;
import com.bookinghealthy.security.userinfo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);

        // 1. Xác định Provider là Google hay Facebook
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = null;

        if ("google".equals(registrationId)) {
            userInfo = new GoogleOAuth2UserInfo(user.getAttributes());
        } else if ("facebook".equals(registrationId)) {
            userInfo = new FacebookOAuth2UserInfo(user.getAttributes());
        } else {
            throw new OAuth2AuthenticationException("Login with " + registrationId + " is not supported yet.");
        }

        // 2. Trả về CustomUser kèm theo userInfo đã chuẩn hóa
        return new CustomOAuth2User(user, userInfo);
    }

    // Hàm này giữ nguyên logic lưu DB nhưng dùng userInfo để lấy dữ liệu
    // Bạn cần update lại OAuth2LoginSuccessHandler để gọi hàm này đúng cách (truyền userInfo)
    // Tuy nhiên, cách tốt nhất là xử lý lưu DB ngay tại đây hoặc trong Handler.
    // Để đơn giản, ta sẽ để logic lưu DB trong Handler như cũ, nhưng update Handler ở bước sau.
}