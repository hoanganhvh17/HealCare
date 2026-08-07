package com.bookinghealthy.config;

import com.bookinghealthy.security.OAuth2LoginSuccessHandler;
import com.bookinghealthy.service.CustomOAuth2UserService;
import com.bookinghealthy.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CustomOAuth2UserService oauth2UserService; // <-- Inject Service mới

    @Autowired
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler; // <-- Inject Handler mới

    @Autowired
    private PasswordEncoder passwordEncoder;

//    @Bean
//    public BCryptPasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }

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
                        // Thêm luật mới cho /api/doctor/** hay /admin/** thì đặt ở ĐÂY, đừng đặt xuống dưới.
                        .requestMatchers("/api/doctor/chat/**").hasRole("DOCTOR")
                        .requestMatchers("/api/admin/chat/**").hasRole("ADMIN")
                        // Giải thích hồ sơ bệnh án: phải đăng nhập, đứng TRÊN /api/chat/**.permitAll()
                        // bên dưới — nếu không lại rơi vào whitelist công khai, đúng bẫy đã từng xảy ra
                        // với /api/doctor/chat/** (xem authentication-and-roles.md).
                        .requestMatchers("/api/chat/medical-record/**").authenticated()
                        // Chuông thông báo (bệnh nhân + mọi vai trò). Controller tự lọc theo
                        // người đang đăng nhập. Khai ở đây cho tường minh: rơi xuống
                        // anyRequest().authenticated() thì cũng đúng, nhưng quy ước của khối 0
                        // là mọi luật /api/... có ràng buộc đều phải nhìn thấy được ngay đầu file.
                        .requestMatchers("/api/notifications/**").authenticated()

                        // --- 1. CÁC TRANG CÔNG KHAI (AI CŨNG XEM ĐƯỢC) ---
                        .requestMatchers(
                                "/", "/home",             // Trang chủ
                                "/login", "/register",       // Đăng nhập, Đăng ký
                                "/doctors", "/doctors/**",   // Trang Bác sĩ
                                "/services", "/services/**", // Trang Dịch vụ
                                "/departments", "/department-details/**", // Trang Khoa (Mới)
                                "/contact", "/about",       // Trang Liên hệ, Giới thiệu
                                "/news", "/news/**",  // <-- THÊM DÒNG NÀY (Cho phép xem tin tức)
                                "/medical-process",
                                "/working-hours",
                                "/doctor-schedule",
                                "/api/chat/**",
                                "/knowledge",
                                "/api/payment/**", // Mở API Webhook cho Casso/SePay
                                "/checkout-qr",    // Mở trang hiển thị mã QR
                                // === MỞ CỬA CHO CÁC TRANG CHI TIẾT ===
                                "/services", "/service-details/**",      // Cho phép /services VÀ /service-details/1...
                                "/departments", "/department-details/**",// Cho phép /departments VÀ /department-details/1...
                                "/api/doctors",                 // Lấy danh sách bác sĩ
                                "/api/doctors/**",              // Tìm bác sĩ theo tên (trợ lý AI dùng)
                                "/api/doctor/**",               // Lấy chi tiết 1 bác sĩ (/api/doctor/{id})
                                "/api/bookings/booked-slots"
                        ).permitAll()

                        // --- 2. TÀI NGUYÊN TĨNH (CSS, JS, ẢNH) ---
                        .requestMatchers(
                                "/assets/**",
                                "/assets-admin/**",
                                "/uploads/**"
                        ).permitAll()

                        // --- 3. PHÂN QUYỀN ADMIN (BẮT BUỘC ROLE_ADMIN) ---
                        // Sửa: Dùng "/admin/**" để bao gồm TẤT CẢ các trang con
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // --- 4. PHÂN QUYỀN BÁC SĨ (BẮT BUỘC ROLE_DOCTOR) ---
                        .requestMatchers("/doctor/**").hasRole("DOCTOR")

                        // --- 4b. PHÂN QUYỀN LỄ TÂN (BẮT BUỘC ROLE_RECEPTIONIST) ---
                        .requestMatchers("/receptionist/**").hasRole("RECEPTIONIST")

                        // --- 4c. PHÂN QUYỀN TRƯỞNG KHOA (DUYỆT ĐƠN NGHỈ & LỊCH TRỰC) ---
                        // Trưởng khoa vẫn giữ ROLE_DOCTOR nên successHandler bên dưới
                        // không cần thêm nhánh: họ vào /doctor/dashboard như bác sĩ thường.
                        .requestMatchers("/head/**").hasRole("HEAD_DOCTOR")

                        // --- 4d. API LỊCH LÀM VIỆC (BÁC SĨ + LỄ TÂN DÙNG CHUNG) ---
                        // Controller tự lọc dữ liệu theo người đang đăng nhập.
                        .requestMatchers("/api/staff/**").authenticated()

                        // --- 5. CÁC TRANG CẦN ĐĂNG NHẬP (USER, DOCTOR, ADMIN đều được) ---
                        .requestMatchers(
                                "/appointment",
                                "/user/profile",          // <-- SỬA TỪ /profile THÀNH /user/profile
                                "/user/change-password",  // <-- SỬA TỪ /change-password THÀNH /user/change-password
                                "/user/update-profile",   // <-- Thêm các action update
                                "/user/upload-avatar",
                                "/user/review/**",        // <-- Cho phép gửi review
                                "/user/booking/**"        // <-- Bệnh nhân tự sửa lịch hẹn của mình
                        ).authenticated()

                        // 6. Mọi request CÒN LẠI đều phải đăng nhập
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        // === NÂNG CẤP: PHÂN LUỒNG ĐĂNG NHẬP ===
                        .successHandler((request, response, authentication) -> {
                            // Nếu là ADMIN, về /admin/dashboard
                            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                                response.sendRedirect("/admin/dashboard");
                            }
                            // Nếu là DOCTOR, về /doctor/dashboard
                            else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"))) {
                                response.sendRedirect("/doctor/dashboard");
                            }
                            // Nếu là LỄ TÂN, về /receptionist/dashboard
                            else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RECEPTIONIST"))) {
                                response.sendRedirect("/receptionist/dashboard");
                            }
                            // Trưởng khoa "thuần" (không kèm ROLE_DOCTOR) — kiểm tra SAU ROLE_DOCTOR
                            // để trưởng khoa được seed (giữ cả ROLE_DOCTOR) vẫn về /doctor/dashboard như cũ.
                            else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HEAD_DOCTOR"))) {
                                response.sendRedirect("/head/dashboard");
                            }
                            // Nếu là USER (Bệnh nhân), về trang chủ
                            else {
                                response.sendRedirect("/");
                            }
                        })
                        .permitAll()
                )
                // === CẤU HÌNH OAUTH2 (GOOGLE) ===
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oauth2UserService) // Dùng Service của mình để load user
                        )
                        .successHandler(oauth2LoginSuccessHandler) // Dùng Handler để xử lý sau khi login xong
                )
                // ================================
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
