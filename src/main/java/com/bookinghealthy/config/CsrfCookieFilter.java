package com.bookinghealthy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ép token CSRF được phân giải ở MỌI request, để cookie {@code XSRF-TOKEN} thật sự được gửi xuống.
 *
 * <p>Từ Spring Security 6, {@code CsrfToken} nạp <b>trễ</b>: {@code CookieCsrfTokenRepository} chỉ
 * ghi cookie vào lúc token được ĐỌC. Một trang không render form nào — trang chủ chỉ có khung chat
 * AI là ví dụ đúng nhất trong dự án này — sẽ không bao giờ chạm vào token, nên khách không nhận được
 * cookie, và lời gọi {@code fetch} POST đầu tiên ăn 403 mà không ai hiểu vì sao.
 *
 * <p>Chỉ cần gọi {@code getToken()} là chuỗi nạp trễ chạy và repository ghi cookie. Đây là mảnh hay
 * bị quên nhất khi bật CSRF theo hướng cookie — và nó hỏng đúng ở những trang tĩnh nhất, tức những
 * trang người ta thử sau cùng.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Giá trị trả về cố tình không dùng: chỉ cần CHẠM vào là đủ để cookie được ghi.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
