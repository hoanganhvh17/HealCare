package com.bookinghealthy.security;

import com.bookinghealthy.model.AuthProvider;
import com.bookinghealthy.model.Role;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.security.userinfo.OAuth2UserInfo;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // 1. Lấy Principal đã được CustomOAuth2UserService xử lý
        CustomOAuth2User oauth2User = (CustomOAuth2User) authentication.getPrincipal();

        // 2. Lấy thông tin chuẩn hóa (Không cần quan tâm là Google hay Facebook nữa)
        String email = oauth2User.getEmail();
        String name = oauth2User.getFullName();
        String picture = oauth2User.getAvatar();

        // Lấy Provider ID (google, facebook...) để lưu vào DB cho đúng
        String providerId = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

        // 3. Xử lý Email (Quan trọng với Facebook: nếu không có email thì tạo email giả lập)
        if (email == null || email.isEmpty()) {
            String id = oauth2User.getName(); // Hoặc lấy ID từ attribute nếu cần thiết
            // Email giả lập để không bị lỗi DB (unique)
            email = id.replaceAll("\\s+", "").toLowerCase() + "@" + providerId + ".com";
        }

        // 4. Lưu hoặc Cập nhật User vào DB
        Optional<User> existUser = userRepository.findByEmail(email);

        if (existUser.isEmpty()) {
            // == USER MỚI -> ĐĂNG KÝ ==
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFullName(name != null ? name : "User " + providerId);
            newUser.setUsername(email);
            newUser.setAuthProvider(AuthProvider.valueOf(providerId.toUpperCase()));
            newUser.setAvatar(picture);
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // Pass ngẫu nhiên
            newUser.setGender("Chưa cập nhật");

            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            newUser.setRoles(Collections.singleton(userRole));

            userRepository.save(newUser);
        } else {
            // == USER CŨ -> CẬP NHẬT (Nếu muốn) ==
            User user = existUser.get();
            // Nếu muốn cập nhật avatar mới nhất từ MXH thì mở dòng này:
            // user.setAvatar(picture);
            // userRepository.save(user);
        }

        // 5. Chuyển hướng theo Role
        Optional<User> userFinal = userRepository.findByEmail(email);
        if(userFinal.isPresent()){
            boolean isAdmin = userFinal.get().getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN"));
            boolean isDoctor = userFinal.get().getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_DOCTOR"));
            boolean isReceptionist = userFinal.get().getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_RECEPTIONIST"));
            // Trưởng khoa được seed vẫn giữ ROLE_DOCTOR nên rơi vào nhánh isDoctor ở trên;
            // nhánh này chỉ dành cho tài khoản trưởng khoa "thuần" (chỉ có ROLE_HEAD_DOCTOR).
            boolean isHeadDoctor = userFinal.get().getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_HEAD_DOCTOR"));

            if(isAdmin) response.sendRedirect("/admin/dashboard");
            else if(isDoctor) response.sendRedirect("/doctor/dashboard");
            else if(isReceptionist) response.sendRedirect("/receptionist/dashboard");
            else if(isHeadDoctor) response.sendRedirect("/head/dashboard");
            else response.sendRedirect("/");
        } else {
            response.sendRedirect("/");
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}