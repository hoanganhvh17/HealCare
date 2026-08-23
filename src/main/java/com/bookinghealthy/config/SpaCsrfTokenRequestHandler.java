package com.bookinghealthy.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Cho phép DÙNG CHUNG một token cho cả form Thymeleaf lẫn lời gọi {@code fetch}.
 *
 * <p>Vấn đề nó giải quyết — và nếu làm sai thì một trong hai đường sẽ hỏng toàn bộ:
 *
 * <ul>
 *   <li>Handler mặc định {@link XorCsrfTokenRequestAttributeHandler} <b>che token bằng mặt nạ XOR</b>
 *       mỗi lần render (chống BREACH), nên giá trị in vào hidden input của form khác nhau ở từng
 *       lần tải trang.</li>
 *   <li>Nhưng {@code CookieCsrfTokenRepository} lại ghi token <b>thô</b> vào cookie. JS đọc cookie
 *       rồi gửi lên header sẽ bị đem đi giải mặt nạ và <b>trượt</b> — mọi fetch POST ăn 403.</li>
 * </ul>
 *
 * <p>Nên: {@code handle()} vẫn ủy quyền cho bản XOR (form giữ nguyên chống BREACH), còn
 * {@code resolveCsrfTokenValue()} thì rẽ theo nguồn — có header thì so THÔ, không thì mới giải mặt
 * nạ như cũ cho tham số form. Đây là khuôn chính thức trong tài liệu Spring Security.
 *
 * <p><b>Đừng "đơn giản hoá"</b> bằng cách bỏ hẳn XOR: làm vậy là tự tay gỡ lớp chống BREACH cho
 * toàn bộ 48 form của dự án chỉ để cho 13 lời gọi fetch chạy.
 */
public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        this.delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            // Đến từ fetch: giá trị lấy thẳng từ cookie nên là token THÔ.
            return super.resolveCsrfTokenValue(request, csrfToken);
        }
        // Đến từ form: là giá trị đã bị XOR lúc render, phải giải mặt nạ.
        return this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
