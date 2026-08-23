/* =========================================================================
   Chuông thông báo của nhân viên (bác sĩ / trưởng khoa / lễ tân).

   Tách khỏi work-schedule.js có lý do: file đó thoát sớm khi không tìm thấy lưới
   lịch (#wsCalendarBody), nên chuông chỉ sống trên đúng trang Lịch làm việc. Bác sĩ
   đứng ở dashboard hay trang quản lý lịch hẹn không hề biết trưởng khoa đã duyệt đơn.

   Markup nằm trong fragment header dùng chung (doctor/include/header :: header-nav),
   nên script này phải tự chịu được việc không có chuông trên trang.
   Không dùng thư viện ngoài: dự án không có bước build frontend.
   ========================================================================= */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var list = document.getElementById('staffNotifList');
        if (!list) {
            return; // trang này không có chuông
        }

        var badge = document.getElementById('staffNotifBadge');
        var toggleBtn = document.getElementById('staffNotifToggle');

        load(list, badge);

        // Mở chuông = đã đọc. Chuông không có nút đánh dấu riêng từng mục nên đây là
        // hành vi duy nhất làm badge tắt đi.
        if (toggleBtn) {
            toggleBtn.addEventListener('click', function () {
                if (!badge || badge.classList.contains('d-none')) {
                    return;
                }
                fetch('/api/staff/notifications/read',
                      { method: 'POST', headers: MediTrustCsrf.headers() })
                    .then(function () { hide(badge); })
                    .catch(function () { /* để nguyên badge, lần sau mở lại vẫn thử */ });
            });
        }
    });

    function load(list, badge) {
        fetch('/api/staff/notifications')
            .then(function (res) {
                return res.ok ? res.json() : { items: [], unreadCount: 0 };
            })
            .then(function (data) {
                render(list, data.items || []);
                showUnread(badge, data.unreadCount || 0);
            })
            .catch(function () {
                list.innerHTML = '<div class="text-muted small py-2">Không tải được thông báo.</div>';
            });
    }

    function render(list, items) {
        if (!items.length) {
            list.innerHTML = '<div class="text-muted small py-2">Không có thông báo mới.</div>';
            return;
        }

        list.innerHTML = '';
        items.forEach(function (item) {
            // Có link thì cả dòng bấm được, không thì chỉ để đọc.
            var row = document.createElement(item.link ? 'a' : 'div');
            row.className = 'd-flex gap-2 py-2 border-bottom text-decoration-none text-body';
            if (item.link) {
                row.setAttribute('href', item.link);
            }
            if (item.read === false) {
                row.classList.add('fw-semibold');
            }

            row.innerHTML = '<i class="bi ' + escapeHtml(item.icon || 'bi-bell') + ' mt-1"></i>'
                + '<div class="flex-grow-1">'
                + '<div class="small">' + escapeHtml(item.title || '') + '</div>'
                + '<div class="text-muted" style="font-size: .78rem;">'
                + escapeHtml(item.subtitle || '') + '</div>'
                + (item.time
                    ? '<div class="text-muted" style="font-size: .72rem;">' + escapeHtml(item.time) + '</div>'
                    : '')
                + '</div>';
            list.appendChild(row);
        });
    }

    function showUnread(badge, count) {
        if (!badge) {
            return;
        }
        if (count > 0) {
            badge.textContent = count > 9 ? '9+' : String(count);
            badge.classList.remove('d-none');
        } else {
            hide(badge);
        }
    }

    function hide(badge) {
        if (badge) {
            badge.classList.add('d-none');
        }
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }
})();
