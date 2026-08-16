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
                                "/working-hours",
                                "/doctor-schedule",
                                "/api/chat/**",
                                "/knowledge",
                                "/services", "/service-details/**",
                                "/departments", "/department-details/**",
                                "/api/doctors",
                                "/api/doctors/**",
                                "/api/doctor/**",
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
                                "/user/allergy/**"
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
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
