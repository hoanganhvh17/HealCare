/**
 * Hai việc cho khu nhân viên trên điện thoại. Nạp từ 3 fragment footer
 * (admin/include/footer, receptionist/include/footer, doctor/include/footer).
 *
 * 1. Tự bọc mọi <table> vào .table-responsive.
 *    19 tệp có <table> mà không bọc tay, bảng rộng tới 29 cột. Làm bằng JS thay vì sửa 19
 *    template vì nó phủ luôn mọi bảng thêm mới sau này, và vì bảng gắn class .datatable bị
 *    simple-datatables bọc lại lúc chạy nên không thể bọc đúng chỗ từ trong template được.
 *
 * 2. Chạm ra ngoài thì đóng sidebar (bổ trợ nền mờ trong responsive-admin.css).
 */
(function () {
    'use strict';

    // Khớp @media (max-width: 1199.98px) của responsive-admin.css và ngưỡng 1199px của
    // style.css. Lệch ngưỡng là sidebar đóng/mở sai vùng.
    var DESKTOP_MIN_WIDTH = 1200;

    /* ------------------------------------------------------------------ */
    /* 1. Bọc bảng                                                         */
    /* ------------------------------------------------------------------ */
    function wrapTables() {
        document.querySelectorAll('table').forEach(function (table) {
            if (table.closest('.table-responsive')) return;

            // simple-datatables bọc bảng trong .datatable-wrapper cùng với ô tìm kiếm và
            // phân trang. Bọc cả cụm chứ không riêng bảng, nếu không thanh cuộn nằm giữa
            // ô tìm kiếm và phân trang trông như một khung lạc lõng.
            var target = table.closest('.datatable-wrapper') || table;
            if (!target.parentNode) return;

            var wrapper = document.createElement('div');
            wrapper.className = 'table-responsive';
            target.parentNode.insertBefore(wrapper, target);
            wrapper.appendChild(target);
        });
    }

    /* ------------------------------------------------------------------ */
    /* 2. Đóng sidebar khi chạm ra ngoài                                   */
    /* ------------------------------------------------------------------ */
    function bindSidebarDismiss() {
        document.addEventListener('click', function (e) {
            // CHỈ ở màn hẹp. Ở >=1200px class .toggle-sidebar mang nghĩa NGƯỢC LẠI —
            // nó là trạng thái người dùng chủ động THU GỌN sidebar — nên gỡ ở đó là cứ
            // bấm vào đâu một cái là sidebar tự bung ra lại.
            if (window.innerWidth >= DESKTOP_MIN_WIDTH) return;
            if (!document.body.classList.contains('toggle-sidebar')) return;
            if (e.target.closest('.sidebar, .toggle-sidebar-btn')) return;

            document.body.classList.remove('toggle-sidebar');
        });

        // Mở rộng cửa sổ qua ngưỡng desktop khi sidebar đang mở: class dính lại và trở
        // thành lệnh "thu gọn", tức sidebar biến mất đúng lúc màn hình vừa đủ rộng.
        window.addEventListener('resize', function () {
            if (window.innerWidth >= DESKTOP_MIN_WIDTH) {
                document.body.classList.remove('toggle-sidebar');
            }
        });
    }

    // Bọc bảng ở 'load' chứ không 'DOMContentLoaded': assets-admin/js/main.js khởi tạo
    // simpleDatatables.DataTable ở DOMContentLoaded, chạy trước nó thì .datatable-wrapper
    // chưa tồn tại và ta bọc nhầm bảng trần, rồi datatables lại bọc chồng lên.
    if (document.readyState === 'complete') {
        wrapTables();
    } else {
        window.addEventListener('load', wrapTables);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bindSidebarDismiss);
    } else {
        bindSidebarDismiss();
    }
})();
