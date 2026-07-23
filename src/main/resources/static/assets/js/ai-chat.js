    document.addEventListener('DOMContentLoaded', function() {
        const widget = document.getElementById('ai-chat-widget');
        const toggleBtn = document.getElementById('ai-chat-toggle');
        const closeBtn = document.getElementById('btn-close');
        const maximizeBtn = document.getElementById('btn-maximize');
        const chatBox = document.getElementById('ai-chat-box');
        const header = document.getElementById('ai-chat-header');
        const chatInput = document.getElementById('ai-chat-input');
        const sendBtn = document.getElementById('ai-chat-send');
        const messagesContainer = document.getElementById('ai-chat-messages');

        const tabChat = document.getElementById('tab-chat');
        const tabHistory = document.getElementById('tab-history');
        const historyPanel = document.getElementById('ai-history-panel');
        const historyList = document.getElementById('history-list');

        // ==========================================
                // 0. KHỞI TẠO CSS CHO NÚT GỢI Ý & HÀM GLOBAL
                // ==========================================
                const style = document.createElement('style');
                style.innerHTML = `
                    .quick-replies-container {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 8px;
                        margin-top: 12px;
                        padding-top: 10px;
                        border-top: 1px dashed #e0e0e0;
                    }
                    .quick-reply-btn {
                        background-color: #f8f9fa;
                        color: #0d6efd;
                        border: 1px solid #0d6efd;
                        border-radius: 16px;
                        padding: 6px 14px;
                        font-size: 12px;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        font-weight: 500;
                        box-shadow: 0 1px 2px rgba(0,0,0,0.05);
                    }
                    .quick-reply-btn:hover {
                        background-color: #0d6efd;
                        color: white;
                        transform: translateY(-1px);
                    }
                    /* CSS CHO HIỆU ỨNG TYPING (3 DẤU CHẤM) */
                                        .typing-dots {
                                            display: inline-flex;
                                            align-items: center;
                                            gap: 4px;
                                            padding: 4px 8px;
                                            height: 24px;
                                        }
                                        .typing-dots span {
                                            width: 6px;
                                            height: 6px;
                                            background-color: #0d6efd;
                                            border-radius: 50%;
                                            animation: bounce 1.4s infinite ease-in-out both;
                                        }
                                        .typing-dots span:nth-child(1) { animation-delay: -0.32s; }
                                        .typing-dots span:nth-child(2) { animation-delay: -0.16s; }
                                        @keyframes bounce {
                                            0%, 80%, 100% { transform: scale(0); opacity: 0.3; }
                                            40% { transform: scale(1); opacity: 1; }
                                        }

                                        /* CSS CHO PHÉP KÉO MỞ RỘNG TỪ CÁC CẠNH (EDGE RESIZE) */
                                                #ai-chat-box {
                                                    position: relative; /* Bắt buộc để đặt các viền kéo */
                                                    min-width: 320px;
                                                    min-height: 400px;
                                                    max-width: 100vw;
                                                    max-height: 100vh;
                                                }

                                                /* CÁC VIỀN VÔ HÌNH ĐỂ BẮT SỰ KIỆN KÉO CHUỘT */
                                                .chat-resizer { position: absolute; z-index: 100; }
                                                .chat-resizer-r { right: -4px; top: 0; width: 8px; height: 100%; cursor: e-resize; }
                                                .chat-resizer-l { left: -4px; top: 0; width: 8px; height: 100%; cursor: w-resize; }
                                                .chat-resizer-b { bottom: -4px; left: 0; width: 100%; height: 8px; cursor: s-resize; }
                                                .chat-resizer-t { top: -4px; left: 0; width: 100%; height: 8px; cursor: n-resize; }
                                                .chat-resizer-br { right: -4px; bottom: -4px; width: 12px; height: 12px; cursor: se-resize; }
                                                /* === [THÊM MỚI] 1. Hiệu ứng nổi lên xuống cho Icon === */
                                                @keyframes floatChat {
                                                    0% { transform: translateY(0); }
                                                    50% { transform: translateY(-12px); }
                                                    100% { transform: translateY(0); }
                                                }
                                                #ai-chat-toggle {
                                                    animation: floatChat 2.5s ease-in-out infinite;
                                                }
                                                /* Tắt nhún nhảy khi người dùng đang bấm giữ kéo thả */
                                                #ai-chat-toggle.dragging {
                                                    animation: none !important;
                                                }

                                                /* === [THÊM MỚI] 2. Giao diện Box Tour Guide === */
                                                .tour-guide-box {
                                                    position: fixed;
                                                    bottom: 110px; /* Nằm cách trên icon một đoạn */
                                                    right: 25px;
                                                    width: 290px;
                                                    background: #fff;
                                                    border: 2px solid #0d6efd;
                                                    border-radius: 12px;
                                                    padding: 16px;
                                                    box-shadow: 0 10px 30px rgba(13, 110, 253, 0.25);
                                                    z-index: 10000;
                                                    opacity: 0;
                                                    visibility: hidden;
                                                    transform: translateY(20px) scale(0.9);
                                                    transition: all 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
                                                }
                                                .tour-guide-box.show {
                                                    opacity: 1;
                                                    visibility: visible;
                                                    transform: translateY(0) scale(1);
                                                }
                                                /* Cái mũi tên nhọn chỉ xuống Icon */
                                                .tour-guide-box::after {
                                                    content: '';
                                                    position: absolute;
                                                    bottom: -12px;
                                                    right: 22px;
                                                    border-width: 12px 12px 0;
                                                    border-style: solid;
                                                    border-color: #0d6efd transparent transparent transparent;
                                                }
                                                .tour-guide-title { font-weight: 800; color: #0d6efd; margin-bottom: 8px; font-size: 15px; display: flex; align-items: center; gap: 8px; }
                                                .tour-guide-desc { font-size: 13px; color: #444; margin-bottom: 12px; line-height: 1.5; }
                                                .tour-guide-btn {
                                                    background: #0d6efd; color: white; border: none; padding: 6px 16px; border-radius: 20px; font-size: 12px; cursor: pointer; float: right; font-weight: bold; transition: 0.2s;
                                                }
                                                .tour-guide-btn:hover { background: #0b5ed7; transform: scale(1.05); }
                                            `;
                                            document.head.appendChild(style);



                // Hàm Global xử lý khi khách bấm vào nút gợi ý
                window.sendQuickReply = function(text, btnElement) {
                    // 1. Xóa toàn bộ cụm nút gợi ý để khung chat sạch sẽ
                    const container = btnElement.closest('.quick-replies-container');
                    if (container) container.remove();

                    // 2. Điền text vào ô input và tự động bấm nút Gửi
                    const chatInput = document.getElementById('ai-chat-input');
                    const sendBtn = document.getElementById('ai-chat-send');
                    if (chatInput && sendBtn) {
                        chatInput.value = text;
                        sendBtn.click(); // Kích hoạt sự kiện gửi tin nhắn
                    }
                };

                function normalizeText(input) {
                    return (input || '')
                        .toLowerCase()
                        .replace(/\s+/g, ' ')
                        .trim();
                }

                function normalizeTimeHint(text) {
                    const normalized = normalizeText(text)
                        .replace(/h/g, ':')
                        .replace(/giờ/g, ':')
                        .replace(/\s+/g, '');

                    const match = normalized.match(/(\d{1,2})(?::?(\d{2}))?/);
                    if (!match) return '';

                    const hour = String(parseInt(match[1], 10)).padStart(2, '0');
                    const minute = match[2] ? String(parseInt(match[2], 10)).padStart(2, '0') : '00';
                    return hour + ':' + minute;
                }

                function addThirtyMinutes(timeText) {
                    const parts = (timeText || '').split(':');
                    if (parts.length !== 2) return '';

                    const date = new Date();
                    date.setHours(parseInt(parts[0], 10), parseInt(parts[1], 10), 0, 0);
                    date.setMinutes(date.getMinutes() + 30);

                    return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
                }

                function normalizeSlotRange(text) {
                    const normalized = normalizeText(text);
                    const rangeMatch = normalized.match(/(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})/);
                    if (rangeMatch) {
                        return normalizeTimeHint(rangeMatch[1]) + ' - ' + normalizeTimeHint(rangeMatch[2]);
                    }

                    const start = normalizeTimeHint(text);
                    if (!start) return '';
                    const end = addThirtyMinutes(start);
                    return end ? start + ' - ' + end : '';
                }

                function extractDoctorName(text) {
                    const normalized = normalizeText(text);
                    const match = normalized.match(/(?:bác sĩ|bs\.?)\s+(.+?)(?:\s+lúc|\s+vào|\s+ngày|\s+thứ|\s+để|\s+đặt|\s+khám|$)/i);
                    if (!match) return '';

                    // Cắt các từ đệm cuối câu ("đổi sang bác sĩ B đi nhé" -> "B").
                    // Không cắt thì keyword tìm kiếm thành "b đi nhé" và LIKE trên fullName không khớp,
                    // dẫn tới việc âm thầm chọn nhầm bác sĩ khác.
                    //
                    // Neo vào CUỐI CHUỖI chứ không dùng \b: trong JS, \b chỉ hiểu chữ cái ASCII,
                    // nên "nhé" hay "ạ" (kết thúc bằng ký tự có dấu) sẽ không bao giờ khớp được.
                    return match[1]
                        .replace(/(\s+(đi|nhé|nha|nhá|ạ|à|với|luôn|thôi|được không|cho tôi|cho mình|giúp tôi|giúp mình))+$/gi, '')
                        .replace(/[.,!?]+$/, '')
                        .trim();
                }

                function extractDateHint(text) {
                    const normalized = normalizeText(text);
                    const today = new Date();

                    if (normalized.includes('hôm nay')) {
                        return today.toISOString().slice(0, 10);
                    }
                    if (normalized.includes('ngày mai')) {
                        const tomorrow = new Date(today);
                        tomorrow.setDate(today.getDate() + 1);
                        return tomorrow.toISOString().slice(0, 10);
                    }

                    const slashMatch = normalized.match(/(\d{1,2})[\/\-](\d{1,2})(?:[\/\-](\d{2,4}))?/);
                    if (slashMatch) {
                        const day = String(parseInt(slashMatch[1], 10)).padStart(2, '0');
                        const month = String(parseInt(slashMatch[2], 10)).padStart(2, '0');
                        const year = slashMatch[3] ? parseInt(slashMatch[3], 10) : today.getFullYear();
                        const resolvedYear = year < 100 ? 2000 + year : year;
                        return resolvedYear + '-' + month + '-' + day;
                    }

                    return '';
                }

                function parseSlotLabel(slotLabel) {
                    const match = slotLabel.match(/\((\d{2}:\d{2}\s*-\s*\d{2}:\d{2})\)/);
                    const dateMatch = slotLabel.match(/(\d{2})\/(\d{2})/);
                    if (!match || !dateMatch) return null;

                    const now = new Date();
                    let year = now.getFullYear();
                    const month = parseInt(dateMatch[2], 10);
                    if (month < (now.getMonth() + 1)) {
                        year += 1;
                    }

                    return {
                        appointmentDate: year + '-' + String(month).padStart(2, '0') + '-' + String(parseInt(dateMatch[1], 10)).padStart(2, '0'),
                        appointmentTime: match[1].replace(/\s+/g, ' ')
                    };
                }

                function buildAppointmentUrl(doctorId, appointmentDate, appointmentTime) {
                    const url = new URL('/appointment', window.location.origin);
                    if (doctorId) url.searchParams.set('doctorId', doctorId);
                    if (appointmentDate) url.searchParams.set('appointmentDate', appointmentDate);
                    if (appointmentTime) url.searchParams.set('appointmentTime', appointmentTime);
                    return url.toString();
                }

                /**
                 * Chọn đúng bác sĩ khách nhắc tới trong danh sách trả về.
                 *
                 * KHÔNG được dùng kiểu "khớp chuỗi con rồi lấy phần tử đầu": API tìm kiếm chạy
                 * LIKE %keyword% trên họ tên, nên "bác sĩ B" khớp gần như mọi bác sĩ và sẽ chọn nhầm.
                 * Ở đây chỉ khớp theo RANH GIỚI TỪ, không khớp thì trả null để bên gọi hỏi lại khách.
                 */
                function pickBestDoctorMatch(doctors, requestedName) {
                    const want = normalizeText(requestedName);
                    if (!want) return null;

                    const wantWords = want.split(' ').filter(Boolean);
                    if (wantWords.length === 0) return null;
                    const wantLast = wantWords[wantWords.length - 1];

                    // 1. Trùng khít cả họ và tên
                    let hit = doctors.find(function(doc) {
                        return normalizeText(doc.fullName) === want;
                    });
                    if (hit) return hit;

                    // 2. Trùng tên gọi — từ cuối trong họ tên tiếng Việt ("bác sĩ Bình" -> "Trần Văn Bình")
                    const byGivenName = doctors.filter(function(doc) {
                        const words = normalizeText(doc.fullName).split(' ').filter(Boolean);
                        return words.length > 0 && words[words.length - 1] === wantLast;
                    });
                    if (byGivenName.length === 1) return byGivenName[0];

                    // 3. Chứa đủ mọi từ khách nói, xét theo từ nguyên vẹn
                    const byAllWords = doctors.filter(function(doc) {
                        const words = normalizeText(doc.fullName).split(' ').filter(Boolean);
                        return wantWords.every(function(w) { return words.indexOf(w) !== -1; });
                    });
                    if (byAllWords.length === 1) return byAllWords[0];

                    // Còn mơ hồ (nhiều bác sĩ cùng tên gọi) thì lấy người đầu trong nhóm đã lọc,
                    // vẫn hơn hẳn việc lấy bừa phần tử đầu của toàn bộ kết quả LIKE.
                    return byGivenName[0] || byAllWords[0] || null;
                }

                async function resolveBookingHandoff(aiData, userText) {
                    const bookingIntent = aiData && (aiData.booking_intent === true || normalizeText(userText).match(/đặt lịch|chuyển sang đặt lịch|tiến hành khám|book lịch|book khám|đặt khám/));
                    if (!bookingIntent) return null;

                    const bookingTarget = aiData.booking_target || {};
                    const deptIds = Array.isArray(aiData.recommended_departments)
                        ? aiData.recommended_departments.filter(function(id) { return id !== null && id !== undefined; })
                        : [];

                    const requestedDoctorName = (bookingTarget.doctor_name || extractDoctorName(userText) || '').trim();
                    const requestedDate = bookingTarget.appointment_date || extractDateHint(userText);
                    const requestedTime = normalizeTimeHint(bookingTarget.appointment_time || userText);
                    const requestedDepartmentId = bookingTarget.department_id || deptIds[0] || null;

                    let doctorSearchResult = null;
                    if (requestedDoctorName) {
                        try {
                            const doctorSearchUrl = new URL('/api/doctors/search', window.location.origin);
                            doctorSearchUrl.searchParams.set('keyword', requestedDoctorName);
                            if (requestedDepartmentId) {
                                doctorSearchUrl.searchParams.set('departmentId', requestedDepartmentId);
                            }
                            const searchRes = await fetch(doctorSearchUrl.toString());
                            if (searchRes.ok) {
                                const doctors = await searchRes.json();
                                if (Array.isArray(doctors) && doctors.length > 0) {
                                    doctorSearchResult = pickBestDoctorMatch(doctors, requestedDoctorName);
                                }
                            }
                        } catch (err) {
                            console.error(err);
                        }

                        if (!doctorSearchResult) {
                            try {
                                const fallbackDoctorsUrl = new URL('/api/doctors', window.location.origin);
                                if (requestedDepartmentId) {
                                    fallbackDoctorsUrl.searchParams.set('departmentId', requestedDepartmentId);
                                }
                                const fallbackRes = await fetch(fallbackDoctorsUrl.toString());
                                if (fallbackRes.ok) {
                                    const fallbackDoctors = await fallbackRes.json();
                                    if (Array.isArray(fallbackDoctors) && fallbackDoctors.length > 0) {
                                        doctorSearchResult = pickBestDoctorMatch(fallbackDoctors, requestedDoctorName);
                                    }
                                }
                            } catch (err) {
                                console.error(err);
                            }
                        }
                    }

                    let candidateDepartmentId = requestedDepartmentId;
                    let candidateDoctorId = doctorSearchResult ? doctorSearchResult.id : (bookingTarget.doctor_id || null);
                    let candidateDoctorName = doctorSearchResult ? doctorSearchResult.fullName : requestedDoctorName;
                    let availableDoctors = [];

                    if (candidateDoctorId && doctorSearchResult && doctorSearchResult.departmentId) {
                        candidateDepartmentId = doctorSearchResult.departmentId;
                    }

                    if (candidateDepartmentId) {
                        try {
                            // Gửi kèm doctorId để backend ghim bác sĩ được chỉ đích danh lên đầu,
                            // không bị .limit(3) cắt mất.
                            let deptUrl = '/api/chat/doctors/department/' + candidateDepartmentId + '?sessionId=' + encodeURIComponent(sessionId);
                            if (candidateDoctorId) deptUrl += '&doctorId=' + encodeURIComponent(candidateDoctorId);
                            const deptRes = await fetch(deptUrl);
                            if (deptRes.ok) {
                                availableDoctors = await deptRes.json();
                            }
                        } catch (err) {
                            console.error(err);
                        }
                    }

                    if (!availableDoctors || availableDoctors.length === 0) {
                        return null;
                    }

                    let selectedDoctor = null;
                    if (candidateDoctorId) {
                        selectedDoctor = availableDoctors.find(function(doc) {
                            return String(doc.id) === String(candidateDoctorId);
                        }) || null;
                    }

                    // Khách đã nêu đích danh một bác sĩ nhưng hệ thống không tìm ra người đó:
                    // TUYỆT ĐỐI không được lặng lẽ chọn bác sĩ khác rồi điều hướng, vì như vậy là đặt nhầm người.
                    if (!selectedDoctor && requestedDoctorName) {
                        return { doctorNotFound: true, requestedDoctorName: requestedDoctorName };
                    }

                    if (!selectedDoctor) {
                        selectedDoctor = availableDoctors[0];
                    }

                    if (!selectedDoctor || !selectedDoctor.availableSlots || selectedDoctor.availableSlots.length === 0) {
                        return null;
                    }

                    const explicitSlotRange = normalizeSlotRange(bookingTarget.appointment_time || userText);
                    if (requestedDate && explicitSlotRange) {
                        try {
                            const bookedSlotsRes = await fetch('/api/bookings/booked-slots?doctorId=' + selectedDoctor.id + '&date=' + encodeURIComponent(requestedDate));
                            if (bookedSlotsRes.ok) {
                                const unavailableSlots = await bookedSlotsRes.json();
                                const slotIsFree = !Array.isArray(unavailableSlots) || unavailableSlots.every(function(slot) {
                                    return slot.indexOf(explicitSlotRange) === -1 && slot.indexOf(explicitSlotRange.split(' - ')[0]) === -1;
                                });

                                if (slotIsFree) {
                                    return {
                                        doctor: selectedDoctor,
                                        doctorName: candidateDoctorName || selectedDoctor.fullName,
                                        appointmentDate: requestedDate,
                                        appointmentTime: explicitSlotRange,
                                        appointmentUrl: buildAppointmentUrl(selectedDoctor.id, requestedDate, explicitSlotRange),
                                        selectedSlotLabel: requestedDate + ' (' + explicitSlotRange + ')',
                                        fallback: false
                                    };
                                }
                            }
                        } catch (err) {
                            console.error(err);
                        }
                    }

                    let selectedSlotLabel = null;
                    if (requestedTime) {
                        selectedSlotLabel = selectedDoctor.availableSlots.find(function(slotLabel) {
                            return slotLabel.indexOf(requestedTime) !== -1;
                        }) || null;
                    }

                    if (!selectedSlotLabel) {
                        selectedSlotLabel = selectedDoctor.availableSlots[0];
                    }

                    const slotInfo = parseSlotLabel(selectedSlotLabel);
                    if (!slotInfo) return null;

                    const appointmentDate = requestedDate || slotInfo.appointmentDate;
                    const appointmentTime = selectedSlotLabel.match(/\((.*?)\)/) ? selectedSlotLabel.match(/\((.*?)\)/)[1] : slotInfo.appointmentTime;
                    const appointmentUrl = buildAppointmentUrl(selectedDoctor.id, appointmentDate, appointmentTime);

                    return {
                        doctor: selectedDoctor,
                        doctorName: candidateDoctorName || selectedDoctor.fullName,
                        appointmentDate: appointmentDate,
                        appointmentTime: appointmentTime,
                        appointmentUrl: appointmentUrl,
                        selectedSlotLabel: selectedSlotLabel,
                        fallback: requestedTime && selectedSlotLabel.indexOf(requestedTime) === -1
                    };
                }

        // ==========================================
        // 1. SESSION MANAGEMENT
        // ==========================================
        let sessionId = sessionStorage.getItem('meditrust_session_id');
        // [THÊM MỚI] 1.1 KIỂM TRA ĐĂNG NHẬP/ĐĂNG XUẤT ĐỂ LÀM SẠCH CHAT
                const userIdInput = document.getElementById('current-user-id');
                const currentUserId = userIdInput ? userIdInput.value : 'guest';
                const savedUserId = sessionStorage.getItem('meditrust_user_id');

                if (savedUserId && savedUserId !== currentUserId) {
                    // Đã đổi tài khoản -> Xóa trắng rác cũ
                    sessionStorage.removeItem('meditrust_session_id');
                    sessionStorage.removeItem('meditrust_chat_html');
                    sessionStorage.removeItem('meditrust_chat_state');
                    sessionStorage.removeItem('meditrust_last_activity');
                }
                sessionStorage.setItem('meditrust_user_id', currentUserId);

                // [THÊM MỚI] 1.2 KIỂM TRA HẾT HẠN PHIÊN CHAT (Quá 60 phút không chat -> Tự xóa)
                const SESSION_TIMEOUT_MINUTES = 60;
                const lastActivityStr = sessionStorage.getItem('meditrust_last_activity');
                if (lastActivityStr) {
                    const minutesPassed = (new Date().getTime() - parseInt(lastActivityStr, 10)) / (1000 * 60);
                    if (minutesPassed > SESSION_TIMEOUT_MINUTES) {
                        sessionStorage.removeItem('meditrust_session_id');
                        sessionStorage.removeItem('meditrust_chat_html');
                        // Lưu ý: Không xóa 'meditrust_chat_state' để giữ nguyên trạng thái đóng/mở UI
                    }
                }
        if (!sessionId) {
            sessionId = 'session_' + Math.random().toString(36).substr(2, 9);
            sessionStorage.setItem('meditrust_se    ssion_id', sessionId);
        }
        const savedChatHtml = sessionStorage.getItem('meditrust_chat_html');
                if (savedChatHtml) {
                    messagesContainer.innerHTML = savedChatHtml;
                    // Đợi 100ms để DOM render xong rồi tự động cuộn xuống cuối cùng
                    setTimeout(() => {
                        messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    }, 100);
                }
                // 1.2 Khôi phục trạng thái Đóng/Mở (Chuyển trang không bị tắt chat)
                        const chatState = sessionStorage.getItem('meditrust_chat_state');
                        if (chatState === 'open') {
                            chatBox.classList.remove('d-none');
                            toggleBtn.classList.add('d-none');
                          if (messagesContainer.innerHTML.trim() === '') {
                                          // 1. Hiển thị hiệu ứng AI đang gõ chữ
                                          const typingMsg = appendMessage('bot', '<div class="typing-dots"><span></span><span></span><span></span></div>');

                                          // 2. Gọi API xin câu chào cá nhân hóa
                                          fetch('/api/chat/welcome')
                                              .then(res => res.text())
                                              .then(greetingText => {
                                                  /// Nhận được câu chào -> Dịch dấu ** thành chữ in đậm (HTML) rồi mới in ra
                                                                           const formattedText = greetingText.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                                                                           typingMsg.innerHTML = formattedText;

                                                  // 3. In kèm Menu Thao tác nhanh
                                                  let quickActionsHtml = `
                                                      <div style="margin-top: 15px; padding: 12px; background: #f8f9fa; border-radius: 8px; border-left: 4px solid #0d6efd;">
                                                          <div style="font-weight: bold; font-size: 13px; color: #333; margin-bottom: 8px;">
                                                              <i class="bi bi-lightning-charge-fill text-warning"></i> Thao tác nhanh:
                                                          </div>
                                                          <div class="quick-replies-container" style="margin-top: 0; padding-top: 0; border: none;">
                                                              <button class="quick-reply-btn" onclick="window.handleQuickAction('booking')">📅 Đặt lịch khám ngay</button>
                                                              <button class="quick-reply-btn" onclick="window.handleQuickAction('doctors')">👨‍⚕️ Tra cứu Bác sĩ</button>
                                                              <button class="quick-reply-btn" onclick="window.handleQuickAction('consult')">💊 Tư vấn triệu chứng bệnh</button>
                                                          </div>
                                                      </div>
                                                  `;
                                                  appendMessage('bot', quickActionsHtml);
                                              })
                                              .catch(() => {
                                                  // Rủi ro mạng lag -> Vẫn có câu chào mặc định cứu cánh
                                                  typingMsg.innerHTML = 'Xin chào! Em là Trợ lý AI HealCare. Anh / Chị cần hỗ trợ vấn đề sức khỏe gì hôm nay?';
                                              });
                                      }
                        }
        // ==========================================
        // 2. LOGIC KÉO THẢ ICON & CHỐNG BUNG CHAT
        // ==========================================
        let isDraggingIcon = false;
        let hasDragged = false; // CỜ PHÂN BIỆT DRAG VS CLICK
        let iconOffsetX, iconOffsetY;
        let dragStartX = 0, dragStartY = 0;

        toggleBtn.addEventListener('mousedown', function(e) {
            dragStartX = e.clientX;
            dragStartY = e.clientY;
            hasDragged = false;

            const rect = widget.getBoundingClientRect();
            iconOffsetX = e.clientX - rect.left;
            iconOffsetY = e.clientY - rect.top;

            // KHÔNG thay đổi style ở đây
            // Chỉ đánh dấu sẵn sàng kéo
            isDraggingIcon = true;
        });

        document.addEventListener('mousemove', function(e) {
            if (!isDraggingIcon) return;

            let moveX = Math.abs(e.clientX - dragStartX);
            let moveY = Math.abs(e.clientY - dragStartY);

            // Chỉ bắt đầu kéo thật khi di chuyển > 5px
            if (moveX > 5 || moveY > 5) {
                hasDragged = true;

                // Chỉ thay đổi style lần đầu khi thực sự kéo
                widget.style.transition = 'none';
                widget.style.bottom = 'auto';
                widget.style.right = 'auto';

                let newX = e.clientX - iconOffsetX;
                let newY = e.clientY - iconOffsetY;

                if (newX < 0) newX = 0;
                if (newY < 0) newY = 0;
                if (newX + widget.offsetWidth > window.innerWidth)
                    newX = window.innerWidth - widget.offsetWidth;
                if (newY + toggleBtn.offsetHeight > window.innerHeight)
                    newY = window.innerHeight - toggleBtn.offsetHeight;

                widget.style.left = newX + 'px';
                widget.style.top = newY + 'px';
            }
        });

        document.addEventListener('mouseup', function() {
            if (isDraggingIcon) {
                isDraggingIcon = false;
                widget.style.transition = 'all 0.3s ease';
            }
        });

      // ==========================================
          // 3. LOGIC ĐÓNG/MỞ VÀ CẮM LOG DEBUG
          // ==========================================

          // CLICK MỞ CHAT CÓ THAO TÁC NHANH (QUICK ACTIONS)
              toggleBtn.addEventListener('click', function(e) {
                  if (hasDragged) {
                      hasDragged = false;
                      return;
                  }
                 // Mở chat, giấu icon và lưu trạng thái
                             chatBox.classList.remove('d-none');
                             toggleBtn.classList.add('d-none'); // Dùng class d-none an toàn tuyệt đối
                             sessionStorage.setItem('meditrust_chat_state', 'open');

                  chatInput.focus();
                 if (messagesContainer.innerHTML.trim() === '') {
                                 // 1. Hiển thị hiệu ứng AI đang gõ chữ
                                 const typingMsg = appendMessage('bot', '<div class="typing-dots"><span></span><span></span><span></span></div>');

                                 // 2. Gọi API xin câu chào cá nhân hóa
                                 fetch('/api/chat/welcome')
                                     .then(res => res.text())
                                     .then(greetingText => {
                                         // Nhận được câu chào -> Xóa 3 dấu chấm, in chữ ra
                                         // Nhận được câu chào -> Dịch dấu ** thành chữ in đậm (HTML) rồi mới in ra
                                                                                  const formattedText = greetingText.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                                                                                  typingMsg.innerHTML = formattedText;

                                         // 3. In kèm Menu Thao tác nhanh
                                         let quickActionsHtml = `
                                             <div style="margin-top: 15px; padding: 12px; background: #f8f9fa; border-radius: 8px; border-left: 4px solid #0d6efd;">
                                                 <div style="font-weight: bold; font-size: 13px; color: #333; margin-bottom: 8px;">
                                                     <i class="bi bi-lightning-charge-fill text-warning"></i> Thao tác nhanh:
                                                 </div>
                                                 <div class="quick-replies-container" style="margin-top: 0; padding-top: 0; border: none;">
                                                     <button class="quick-reply-btn" onclick="window.handleQuickAction('booking')">📅 Đặt lịch khám ngay</button>
                                                     <button class="quick-reply-btn" onclick="window.handleQuickAction('doctors')">👨‍⚕️ Tra cứu Bác sĩ</button>
                                                     <button class="quick-reply-btn" onclick="window.handleQuickAction('consult')">💊 Tư vấn triệu chứng bệnh</button>
                                                 </div>
                                             </div>
                                         `;
                                         appendMessage('bot', quickActionsHtml);
                                     })
                                     .catch(() => {
                                         // Rủi ro mạng lag -> Vẫn có câu chào mặc định cứu cánh
                                         typingMsg.innerHTML = 'Xin chào! Em là Trợ lý AI HealCare. Anh / Chị cần hỗ trợ vấn đề sức khỏe gì hôm nay?';
                                     });
                             }
              });

              // ==========================================
              // HÀM XỬ LÝ SỰ KIỆN KHI BẤM VÀO THAO TÁC NHANH
              // ==========================================
              window.handleQuickAction = function(actionType) {
                  if (actionType === 'booking') {
                      appendMessage('user', 'Tôi muốn đặt lịch khám');
                      appendMessage('bot', '✅ Đang chuyển hướng bạn đến màn hình Đặt lịch...');
                      setTimeout(() => {
                          window.location.href = '/appointment'; // Link trang đặt lịch
                      }, 800);
                  }
                  else if (actionType === 'doctors') {
                      appendMessage('user', 'Tôi muốn tra cứu thông tin Bác sĩ');
                      appendMessage('bot', '✅ Đang chuyển hướng bạn đến Danh sách Bác sĩ...');
                      setTimeout(() => {
                          window.location.href = '/doctors'; // Link trang danh sách bác sĩ
                      }, 800);
                  }
                  else if (actionType === 'consult') {
                      // Dùng hàm sendQuickReply có sẵn để tự động gửi tin nhắn cho AI phân tích
                      // Nhớ truyền vào 1 element ảo để nó không bị lỗi xóa nút
                      const fakeBtn = document.createElement('div');
                      window.sendQuickReply('Tôi muốn được tư vấn triệu chứng bệnh', fakeBtn);
                  }
              };

          // CLICK ĐÓNG CHAT BẰNG NÚT X
              closeBtn.addEventListener('click', (e) => {
                  e.preventDefault();

                  // Giấu chat, hiện icon và lưu trạng thái
                              chatBox.classList.add('d-none');
                              toggleBtn.classList.remove('d-none'); // Gỡ d-none để icon hiện lại lập tức
                              sessionStorage.setItem('meditrust_chat_state', 'closed');

                  // 3. Đè CSS bạo lực để Icon hiện lên
                  toggleBtn.style.cssText = "display: flex !important; visibility: visible !important; opacity: 1 !important; pointer-events: auto !important; z-index: 9999 !important;";

                  // 4. CHỮA BỆNH RỚT TỌA ĐỘ: Reset toàn bộ Widget về góc dưới bên phải
                  widget.style.transition = 'none'; // Tắt hiệu ứng kéo thả cũ
                  widget.style.top = 'auto';
                  widget.style.left = 'auto';
                  widget.style.bottom = '20px'; // Ép nó nằm cách đáy 20px
                  widget.style.right = '20px';  // Ép nó nằm cách phải 20px

                  // Cập nhật lại cờ trạng thái để người dùng có thể bấm/kéo tiếp
                  hasDragged = false;
                  isDraggingIcon = false;
              });
maximizeBtn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();

    const isFullscreen = chatBox.classList.toggle('fullscreen');

    if (isFullscreen) {
        maximizeBtn.innerHTML = '<i class="bi bi-fullscreen-exit"></i>';
    } else {
        maximizeBtn.innerHTML = '<i class="bi bi-arrows-fullscreen"></i>';
        chatBox.style.width  = '';
        chatBox.style.height = '';
    }

    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        });
    });
});
        // ==========================================
        // 4. KÉO THẢ DI CHUYỂN KHUNG CHAT BẰNG HEADER
        // ==========================================
        let isDraggingHeader = false, startHeaderX, startHeaderY, startBoxX, startBoxY;
        header.addEventListener('mousedown', (e) => {
            if(chatBox.classList.contains('fullscreen')) return;
            isDraggingHeader = true;
            startHeaderX = e.clientX;
            startHeaderY = e.clientY;
            const rect = widget.getBoundingClientRect();

            widget.style.bottom = 'auto';
            widget.style.right = 'auto';
            startBoxX = rect.left;
            startBoxY = rect.top;
        });

        document.addEventListener('mousemove', (e) => {
            if (!isDraggingHeader) return;
            let newX = startBoxX + (e.clientX - startHeaderX);
            let newY = startBoxY + (e.clientY - startHeaderY);
            widget.style.left = newX + 'px';
            widget.style.top = newY + 'px';
        });
        document.addEventListener('mouseup', () => isDraggingHeader = false);

        // ==========================================
            // 5. EDGE RESIZING (KÉO CẠNH ĐỂ THAY ĐỔI KÍCH THƯỚC)
            // ==========================================
            const dirs = ['r', 'l', 'b', 't', 'br'];
            dirs.forEach(dir => {
                // Tự động tạo các viền vô hình bọc quanh khung chat
                const resizer = document.createElement('div');
                resizer.className = `chat-resizer chat-resizer-${dir}`;
                chatBox.appendChild(resizer);

                resizer.addEventListener('mousedown', function(e) {
                    if(chatBox.classList.contains('fullscreen')) return; // Không cho kéo khi đang phóng to toàn màn hình
                    e.preventDefault();
                    e.stopPropagation();

                    const startX = e.clientX;
                    const startY = e.clientY;
                    const startWidth = chatBox.offsetWidth;
                    const startHeight = chatBox.offsetHeight;

                    // Cần lấy vị trí hiện tại của toàn bộ Widget để tính toán khi kéo cạnh trái/trên
                    const startWidgetLeft = widget.offsetLeft;
                    const startWidgetTop = widget.offsetTop;

                    function doDrag(e) {
                        // Kéo cạnh Phải
                        if (dir.includes('r')) {
                            chatBox.style.width = startWidth + (e.clientX - startX) + 'px';
                        }
                        // Kéo cạnh Trái (Vừa tăng chiều rộng, vừa đẩy Widget dịch sang trái)
                        if (dir.includes('l')) {
                            const dx = e.clientX - startX;
                            chatBox.style.width = startWidth - dx + 'px';
                            widget.style.left = startWidgetLeft + dx + 'px';
                        }
                        // Kéo cạnh Dưới
                        if (dir.includes('b')) {
                            chatBox.style.height = startHeight + (e.clientY - startY) + 'px';
                        }
                        // Kéo cạnh Trên (Vừa tăng chiều cao, vừa đẩy Widget dịch lên trên)
                        if (dir.includes('t')) {
                            const dy = e.clientY - startY;
                            chatBox.style.height = startHeight - dy + 'px';
                            widget.style.top = startWidgetTop + dy + 'px';
                        }
                    }

                    function stopDrag() {
                        document.removeEventListener('mousemove', doDrag);
                        document.removeEventListener('mouseup', stopDrag);
                    }

                    document.addEventListener('mousemove', doDrag);
                    document.addEventListener('mouseup', stopDrag);
                });
            });


        // ==========================================
        // 6. TABS & HISTORY
        // ==========================================
        tabChat.addEventListener('click', () => {
            tabChat.classList.add('active'); tabHistory.classList.remove('active');
            historyPanel.style.display = 'none';
        });

        tabHistory.addEventListener('click', async () => {
            tabHistory.classList.add('active'); tabChat.classList.remove('active');
            historyPanel.style.display = 'block';

            try {
                const res = await fetch('/api/chat/history');
                if (res.ok) {
                    const data = await res.json();
                    if(data.length === 0) {
                        historyList.innerHTML = '<div style="text-align:center; color:#888; margin-top:20px;">Bạn chưa có lịch sử tư vấn nào hoặc chưa đăng nhập.</div>';
                        document.getElementById('history-loading').style.display = 'none';
                        return;
                    }

                    let html = '';
                    data.forEach(item => {
                        const dateStr = new Date(item.date).toLocaleString('vi-VN');
                        let previewText = "Phiên tư vấn sức khỏe";
                        try {
                            const parsedChat = JSON.parse(item.chatData);
                                                        const firstUserMsg = parsedChat.find(m => m.role === 'user');
                                                        if(firstUserMsg) {
                                                            // Tẩy trang: Cắt bỏ đoạn lệnh ngầm bị dính trong DB cũ
                                                            previewText = firstUserMsg.content.replace(/\n*\s*\(Lệnh hệ thống ngầm:[\s\S]*?\)/gi, '').trim();
                                                        }
                        } catch(e) {}

                      html += `
                                                  <div class="history-item" data-session="${item.sessionCode}" data-chat='${item.chatData.replace(/'/g, "&#39;")}'>
                                                      <div class="history-date"><i class="bi bi-clock-history"></i> ${dateStr}</div>
                                                      <div class="history-preview"><b>Hỏi:</b> ${previewText}</div>
                                                  </div>
                                              `;
                    });
                    historyList.innerHTML = html;
                    document.getElementById('history-loading').style.display = 'none';

                   document.querySelectorAll('.history-item').forEach(el => {
                                           el.addEventListener('click', function() {
                                               const rawData = this.getAttribute('data-chat');
                                               const oldSessionId = this.getAttribute('data-session');

                                               // 1. CẬP NHẬT LẠI SESSION ID ĐỂ NỐI TIẾP CUỘC TRÒ CHUYỆN CŨ
                                               if (oldSessionId && oldSessionId !== "undefined") {
                                                   sessionId = oldSessionId;
                                                   sessionStorage.setItem('meditrust_session_id', oldSessionId);
                                               }

                                               // 2. VẼ LẠI GIAO DIỆN (CHỈ LẤY PHẦN TEXT, BỎ QUA GỌI API BÁC SĨ ĐỂ TRÁNH SPAM)
                                               const chatArray = JSON.parse(rawData);
                                               messagesContainer.innerHTML = '';
                                               chatArray.forEach(msg => {
                                                   if (msg.role === 'user') {
                                                      // Tẩy trang: Dọn sạch lệnh ngầm trước khi vẽ bong bóng chat
                                                                                                             let cleanStr = msg.content.replace(/\n*\s*\(Lệnh hệ thống ngầm:[\s\S]*?\)/gi, '').trim();
                                                                                                             appendMessage('user', cleanStr);
                                                   } else if (msg.role === 'assistant') {
                                                       try {
                                                           let cleanStr = msg.content.replace(/```json/gi, '').replace(/```/g, '').trim();
                                                           let aiData = JSON.parse(cleanStr);
                                                           appendMessage('bot', aiData.ai_reply);
                                                       } catch(e) {
                                                           appendMessage('bot', msg.content.replace(/\n/g, '<br>'));
                                                       }
                                                   }
                                               });

                                               tabChat.click();
                                           });
                                       });
                }
            } catch(e) { console.error(e); }
        });

        // ==========================================
        // 7. SEND MESSAGE & GENERATIVE UI
        // ==========================================
        function appendMessage(sender, htmlContent) {
           const msgDiv = document.createElement('div');
                   msgDiv.classList.add('chat-msg', sender === 'assistant' || sender === 'bot' ? 'bot' : 'user');
                   msgDiv.innerHTML = htmlContent;
                   messagesContainer.appendChild(msgDiv);
                   messagesContainer.scrollTop = messagesContainer.scrollHeight;

                   // Lưu toàn bộ nội dung HTML của khung chat vào Session ngay lập tức
                   sessionStorage.setItem('meditrust_chat_html', messagesContainer.innerHTML);
                   // [THÊM MỚI] Cập nhật thời gian hoạt động cuối cùng
                   sessionStorage.setItem('meditrust_last_activity', new Date().getTime().toString());
                   return msgDiv;
        }

        // Báo kết quả một lượt chat cho các module bên ngoài (hiện tại: chế độ gọi bằng giọng nói).
        // Luôn được gọi đúng MỘT lần cho mỗi lượt, kể cả khi lỗi, để bên nghe không bị treo.
        function notifyReply(payload) {
            const subscribers = (window.MediTrustChat && window.MediTrustChat.onReply) || [];
            subscribers.forEach(function(fn) {
                try { fn(payload); } catch (e) { console.error('Lỗi ở subscriber onReply:', e); }
            });
        }

        async function sendMessage() {
            const text = chatInput.value.trim();
            if (!text) return;
            appendMessage('user', text);
            chatInput.value = '';

            // Hiển thị hiệu ứng 3 dấu chấm nhảy trong lúc chờ AI phản hồi
                        const typingMsg = appendMessage('bot', '<div class="typing-dots"><span></span><span></span><span></span></div>');

            try {
                const response = await fetch('/api/chat/ask', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sessionId: sessionId, prompt: text })
                });

                if (response.ok) {
                    const data = await response.json();
                    let aiRawText = data.answer;

                                        try {
                                            // 1. Dọn dẹp markdown và parse JSON
                                            let cleanJsonStr = aiRawText.replace(/```json/gi, '').replace(/```/g, '').trim();
                                            const aiData = JSON.parse(cleanJsonStr);

                                            // THÊM DÒNG NÀY ĐỂ SOI DATA:
                                                                    console.log("🔍 DỮ LIỆU AI TRẢ VỀ:", aiData);

                                            // 2. In câu trả lời tư vấn
                                            typingMsg.innerHTML = aiData.ai_reply;
                                            if (aiData.is_emergency) {
                                                typingMsg.innerHTML = `<div style="color: red; font-weight: bold; margin-bottom: 8px;"><i class="bi bi-exclamation-triangle-fill"></i> 🚨 CẢNH BÁO KHẨN CẤP:</div>` + typingMsg.innerHTML;
                                            }

                                            // 3. Xử lý đa ý định (Vòng lặp quét mảng recommended_departments)
                                            const deptIds = aiData.recommended_departments;
                                            if (deptIds && Array.isArray(deptIds) && deptIds.length > 0) {
                                                let allActionHtml = `<div class="mt-3">
                                                    <div style="background: #fff3cd; color: #856404; padding: 6px 10px; border-radius: 5px; font-size: 11px; font-weight: bold; margin-bottom: 10px; border-left: 3px solid #ffeeba; display: flex; align-items: center; gap: 5px;">
                                                        <i class="bi bi-hourglass-split" style="animation: spin 2s linear infinite;"></i> Hệ thống đang tạm giữ các lịch trống trong 3 phút. Hãy chọn nhanh!
                                                    </div>`;

                                                for (let i = 0; i < deptIds.length; i++) {
                                                    const deptId = deptIds[i];
                                                    try {
                                                        const docRes = await fetch(`/api/chat/doctors/department/${deptId}?sessionId=${sessionId}`);
                                                        if (docRes.ok) {
                                                            const doctors = await docRes.json();
                                                            if (doctors && doctors.length > 0) {
                                                                allActionHtml += `
                                                                    <p class="mb-2 mt-3" style="font-size: 13px; font-weight: bold; color: #198754;">
                                                                        <i class="bi bi-hospital"></i> Bác sĩ chuyên khoa đang sẵn sàng:
                                                                    </p>
                                                                    <div style="display: flex; gap: 12px; overflow-x: auto; padding-bottom: 10px; scroll-snap-type: x mandatory; -webkit-overflow-scrolling: touch;">`;

                                                                for (const doc of doctors) {
                                                                    let slotsHtml = `<div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                                                                            <span style="font-size: 11px; color: #888; font-weight: 600;">Ca trống gần nhất:</span>
                                                                            <a href="/appointment?doctorId=${doc.id}" style="font-size: 10px; color: #198754; text-decoration: none; font-weight: bold; background: #e8f5e9; padding: 3px 8px; border-radius: 12px;"><i class="bi bi-calendar-plus"></i> Chọn lịch khác</a>
                                                                        </div>`;

                                                                    if (doc.availableSlots && doc.availableSlots.length > 0) {
                                                                        slotsHtml += doc.availableSlots.map(time => `<a href="/appointment?doctorId=${doc.id}&time=${time}" style="display: inline-block; padding: 4px 8px; margin: 2px; border: 1px solid #0d6efd; color: #0d6efd; border-radius: 5px; text-decoration: none; font-size: 11px;">${time}</a>`).join('');
                                                                    } else {
                                                                        slotsHtml += `<span style="font-size: 11px; color: #dc3545;">Tạm hết lịch trực</span>`;
                                                                    }

                                                                    allActionHtml += `
                                                                        <div style="background: #fff; border: 1px solid #e0e0e0; border-radius: 10px; padding: 12px; min-width: 260px; scroll-snap-align: start; flex-shrink: 0; box-shadow: 0 2px 4px rgba(0,0,0,0.05);">
                                                                            <div style="display: flex; align-items: center; margin-bottom: 10px;">
                                                                                <img src="${doc.avatar}" onerror="this.src='/assets/img/default-doctor.png'" style="width: 45px; height: 45px; border-radius: 50%; object-fit: cover; border: 2px solid #f8f9fa; margin-right: 12px;">
                                                                                <div>
                                                                                    <div style="font-size: 14px; font-weight: bold; color: #333;">${doc.fullName}</div>
                                                                                    <div style="font-size: 12px; color: #666;">${doc.degree} • ${doc.experienceYears} năm KN</div>
                                                                                    <div style="font-size: 12px; color: #ffc107;">⭐⭐⭐⭐⭐ 5.0</div>
                                                                                </div>
                                                                            </div>
                                                                            <div style="border-top: 1px dashed #eee; padding-top: 8px;">${slotsHtml}</div>
                                                                        </div>`;
                                                                }
                                                                allActionHtml += `
                                                                    <div style="min-width: 120px; display: flex; align-items: center; justify-content: center; scroll-snap-align: start; flex-shrink: 0;">
                                                                        <a href="/doctors?departmentId=${deptId}" style="text-align: center; color: #0d6efd; text-decoration: none; font-weight: bold; font-size: 13px;">
                                                                            <div style="width: 40px; height: 40px; border-radius: 50%; background: #e9ecef; display: flex; align-items: center; justify-content: center; margin: 0 auto 5px;"><i class="bi bi-arrow-right"></i></div>
                                                                            Xem tất cả
                                                                        </a>
                                                                    </div></div>`;
                                                            } else {
                                                                allActionHtml += `<div class="mt-3 p-3" style="background: #f8f9fa; border-radius: 8px; border-left: 4px solid #17a2b8;"><p style="font-size: 13px; margin-bottom: 8px;"><strong><i class="bi bi-info-circle text-info"></i> Thông báo:</strong> Chuyên khoa này hiện đang kín lịch.</p><a href="/appointment" style="display: inline-block; background: #0d6efd; color: white; padding: 6px 12px; border-radius: 5px; text-decoration: none; font-size: 12px; font-weight: bold;">Xem lịch hẹn khác</a></div>`;
                                                            }
                                                        }
                                                    } catch (err) { console.error(err); }
                                                }
                                                allActionHtml += `</div>`;
                                                typingMsg.innerHTML += allActionHtml;
                                            }

                                            // 4. XỬ LÝ GỢI Ý TRẢ LỜI NHANH (QUICK REPLIES)

                                            // Kiểm tra xem AI có đang đề xuất chuyên khoa (bung thẻ bác sĩ) không
                                                                                        const isShowingDoctors = aiData.recommended_departments && Array.isArray(aiData.recommended_departments) && aiData.recommended_departments.length > 0;
                                                                                        if (!aiData.is_emergency && !isShowingDoctors && aiData.suggested_prompts && Array.isArray(aiData.suggested_prompts) && aiData.suggested_prompts.length > 0) {
                                                                                            let suggestHtml = `<div class="quick-replies-container">`;
                                                                                            aiData.suggested_prompts.forEach(promptText => {
                                                                                                // Xử lý an toàn chuỗi (Escape string) để không lỗi nháy kép
                                                                                                const safeText = promptText.replace(/'/g, "\\'").replace(/"/g, "&quot;");
                                                                                                suggestHtml += `<button class="quick-reply-btn" onclick="window.sendQuickReply('${safeText}', this)">${promptText}</button>`;
                                                                                            });
                                                                                            suggestHtml += `</div>`;
                                                                                            typingMsg.innerHTML += suggestHtml;
                                                                                        }

                                            const bookingHandoff = await resolveBookingHandoff(aiData, text);

                                            // Khách nêu đích danh bác sĩ nhưng không tìm thấy: nói thật cho khách biết,
                                            // tuyệt đối không lặng lẽ điều hướng sang bác sĩ khác.
                                            if (bookingHandoff && bookingHandoff.doctorNotFound) {
                                                typingMsg.innerHTML += `
                                                    <div class="mt-3 p-3" style="background: #fff8e1; border-left: 4px solid #ffc107; border-radius: 8px;">
                                                        <div class="fw-bold mb-1" style="color: #b8860b;">
                                                            <i class="bi bi-exclamation-circle"></i> Em chưa tìm thấy bác sĩ "${bookingHandoff.requestedDoctorName}"
                                                        </div>
                                                        <div style="font-size: 13px; color: #334155;">
                                                            Anh/chị kiểm tra lại tên giúp em, hoặc chọn một bác sĩ trong danh sách phía trên ạ.
                                                        </div>
                                                    </div>`;
                                                sessionStorage.setItem('meditrust_chat_html', messagesContainer.innerHTML);
                                                notifyReply({ aiData: aiData, userText: text, bookingHandoff: bookingHandoff });
                                                return;
                                            }

                                            if (bookingHandoff) {
                                                const fallbackText = bookingHandoff.fallback
                                                    ? 'Khung giờ anh/chị yêu cầu chưa khớp hoàn toàn, em đã chọn khung giờ gần nhất còn trống.'
                                                    : 'Em đã mở đúng bác sĩ và khung giờ anh/chị vừa yêu cầu.';
                                                typingMsg.innerHTML += `
                                                    <div class="mt-3 p-3" style="background: #eef6ff; border-left: 4px solid #0d6efd; border-radius: 8px;">
                                                        <div class="fw-bold mb-1" style="color: #0d6efd;"><i class="bi bi-calendar-check"></i> ${fallbackText}</div>
                                                        <div style="font-size: 13px; color: #334155;">
                                                            <div><strong>Bác sĩ:</strong> ${bookingHandoff.doctorName || bookingHandoff.doctor.fullName}</div>
                                                            <div><strong>Lịch hẹn:</strong> ${bookingHandoff.selectedSlotLabel}</div>
                                                        </div>
                                                        <a href="${bookingHandoff.appointmentUrl}" class="btn btn-sm btn-primary mt-2">Mở trang đặt lịch</a>
                                                    </div>`;
                                                sessionStorage.setItem('meditrust_chat_html', messagesContainer.innerHTML);

                                                notifyReply({ aiData: aiData, userText: text, bookingHandoff: bookingHandoff });

                                                // Chế độ gọi tự đọc câu xác nhận rồi chờ khách nói "đồng ý",
                                                // nên khi đang gọi thì KHÔNG tự nhảy trang.
                                                if (!window.MediTrustChat || !window.MediTrustChat.suppressAutoRedirect) {
                                                    setTimeout(() => {
                                                        window.location.href = bookingHandoff.appointmentUrl;
                                                    }, 900);
                                                }
                                                return;
                                            }

                                            // Lưu lại khung HTML (Đã chạy ngầm memory JSON)
                                            sessionStorage.setItem('meditrust_chat_html', messagesContainer.innerHTML);
                                            notifyReply({ aiData: aiData, userText: text, bookingHandoff: null });

                                        } catch (parseError) {
                                            // FALLBACK: Đề phòng rủi ro AI bị ảo giác sinh ra text thường thay vì JSON
                                            console.error("Lỗi parse JSON:", parseError);
                                            typingMsg.innerHTML = aiRawText.replace(/\n/g, '<br>');
                                            sessionStorage.setItem('meditrust_chat_html', messagesContainer.innerHTML);
                                            notifyReply({ aiData: null, rawText: aiRawText, userText: text, bookingHandoff: null });
                                        }
                } else {
                    typingMsg.innerHTML = 'Hệ thống bận.';
                    notifyReply({ aiData: null, userText: text, bookingHandoff: null, error: 'server-busy' });
                }
            } catch (error) {
                typingMsg.innerHTML = 'Lỗi kết nối.';
                notifyReply({ aiData: null, userText: text, bookingHandoff: null, error: 'network' });
            }
        }

        sendBtn.addEventListener('click', sendMessage);
        chatInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') sendMessage(); });
        // [THÊM MỚI] Sự kiện cho nút Làm mới Chat (Tự động xóa và reset phiên)
                const btnNewChat = document.getElementById('btn-new-chat');
                if (btnNewChat) {
                    btnNewChat.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (confirm('Bạn muốn kết thúc ca tư vấn này và bắt đầu hỏi vấn đề mới?')) {
                            sessionStorage.removeItem('meditrust_session_id');
                            sessionStorage.removeItem('meditrust_chat_html');
                            sessionStorage.removeItem('meditrust_last_activity');
                            sessionStorage.setItem('meditrust_chat_state', 'open'); // Ép mở lại sau khi reload
                            window.location.reload();
                        }

                    });
                }
// ==========================================
        // [THÊM MỚI] 8. TOUR GUIDE & ĐIỀU KHIỂN HIỆU ỨNG
        // ==========================================

        // 1. Tự động Render HTML cho Tour Guide
        const tourGuideHtml = `
            <div id="chat-tour-guide" class="tour-guide-box">
                <div class="tour-guide-title"><i class="bi bi-robot fs-4"></i> Trợ lý AI MediTrust</div>
                <div class="tour-guide-desc">Hệ thống AI y tế đã sẵn sàng! Có thể giúp bạn chẩn đoán bệnh sơ bộ và đặt lịch nhanh chóng. Hãy hỏi tôi nhé!</div>
                <button id="btn-close-tour" class="tour-guide-btn">Đã hiểu</button>
                <div style="clear:both;"></div>
            </div>
        `;
        document.body.insertAdjacentHTML('beforeend', tourGuideHtml);

        const tourGuideBox = document.getElementById('chat-tour-guide');
        const btnCloseTour = document.getElementById('btn-close-tour');

        // 2. Logic Hiển thị (Chỉ hiện 1 lần duy nhất trên mỗi trình duyệt)
        const hasSeenTour = localStorage.getItem('meditrust_tour_seen');

        // Nếu Chat đang đóng và chưa từng xem Tour -> Chờ 2 giây sau khi load web rồi bật lên
        if (!hasSeenTour && sessionStorage.getItem('meditrust_chat_state') !== 'open') {
            setTimeout(() => {
                tourGuideBox.classList.add('show');
            }, 2000);
        }

        // 3. Hàm tắt Tour và chuyển sang Tour Cảnh báo Khẩn cấp
        function closeTourGuide() {
            localStorage.setItem('meditrust_tour_seen', 'true'); // Đã xem tour cơ bản

            // Kiểm tra tin tức y tế khẩn cấp
            fetch('/api/public/news/latest-alert')
                .then(response => {
                    if (response.status === 204) {
                        return null; // Không có bài viết nào
                    }
                    return response.json();
                })
                .then(data => {
                    if (data) {
                        const lastAlertId = localStorage.getItem('meditrust_last_alert_id');
                        if (lastAlertId === data.id.toString()) {
                            // Đã xem cảnh báo này rồi thì đóng luôn
                            tourGuideBox.classList.remove('show');
                            return;
                        }

                        // Nếu có bài viết mới và chưa xem, đổi giao diện Tour Guide
                        tourGuideBox.innerHTML = `
                            <div class="tour-guide-title text-danger"><i class="bi bi-exclamation-triangle-fill fs-4"></i> Cảnh báo Y tế</div>
                            <div class="tour-guide-desc">
                                <strong>${data.title}</strong><br/>
                                <span style="font-size: 0.9em;">${data.summary}</span>
                            </div>
                            <div style="display: flex; gap: 10px; margin-top: 10px;">
                                <button id="btn-read-alert" class="tour-guide-btn" style="background-color: #dc3545; color: white;">Đọc tiếp</button>
                                <button id="btn-skip-alert" class="tour-guide-btn" style="background-color: #6c757d; color: white;">Bỏ qua</button>
                            </div>
                            <div style="clear:both;"></div>
                        `;

                        document.getElementById('btn-read-alert').addEventListener('click', function() {
                            localStorage.setItem('meditrust_last_alert_id', data.id.toString());
                            window.location.href = '/news/' + data.id;
                        });

                        document.getElementById('btn-skip-alert').addEventListener('click', function() {
                            localStorage.setItem('meditrust_last_alert_id', data.id.toString());
                            tourGuideBox.classList.remove('show');
                        });
                    } else {
                        // Không có dữ liệu, ẩn luôn
                        tourGuideBox.classList.remove('show');
                    }
                })
                .catch(err => {
                    console.error("Lỗi khi tải tin tức khẩn cấp:", err);
                    tourGuideBox.classList.remove('show');
                });
        }

        // Tắt khi ấn nút "Đã hiểu"
        btnCloseTour.addEventListener('click', closeTourGuide);

        // Tắt khi ấn mở Icon Chat (Khách tự mò mở thì tắt luôn hướng dẫn)
        toggleBtn.addEventListener('click', function() {
            tourGuideBox.classList.remove('show');
            localStorage.setItem('meditrust_tour_seen', 'true');
        });

        // 4. Tắt hiệu ứng nhún nhảy khi khách bắt đầu bấm giữ để KÉO THẢ icon
        toggleBtn.addEventListener('mousedown', function() {
            toggleBtn.classList.add('dragging');
        });
        document.addEventListener('mouseup', function() {
            toggleBtn.classList.remove('dragging');
        });

        // ==========================================
        // 9. GIỌNG NÓI (Voice Agent)
        // ==========================================

        // 9a. Nút mic + nút loa. Module tự ẩn nếu trình duyệt không hỗ trợ.
        if (window.MediTrustVoice) {
            window.MediTrustVoice.attach({
                inputId: 'ai-chat-input',
                sendBtnId: 'ai-chat-send',
                messagesId: 'ai-chat-messages',
                botSelector: '.chat-msg.bot',
                micTitle: 'Bấm để nói với trợ lý'
            });
        }

        // 9b. Mở khung chat ra ngoài cho chế độ gọi rảnh tay.
        //     Nhờ vậy meditrust-voice-call.js làm việc trên dữ liệu có cấu trúc
        //     (speech_reply, is_emergency, booking_target) thay vì bóc chữ từ DOM.
        window.MediTrustChat = {
            sendMessage: sendMessage,
            appendMessage: appendMessage,
            openChat: function() { if (chatBox.classList.contains('d-none')) toggleBtn.click(); },
            get sessionId() { return sessionId; },
            // Chế độ gọi bật cờ này để tự lo phần xác nhận bằng giọng nói
            // thay vì để khung chat tự nhảy trang sau 900ms.
            suppressAutoRedirect: false,
            onReply: []
        };
    });