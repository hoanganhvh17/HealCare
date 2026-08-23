/**
 * Token CSRF cho mọi lời gọi fetch ĐỔI TRẠNG THÁI.
 *
 * Form Thymeleaf không cần tệp này: bộ xử lý `th:action` tự chèn hidden input `_csrf`. Chỉ có
 * `fetch` là phải tự lo, vì JS tĩnh không đọc được biểu thức Thymeleaf.
 *
 * Token lấy từ COOKIE chứ không phải từ một thẻ <meta> do Thymeleaf in ra, và đó là lựa chọn có
 * chủ đích: dự án này đã bị cắn nhiều lần vì "fragment A có ở 31 trang, fragment B chỉ có ở 12"
 * (main.js, footer, bootstrap nạp hai lần). Đọc cookie thì không phụ thuộc trang đó nạp fragment
 * nào — máy chủ đặt cookie cho MỌI request, kể cả khách chưa đăng nhập.
 *
 * PHẢI đọc lại ở từng lời gọi, không được đọc một lần rồi nhớ: Spring cấp token MỚI sau khi đăng
 * nhập, nên một giá trị nhớ từ lúc tải trang sẽ chết ngay sau lần đăng nhập kế tiếp.
 */
(function () {
    'use strict';

    // Tên do CookieCsrfTokenRepository quy định; đổi ở SecurityConfig thì phải đổi cả ở đây.
    var COOKIE_NAME = 'XSRF-TOKEN';
    var HEADER_NAME = 'X-XSRF-TOKEN';

    function readToken() {
        // Bọc try/catch cùng lý do với safeStorage trong ai-chat.js: ở chế độ ẩn danh hoặc khi
        // trình duyệt chặn lưu trữ của bên thứ ba, chạm vào document.cookie có thể ném — mà lỗi
        // đó nổ giữa lúc tải trang sẽ giết phần script còn lại.
        try {
            var parts = document.cookie ? document.cookie.split('; ') : [];
            for (var i = 0; i < parts.length; i++) {
                var eq = parts[i].indexOf('=');
                if (eq > 0 && parts[i].slice(0, eq) === COOKIE_NAME) {
                    return decodeURIComponent(parts[i].slice(eq + 1));
                }
            }
        } catch (e) {
            console.warn('[CSRF] Không đọc được cookie token:', e);
        }
        return '';
    }

    window.MediTrustCsrf = {
        headerName: HEADER_NAME,
        token: readToken,

        /**
         * Trộn header token vào bộ header sẵn có.
         *
         * Với multipart (FormData) thì gọi headers() KHÔNG tham số và ĐỪNG tự đặt Content-Type:
         * trình duyệt phải tự sinh Content-Type kèm boundary, đặt tay là hỏng cả lần tải tệp.
         */
        headers: function (extra) {
            var out = {};
            if (extra) {
                for (var k in extra) {
                    if (Object.prototype.hasOwnProperty.call(extra, k)) out[k] = extra[k];
                }
            }
            var t = readToken();
            if (t) out[HEADER_NAME] = t;
            return out;
        }
    };
})();
