/* =========================================================================
   Lịch làm việc & Nghỉ phép — vẽ lưới tuần/ngày (theo giờ) và lưới THÁNG (ô ngày),
   điều hướng, tìm kiếm, thông báo.

   Không dùng thư viện ngoài: dự án không có bước build frontend, chỉ có
   Bootstrap 5 + Bootstrap Icons đã nạp sẵn ở header.
   ========================================================================= */
(function () {
    'use strict';

    // Lưới giờ hiển thị 06:00–22:00. Ca trực qua đêm bị cắt ở hai đầu và đánh dấu bằng
    // chữ "(qua đêm)", không kéo dài vô hạn.
    var GRID_START_HOUR = 6;
    var GRID_END_HOUR = 22;
    var HOUR_HEIGHT = 44; // khớp .ws-hour-label trong work-schedule.css

    var DOW_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

    var state = {
        anchor: new Date(),   // ngày đại diện cho khoảng đang xem
        view: 'week',         // 'day' | 'week' | 'month' | 'duty'
        events: [],
        search: ''
    };

    var els = {};

    document.addEventListener('DOMContentLoaded', function () {
        els.body = document.getElementById('wsCalendarBody');
        els.title = document.getElementById('wsCalendarTitle');
        els.searchInput = document.getElementById('wsSearchInput');
        els.notifList = document.getElementById('wsNotifList');
        els.notifDot = document.getElementById('wsNotifDot');
        els.notifCount = document.getElementById('wsNotifCount');
        els.segmented = document.getElementById('wsSegmented');

        if (!els.body) {
            return; // không phải trang lịch
        }

        bindTabs();
        bindNavigation();
        bindSearch();
        bindShiftTypePreview();
        bindLeaveForm();
        bindClinicPicker();

        loadNotifications();
        reload();
    });

    // ===================== ĐIỀU HƯỚNG =====================

    function bindTabs() {
        document.querySelectorAll('[data-ws-tab]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var tab = btn.getAttribute('data-ws-tab');
                document.querySelectorAll('[data-ws-tab]').forEach(function (other) {
                    other.classList.toggle('active', other === btn);
                });

                var isDuty = tab === 'duty';
                toggle(document.getElementById('wsCalendarSection'), !isDuty);
                toggle(document.getElementById('wsDutySection'), isDuty);

                if (isDuty) {
                    state.view = 'duty';
                    return;
                }

                state.view = (tab === 'month') ? 'month' : 'week';
                // Toggle Ngày/Tuần chỉ có nghĩa với lịch theo giờ.
                toggle(els.segmented, state.view !== 'month');
                syncModeButtons();
                reload();
            });
        });

        document.querySelectorAll('[data-ws-mode]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                state.view = btn.getAttribute('data-ws-mode'); // 'day' | 'week'
                syncModeButtons();
                reload();
            });
        });
    }

    function syncModeButtons() {
        document.querySelectorAll('[data-ws-mode]').forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-ws-mode') === state.view);
        });
    }

    function bindNavigation() {
        on('wsPrev', function () { shiftAnchor(-1); });
        on('wsNext', function () { shiftAnchor(1); });
        on('wsToday', function () {
            state.anchor = new Date();
            reload();
        });
    }

    function shiftAnchor(direction) {
        if (state.view === 'day') {
            state.anchor.setDate(state.anchor.getDate() + direction);
        } else if (state.view === 'month') {
            state.anchor.setDate(1); // tránh nhảy tháng sai khi ngày > 28
            state.anchor.setMonth(state.anchor.getMonth() + direction);
        } else {
            state.anchor.setDate(state.anchor.getDate() + direction * 7);
        }
        reload();
    }

    function bindSearch() {
        if (!els.searchInput) {
            return;
        }
        els.searchInput.addEventListener('input', function () {
            state.search = els.searchInput.value.trim().toLowerCase();
            render();
        });

        on('wsSearchToggle', function () {
            var box = document.getElementById('wsSearchBox');
            if (!box) {
                return;
            }
            var hidden = box.classList.toggle('d-none');
            if (!hidden) {
                els.searchInput.focus();
            }
        });
    }

    // ===================== TẢI DỮ LIỆU =====================

    function reload() {
        var range = currentRange();
        renderTitle(range);

        fetch('/api/staff/schedule?from=' + iso(range.from) + '&to=' + iso(range.to))
            .then(function (res) { return res.ok ? res.json() : []; })
            .then(function (data) {
                state.events = Array.isArray(data) ? data : [];
                render();
            })
            .catch(function () {
                state.events = [];
                render();
            });
    }

    function currentRange() {
        if (state.view === 'day') {
            return { from: new Date(state.anchor), to: new Date(state.anchor) };
        }
        if (state.view === 'month') {
            var first = new Date(state.anchor.getFullYear(), state.anchor.getMonth(), 1);
            var last = new Date(state.anchor.getFullYear(), state.anchor.getMonth() + 1, 0);
            return { from: startOfWeek(first), to: endOfWeek(last) };
        }
        return { from: startOfWeek(state.anchor), to: endOfWeek(state.anchor) };
    }

    function renderTitle(range) {
        if (!els.title) {
            return;
        }
        if (state.view === 'day') {
            els.title.textContent = 'Ngày ' + formatDMY(state.anchor);
        } else if (state.view === 'month') {
            els.title.textContent = 'Tháng ' + (state.anchor.getMonth() + 1) + ' ' + state.anchor.getFullYear();
        } else {
            els.title.textContent = 'Tuần ' + isoWeekNumber(range.from)
                + ', Tháng ' + (range.from.getMonth() + 1) + ' ' + range.from.getFullYear();
        }
    }

    // ===================== VẼ =====================

    function render() {
        if (!els.body) {
            return;
        }
        els.body.innerHTML = '';
        var visible = filterEvents(state.events);

        if (state.view === 'month') {
            renderMonth(visible);
        } else {
            var days = (state.view === 'day')
                ? [new Date(state.anchor)]
                : daysBetween(startOfWeek(state.anchor), endOfWeek(state.anchor));
            renderTimeGrid(days, visible);
        }
    }

    // ---- Lưới theo giờ (ngày / tuần) ----

    function renderTimeGrid(days, events) {
        var grid = el('div', 'ws-grid');
        grid.style.setProperty('--ws-day-count', days.length);
        if (days.length === 1) {
            grid.classList.add('ws-day-view');
        }

        // Hàng tiêu đề: ô trống ở góc + một ô mỗi ngày
        grid.appendChild(el('div', 'ws-grid-head'));
        days.forEach(function (day) {
            grid.appendChild(buildDayHeader(day));
        });

        // Cột giờ
        var hours = el('div', 'ws-hours');
        for (var h = GRID_START_HOUR; h < GRID_END_HOUR; h++) {
            var label = el('div', 'ws-hour-label');
            label.textContent = pad(h) + ':00';
            hours.appendChild(label);
        }
        grid.appendChild(hours);

        // Cột từng ngày kèm sự kiện
        days.forEach(function (day) {
            grid.appendChild(buildDayColumn(day, events));
        });

        els.body.appendChild(grid);
    }

    function buildDayHeader(day) {
        var head = el('div', 'ws-grid-head');
        var dowIndex = (day.getDay() + 6) % 7; // JS: CN=0 -> đổi về T2=0

        if (isSameDay(day, new Date())) {
            head.classList.add('ws-today');
        }
        if (dowIndex >= 5) {
            head.classList.add('ws-weekend');
        }

        var dow = el('div', 'ws-dow');
        dow.textContent = DOW_LABELS[dowIndex];
        var num = el('div', 'ws-day-num');
        num.textContent = day.getDate();

        head.appendChild(dow);
        head.appendChild(num);
        return head;
    }

    function buildDayColumn(day, events) {
        var col = el('div', 'ws-day-col');
        col.style.height = ((GRID_END_HOUR - GRID_START_HOUR) * HOUR_HEIGHT) + 'px';

        var today = new Date();
        today.setHours(0, 0, 0, 0);
        if (day < today) {
            col.classList.add('ws-past');
        }

        eventsOnDay(events, day).forEach(function (event) {
            col.appendChild(buildEventBlock(event, day));
        });
        return col;
    }

    /** Một sự kiện hiện trên mọi ngày nó bao phủ (nghỉ nhiều ngày, trực qua đêm). */
    function eventsOnDay(events, day) {
        var key = iso(day);
        return events.filter(function (event) {
            return key >= event.date && key <= (event.endDate || event.date);
        });
    }

    function buildEventBlock(event, day) {
        var block = el('button', 'ws-event ws-kind-' + (event.kind || 'CLINIC'));
        block.type = 'button';

        if (event.status === 'APPROVED') {
            block.classList.add('ws-approved');
        }

        // Ca khám của tuần đã chốt (hoặc tuần dự kiến xa hơn): chỉ xem, không sửa được.
        if (event.readOnly) {
            block.classList.add('ws-locked');
            var lock = el('span', 'ws-lock');
            lock.innerHTML = '<i class="bi bi-lock-fill"></i>';
            block.appendChild(lock);
        }

        var bounds = boundsForDay(event, day);
        block.style.top = bounds.top + 'px';
        block.style.height = Math.max(bounds.height, 26) + 'px';

        var title = el('span', 'ws-event-title');
        title.textContent = event.title + (event.subtitle ? ' (' + event.subtitle + ')' : '');
        block.appendChild(title);

        var time = el('span', 'ws-event-time');
        time.textContent = bounds.timeLabel;
        block.appendChild(time);

        if (event.status === 'PENDING') {
            var pending = el('span', 'ws-pending-icon');
            pending.innerHTML = ' <i class="bi bi-clock-history"></i>';
            block.appendChild(pending);
        }

        if (event.needsCover) {
            block.appendChild(el('span', 'ws-dot'));
            var chip = el('span', 'ws-chip-cover');
            chip.innerHTML = '<i class="bi bi-arrow-repeat"></i> Cần thay ca';
            block.appendChild(chip);
        }

        block.addEventListener('click', function () { openEventDetail(event); });
        return block;
    }

    /**
     * Vị trí và chiều cao khối trong ngày đang vẽ. Ca qua đêm bị cắt ở biên lưới:
     * ngày bắt đầu chạy tới đáy, ngày kết thúc chạy từ đỉnh xuống.
     */
    function boundsForDay(event, day) {
        var key = iso(day);
        var isFirstDay = key === event.date;
        var isLastDay = key === (event.endDate || event.date);
        var multiDay = event.date !== (event.endDate || event.date);

        var startMinutes = isFirstDay ? toMinutes(event.startTime) : GRID_START_HOUR * 60;
        var endMinutes = isLastDay ? toMinutes(event.endTime) : GRID_END_HOUR * 60;

        if (multiDay && isFirstDay) {
            endMinutes = GRID_END_HOUR * 60;
        }
        if (multiDay && isLastDay) {
            startMinutes = GRID_START_HOUR * 60;
        }

        var gridStart = GRID_START_HOUR * 60;
        var gridEnd = GRID_END_HOUR * 60;
        startMinutes = Math.max(startMinutes, gridStart);
        endMinutes = Math.min(Math.max(endMinutes, startMinutes + 30), gridEnd);

        var label = event.startTime + ' - ' + event.endTime;
        if (event.overnight) {
            label += ' (qua đêm)';
        }

        return {
            top: (startMinutes - gridStart) / 60 * HOUR_HEIGHT,
            height: (endMinutes - startMinutes) / 60 * HOUR_HEIGHT,
            timeLabel: label
        };
    }

    // ---- Lưới THÁNG (ô ngày) ----

    function renderMonth(events) {
        var month = state.anchor.getMonth();
        var first = new Date(state.anchor.getFullYear(), month, 1);
        var start = startOfWeek(first);
        var last = new Date(state.anchor.getFullYear(), month + 1, 0);
        var end = endOfWeek(last);

        var wrap = el('div', 'ws-month');

        // Hàng thứ trong tuần
        var dowRow = el('div', 'ws-month-dow');
        DOW_LABELS.forEach(function (label, idx) {
            var cell = el('div', idx >= 5 ? 'ws-weekend' : '');
            cell.textContent = label;
            dowRow.appendChild(cell);
        });
        wrap.appendChild(dowRow);

        // Các ô ngày
        var body = el('div', 'ws-month-body');
        var cursor = new Date(start);
        while (cursor <= end) {
            body.appendChild(buildMonthCell(new Date(cursor), month, events));
            cursor.setDate(cursor.getDate() + 1);
        }
        wrap.appendChild(body);
        els.body.appendChild(wrap);
    }

    function buildMonthCell(day, currentMonth, events) {
        var cell = el('div', 'ws-month-cell');
        if (day.getMonth() !== currentMonth) {
            cell.classList.add('ws-outside');
        }
        if (isSameDay(day, new Date())) {
            cell.classList.add('ws-today');
        }

        var num = el('div', 'ws-month-daynum');
        num.textContent = day.getDate();
        cell.appendChild(num);

        var dayEvents = eventsOnDay(events, day);
        var shown = dayEvents.slice(0, 3);
        shown.forEach(function (event) {
            cell.appendChild(buildMonthChip(event));
        });

        if (dayEvents.length > shown.length) {
            var more = el('div', 'ws-month-more');
            more.textContent = '+' + (dayEvents.length - shown.length) + ' mục';
            more.addEventListener('click', function () {
                // Bấm "+N" mở ngày đó ở chế độ Ngày để xem đầy đủ.
                state.anchor = new Date(day);
                state.view = 'day';
                activateTab('week');
                toggle(els.segmented, true);
                syncModeButtons();
                reload();
            });
            cell.appendChild(more);
        }
        return cell;
    }

    function buildMonthChip(event) {
        var chip = el('button', 'ws-chip ws-kind-' + (event.kind || 'CLINIC'));
        chip.type = 'button';
        if (event.readOnly) {
            chip.classList.add('ws-locked');
        }
        var prefix = (event.startTime && event.kind !== 'LEAVE') ? event.startTime + ' ' : '';
        chip.textContent = prefix + event.title;
        if (event.needsCover) {
            chip.appendChild(el('span', 'ws-chip-dot'));
        }
        chip.addEventListener('click', function () { openEventDetail(event); });
        return chip;
    }

    function activateTab(tab) {
        document.querySelectorAll('[data-ws-tab]').forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-ws-tab') === tab);
        });
        toggle(document.getElementById('wsCalendarSection'), tab !== 'duty');
        toggle(document.getElementById('wsDutySection'), tab === 'duty');
    }

    function filterEvents(events) {
        if (!state.search) {
            return events;
        }
        return events.filter(function (event) {
            var haystack = [event.title, event.subtitle, event.note, event.statusLabel]
                .filter(Boolean).join(' ').toLowerCase();
            return haystack.indexOf(state.search) !== -1;
        });
    }

    // ===================== CHI TIẾT SỰ KIỆN =====================

    function openEventDetail(event) {
        var modalEl = document.getElementById('wsEventModal');
        if (!modalEl) {
            return;
        }

        setText('wsEventTitle', event.title);
        setText('wsEventSubtitle', event.subtitle || '—');
        setText('wsEventWhen', formatRangeLabel(event));
        setText('wsEventStatus', event.statusLabel || '—');
        setText('wsEventNote', event.note || 'Không có ghi chú.');

        // Chỉ ca trong bảng StaffShift (trực/hội chẩn) mới hủy / xin đổi được.
        var isShift = event.kind === 'DUTY' || event.kind === 'MEETING';
        var cancelForm = document.getElementById('wsEventCancelForm');
        var coverLink = document.getElementById('wsEventCoverLink');

        toggle(cancelForm, isShift);
        toggle(coverLink, isShift);

        if (isShift && cancelForm) {
            cancelForm.action = window.WS_BASE_PATH + '/shift/cancel/' + event.id;
        }
        if (isShift && coverLink) {
            coverLink.href = window.WS_BASE_PATH + '/shift/cover/' + event.id;
        }

        new bootstrap.Modal(modalEl).show();
    }

    function formatRangeLabel(event) {
        var text = formatDMY(parseIso(event.date));
        if (event.endDate && event.endDate !== event.date) {
            text += ' - ' + formatDMY(parseIso(event.endDate));
        }
        return text + ' • ' + event.startTime + ' - ' + event.endTime;
    }

    // ===================== FORM ĐĂNG KÝ TRỰC =====================

    /** Hiện trước khung giờ và cảnh báo pháp lý ngay khi chọn loại ca. */
    function bindShiftTypePreview() {
        var select = document.getElementById('wsShiftType');
        var hint = document.getElementById('wsShiftHint');
        var dutyRoleBox = document.getElementById('wsDutyRoleBox');
        if (!select || !hint) {
            return;
        }

        function update() {
            var option = select.options[select.selectedIndex];
            if (!option || !option.value) {
                hint.textContent = '';
                return;
            }
            hint.innerHTML = option.getAttribute('data-hint') || '';
            toggle(dutyRoleBox, option.getAttribute('data-duty') === 'true');
        }

        select.addEventListener('change', update);
        update();
    }

    /** Nửa ngày chỉ có nghĩa khi đơn gói trong một ngày; lý do việc riêng chỉ hiện đúng lúc. */
    function bindLeaveForm() {
        var typeSelect = document.getElementById('wsLeaveType');
        var start = document.getElementById('wsLeaveStart');
        var end = document.getElementById('wsLeaveEnd');
        var halfDayBox = document.getElementById('wsHalfDayBox');
        var subReasonBox = document.getElementById('wsSubReasonBox');

        if (!typeSelect || !start || !end) {
            return;
        }

        function update() {
            var personal = typeSelect.value === 'VIEC_RIENG_CO_LUONG'
                || typeSelect.value === 'VIEC_RIENG_KHONG_LUONG';
            toggle(subReasonBox, personal);
            toggle(halfDayBox, !!start.value && start.value === end.value);
        }

        typeSelect.addEventListener('change', update);
        start.addEventListener('change', function () {
            if (!end.value || end.value < start.value) {
                end.value = start.value;
            }
            update();
        });
        end.addEventListener('change', update);
        update();
    }

    /** Nút chọn nhanh cả tuần / xoá hết trong bảng đăng ký ca khám theo tuần. */
    function bindClinicPicker() {
        document.querySelectorAll('[data-ws-clinic-all]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var session = btn.getAttribute('data-ws-clinic-all'); // 'morning' | 'afternoon'
                var check = btn.getAttribute('data-ws-check') === 'true';
                document.querySelectorAll('input[data-session="' + session + '"]').forEach(function (box) {
                    box.checked = check;
                });
            });
        });
    }

    // ===================== THÔNG BÁO =====================

    function loadNotifications() {
        if (!els.notifList) {
            return;
        }

        fetch('/api/staff/notifications')
            .then(function (res) { return res.ok ? res.json() : { count: 0, items: [] }; })
            .then(function (data) {
                var items = data.items || [];
                toggle(els.notifDot, items.length > 0);
                if (els.notifCount) {
                    els.notifCount.textContent = items.length;
                }

                if (!items.length) {
                    els.notifList.innerHTML = '<div class="ws-empty">Không có thông báo mới.</div>';
                    return;
                }

                els.notifList.innerHTML = '';
                items.forEach(function (item) {
                    var row = el('div', 'ws-notif-item');
                    row.innerHTML = '<i class="bi ' + escapeHtml(item.icon) + '"></i>'
                        + '<div><div class="ws-notif-title">' + escapeHtml(item.title) + '</div>'
                        + '<div class="ws-notif-sub">' + escapeHtml(item.subtitle || '') + '</div></div>';
                    els.notifList.appendChild(row);
                });
            })
            .catch(function () {
                els.notifList.innerHTML = '<div class="ws-empty">Không tải được thông báo.</div>';
            });
    }

    // ===================== TIỆN ÍCH =====================

    function el(tag, className) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        return node;
    }

    function on(id, handler) {
        var node = document.getElementById(id);
        if (node) {
            node.addEventListener('click', handler);
        }
    }

    function toggle(node, visible) {
        if (node) {
            node.classList.toggle('d-none', !visible);
        }
    }

    function setText(id, text) {
        var node = document.getElementById(id);
        if (node) {
            node.textContent = text;
        }
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text == null ? '' : text;
        return div.innerHTML;
    }

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function iso(date) {
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }

    function parseIso(text) {
        var parts = (text || '').split('-');
        return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    }

    function formatDMY(date) {
        return pad(date.getDate()) + '/' + pad(date.getMonth() + 1) + '/' + date.getFullYear();
    }

    function toMinutes(time) {
        var parts = (time || '00:00').split(':');
        return Number(parts[0]) * 60 + Number(parts[1] || 0);
    }

    function startOfWeek(date) {
        var result = new Date(date);
        var offset = (result.getDay() + 6) % 7; // đưa thứ 2 về 0
        result.setDate(result.getDate() - offset);
        result.setHours(0, 0, 0, 0);
        return result;
    }

    function endOfWeek(date) {
        var result = startOfWeek(date);
        result.setDate(result.getDate() + 6);
        return result;
    }

    function daysBetween(from, to) {
        var days = [];
        var cursor = new Date(from);
        while (cursor <= to) {
            days.push(new Date(cursor));
            cursor.setDate(cursor.getDate() + 1);
        }
        return days;
    }

    function isSameDay(a, b) {
        return a.getFullYear() === b.getFullYear()
            && a.getMonth() === b.getMonth()
            && a.getDate() === b.getDate();
    }

    /** Số tuần theo chuẩn ISO-8601, khớp cách gọi "Tuần 42" trong thiết kế. */
    function isoWeekNumber(date) {
        var target = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
        var dayNumber = (target.getUTCDay() + 6) % 7;
        target.setUTCDate(target.getUTCDate() - dayNumber + 3);
        var firstThursday = new Date(Date.UTC(target.getUTCFullYear(), 0, 4));
        var firstDayNumber = (firstThursday.getUTCDay() + 6) % 7;
        firstThursday.setUTCDate(firstThursday.getUTCDate() - firstDayNumber + 3);
        return 1 + Math.round((target - firstThursday) / (7 * 24 * 3600 * 1000));
    }
})();
