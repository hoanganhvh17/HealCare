package com.bookinghealthy.config;

import com.bookinghealthy.security.OAuth2LoginSuccessHandler;
import com.bookinghealthy.service.CustomOAuth2UserService;
import com.bookinghealthy.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
// Bật @PreAuthorize/@PostAuthorize. THIẾU dòng này thì mọi annotation đó chỉ là chú thích:
// chúng KHÔNG chặn gì cả, và không có cảnh báo nào lúc biên dịch lẫn lúc chạy.
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CustomOAuth2UserService oauth2UserService;

    @Autowired
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/doctor/chat/**").hasRole("DOCTOR")
                        .requestMatchers("/api/admin/chat/**").hasRole("ADMIN")
                        .requestMatchers("/api/chat/medical-record/**").authenticated()
                        .requestMatchers("/api/chat/my-bookings").authenticated()
                        // Hồ sơ bệnh án ngoại viện bệnh nhân tự tải lên. Cùng lý do với dòng
                        // trên: /api/chat/** là permitAll ở khối dưới, mà Spring lấy luật khớp
                        // ĐẦU TIÊN — khai ở dưới đó thì dòng này hoàn toàn vô tác dụng.
                        .requestMatchers("/api/chat/my-documents").authenticated()
                        // Giữ chỗ tạm thời cho MỘT khung giờ. Nằm dưới /api/chat/** (permitAll) nên
                        // trước đây khách vô danh gửi sessionId tuỳ ý là giữ được cả lịch; chỉ người đã
                        // đăng nhập mới đặt được lịch nên siết ở đây không mất chức năng nào.
                        .requestMatchers("/api/chat/hold-slot").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/payment/webhook").permitAll()
                        .requestMatchers("/api/payment/**").authenticated()
                        .requestMatchers("/checkout-qr").authenticated()
                        .requestMatchers(
                                "/", "/home",
                                "/login", "/register",
                                "/doctors", "/doctors/**",
                                "/services", "/services/**",
                                "/departments", "/department-details/**",
                                "/contact", "/about",
                                "/news", "/news/**",
                                "/medical-process",
                                // Trang tuyển dụng là trang CÔNG KHAI. Thiếu ba dòng này, khách vãng
                                // lai bấm "Tuyển dụng" bị đẩy sang /login và cả tính năng vô hình
                                // với đúng đối tượng nó phục vụ.
                                "/careers",
                                "/career-details/**",
                                "/career-apply",
                                "/working-hours",
                                "/doctor-schedule",
                                "/api/chat/**",
                                "/knowledge",
                                "/services", "/service-details/**",
                                "/departments", "/department-details/**",
                                "/api/doctors",
                                "/api/doctors/**",
                                // KHÔNG được để "/api/doctor/**": luật rộng đó từng nuốt cả
                                // /api/doctor/assistant/** và biến nó thành endpoint công khai gọi
                                // OpenRouter bằng API key bệnh viện. Chỉ /api/doctor/{id} cần mở.
                                "/api/doctor/{id:[0-9]+}",
                                "/api/bookings/booked-slots"
                        ).permitAll()
                        .requestMatchers(
                                "/assets/**",
                                "/assets-admin/**",
                                "/uploads/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/doctor/**").hasRole("DOCTOR")
                        .requestMatchers("/receptionist/**").hasRole("RECEPTIONIST")
                        .requestMatchers("/head/**").hasRole("HEAD_DOCTOR")
                        .requestMatchers("/api/staff/**").authenticated()
                        .requestMatchers(
                                "/appointment",
                                "/user/profile",
                                "/user/change-password",
                                "/user/update-profile",
                                "/user/upload-avatar",
                                "/user/review/**",
                                "/user/booking/**",
                                "/user/allergy/**",
                                "/user/medical-document/**"
                        ).authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                                response.sendRedirect("/admin/dashboard");
                            }
                            else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"))) {
                                response.sendRedirect("/doctor/dashboard");
                            }
                            else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RECEPTIONIST"))) {
                                response.sendRedirect("/receptionist/dashboard");
                            }
                            else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HEAD_DOCTOR"))) {
                                response.sendRedirect("/head/dashboard");
                            }
                            else {
                                response.sendRedirect("/");
                            }
                        })
                        .permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oauth2UserService)
                        )
                        .successHandler(oauth2LoginSuccessHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                // CSRF: BẬT. Trước đây là csrf.disable() cho toàn ứng dụng — mọi POST đổi trạng
                // thái (ví, đặt/sửa/huỷ lịch, CRUD admin, ghi bệnh án) đều giả mạo được từ site lạ.
                // SameSite=lax trên cookie phiên chỉ là giảm nhẹ, không phải cưỡng chế từ máy chủ.
                //
                // Token để ở COOKIE chứ không ở session: JS tĩnh đọc được nó ở mọi trang, không phụ
                // thuộc trang đó nạp fragment nào (xem assets/js/csrf.js). Nó cũng không đi vào
                // SPRING_SESSION_ATTRIBUTES nên không làm phình blob phiên.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Webhook Casso/SePay gọi server-to-server: KHÔNG phiên, KHÔNG cookie, nên
                        // không thể có token. Nó tự xác thực bằng bí mật trong header
                        // (VietQRController.isAuthorizedWebhook). permitAll() KHÔNG cứu được ở đây:
                        // CsrfFilter chạy TRƯỚC tầng phân quyền. Quên dòng này là tiền chuyển khoản
                        // của khách ngừng được ghi nhận, im lặng.
                        .ignoringRequestMatchers("/api/payment/webhook"))
                // Xem CsrfCookieFilter: thiếu nó thì trang không có form nào sẽ không bao giờ nhận
                // được cookie token, và fetch POST đầu tiên trên trang đó ăn 403.
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }
}
