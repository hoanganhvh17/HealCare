/**
 * Menu điều hướng trên điện thoại — khu bệnh nhân.
 *
 * Vì sao tách khỏi assets/js/main.js (theme MediTrust): khối mobile-nav nằm trong main.js,
 * nhưng chỉ 12/23 trang bệnh nhân nạp tệp đó. Không nạp được cho 11 trang còn lại vì main.js
 * gọi `new PureCounter()`, `GLightbox(...)`, `scrollTop.addEventListener(...)` VÔ ĐIỀU KIỆN —
 * trang nào thiếu vendor tương ứng là ném lỗi. Nên phần mobile-nav được gỡ khỏi main.js và
 * chuyển hẳn sang đây; tệp này không phụ thuộc thư viện nào.
 *
 * TUYỆT ĐỐI không để nơi thứ hai cũng bind `.mobile-nav-toggle`: hai listener trên một cú chạm
 * là toggle() chạy hai lần, menu mở rồi đóng ngay và trông như nút bị liệt — đúng con bug
 * Bootstrap-nạp-hai-lần đã ghi trong coding-conventions.md.
 *
 * Nạp từ user/include/header.html, tức có mặt trên mọi trang dùng fragment header-nav.
 */
(function () {
    'use strict';

    // Ngưỡng phải khớp @media (max-width: 1199px) của assets/css/main.css. Lệch một pixel là
    // menu tự đóng ở đúng khoảng theme vẫn đang hiện panel mobile.
    var DESKTOP_MIN_WIDTH = 1200;

    function init() {
        var toggleBtn = document.querySelector('.mobile-nav-toggle');
        var navmenu = document.querySelector('#navmenu');
        if (!toggleBtn || !navmenu) return;

        var body = document.body;

        function isOpen() {
            return body.classList.contains('mobile-nav-active');
        }

        function setOpen(open) {
            body.classList.toggle('mobile-nav-active', open);
            toggleBtn.classList.toggle('bi-list', !open);
            toggleBtn.classList.toggle('bi-x', open);
        }

        toggleBtn.addEventListener('click', function (e) {
            e.preventDefault();
            // stopPropagation: nếu không, cú chạm này lan tới listener "chạm ra ngoài" bên dưới
            // và đóng lại ngay lập tức cái menu vừa mở.
            e.stopPropagation();
            setOpen(!isOpen());
        });

        // Bấm một mục trong menu: đóng rồi mới điều hướng, để lúc quay lại bằng nút Back
        // (bfcache) trang không hiện ra với menu đang mở.
        navmenu.addEventListener('click', function (e) {
            if (e.target.closest('a') && isOpen()) setOpen(false);
        });

        // Chạm ra ngoài menu thì đóng — trên điện thoại panel phủ gần kín màn hình nên nếu
        // không có lối này, cách duy nhất để đóng là tìm lại đúng nút hamburger bị panel che.
        document.addEventListener('click', function (e) {
            if (!isOpen()) return;
            if (e.target.closest('#navmenu')) return;
            setOpen(false);
        });

        // Xoay ngang / mở rộng cửa sổ qua ngưỡng desktop: theme trả menu về thanh ngang, còn
        // class mobile-nav-active thì vẫn dính lại. Dọn để lần thu nhỏ sau trạng thái vẫn đúng.
        window.addEventListener('resize', function () {
            if (isOpen() && window.innerWidth >= DESKTOP_MIN_WIDTH) setOpen(false);
        });
    }

    // Script nằm trong <header> ở đầu <body>, tức phần còn lại của trang chưa parse xong.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
