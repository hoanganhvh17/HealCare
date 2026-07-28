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

                /**
                 * sessionStorage KHÔNG BAO GIỜ được phép ném ra ngoài.
                 *
                 * Ở chế độ riêng tư, khi trình duyệt chặn cookie/bộ nhớ bên thứ ba, hoặc trong iframe
                 * sandbox, chỉ riêng việc ĐỌC cũng ném SecurityError. Lời gọi đầu tiên nằm ngay trong
                 * DOMContentLoaded, nên exception ở đó làm chết cả phần còn lại của script: nút Gửi
                 * không được gắn sự kiện, window.MediTrustChat không tồn tại — khung chat hiện ra
                 * nhưng bấm Gửi hoàn toàn không phản ứng, và không có một lời báo lỗi nào.
                 *
                 * Trường hợp thứ hai là HẾT DUNG LƯỢNG: 'meditrust_chat_html' lưu toàn bộ HTML khung
                 * chat (kể cả thẻ bác sĩ, vài KB mỗi lượt) và không bao giờ được cắt bớt. setItem ném
                 * QuotaExceededError ngay bên trong appendMessage, mà appendMessage lại được gọi
                 * ngoài khối try của sendMessage -> tin nhắn không gửi đi, không bong bóng, im lặng.
                 */
                const CHAT_HTML_LIMIT = 300000;   // ~300KB, còn cách xa hạn mức ~5MB của trình duyệt
                const safeStorage = {
                    get: function(key) {
                        try { return sessionStorage.getItem(key); } catch (e) { return null; }
                    },
                    set: function(key, value) {
                        try {
                            sessionStorage.setItem(key, value);
                            return true;
                        } catch (e) {
                            // Đầy bộ nhớ: bỏ lịch sử HTML (thứ cồng kềnh nhất) rồi thử lại một lần.
                            try {
                                sessionStorage.removeItem('meditrust_chat_html');
                                sessionStorage.setItem(key, value);
                                return true;
                            } catch (e2) {
                                console.warn('Không ghi được sessionStorage:', e2 && e2.name);
                                return false;
                            }
                        }
                    },
                    remove: function(key) {
                        try { sessionStorage.removeItem(key); } catch (e) { /* không sao */ }
                    },
                    /** Lưu HTML khung chat, cắt bớt phần đầu khi quá dài. */
                    setChatHtml: function(html) {
                        const value = (html && html.length > CHAT_HTML_LIMIT)
                            ? html.slice(html.length - CHAT_HTML_LIMIT)
                            : html;
                        return safeStorage.set('meditrust_chat_html', value);
                    }
                };

                function normalizeText(input) {
                    return (input || '')
                        .toLowerCase()
                        .replace(/\s+/g, ' ')
                        .trim();
                }

                /**
                 * Bỏ dấu tiếng Việt: "Trần Văn Bình" -> "tran van binh".
                 *
                 * Rất nhiều khách gõ không dấu ("dat lich voi bac si binh"). Trước đây mọi so khớp
                 * tên đều chạy trên chuỗi CÓ DẤU nên cả nhóm khách này không bao giờ chỉ định được
                 * bác sĩ — luôn nhận "Em chưa tìm thấy bác sĩ ...".
                 *
                 * CHỈ dùng để so khớp TÊN. Đừng đem đi so khớp câu nói: bỏ dấu sẽ biến "sáng" thành
                 * "sang" (giới từ) và "tôi" thành "toi", đúng hai cái bẫy đã ghi ở extractSessionHint.
                 */
                function stripDiacritics(input) {
                    return (input || '')
                        .normalize('NFD')
                        .replace(/[̀-ͯ]/g, '')
                        .replace(/đ/g, 'd')
                        .replace(/Đ/g, 'D')
                        .toLowerCase()
                        .replace(/\s+/g, ' ')
                        .trim();
                }

                /** Ngày hôm nay theo múi giờ MÁY KHÁCH. Không dùng toISOString() vì đó là giờ UTC:
                 *  ở Việt Nam (UTC+7), sau 17h nó đã nhảy sang ngày mai. */
                function toIsoDate(date) {
                    return date.getFullYear()
                        + '-' + String(date.getMonth() + 1).padStart(2, '0')
                        + '-' + String(date.getDate()).padStart(2, '0');
                }

                /**
                 * Lấy giờ BẮT ĐẦU của một nhãn khung giờ.
                 *   "T5 24/07 (10:00 - 10:30)" -> "10:00"
                 *   "10:30 - 11:00"            -> "10:30"
                 *
                 * Bắt buộc phải so sánh theo giờ bắt đầu, KHÔNG được dùng indexOf: chuỗi "10:30"
                 * nằm trong cả khung "10:00 - 10:30" (giờ KẾT THÚC) lẫn khung "10:30 - 11:00".
                 * Khách xin 10h30 mà khớp bằng indexOf thì trúng ngay khung 10h00.
                 */
                function slotStartTime(slotLabel) {
                    const inside = (slotLabel || '').match(/\((.*?)\)/);
                    const range = inside ? inside[1] : (slotLabel || '');
                    const start = range.match(/(\d{1,2}):(\d{2})/);
                    return start ? String(parseInt(start[1], 10)).padStart(2, '0') + ':' + start[2] : '';
                }

                function normalizeTimeHint(text) {
                    const raw = normalizeText(text);
                    if (!raw) return '';

                    // Neo vào dấu hiệu chỉ giờ ("h", ":", "giờ", "rưỡi") thay vì vơ lấy con số
                    // ĐẦU TIÊN trong câu: "ngày 25 tháng 7 lúc 10h30" mà lấy số đầu thì ra "25:00",
                    // còn trong "đặt lịch ngày 5/8" thì con số đó là NGÀY chứ không phải giờ.
                    //
                    // `h(?![\p{L}])`, KHÔNG phải `h` trần: chữ "h" trần khớp luôn chữ h mở đầu các từ
                    // tiếng Việt rất hay đi ngay sau một con số trong hội thoại y tế —
                    // "đau bụng 3 HÔM nay" ra 15:00, "sốt 2 HÔM rồi" ra 14:00, "2 HOẶC 3 ngày nữa"
                    // ra 14:00 — rồi giờ ma đó thành mong muốn của khách và được đem đi chốt lịch.
                    // Lookahead hợp lệ; chỉ LOOKBEHIND `(?<=…)` mới bị cấm (Safari cũ) — xem
                    // coding-conventions.md.
                    let match = raw.match(/(\d{1,2})\s*(?:h(?![\p{L}])|:|giờ|gio|rưỡi|ruoi)\s*(\d{1,2})?/u);
                    if (!match) match = raw.match(/^(\d{1,2})$/);   // booking_target trả về trần một con số
                    if (!match) return '';

                    let hour = parseInt(match[1], 10);
                    let minute = match[2] !== undefined ? parseInt(match[2], 10) : null;
                    const tail = raw.slice(match.index + match[0].length);

                    // "8h kém 15" = 07:45. Không hiểu "kém" thì hàm trả 08:00 — sai 15 phút mà vẫn
                    // trông như một giờ hợp lệ nên không ai phát hiện ra.
                    const kem = tail.match(/^\s*(?:kém|kem|thiếu|thieu)\s*(\d{1,2})/);
                    if (kem && minute === null) {
                        hour -= 1;
                        minute = 60 - parseInt(kem[1], 10);
                        if (minute >= 60 || minute < 0) return '';
                    }

                    // "10 giờ rưỡi" / "10 rưỡi" = 10:30 — cách nói rất phổ biến khi gọi bằng giọng nói
                    if (minute === null) {
                        minute = /(rưỡi|ruoi)/.test(match[0] + tail.slice(0, 6)) ? 30 : 0;
                    }

                    // "3 giờ chiều" -> 15:00. Ngoài ra bệnh viện chỉ nhận khám trong giờ hành
                    // chính 07:30-11:30 và 13:30-17:30, nên giờ 1-5 chỉ có thể là buổi chiều,
                    // trừ khi khách nói rõ "sáng".
                    //
                    // Chữ "sáng" phải xét ở NGAY SAU con số chứ không quét cả câu: "đặt 4h chiều mai,
                    // sáng nay tôi bận" có chữ "sáng" ở vế sau và làm 4h chiều thành 04:00.
                    const nearTail = tail.slice(0, 12);
                    if (hour >= 1 && hour <= 11 && /^[^0-9]{0,10}(chiều|chieu|tối|đêm)/.test(tail)) {
                        hour += 12;
                    } else if (hour >= 1 && hour <= 5 && !/sáng/.test(nearTail)) {
                        hour += 12;
                    }

                    if (hour < 0 || hour > 23 || minute > 59) return '';
                    return String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0');
                }

                /**
                 * BUỔI khách nhắc tới: 'morning' | 'afternoon' | 'evening' | ''.
                 *
                 * Là anh em với normalizeTimeHint() và phải đặt cạnh nó: "sáng"/"chiều" là BUỔI chứ
                 * không phải giờ, nên normalizeTimeHint cố tình bỏ qua và trả ''. Trước đây không ai
                 * bắt lấy phần này, nên "đổi sang sáng thứ ba" bị coi như khách không nêu gì cả và
                 * hệ thống lặng lẽ chốt một khung giờ bất kỳ.
                 *
                 * CHỈ gọi khi requestedTime rỗng — "3 giờ chiều" đã được normalizeTimeHint quy thành
                 * 15:00 rồi, hỏi thêm buổi ở đó là thừa.
                 *
                 * Cố ý KHÔNG map buổi ra khung giờ ở đây: việc đó nằm ở server (/slot-alternatives),
                 * để trình duyệt không trở thành nơi khai báo lưới khung giờ thứ 12.
                 *
                 * So khớp bằng chuỗi có ĐỆM KHOẢNG TRẮNG chứ không dùng \b: trong JS, \b chỉ hiểu
                 * chữ cái ASCII nên "\bđêm\b" KHÔNG BAO GIỜ khớp ('đ' không phải word char, đứng sau
                 * dấu cách thì không có ranh giới nào cả). Cùng thành ngữ với resolveAlternativeChoice().
                 */
                function extractSessionHint(text) {
                    const padded = ' ' + normalizeText(text).replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim() + ' ';
                    if (padded === '  ') return '';

                    // padded luôn có dấu cách ở CẢ hai đầu nên ' từ ' đủ bắt mọi vị trí, kể cả đầu/cuối câu.
                    const hasWord = function(words) {
                        return words.some(function(w) { return padded.indexOf(' ' + w + ' ') !== -1; });
                    };

                    const hasMorning = hasWord(['sáng', 'buổi sang', 'buoi sang']);
                    const hasAfternoon = hasWord(['chiều', 'chieu', 'trưa', 'trua']);

                    // Khách nêu CẢ HAI buổi ("sáng hoặc chiều đều được", "sáng hay chiều cũng được")
                    // là KHÔNG kén buổi — trả '' để đừng ép họ vào một buổi họ không hề chọn.
                    if (hasMorning && hasAfternoon && /hoặc|hay|đều được|cũng được|deu duoc|cung duoc/.test(padded)) {
                        return '';
                    }

                    // Xét CHIỀU/TỐI trước rồi mới tới SÁNG. "đổi sang buổi chiều" chứa cả hai manh mối,
                    // và cái khách thật sự muốn là vế sau.
                    if (hasAfternoon) return 'afternoon';
                    if (hasWord(['tối', 'đêm', 'dem', 'buổi toi', 'buoi toi'])) return 'evening';
                    if (hasMorning) return 'morning';
                    return '';

                    // TUYỆT ĐỐI KHÔNG thêm 'sang' hay 'toi' trần vào danh sách trên:
                    //   - "sang" là giới từ cực phổ biến ("đổi SANG buổi chiều", "chuyển SANG chiều mai"),
                    //     nhận nhầm thành "sáng" là hỏi server đúng buổi NGƯỢC LẠI ý khách.
                    //   - "toi" trùng "tôi" khi khách gõ không dấu ("cho toi dat lich").
                    // Khách gõ không dấu vẫn được hỗ trợ qua cụm rõ nghĩa "buoi sang" / "buoi toi".
                }

                /**
                 * Buổi đó của HÔM NAY đã trôi qua chưa (sáng hết sau 11:30, chiều hết sau 17:30).
                 *
                 * Khách KHÔNG nêu buổi thì mốc là hết giờ hành chính: 18h thứ Năm mà nói "đặt lịch
                 * thứ 5" thì ý là thứ Năm TUẦN SAU. Trả false ở đây (như trước) khiến hệ thống chọn
                 * hôm nay, mọi khung đã qua, và khách nhận thẻ "đã kín lịch" — sai mà lại còn khó hiểu.
                 */
                function sessionAlreadyPassed(session) {
                    const now = new Date();
                    const minutes = now.getHours() * 60 + now.getMinutes();
                    if (session === 'morning') return minutes >= 11 * 60 + 30;
                    if (session === 'afternoon') return minutes >= 17 * 60 + 30;
                    if (session === 'evening') return false;   // buổi tối bị từ chối ở nơi khác
                    return minutes >= 17 * 60 + 30;
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

                /**
                 * Những cụm đứng ngay sau "bác sĩ" nhưng KHÔNG PHẢI TÊN NGƯỜI.
                 *
                 * Thiếu danh sách này thì "bác sĩ nào cũng được" bị coi là tên và khách nhận nguyên
                 * văn "Em chưa tìm thấy bác sĩ nào cũng được" — trong khi ý họ là KHÔNG kén bác sĩ.
                 * So khớp không dấu để bắt luôn các biến thể gõ tay.
                 */
                const NOT_A_DOCTOR_NAME = [
                    'nao', 'nao cung duoc', 'nao cung dc', 'bat ky', 'bat cu', 'gi cung duoc',
                    'khac', 'nu', 'nam', 'gioi', 'tot', 'ranh', 'truc', 'nay', 'do', 'kia',
                    'chuyen khoa', 'chuyen mon', 'truc hom nay', 'truc ca nay', 'phu trach'
                ];

                function extractDoctorName(text) {
                    const normalized = normalizeText(text);
                    const match = normalized.match(/(?:bác sĩ|bs\.?)\s+(.+?)(?:\s+lúc|\s+vào|\s+ngày|\s+thứ|\s+để|\s+đặt|\s+khám|$)/i);
                    if (!match) return '';

                    // Cắt DẤU CÂU trước rồi mới tới từ đệm, rồi dấu câu lần nữa.
                    // Thứ tự cũ (từ đệm trước) hỏng với "bác sĩ Bình ạ." — cuối chuỗi là "." nên "ạ"
                    // neo `$` không khớp, kết quả còn "bình ạ" và tìm không ra ai.
                    //
                    // Neo vào CUỐI CHUỖI chứ không dùng \b: trong JS, \b chỉ hiểu chữ cái ASCII,
                    // nên "nhé" hay "ạ" (kết thúc bằng ký tự có dấu) sẽ không bao giờ khớp được.
                    const FILLER = /(\s+(đi|nhé|nha|nhá|ạ|à|với|luôn|thôi|nhá|nhở|được không|cho tôi|cho mình|giúp tôi|giúp mình|giúp em|cũng được|cung duoc))+$/gi;
                    const name = match[1]
                        .replace(/[.,!?]+$/, '')
                        .replace(FILLER, '')
                        .replace(/[.,!?]+$/, '')
                        .trim();
                    if (!name) return '';

                    const bare = stripDiacritics(name);
                    if (NOT_A_DOCTOR_NAME.indexOf(bare) !== -1) return '';
                    // "bác sĩ chuyên khoa tim mạch", "bác sĩ trực hôm nay" — mở đầu bằng cụm không phải tên.
                    if (NOT_A_DOCTOR_NAME.some(function(w) { return bare === w || bare.indexOf(w + ' ') === 0; })) return '';

                    return name;
                }

                /**
                 * Khách xin DỪNG việc đặt lịch.
                 *
                 * Chế độ gõ chữ trước đây không hề hiểu ý này (chỉ chế độ gọi có parseYesNo). Khi đang
                 * có pendingAlternatives, buildAlternativeContext ra lệnh cho model "TUYỆT ĐỐI KHÔNG
                 * hỏi lại khách chọn hướng nào", nên model buộc phải chốt một hướng và hệ thống mở
                 * trang đặt lịch — ngay sau khi khách vừa nói "thôi không đặt nữa".
                 *
                 * Đệm khoảng trắng, không dùng \b (ASCII-only, xem coding-conventions.md).
                 */
                function parseCancelIntent(text) {
                    const padded = ' ' + normalizeText(text).replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim() + ' ';
                    if (padded === '  ') return false;

                    // "thôi" đứng riêng cũng là từ chối, nhưng "thôi thì" / "thôi được" thì không.
                    if (/(hủy|huỷ|hủy đi|huỷ đi|hủy lịch|huỷ lịch)/.test(padded)) return true;
                    if (/không đặt nữa|khong dat nua|thôi không đặt|thoi khong dat|khỏi đặt|khoi dat/.test(padded)) return true;
                    if (/để sau|de sau|lúc khác|luc khac|dừng lại|dung lai|không cần nữa|khong can nua/.test(padded)) return true;
                    if (padded.indexOf(' thôi ') !== -1 && /không|khong|đừng|dung lai|khỏi/.test(padded)) return true;
                    return false;
                }

                /**
                 * Câu này có thật sự là YÊU CẦU đặt lịch không.
                 *
                 * Chỉ dò chuỗi "đặt lịch" là nhận nhầm hàng loạt câu HỎI VỀ việc đặt lịch:
                 * "phí đặt lịch bao nhiêu ạ?", "làm sao để hủy đặt lịch?", "em đặt lịch hôm qua mà
                 * chưa thấy xác nhận" — cả ba đều bị chạy hết luồng rồi mở trang đặt lịch.
                 */
                function looksLikeBookingRequest(text) {
                    const raw = normalizeText(text);
                    if (!/đặt lịch|chuyển sang đặt lịch|tiến hành khám|book lịch|book khám|đặt khám/.test(raw)) {
                        return false;
                    }
                    if (/phí|phi |giá|bao nhiêu|hủy|huỷ|làm sao|làm thế nào|thế nào|cách |đã đặt|hôm qua|quy trình/.test(raw)) {
                        return false;
                    }
                    return true;
                }

                /**
                 * Các cách gọi tên thứ, xếp CHUỖI DÀI TRƯỚC để "thứ hai" không bị "t2"… nuốt mất
                 * và để "chủ nhật" được thử trước "cn". Số là getDay() của JS (0 = Chủ nhật).
                 */
                const WEEKDAY_WORDS = [
                    { words: ['chủ nhật', 'chu nhat', 'cn'], day: 0 },
                    { words: ['thứ hai', 'thu hai', 'thứ 2', 'thu 2', 't2'], day: 1 },
                    { words: ['thứ ba', 'thu ba', 'thứ 3', 'thu 3', 't3'], day: 2 },
                    { words: ['thứ tư', 'thu tu', 'thứ 4', 'thu 4', 't4'], day: 3 },
                    { words: ['thứ năm', 'thu nam', 'thứ 5', 'thu 5', 't5'], day: 4 },
                    { words: ['thứ sáu', 'thu sau', 'thứ 6', 'thu 6', 't6'], day: 5 },
                    { words: ['thứ bảy', 'thu bay', 'thứ 7', 'thu 7', 't7'], day: 6 }
                ];

                /** Cộng n ngày vào hôm nay rồi trả về chuỗi ISO. */
                function isoAfterDays(n) {
                    const target = new Date();
                    target.setDate(target.getDate() + n);
                    return toIsoDate(target);
                }

                function extractDateHint(text) {
                    const normalized = normalizeText(text);
                    const today = new Date();
                    const padded = ' ' + normalized.replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim() + ' ';
                    const nextWeek = /tuần sau|tuan sau|tuần tới|tuan toi/.test(normalized);

                    // ===== TÊN THỨ — xét TRƯỚC "hôm nay"/"ngày mai" =====
                    // Tên thứ là chỉ định cụ thể hơn, nên nó phải thắng. Xét sau như trước đây thì
                    // "HÔM NAY em bận, đặt giúp em THỨ 5 nhé" trả về hôm nay: cụm chỉ thời điểm
                    // TRIỆU CHỨNG bị hiểu thành thời điểm KHÁM.
                    //
                    // Đệm khoảng trắng chứ KHÔNG dùng \b — \b trong JS chỉ hiểu chữ ASCII nên không
                    // bao giờ khớp được từ có dấu (xem coding-conventions.md).
                    for (const entry of WEEKDAY_WORDS) {
                        const hit = entry.words.some(function(w) { return padded.indexOf(' ' + w + ' ') !== -1; });
                        if (!hit) continue;

                        let delta;
                        if (nextWeek) {
                            // "thứ 2 tuần sau" = thứ Hai của TUẦN SAU, đếm từ thứ Hai tuần sau.
                            // Cách cũ lấy lần xuất hiện kế tiếp RỒI cộng thêm 7 nên dư đúng một tuần:
                            // hôm nay thứ Tư thì "thứ 2 tuần sau" ra 12 ngày thay vì 5.
                            // Tuần bắt đầu từ thứ Hai, cùng quy ước với LeavePolicy.weekStartOf.
                            const daysToNextMonday = (8 - today.getDay()) % 7 || 7;
                            delta = daysToNextMonday + (entry.day + 6) % 7;
                        } else {
                            delta = (entry.day - today.getDay() + 7) % 7;
                            // Khách nói tên thứ ĐÚNG BẰNG hôm nay: vẫn là hôm nay nếu buổi họ xin chưa
                            // trôi qua; 14h mà nói "sáng thứ ba" thì ý là thứ Ba TUẦN SAU.
                            if (delta === 0 && sessionAlreadyPassed(extractSessionHint(text))) {
                                delta = 7;
                            }
                        }
                        return isoAfterDays(delta);
                    }

                    if (normalized.includes('hôm nay')) {
                        return toIsoDate(today);
                    }
                    // Chỉ nhận các cụm CHẮC CHẮN là "ngày mai". Không bắt chữ "mai" đứng một mình:
                    // "Mai" là tên người rất phổ biến, "đặt lịch với bác sĩ Mai" mà hiểu thành ngày mai
                    // thì lại đúng kiểu tự suy diễn đang phải sửa.
                    if (/ngày mai|sáng mai|chiều mai|trưa mai|tối mai|hôm sau/.test(normalized)) {
                        return isoAfterDays(1);
                    }
                    // "ngày mốt" = ngày kia (cách nói miền Nam).
                    if (/ngày kia|ngay kia|ngày mốt|ngay mot/.test(normalized)) {
                        return isoAfterDays(2);
                    }
                    // "3 ngày nữa", "sau 2 ngày nữa"
                    const inDays = normalized.match(/(\d{1,2})\s*ngày\s*(?:nữa|sau)/);
                    if (inDays) {
                        const n = parseInt(inDays[1], 10);
                        if (n >= 1 && n <= 90) return isoAfterDays(n);
                    }
                    // "cuối tuần này" -> thứ Bảy gần nhất (T7 vẫn là ngày làm việc của phòng khám).
                    if (/cuối tuần|cuoi tuan/.test(normalized)) {
                        const toSaturday = (6 - today.getDay() + 7) % 7;
                        return isoAfterDays(nextWeek ? toSaturday + 7 : (toSaturday === 0 ? 0 : toSaturday));
                    }
                    // "tuần sau" đứng một mình (không kèm tên thứ) -> thứ Hai tuần sau.
                    if (nextWeek) {
                        return isoAfterDays((8 - today.getDay()) % 7 || 7);
                    }

                    // "mùng 5 tháng 8" / "ngày 5 tháng 8" — dạng viết chữ, không có dấu gạch chéo.
                    const wordDate = normalized.match(/(?:ngày|mùng|mồng|mung|mong)\s*(\d{1,2})\s*(?:tháng|thang)\s*(\d{1,2})/);
                    if (wordDate) {
                        return buildDateHint(parseInt(wordDate[1], 10), parseInt(wordDate[2], 10), null, today);
                    }

                    // Dạng "5/8" hoặc "5/8/2026".
                    //
                    // CHỈ nhận dấu "/" — người Việt viết ngày là 5/8, còn "-" gần như luôn là một
                    // KHOẢNG SỐ ĐO trong câu chuyện bệnh: "sốt 39-40 độ" từng thành ngày 39/40,
                    // "uống 2-3 lần/ngày" thành ngày 02/03. Và phải chặn số dính liền hai đầu, nếu
                    // không "huyết áp 120/80" khớp thành ngày 20/80.
                    const slashMatch = normalized.match(/(^|[^\d])(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?([^\d]|$)/);
                    if (slashMatch) {
                        return buildDateHint(parseInt(slashMatch[2], 10), parseInt(slashMatch[3], 10),
                            slashMatch[4] ? parseInt(slashMatch[4], 10) : null, today);
                    }

                    return '';
                }

                /**
                 * Dựng ngày ISO từ (ngày, tháng, năm?) và kiểm tra tính hợp lệ.
                 * Trả '' khi không phải một ngày thật — thà không có ngày còn hơn có ngày rác, vì ngày
                 * rác được ghi vào lastHandoffDate rồi tái dùng cho MỌI lượt sau đó.
                 */
                function buildDateHint(day, month, year, today) {
                    if (!(day >= 1 && day <= 31) || !(month >= 1 && month <= 12)) return '';

                    let resolvedYear = year === null ? today.getFullYear() : (year < 100 ? 2000 + year : year);
                    const iso = function(y) {
                        return y + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
                    };

                    // Khách không nêu năm mà ngày đã trôi qua thì ý là năm sau: nói "đặt lịch 3/1"
                    // vào tháng 12 nghĩa là mùng 3 tháng 1 SANG NĂM, không phải một ngày đã qua.
                    if (year === null && iso(resolvedYear) < toIsoDate(today)) {
                        resolvedYear += 1;
                    }

                    // Chốt lại bằng Date thật để loại 31/02, 30/02…
                    const check = new Date(resolvedYear, month - 1, day);
                    if (check.getMonth() !== month - 1 || check.getDate() !== day) return '';
                    return iso(resolvedYear);
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

                const DAY_LABELS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

                /** "2026-07-24" + "10:30 - 11:00" -> "T6 24/07 (10:30 - 11:00)" (đúng dạng translateDay ở AiController). */
                function buildSlotLabelFrom(isoDate, slot) {
                    const parts = (isoDate || '').split('-');
                    if (parts.length !== 3) return slot;
                    const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
                    return DAY_LABELS[d.getDay()] + ' ' + parts[2] + '/' + parts[1] + ' (' + slot + ')';
                }

                function formatDayMonth(isoDate) {
                    const parts = (isoDate || '').split('-');
                    return parts.length === 3 ? parts[2] + '/' + parts[1] : isoDate;
                }

                /** Câu giải thích VÌ SAO em gợi ý bác sĩ này — khách cần biết lý do, không chỉ cái tên. */
                function describeLoad(nearbyLoad) {
                    if (nearbyLoad === 0) return 'quanh giờ đó bác sĩ chưa có ca nào nên anh/chị gần như không phải chờ';
                    if (nearbyLoad <= 2) return 'quanh giờ đó bác sĩ chỉ có ' + nearbyLoad + ' ca nên anh/chị ít phải chờ';
                    return 'quanh giờ đó bác sĩ có ' + nearbyLoad + ' ca khám';
                }

                // Hai hướng vừa gợi ý cho khách, đang chờ khách chọn. Danh sách này do hệ thống
                // tra ra chứ KHÔNG nằm trong hội thoại, nên model hoàn toàn không biết gì về nó.
                let pendingAlternatives = null;

                function lastNameWord(fullName) {
                    const words = normalizeText(fullName).split(' ').filter(Boolean);
                    return words.length ? words[words.length - 1] : '';
                }

                /**
                 * Hiểu câu trả lời cho "anh/chị chọn hướng nào ạ?" ngay tại chỗ.
                 * Trả về { kind: 'doctor', doctor } | { kind: 'time', option } | null.
                 */
                function resolveAlternativeChoice(text, handoff) {
                    const alt = (handoff && handoff.alternatives) || {};
                    const sameTime = Array.isArray(alt.sameTimeDoctors) ? alt.sameTimeDoctors : [];
                    const otherTimes = Array.isArray(alt.otherTimes) ? alt.otherTimes : [];
                    if (!sameTime.length && !otherTimes.length) return null;

                    const raw = normalizeText(text).replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim();
                    if (!raw) return null;
                    const padded = ' ' + raw + ' ';

                    // Thứ tự in ra trong thẻ: (1) đổi bác sĩ giữ giờ, (2) giữ bác sĩ đổi giờ.
                    const directions = [];
                    if (sameTime.length) directions.push('doctor');
                    if (otherTimes.length) directions.push('time');

                    let want = null;

                    // 1. Cả câu chỉ là một con số / từ chỉ thứ tự.
                    //    ĐÃ BỎ "thứ hai"/"thu hai" khỏi nhóm này: từ khi extractDateHint hiểu tên thứ,
                    //    "thứ hai" là NGÀY chứ không phải "hướng số 2". Ý chọn hướng 2 vẫn bắt được
                    //    qua "hướng 2"/"cách 2" ở bước 2 và qua số "2" trần ngay dưới đây.
                    if (/^(1|một|mot|đầu tiên|dau tien|cái đầu|cai dau|cái đầu tiên|cai dau tien)$/.test(raw)) want = directions[0];
                    else if (/^(2|hai|cái sau|cai sau|cái thứ hai|cai thu hai)$/.test(raw)) want = directions[1];

                    // 2. "hướng 1", "cách 2", "chọn 1", "lấy cái 1". Chặn con số của giờ giấc
                    //    ("chọn 10 giờ") bằng cách chỉ nhận số đứng ngay sau từ chỉ lựa chọn và
                    //    không dính h/:/giờ.
                    if (!want) {
                        const ordinal = raw.match(/(?:hướng|huong|cách|cach|phương án|phuong an|option|số|so|chọn|chon|lấy|lay|cái|cai)\s*(\d)(?!\s*(?:\d|:|h|giờ|gio|rưỡi|ruoi))/);
                        if (ordinal) want = directions[parseInt(ordinal[1], 10) - 1] || null;
                    }

                    // 2b. "cái nào cũng được" / "sớm nhất có thể" / "ok" / "vâng" -> nhận hướng đầu tiên.
                    //     Trước đây các câu này rơi xuống model KÈM mệnh lệnh "TUYỆT ĐỐI KHÔNG hỏi lại",
                    //     nên model buộc phải đoán bừa một hướng.
                    if (!want && /^(ok|okay|oke|vâng|vang|dạ|da|ừ|u|uh|được|duoc|đồng ý|dong y|nhất trí)( ạ| a| em| nhé| nhe)?$/.test(raw)) {
                        want = directions[0];
                    }
                    if (!want && /(nào cũng được|nao cung duoc|cái nào cũng|sao cũng được|sao cung duoc|sớm nhất|som nhat|gần nhất|gan nhat|tùy em|tuy em|em chọn giúp|em chon giup)/.test(raw)) {
                        want = directions[0];
                    }

                    // 3. Gọi đích danh một bác sĩ trong danh sách gợi ý.
                    //    Dò cả otherTimes: khách đọc tên bác sĩ mình muốn GIỮ thì ý là đổi giờ.
                    if (!want) {
                        for (let i = 0; i < sameTime.length; i++) {
                            const given = lastNameWord(sameTime[i].fullName);
                            if (given && padded.indexOf(' ' + given + ' ') !== -1) {
                                return { kind: 'doctor', doctor: sameTime[i] };
                            }
                        }
                        const keptName = lastNameWord(alt.requestedDoctorName || (handoff && handoff.doctorName) || '');
                        if (keptName && padded.indexOf(' ' + keptName + ' ') !== -1 && otherTimes.length) {
                            want = 'time';
                        }
                    }

                    // 4. Đọc thẳng một khung giờ có trong danh sách.
                    //    Khớp theo giờ bắt đầu là đủ vì server chỉ trả gợi ý của MỘT ngày duy nhất
                    //    (dừng ở ngày làm việc đầu tiên tìm được), nên không có hai mục trùng giờ.
                    if (!want) {
                        const spokenTime = normalizeTimeHint(text);
                        if (spokenTime) {
                            const hit = otherTimes.find(function(o) { return slotStartTime(o.slot) === spokenTime; });
                            if (hit) return { kind: 'time', option: hit };
                        }
                    }

                    // 5. Nói thẳng ý định. Ba bước, và THỨ TỰ NÀY LÀ CỐ Ý:
                    //
                    //    (a) "giữ bác sĩ" — câu này cũng chứa chữ "bác sĩ" nên phải xét trước (b),
                    //        nếu không sẽ bị hiểu ngược thành đổi bác sĩ.
                    //    (b) có nhắc "bác sĩ" + từ chỉ sự thay đổi -> ĐỔI BÁC SĨ. Phải đứng TRƯỚC (c):
                    //        "đổi LỊCH sang BÁC SĨ khác" và "DỜI sang bác sĩ khác" đều khớp cụm
                    //        "đổi lịch"/"dời" ở (c), nên xét (c) trước là giữ nguyên bác sĩ cũ rồi
                    //        đổi giờ — ngược hẳn ý khách, mà thẻ còn ghi "em giữ bác sĩ X".
                    //    (c) còn lại mà nói tới giờ giấc -> đổi giờ.
                    if (!want) {
                        if (/giữ bác sĩ|giu bac si|vẫn bác sĩ|van bac si|bác sĩ này|bac si nay|bác sĩ cũ|bac si cu/.test(raw)) {
                            want = 'time';
                        } else if (/bác sĩ|bac si/.test(raw) && /đổi|doi|chuyển|chuyen|khác|khac|chọn|chon|thay/.test(raw)) {
                            want = 'doctor';
                        } else if (/đổi giờ|doi gio|đổi lịch|doi lich|dời|doi sang gio|giờ khác|gio khac|khung giờ khác|hôm khác|hom khac|ngày khác|ngay khac/.test(raw)) {
                            want = 'time';
                        }
                    }

                    if (want === 'doctor' && sameTime.length) return { kind: 'doctor', doctor: sameTime[0] };
                    if (want === 'time' && otherTimes.length) return { kind: 'time', option: otherTimes[0] };
                    return null;
                }

                /**
                 * Biến lựa chọn của khách thành một handoff hoàn chỉnh (đã hết fallback).
                 *
                 * Mỗi gợi ý mang theo `date` của RIÊNG nó: khi bác sĩ nghỉ nguyên ngày khách xin,
                 * server đã quét sang ngày làm việc gần nhất. Lấy ngày của handoff cũ ở đây là đặt
                 * đúng giờ nhưng sai ngày.
                 */
                function handoffFromAlternative(prev, choice) {
                    if (choice.kind === 'doctor') {
                        const d = choice.doctor;
                        const date = d.date || prev.appointmentDate;
                        return {
                            doctor: { id: d.id, fullName: d.fullName, avatar: d.avatar },
                            doctorName: d.fullName,
                            appointmentDate: date,
                            appointmentTime: d.slot,
                            appointmentUrl: buildAppointmentUrl(d.id, date, d.slot),
                            selectedSlotLabel: d.slotLabel || buildSlotLabelFrom(date, d.slot),
                            requestedTime: prev.requestedTime,
                            requestedSession: prev.requestedSession,
                            fallback: false,
                            suggested: false,
                            alternatives: null
                        };
                    }

                    const o = choice.option;
                    const date = o.date || prev.appointmentDate;
                    return {
                        doctor: { id: o.doctorId, fullName: o.fullName },
                        doctorName: o.fullName,
                        appointmentDate: date,
                        appointmentTime: o.slot,
                        appointmentUrl: buildAppointmentUrl(o.doctorId, date, o.slot),
                        selectedSlotLabel: o.slotLabel || buildSlotLabelFrom(date, o.slot),
                        requestedTime: prev.requestedTime,
                        requestedSession: prev.requestedSession,
                        fallback: false,
                        suggested: false,
                        alternatives: null
                    };
                }

                /** Nhét danh sách vừa gợi ý vào lượt hỏi tiếp theo, để model không trả lời mù. */
                function buildAlternativeContext(handoff) {
                    const alt = handoff.alternatives || {};
                    const sameTime = Array.isArray(alt.sameTimeDoctors) ? alt.sameTimeDoctors : [];
                    const otherTimes = Array.isArray(alt.otherTimes) ? alt.otherTimes : [];

                    const lines = ['[NGỮ CẢNH HỆ THỐNG — không đọc lại nguyên văn cho khách]'];
                    // Nói cho model biết LÝ DO THẬT, không chỉ "đã kín": bác sĩ nghỉ buổi đó và
                    // khung có người đặt là hai chuyện khác nhau, khách hỏi lại thì phải trả lời đúng.
                    lines.push(alt.reasonText
                        || ('Khung giờ ' + (handoff.requestedTime || '') + ' ngày ' + handoff.appointmentDate + ' ĐÃ KÍN.'));
                    if (sameTime.length) {
                        lines.push('Hướng 1 — đổi bác sĩ, giữ nguyên giờ: '
                            + sameTime.map(function(d) { return d.fullName + ' (' + (d.slotLabel || d.slot) + ')'; }).join('; '));
                    }
                    if (otherTimes.length) {
                        // slotLabel có kèm thứ/ngày — gợi ý có thể đã rơi sang ngày khác.
                        lines.push('Hướng 2 — giữ bác sĩ ' + (alt.requestedDoctorName || handoff.doctorName || '') + ', dời sang: '
                            + otherTimes.map(function(o) { return o.slotLabel || o.slot; }).join('; '));
                    }
                    lines.push('Khách đang trả lời câu hỏi "anh/chị chọn hướng nào ạ?". '
                        + 'Hãy điền booking_target đúng theo lựa chọn của khách (doctor_name, appointment_date, appointment_time) '
                        + 'và TUYỆT ĐỐI KHÔNG hỏi lại khách chọn hướng nào.');
                    return lines.join('\n');
                }

                /** Câu mô tả điều khách vừa xin, dùng khi server không trả về lý do cụ thể. */
                function describeWanted(handoff) {
                    if (handoff.requestedTime) return 'Khung giờ ' + handoff.requestedTime;
                    if (handoff.requestedSession === 'morning') return 'Buổi sáng';
                    if (handoff.requestedSession === 'afternoon') return 'Buổi chiều';
                    return 'Khung giờ anh/chị yêu cầu';
                }

                /**
                 * Thẻ "không đặt được khung giờ này" — thay cho việc âm thầm đẩy khách sang giờ khác.
                 * Nói thẳng LÝ DO (do server trả về ở reasonText: bác sĩ không có ca khám buổi đó,
                 * đã có người đặt, bác sĩ báo bận…), rồi đưa hai hướng: đổi bác sĩ mà giữ giờ, hoặc
                 * giữ bác sĩ mà đổi giờ. Mỗi bác sĩ gợi ý đều kèm lý do vì sao em chọn người đó.
                 */
                function buildSlotFullHtml(handoff) {
                    const alt = handoff.alternatives || {};
                    const wantedTime = handoff.requestedTime || 'khung giờ anh/chị yêu cầu';
                    const sameTime = Array.isArray(alt.sameTimeDoctors) ? alt.sameTimeDoctors : [];
                    const otherTimes = Array.isArray(alt.otherTimes) ? alt.otherTimes : [];

                    // Lý do thật từ server. Không có (endpoint lỗi) thì mới nói chung chung "đã kín".
                    const headline = alt.reasonText
                        || (describeWanted(handoff) + ' ngày ' + formatDayMonth(handoff.appointmentDate) + ' đã kín lịch rồi ạ.');

                    let html = `<div class="mt-3 p-3" style="background:#fff8e1;border-left:4px solid #ffc107;border-radius:8px;">
                        <div class="fw-bold mb-2" style="color:#b8860b;">
                            <i class="bi bi-clock-history"></i> ${headline}
                        </div>`;

                    if (sameTime.length > 0) {
                        html += `<div style="font-size:13px;color:#334155;margin-bottom:6px;">
                            Em gợi ý anh/chị mấy bác sĩ cùng chuyên khoa vẫn còn nhận <strong>${wantedTime}</strong>:
                        </div>`;
                        sameTime.forEach(function(doc) {
                            html += `<div class="d-flex align-items-center gap-2 mb-2 p-2" style="background:#fff;border-radius:6px;">
                                <img src="${doc.avatar}" alt="" style="width:38px;height:38px;border-radius:50%;object-fit:cover;">
                                <div style="flex:1;font-size:13px;color:#334155;">
                                    <div><strong>${doc.fullName}</strong>${doc.degree ? ' — ' + doc.degree : ''}</div>
                                    <div style="color:#64748b;">${doc.slotLabel} — em gợi ý bác sĩ này vì ${describeLoad(doc.nearbyLoad)}.</div>
                                </div>
                                <a href="${buildAppointmentUrl(doc.id, doc.date || handoff.appointmentDate, doc.slot)}"
                                   class="btn btn-sm btn-primary">Chọn</a>
                            </div>`;
                        });
                    }

                    if (otherTimes.length > 0) {
                        const keepName = alt.requestedDoctorName || handoff.doctorName || 'bác sĩ hiện tại';
                        // Gợi ý có thể rơi sang NGÀY KHÁC (bác sĩ nghỉ nguyên ngày khách xin), nên nút
                        // phải lấy `item.date` của chính nó và hiện nhãn có thứ/ngày. Dùng ngày của
                        // handoff ở đây là đặt đúng giờ nhưng sai ngày.
                        const movedDay = otherTimes[0].date && otherTimes[0].date !== handoff.appointmentDate;
                        html += `<div style="font-size:13px;color:#334155;margin:8px 0 6px;">
                            Hoặc anh/chị vẫn giữ <strong>${keepName}</strong> và dời sang
                            ${movedDay ? 'ngày bác sĩ có ca khám gần nhất' : 'khung giờ gần nhất'}:
                        </div><div class="d-flex flex-wrap gap-2">`;
                        otherTimes.forEach(function(item) {
                            html += `<a href="${buildAppointmentUrl(item.doctorId, item.date || handoff.appointmentDate, item.slot)}"
                                        class="btn btn-sm btn-outline-primary">${item.slotLabel || item.slot}</a>`;
                        });
                        html += `</div>`;
                    }

                    if (sameTime.length === 0 && otherTimes.length === 0) {
                        html += `<div style="font-size:13px;color:#334155;">
                            Em chưa tìm được khung giờ nào thay thế trong tuần này ạ. Anh/chị thử chọn
                            bác sĩ khác, hoặc mở trang đặt lịch để xem toàn bộ khung giờ giúp em nhé.
                        </div>
                        <a href="/doctors" class="btn btn-sm btn-primary mt-2">Xem danh sách bác sĩ</a>`;
                    }

                    return html + `</div>`;
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
                 * Ở đây chỉ khớp theo RANH GIỚI TỪ.
                 *
                 * So khớp KHÔNG DẤU (stripDiacritics) để khách gõ "bac si binh" vẫn ra "Trần Văn Bình".
                 * Bỏ dấu chỉ nới phần CHÍNH TẢ, luật ranh giới từ giữ nguyên.
                 *
                 * Ba kết quả có thể trả về:
                 *   - object bác sĩ  : chắc chắn đúng một người
                 *   - {ambiguous}    : nhiều người cùng khớp -> PHẢI hỏi lại khách
                 *   - null           : không ai khớp
                 *
                 * Nhánh `ambiguous` là bắt buộc: bệnh viện có hai bác sĩ tên Bình thì "đặt lịch với
                 * bác sĩ Bình" trước đây lặng lẽ lấy người đầu danh sách — đúng kiểu đặt nhầm người
                 * mà cả hàm này sinh ra để chống.
                 */
                function pickBestDoctorMatch(doctors, requestedName) {
                    const want = stripDiacritics(requestedName);
                    if (!want) return null;

                    const wantWords = want.split(' ').filter(Boolean);
                    if (wantWords.length === 0) return null;
                    const wantLast = wantWords[wantWords.length - 1];
                    const wordsOf = function(doc) {
                        return stripDiacritics(doc.fullName).split(' ').filter(Boolean);
                    };
                    const ambiguous = function(list) {
                        return { ambiguous: true, candidates: list.slice(0, 4) };
                    };

                    // 1. Trùng khít cả họ và tên
                    const exact = doctors.filter(function(doc) {
                        return stripDiacritics(doc.fullName) === want;
                    });
                    if (exact.length === 1) return exact[0];
                    if (exact.length > 1) return ambiguous(exact);

                    // 2. Trùng tên gọi — từ cuối trong họ tên tiếng Việt ("bác sĩ Bình" -> "Trần Văn Bình")
                    const byGivenName = doctors.filter(function(doc) {
                        const words = wordsOf(doc);
                        return words.length > 0 && words[words.length - 1] === wantLast;
                    });
                    if (byGivenName.length === 1) return byGivenName[0];

                    // 3. Chứa đủ mọi từ khách nói, xét theo từ nguyên vẹn
                    const byAllWords = doctors.filter(function(doc) {
                        const words = wordsOf(doc);
                        return wantWords.every(function(w) { return words.indexOf(w) !== -1; });
                    });
                    if (byAllWords.length === 1) return byAllWords[0];

                    if (byGivenName.length > 1) return ambiguous(byGivenName);
                    if (byAllWords.length > 1) return ambiguous(byAllWords);
                    return null;
                }

                // Ngày của lịch hẹn vừa chốt gần nhất — xem chỗ dùng bên dưới.
                let lastHandoffDate = '';

                /**
                 * Hỏi server xem còn cách nào cho khung giờ khách vừa xin: bác sĩ cùng khoa
                 * còn trống ĐÚNG giờ đó (ưu tiên người ít ca khám quanh giờ đó nhất), hoặc
                 * khung giờ gần nhất của chính bác sĩ khách đang nhắm tới.
                 */
                async function fetchSlotAlternatives(departmentId, date, time, doctorId, session) {
                    try {
                        const url = new URL('/api/chat/slot-alternatives', window.location.origin);
                        url.searchParams.set('departmentId', departmentId);
                        url.searchParams.set('date', date);
                        if (time) url.searchParams.set('time', time);
                        if (session) url.searchParams.set('session', session);
                        if (doctorId) url.searchParams.set('doctorId', doctorId);
                        if (sessionId) url.searchParams.set('sessionId', sessionId);

                        const res = await fetch(url.toString());
                        if (!res.ok) return null;
                        const data = await res.json();
                        // GIỮ payload khi có `reason`, dù không còn hướng nào thay thế: chính cái
                        // lý do đó ("hôm đó bác sĩ không có ca khám") mới là thứ khách cần nghe.
                        // Trước đây chỉ giữ khi có gợi ý nên lý do thật bị vứt đi.
                        const hasAny = data.reason
                            || data.requestedDoctorFree
                            || (data.sameTimeDoctors && data.sameTimeDoctors.length)
                            || (data.otherTimes && data.otherTimes.length);
                        return hasAny ? data : null;
                    } catch (err) {
                        console.error(err);
                        return null;
                    }
                }

                /**
                 * Khung giờ này còn đặt được không — hỏi thẳng nguồn sự thật.
                 *
                 * /api/bookings/booked-slots tôn trọng lịch làm việc (ca khám bác sĩ đăng ký), giờ đã
                 * đặt và giờ bác sĩ tự chặn. `availableSlots` trong danh sách bác sĩ chỉ là 4 khung
                 * XEM TRƯỚC của ngày gần nhất, tuyệt đối không dùng để chốt.
                 *
                 * CHỈ được gọi với khung giờ do SERVER sinh ra (nhãn xem trước). Nó KHÔNG kiểm tra
                 * khung giờ có nằm trong lưới hay không: booked-slots chỉ liệt kê các khung trong
                 * lưới, nên một chuỗi tự chế như "10:20 - 10:50" hay "19:00 - 19:30" sẽ không khớp
                 * mục bận nào và bị coi là còn trống. Giờ do KHÁCH nêu phải đi qua
                 * /api/chat/slot-alternatives (nhánh A), nơi server đối chiếu với lưới thật.
                 *
                 * So sánh theo GIỜ BẮT ĐẦU chứ không indexOf — xem chú thích ở slotStartTime().
                 */
                async function isSlotBookable(doctorId, isoDate, slotRange) {
                    if (!doctorId || !isoDate || !slotRange) return false;
                    try {
                        const res = await fetch('/api/bookings/booked-slots?doctorId=' + doctorId
                            + '&date=' + encodeURIComponent(isoDate));
                        if (!res.ok) return false;
                        const unavailable = await res.json();
                        if (!Array.isArray(unavailable)) return true;

                        const wantedStart = slotStartTime(slotRange);
                        return unavailable.every(function(slot) { return slotStartTime(slot) !== wantedStart; });
                    } catch (err) {
                        console.error(err);
                        return false;
                    }
                }

                /** Một chỗ duy nhất dựng handoff, để 4 nhánh khỏi trôi dạt mỗi nơi một kiểu. */
                function makeHandoff(opts) {
                    return {
                        doctor: opts.doctor,
                        doctorName: opts.doctorName || (opts.doctor && opts.doctor.fullName) || '',
                        appointmentDate: opts.date,
                        appointmentTime: opts.slot,
                        appointmentUrl: buildAppointmentUrl(opts.doctor.id, opts.date, opts.slot),
                        selectedSlotLabel: opts.label || buildSlotLabelFrom(opts.date, opts.slot),
                        requestedTime: opts.requestedTime || '',
                        requestedSession: opts.requestedSession || '',
                        fallback: !!opts.fallback,
                        suggested: !!opts.suggested,
                        alternatives: opts.alternatives || null
                    };
                }

                async function resolveBookingHandoff(aiData, userText) {
                    // Model là nguồn chính; nhánh dò chữ chỉ để vớt khi model bỏ sót, và phải loại
                    // các câu HỎI VỀ việc đặt lịch (xem looksLikeBookingRequest).
                    const bookingIntent = aiData
                        && (aiData.booking_intent === true || looksLikeBookingRequest(userText));
                    if (!bookingIntent) return null;

                    const bookingTarget = aiData.booking_target || {};
                    const deptIds = Array.isArray(aiData.recommended_departments)
                        ? aiData.recommended_departments.filter(function(id) { return id !== null && id !== undefined; })
                        : [];

                    const requestedDoctorName = (bookingTarget.doctor_name || extractDoctorName(userText) || '').trim();
                    // MONG MUỐN CỦA KHÁCH LẤY TỪ CHÍNH CÂU KHÁCH NÓI, không lấy từ model.
                    // Model bịa rất nhiều: khách nói "sáng thứ 3" thì nó điền appointment_time
                    // "09:00 - 11:00", khách nói "chuyển sang buổi chiều" thì nó điền "13:30 - 17:30".
                    // Tin mấy con số đó là chốt một khung giờ khách chưa hề chọn.
                    const textTime = normalizeTimeHint(userText);
                    const textSession = extractSessionHint(userText);

                    // Khách nêu BUỔI mà không nêu giờ -> buổi thắng, bỏ hẳn giờ model bịa ra. Chỉ khi
                    // câu của khách không có cả giờ lẫn buổi mới dùng giờ của model (lúc đó nó thường
                    // chỉ lặp lại khung giờ đã chốt ở lượt trước).
                    const requestedTime = textTime
                        || (textSession ? '' : normalizeTimeHint(bookingTarget.appointment_time || ''));
                    // Buổi chỉ có nghĩa khi khách CHƯA nói giờ cụ thể ("3 giờ chiều" đã thành 15:00).
                    const requestedSession = textTime ? '' : textSession;

                    // Ngày cũng vậy: câu của khách trước, rồi tới ngày đã chốt ở lượt trước, CUỐI CÙNG
                    // mới tới model. Trình duyệt biết hôm nay là ngày mấy, model thì không — nó từng
                    // trả "ngày 29 tháng 7" cho "thứ Ba" trong khi thứ Ba là 28/07.
                    const textDate = extractDateHint(userText);
                    let requestedDate = textDate || '';
                    // Ngày khách THẬT SỰ vừa nêu, KHÔNG tính ngày mượn lại của lượt trước — đây là
                    // căn cứ để biết có phải hệ thống đang đổi ngày sau lưng khách hay không.
                    const dateExplicit = !!requestedDate;

                    // Câu sửa ("đổi thành 10h30", "sang buổi chiều") không mang theo ngày. Ngày đã chốt
                    // ở lượt trước là ngày hệ thống ĐÃ tra và ĐÃ in cho khách xem, nên đáng tin hơn hẳn
                    // ngày model tự nghĩ ra.
                    if (!requestedDate && lastHandoffDate && lastHandoffDate >= toIsoDate(new Date())) {
                        requestedDate = lastHandoffDate;
                    }
                    if (!requestedDate) {
                        requestedDate = bookingTarget.appointment_date || '';
                    }
                    const requestedDepartmentId = bookingTarget.department_id || deptIds[0] || null;

                    let doctorSearchResult = null;
                    // Có gọi được API tra bác sĩ hay không. PHẢI phân biệt với "gọi được nhưng không
                    // ai khớp tên": nuốt lỗi mạng rồi kết luận doctorNotFound khiến khách nhận
                    // "Em chưa tìm thấy bác sĩ X" và gõ lại tên đúng nhiều lần vô ích, trong khi
                    // nguyên nhân thật là hạ tầng.
                    let doctorLookupReached = false;
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
                                if (Array.isArray(doctors)) {
                                    doctorLookupReached = true;
                                    if (doctors.length > 0) {
                                        doctorSearchResult = pickBestDoctorMatch(doctors, requestedDoctorName);
                                    }
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
                                    if (Array.isArray(fallbackDoctors)) {
                                        doctorLookupReached = true;
                                        if (fallbackDoctors.length > 0) {
                                            doctorSearchResult = pickBestDoctorMatch(fallbackDoctors, requestedDoctorName);
                                        }
                                    }
                                }
                            } catch (err) {
                                console.error(err);
                            }
                        }

                        if (!doctorLookupReached) {
                            return { error: 'NETWORK' };
                        }
                        // Nhiều bác sĩ cùng khớp tên khách nói (bệnh viện có 2 bác sĩ tên Bình).
                        // HỎI LẠI, tuyệt đối không tự chọn — xem pickBestDoctorMatch.
                        if (doctorSearchResult && doctorSearchResult.ambiguous) {
                            return {
                                doctorAmbiguous: true,
                                requestedDoctorName: requestedDoctorName,
                                candidates: doctorSearchResult.candidates
                            };
                        }
                    }

                    let candidateDepartmentId = requestedDepartmentId;
                    let candidateDoctorId = doctorSearchResult ? doctorSearchResult.id : (bookingTarget.doctor_id || null);
                    let candidateDoctorName = doctorSearchResult ? doctorSearchResult.fullName : requestedDoctorName;
                    let availableDoctors = [];

                    if (candidateDoctorId && doctorSearchResult && doctorSearchResult.departmentId) {
                        candidateDepartmentId = doctorSearchResult.departmentId;
                    }

                    let deptLookupReached = false;
                    if (candidateDepartmentId) {
                        try {
                            // Gửi kèm doctorId để backend ghim bác sĩ được chỉ đích danh lên đầu,
                            // không bị .limit(3) cắt mất.
                            let deptUrl = '/api/chat/doctors/department/' + candidateDepartmentId + '?sessionId=' + encodeURIComponent(sessionId);
                            if (candidateDoctorId) deptUrl += '&doctorId=' + encodeURIComponent(candidateDoctorId);
                            const deptRes = await fetch(deptUrl);
                            if (deptRes.ok) {
                                const parsed = await deptRes.json();
                                if (Array.isArray(parsed)) {
                                    deptLookupReached = true;
                                    availableDoctors = parsed;
                                }
                            }
                        } catch (err) {
                            console.error(err);
                        }
                    }

                    // Trả null ở đây là IM LẶNG HOÀN TOÀN: khách nói "đặt lịch giúp em" rồi không
                    // nhận được thẻ nào, không lời giải thích nào. Nói rõ đang hỏng ở đâu.
                    if (!candidateDepartmentId) {
                        return { error: 'NO_DEPARTMENT' };
                    }
                    if (!deptLookupReached) {
                        return { error: 'NETWORK' };
                    }
                    if (!availableDoctors || availableDoctors.length === 0) {
                        return { error: 'NO_DOCTORS' };
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
                    if (!selectedDoctor) return { error: 'NO_DOCTORS' };

                    // /slot-alternatives BẮT BUỘC có departmentId; thiếu nó thì askAlternatives trả
                    // null và khách mất luôn phần lý do. Model hay điền department_id theo triệu
                    // chứng của lượt trước nên nó có thể lệch với khoa của bác sĩ vừa chọn — lấy
                    // khoa của chính bác sĩ mới là đúng.
                    if (selectedDoctor.departmentId) {
                        candidateDepartmentId = selectedDoctor.departmentId;
                    }

                    // CỐ Ý KHÔNG bỏ cuộc khi availableSlots rỗng. Từ khi danh sách bác sĩ lọc theo ca
                    // khám đã đăng ký, bác sĩ nghỉ cả tuần sẽ cho preview rỗng — bỏ cuộc ở đây thì
                    // khách không nhận được thẻ nào, tức là lại im lặng theo một kiểu khác. Cứ đi tiếp
                    // để nhánh dưới hỏi /slot-alternatives và NÓI RA lý do.
                    const previewSlots = Array.isArray(selectedDoctor.availableSlots)
                        ? selectedDoctor.availableSlots : [];
                    const doctorName = candidateDoctorName || selectedDoctor.fullName;

                    const baseOpts = {
                        doctor: selectedDoctor,
                        doctorName: doctorName,
                        requestedTime: requestedTime,
                        requestedSession: requestedSession
                    };

                    /** Hỏi server các hướng thay thế + LÝ DO, rồi dựng thẻ "không đặt được". */
                    const askAlternatives = async function(date) {
                        const alternatives = candidateDepartmentId
                            ? await fetchSlotAlternatives(candidateDepartmentId, date, requestedTime,
                                selectedDoctor.id, requestedSession)
                            : null;

                        // Hoá ra bác sĩ này vẫn nhận đúng điều khách xin: danh sách xem trước chỉ lấy
                        // 4 khung giờ của ngày gần nhất nên đã cắt mất khung đó.
                        if (alternatives && alternatives.requestedDoctorFree && alternatives.slot) {
                            lastHandoffDate = date;
                            return makeHandoff(Object.assign({}, baseOpts, {
                                date: date, slot: alternatives.slot, fallback: false
                            }));
                        }

                        lastHandoffDate = date;
                        return makeHandoff(Object.assign({}, baseOpts, {
                            date: date,
                            slot: alternatives && alternatives.slot ? alternatives.slot : '',
                            label: '',
                            fallback: true,
                            alternatives: alternatives
                        }));
                    };

                    // Ngày của khung giờ xem trước — dùng khi khách nêu giờ mà không nêu ngày nào cả.
                    const previewInfo = previewSlots.length ? parseSlotLabel(previewSlots[0]) : null;

                    // ===== NHÁNH A — khách nêu GIỜ CỤ THỂ =====
                    if (requestedTime) {
                        // Không có ngày thì lấy ngày của khung xem trước (ngày gần nhất bác sĩ còn
                        // nhận); vẫn không có thì hôm nay — server sẽ trả lý do PAST nếu đã muộn.
                        const date = requestedDate
                            || (previewInfo && previewInfo.appointmentDate)
                            || toIsoDate(new Date());

                        // Giao HẲN cho /slot-alternatives, KHÔNG tự dựng khung giờ rồi hỏi
                        // booked-slots nữa. Hai lý do, cả hai đều từng làm khách đặt nhầm:
                        //
                        // 1. Khung giờ cũ dựng từ `bookingTarget.appointment_time` — tức là GIỜ MODEL
                        //    BỊA. Khách xin 10h30, model điền "09:00 - 11:00", hệ thống chốt 09:00 và
                        //    vẫn in "khung giờ anh/chị vừa chọn". Xem chú thích ở đầu hàm.
                        // 2. booked-slots chỉ liệt kê các khung TRONG lưới, nên mọi giờ NGOÀI lưới
                        //    ("10h20", "7 giờ tối", "12h trưa") không nằm trong danh sách bận và bị
                        //    coi là còn trống -> chốt một khung không hề tồn tại, rồi trang đặt lịch
                        //    mở ra không khung nào được chọn.
                        //
                        // Server biết lưới khung giờ (resolveCanonicalSlot) nên trả OUTSIDE_HOURS khi
                        // giờ nằm ngoài, quy giờ lẻ về khung chứa nó, và trả FREE + khung chuẩn khi
                        // còn trống. Nhờ vậy trình duyệt KHÔNG cần biết lưới -> không phát sinh nơi
                        // khai báo lưới khung giờ thứ 12 (xem /skills/sync-slot-grid).
                        return await askAlternatives(date);
                    }

                    // ===== NHÁNH B — khách nêu BUỔI và/hoặc NGÀY, chưa nêu giờ =====
                    if (requestedSession || (dateExplicit && requestedDate)) {
                        const date = requestedDate || toIsoDate(new Date());
                        const alternatives = candidateDepartmentId
                            ? await fetchSlotAlternatives(candidateDepartmentId, date, '',
                                selectedDoctor.id, requestedSession)
                            : null;

                        // Bác sĩ còn chỗ đúng buổi/ngày khách xin -> chốt khung sớm nhất của buổi đó.
                        // Vẫn là suggested: khách mới chỉ chọn BUỔI, còn khung giờ cụ thể là em chọn
                        // giúp. Ghi "khung giờ anh/chị vừa chọn" ở đây là nói vống.
                        if (alternatives && alternatives.requestedDoctorFree && alternatives.slot) {
                            lastHandoffDate = date;
                            return makeHandoff(Object.assign({}, baseOpts, {
                                date: date, slot: alternatives.slot, fallback: false, suggested: true
                            }));
                        }

                        lastHandoffDate = date;
                        return makeHandoff(Object.assign({}, baseOpts, {
                            date: date,
                            slot: alternatives && alternatives.slot ? alternatives.slot : '',
                            label: '',
                            fallback: true,
                            alternatives: alternatives
                        }));
                    }

                    // ===== NHÁNH C — khách KHÔNG nêu mong muốn nào =====
                    // Được phép chọn giúp, nhưng phải nói rõ là EM chọn (suggested), và vẫn kiểm tra
                    // lại khung giờ: preview có thể cũ tới 3 phút theo TTL của soft-lock.
                    // Bác sĩ không còn khung nào trong tầm quét: vẫn phải hỏi server để NÓI RA lý do
                    // (nghỉ cả tuần, kín lịch…). Trả null ở đây là im lặng theo một kiểu khác.
                    if (previewSlots.length === 0) {
                        return await askAlternatives(requestedDate || toIsoDate(new Date()));
                    }

                    const suggestedLabel = previewSlots[0];
                    if (!previewInfo) {
                        return await askAlternatives(requestedDate || toIsoDate(new Date()));
                    }

                    const suggestedDate = previewInfo.appointmentDate;
                    const suggestedSlot = previewInfo.appointmentTime;

                    if (!(await isSlotBookable(selectedDoctor.id, suggestedDate, suggestedSlot))) {
                        return await askAlternatives(suggestedDate);
                    }

                    lastHandoffDate = suggestedDate;
                    return makeHandoff(Object.assign({}, baseOpts, {
                        date: suggestedDate,
                        slot: suggestedSlot,
                        label: suggestedLabel,
                        fallback: false,
                        suggested: true
                    }));
                }

        // ==========================================
        // 1. SESSION MANAGEMENT
        // ==========================================
        let sessionId = safeStorage.get('meditrust_session_id');
        // [THÊM MỚI] 1.1 KIỂM TRA ĐĂNG NHẬP/ĐĂNG XUẤT ĐỂ LÀM SẠCH CHAT
                const userIdInput = document.getElementById('current-user-id');
                const currentUserId = userIdInput ? userIdInput.value : 'guest';
                const savedUserId = safeStorage.get('meditrust_user_id');

                if (savedUserId && savedUserId !== currentUserId) {
                    // Đã đổi tài khoản -> Xóa trắng rác cũ
                    safeStorage.remove('meditrust_session_id');
                    safeStorage.remove('meditrust_chat_html');
                    safeStorage.remove('meditrust_chat_state');
                    safeStorage.remove('meditrust_last_activity');
                    // Phải bỏ luôn biến đang giữ trong bộ nhớ: sessionId đã được đọc ở trên rồi,
                    // chỉ xóa sessionStorage thì phiên cũ vẫn tiếp tục được dùng.
                    sessionId = null;
                }
                safeStorage.set('meditrust_user_id', currentUserId);

                // [THÊM MỚI] 1.2 KIỂM TRA HẾT HẠN PHIÊN CHAT (Quá 60 phút không chat -> Tự xóa)
                const SESSION_TIMEOUT_MINUTES = 60;
                const lastActivityStr = safeStorage.get('meditrust_last_activity');
                if (lastActivityStr) {
                    const minutesPassed = (new Date().getTime() - parseInt(lastActivityStr, 10)) / (1000 * 60);
                    if (minutesPassed > SESSION_TIMEOUT_MINUTES) {
                        safeStorage.remove('meditrust_session_id');
                        safeStorage.remove('meditrust_chat_html');
                        sessionId = null;   // xem chú thích ở nhánh đổi tài khoản phía trên
                        // Lưu ý: Không xóa 'meditrust_chat_state' để giữ nguyên trạng thái đóng/mở UI
                    }
                }
        if (!sessionId) {
            sessionId = 'session_' + Math.random().toString(36).substr(2, 9);
            // Tên khóa phải trùng KHÍT với chỗ đọc ở trên. Trước đây khóa này bị lọt khoảng trắng
            // ('meditrust_se    ssion_id') nên id mới không bao giờ được lưu: mỗi lần tải trang lại
            // sinh một phiên khác, làm mất ký ức hội thoại VÀ khiến soft-lock của chính lần tải
            // trước bị coi là của phiên lạ (AiController bỏ qua slot đó suốt 3 phút) — tức là khung
            // giờ đang trống lại biến mất khỏi tầm nhìn của trợ lý.
            safeStorage.set('meditrust_session_id', sessionId);
        }
        const savedChatHtml = safeStorage.get('meditrust_chat_html');
                if (savedChatHtml) {
                    messagesContainer.innerHTML = savedChatHtml;
                    // Đợi 100ms để DOM render xong rồi tự động cuộn xuống cuối cùng
                    setTimeout(() => {
                        messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    }, 100);
                }
                // 1.2 Khôi phục trạng thái Đóng/Mở (Chuyển trang không bị tắt chat)
                        const chatState = safeStorage.get('meditrust_chat_state');
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
                                                  typingMsg.innerHTML = 'Xin chào! Em là Trợ lý AI Heal Care. Anh / Chị cần hỗ trợ vấn đề sức khỏe gì hôm nay?';
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
                             safeStorage.set('meditrust_chat_state', 'open');

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
                                         typingMsg.innerHTML = 'Xin chào! Em là Trợ lý AI Heal Care. Anh / Chị cần hỗ trợ vấn đề sức khỏe gì hôm nay?';
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
                              safeStorage.set('meditrust_chat_state', 'closed');

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
                                                   safeStorage.set('meditrust_session_id', oldSessionId);
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
                   safeStorage.setChatHtml(messagesContainer.innerHTML);
                   // [THÊM MỚI] Cập nhật thời gian hoạt động cuối cùng
                   safeStorage.set('meditrust_last_activity', new Date().getTime().toString());
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

        /** In thẻ "đã chốt lịch" rồi mở trang đặt lịch (trừ khi đang trong cuộc gọi thoại). */
        function finishBookingHandoff(typingMsg, handoff, aiData, userText) {
            // Câu "khung giờ anh/chị vừa chọn" CHỈ được nói khi khách thật sự đã chọn. Khách chưa
            // nêu giờ mà hệ thống tự lấy khung sớm nhất thì phải nhận là EM chọn — nói vống lên
            // chính là thứ khiến khách tưởng đã đặt đúng ý rồi mở trang ra mới thấy khác.
            const inSession = handoff.requestedSession === 'morning' ? ' trong buổi sáng'
                : (handoff.requestedSession === 'afternoon' ? ' trong buổi chiều' : '');
            const headline = handoff.suggested
                ? 'Em xin phép chọn giúp anh/chị khung giờ trống sớm nhất' + inSession + ' của bác sĩ.'
                : 'Em đã mở đúng bác sĩ và khung giờ anh/chị vừa chọn.';
            const note = handoff.suggested
                ? `<div style="font-size:12px;color:#64748b;margin-top:4px;">
                       Anh/chị đổi sang khung giờ khác ngay trên trang đặt lịch được ạ.
                   </div>`
                : '';

            typingMsg.innerHTML += `
                <div class="mt-3 p-3" style="background: #eef6ff; border-left: 4px solid #0d6efd; border-radius: 8px;">
                    <div class="fw-bold mb-1" style="color: #0d6efd;"><i class="bi bi-calendar-check"></i> ${headline}</div>
                    <div style="font-size: 13px; color: #334155;">
                        <div><strong>Bác sĩ:</strong> ${handoff.doctorName || handoff.doctor.fullName}</div>
                        <div><strong>Lịch hẹn:</strong> ${handoff.selectedSlotLabel}</div>
                    </div>
                    ${note}
                    <div class="d-flex align-items-center gap-2 mt-2 flex-wrap">
                        <a href="${handoff.appointmentUrl}" class="btn btn-sm btn-primary">Mở trang đặt lịch</a>
                        <button type="button" class="btn btn-sm btn-outline-secondary js-cancel-redirect">Ở lại trang này</button>
                        <span class="js-redirect-countdown" style="font-size:12px;color:#64748b;"></span>
                    </div>
                </div>`;
            safeStorage.setChatHtml(messagesContainer.innerHTML);

            // Giữ tạm khung giờ này cho phiên hiện tại. Đây là thời điểm DUY NHẤT được đặt khoá:
            // khách đã thật sự được chốt một khung cụ thể. Hỏng thì kệ, đây chỉ là lớp giảm va chạm,
            // chốt chặn thật nằm ở BookingServiceImpl.reserve().
            if (handoff.doctor && handoff.appointmentDate && handoff.appointmentTime) {
                fetch('/api/chat/hold-slot?doctorId=' + encodeURIComponent(handoff.doctor.id)
                    + '&date=' + encodeURIComponent(handoff.appointmentDate)
                    + '&slot=' + encodeURIComponent(handoff.appointmentTime)
                    + '&sessionId=' + encodeURIComponent(sessionId), { method: 'POST' })
                    .catch(function(err) { console.error(err); });
            }

            notifyReply({ aiData: aiData, userText: userText, bookingHandoff: handoff });

            // Chế độ gọi tự đọc câu xác nhận rồi chờ khách nói "đồng ý",
            // nên khi đang gọi thì KHÔNG tự nhảy trang.
            if (!window.MediTrustChat || !window.MediTrustChat.suppressAutoRedirect) {
                startRedirectCountdown(typingMsg, handoff.appointmentUrl);
            }
        }

        // ---------------------------------------------------------------------------
        // ĐẾM NGƯỢC TRƯỚC KHI CHUYỂN TRANG
        //
        // Trước đây là setTimeout 900ms không thể huỷ: khách chưa kịp đọc xong thẻ (tên bác sĩ +
        // ngày giờ) thì trang đã nhảy, câu đang gõ dở bị mất trắng, và nút "Mở trang đặt lịch" in
        // ra chỉ để làm cảnh vì luôn bị timer cướp trước. Đóng chat hay gửi tin mới cũng không
        // dừng được nó.
        // ---------------------------------------------------------------------------
        const REDIRECT_DELAY_SECONDS = 5;
        let redirectTimer = null;

        /** Dừng việc tự chuyển trang. Gọi được nhiều lần, không cần biết có timer hay không. */
        function cancelRedirect(reason) {
            if (!redirectTimer) return;
            clearInterval(redirectTimer.interval);
            redirectTimer.label.textContent = reason || 'Đã dừng chuyển trang.';
            const btn = redirectTimer.button;
            if (btn) btn.remove();
            redirectTimer = null;
            safeStorage.setChatHtml(messagesContainer.innerHTML);
        }

        function startRedirectCountdown(card, url) {
            cancelRedirect('');
            const label = card.querySelector('.js-redirect-countdown');
            const button = card.querySelector('.js-cancel-redirect');
            if (!label || !button) { window.location.href = url; return; }

            let left = REDIRECT_DELAY_SECONDS;
            label.textContent = 'Tự mở sau ' + left + 's…';
            button.addEventListener('click', function() { cancelRedirect('Anh/chị bấm nút bên cạnh khi cần mở trang đặt lịch ạ.'); });

            redirectTimer = {
                label: label,
                button: button,
                interval: setInterval(function() {
                    left -= 1;
                    if (left <= 0) {
                        clearInterval(redirectTimer.interval);
                        redirectTimer = null;
                        window.location.href = url;
                        return;
                    }
                    label.textContent = 'Tự mở sau ' + left + 's…';
                }, 1000)
            };
        }

        // Đang chờ trả lời cho một lượt. Thiếu cờ này thì khách bấm Gửi hai lần (hoặc Enter hai
        // lần) sẽ chạy song song hai lượt: hai POST cùng sessionId (ghi đè lịch sử của nhau, mất
        // tin nhắn), hai lần ghi lastHandoffDate/pendingAlternatives, và hai bộ đếm chuyển trang.
        let isSending = false;

        async function sendMessage() {
            if (isSending) return;
            const text = chatInput.value.trim();
            if (!text) return;

            // Khách gõ tiếp nghĩa là họ chưa muốn rời trang.
            cancelRedirect('Em đã dừng việc tự mở trang đặt lịch ạ.');

            isSending = true;
            if (sendBtn) sendBtn.disabled = true;

            appendMessage('user', text);
            chatInput.value = '';

            // Khách xin DỪNG. Phải xử lý TRƯỚC buildAlternativeContext: ngữ cảnh đó ra lệnh cho
            // model "TUYỆT ĐỐI KHÔNG hỏi lại khách chọn hướng nào", nên gửi câu từ chối kèm nó là
            // ép model chốt một hướng và hệ thống mở trang đặt lịch ngay sau khi khách nói thôi.
            if (parseCancelIntent(text)) {
                pendingAlternatives = null;
                lastHandoffDate = '';
                const byeText = 'Dạ vâng ạ, em dừng việc đặt lịch tại đây. '
                    + 'Khi nào cần anh/chị nhắn lại giúp em nhé.';
                const byeMsg = appendMessage('bot', byeText);
                notifyReply({ aiData: { ai_reply: byeText, speech_reply: byeText }, userText: text, bookingHandoff: null });
                safeStorage.setChatHtml(messagesContainer.innerHTML);
                isSending = false;
                if (sendBtn) sendBtn.disabled = false;
                return;
            }

            // Khách đang trả lời câu "anh/chị chọn hướng nào ạ?".
            // Model KHÔNG biết em vừa gợi ý những gì (danh sách do hệ thống tra ra, không nằm
            // trong hội thoại), nên tự chốt ngay ở đây thay vì hỏi model rồi bị hỏi lại khách.
            if (pendingAlternatives) {
                const choice = resolveAlternativeChoice(text, pendingAlternatives);
                if (choice) {
                    const chosen = handoffFromAlternative(pendingAlternatives, choice);
                    pendingAlternatives = null;

                    const replyText = choice.kind === 'doctor'
                        ? `Dạ vâng ạ, em chuyển sang bác sĩ ${chosen.doctorName} giữ nguyên khung giờ anh/chị muốn.`
                        : `Dạ vâng ạ, em giữ bác sĩ ${chosen.doctorName} và dời sang khung giờ mới.`;

                    const localMsg = appendMessage('bot', replyText);
                    finishBookingHandoff(localMsg, chosen, {
                        ai_reply: replyText,
                        speech_reply: replyText,
                        is_emergency: false,
                        booking_intent: true
                    }, text);
                    isSending = false;
                    if (sendBtn) sendBtn.disabled = false;
                    return;
                }
            }

            // Hiển thị hiệu ứng 3 dấu chấm nhảy trong lúc chờ AI phản hồi
                        const typingMsg = appendMessage('bot', '<div class="typing-dots"><span></span><span></span><span></span></div>');

            try {
                // Khách trả lời kiểu khác ("cho tôi bác sĩ nào rảnh sớm nhất") thì vẫn phải hỏi
                // model — nhưng phải kèm theo danh sách em vừa gợi ý, nếu không model trả lời mù.
                const promptToSend = pendingAlternatives
                    ? buildAlternativeContext(pendingAlternatives) + '\n\n' + text
                    : text;

                const response = await fetch('/api/chat/ask', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sessionId: sessionId, prompt: promptToSend })
                });

                if (response.ok) {
                    const data = await response.json();
                    let aiRawText = data.answer;

                                        // Chỉ bọc ĐÚNG bước parse. Trước đây khối try này ôm cả
                                        // resolveBookingHandoff lẫn các vòng fetch bác sĩ, nên MỌI lỗi
                                        // xảy ra SAU KHI parse đã thành công đều rơi vào catch bên dưới
                                        // và in NGUYÊN KHỐI JSON nội bộ ra bong bóng chat — gồm cả
                                        // `reasoning` và `patient_summary` (tóm tắt bệnh của khách).
                                        // Chế độ gọi còn đọc to khối đó lên.
                                        let aiData = null;
                                        try {
                                            const cleanJsonStr = aiRawText.replace(/```json/gi, '').replace(/```/g, '').trim();
                                            aiData = JSON.parse(cleanJsonStr);
                                        } catch (parseError) {
                                            aiData = null;
                                        }

                                        if (!aiData || typeof aiData !== 'object') {
                                            // Model lỡ trả text thường thay vì JSON: in nguyên văn vẫn
                                            // đọc được, vì đó là lời tư vấn chứ không phải dữ liệu nội bộ.
                                            pendingAlternatives = null;
                                            typingMsg.innerHTML = String(aiRawText || 'Dạ em chưa nhận được câu trả lời, anh/chị nhắn lại giúp em ạ.').replace(/\n/g, '<br>');
                                            safeStorage.setChatHtml(messagesContainer.innerHTML);
                                            notifyReply({ aiData: null, rawText: aiRawText, userText: text, bookingHandoff: null });
                                            return;
                                        }

                                        try {
                                            // 2. In câu trả lời tư vấn. Model thiếu key `ai_reply` thì
                                            // trước đây bong bóng chat hiện đúng chữ "undefined".
                                            typingMsg.innerHTML = aiData.ai_reply
                                                || 'Dạ em chưa nghe rõ ý anh/chị, anh/chị nói lại giúp em với ạ.';
                                            if (aiData.is_emergency) {
                                                typingMsg.innerHTML = `<div style="color: red; font-weight: bold; margin-bottom: 8px;"><i class="bi bi-exclamation-triangle-fill"></i> 🚨 CẢNH BÁO KHẨN CẤP:</div>` + typingMsg.innerHTML;
                                            }

                                            // 3. Xử lý đa ý định (Vòng lặp quét mảng recommended_departments)
                                            const deptIds = aiData.recommended_departments;
                                            if (deptIds && Array.isArray(deptIds) && deptIds.length > 0) {
                                                // KHÔNG quảng cáo "đang tạm giữ lịch": việc xem danh sách
                                                // không còn đặt khoá nữa (xem softLockCache ở AiController),
                                                // nên câu đó vừa sai vừa hối thúc khách một cách vô cớ.
                                                let allActionHtml = `<div class="mt-3">`;

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

                                            // Việc đặt lịch hỏng vì hệ thống, không phải vì khách nói sai.
                                            // Trước đây mọi trường hợp này đều `return null` -> khách nói
                                            // "đặt lịch giúp em" rồi không nhận được thẻ nào, không một
                                            // lời giải thích, chỉ có câu tư vấn chung chung của model.
                                            if (bookingHandoff && bookingHandoff.error) {
                                                const WHY = {
                                                    NETWORK: 'Em chưa kết nối được tới danh sách bác sĩ. Anh/chị thử lại giúp em ạ.',
                                                    NO_DOCTORS: 'Chuyên khoa này hiện chưa có bác sĩ nào nhận lịch ạ. Anh/chị xem giúp em <a href="/doctors">danh sách bác sĩ</a> nhé.',
                                                    NO_DEPARTMENT: 'Em chưa rõ anh/chị muốn khám chuyên khoa nào ạ. Anh/chị mô tả thêm triệu chứng giúp em nhé.'
                                                };
                                                typingMsg.innerHTML += `
                                                    <div class="mt-3 p-3" style="background:#fff8e1;border-left:4px solid #ffc107;border-radius:8px;">
                                                        <div style="font-size:13px;color:#334155;">
                                                            <i class="bi bi-exclamation-circle" style="color:#b8860b;"></i>
                                                            ${WHY[bookingHandoff.error] || WHY.NETWORK}
                                                        </div>
                                                    </div>`;
                                                pendingAlternatives = null;
                                                safeStorage.setChatHtml(messagesContainer.innerHTML);
                                                notifyReply({ aiData: aiData, userText: text, bookingHandoff: bookingHandoff });
                                                return;
                                            }

                                            // Nhiều bác sĩ cùng khớp tên khách nói -> HỎI LẠI.
                                            // Tự chọn người đầu danh sách chính là đặt nhầm người.
                                            if (bookingHandoff && bookingHandoff.doctorAmbiguous) {
                                                const names = (bookingHandoff.candidates || [])
                                                    .map(function(d) {
                                                        return `<button class="quick-reply-btn" onclick="window.sendQuickReply('Đặt lịch với bác sĩ ${String(d.fullName).replace(/'/g, "\\'")}', this)">${d.fullName}</button>`;
                                                    }).join('');
                                                typingMsg.innerHTML += `
                                                    <div class="mt-3 p-3" style="background:#fff8e1;border-left:4px solid #ffc107;border-radius:8px;">
                                                        <div class="fw-bold mb-1" style="color:#b8860b;">
                                                            <i class="bi bi-people"></i> Bên em có mấy bác sĩ cùng tên "${bookingHandoff.requestedDoctorName}" ạ
                                                        </div>
                                                        <div style="font-size:13px;color:#334155;margin-bottom:6px;">Anh/chị chọn giúp em một người nhé:</div>
                                                        <div class="quick-replies-container">${names}</div>
                                                    </div>`;
                                                pendingAlternatives = null;
                                                safeStorage.setChatHtml(messagesContainer.innerHTML);
                                                notifyReply({ aiData: aiData, userText: text, bookingHandoff: bookingHandoff });
                                                return;
                                            }

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
                                                // Danh sách gợi ý cũ không còn liên quan tới lượt này nữa. Không xoá
                                                // thì buildAlternativeContext cứ nhét mãi vào các lượt sau, model trả
                                                // lời theo một danh sách khách đã bỏ qua từ lâu.
                                                pendingAlternatives = null;
                                                safeStorage.setChatHtml(messagesContainer.innerHTML);
                                                notifyReply({ aiData: aiData, userText: text, bookingHandoff: bookingHandoff });
                                                return;
                                            }

                                            // Không đặt được khung giờ khách xin: hỏi ý khách chứ KHÔNG tự nhảy sang giờ khác.
                                            if (bookingHandoff && bookingHandoff.fallback) {
                                                pendingAlternatives = bookingHandoff;
                                                typingMsg.innerHTML += buildSlotFullHtml(bookingHandoff);
                                                safeStorage.setChatHtml(messagesContainer.innerHTML);
                                                notifyReply({ aiData: aiData, userText: text, bookingHandoff: bookingHandoff });
                                                return;
                                            }

                                            if (bookingHandoff) {
                                                pendingAlternatives = null;
                                                finishBookingHandoff(typingMsg, bookingHandoff, aiData, text);
                                                return;
                                            }

                                            // Lượt này không còn dính tới việc đặt lịch nữa -> bỏ danh sách gợi ý cũ,
                                            // kẻo nó bám theo mãi vào ngữ cảnh của các lượt sau (xem chú thích ở trên).
                                            pendingAlternatives = null;

                                            // Lưu lại khung HTML (Đã chạy ngầm memory JSON)
                                            safeStorage.setChatHtml(messagesContainer.innerHTML);
                                            notifyReply({ aiData: aiData, userText: text, bookingHandoff: null });

                                        } catch (renderError) {
                                            // Lỗi ở phần DỰNG GIAO DIỆN (sau khi JSON đã parse xong).
                                            // Câu tư vấn của model đã in ở trên rồi, chỉ báo thêm phần
                                            // đặt lịch hỏng — TUYỆT ĐỐI không đổ JSON nội bộ ra đây.
                                            console.error('Lỗi khi dựng phần đặt lịch:', renderError);
                                            pendingAlternatives = null;
                                            typingMsg.innerHTML += `<div class="mt-2" style="font-size:12px;color:#b91c1c;">
                                                Em đang gặp trục trặc khi tra lịch khám. Anh/chị thử lại giúp em, hoặc mở
                                                <a href="/appointment">trang đặt lịch</a> ạ.
                                            </div>`;
                                            safeStorage.setChatHtml(messagesContainer.innerHTML);
                                            notifyReply({ aiData: aiData, userText: text, bookingHandoff: null, error: 'render' });
                                        }
                } else {
                    pendingAlternatives = null;
                    typingMsg.innerHTML = 'Dạ hệ thống đang bận, anh/chị nhắn lại giúp em sau ít phút ạ.';
                    notifyReply({ aiData: null, userText: text, bookingHandoff: null, error: 'server-busy' });
                }
            } catch (error) {
                // Mạng lỗi giữa chừng: danh sách gợi ý cũ PHẢI được xoá, nếu không mọi lượt sau đó
                // vẫn bị chèn ngữ cảnh ép model chốt một lịch hẹn khách đã bỏ qua từ lâu.
                pendingAlternatives = null;
                typingMsg.innerHTML = 'Dạ em không kết nối được với hệ thống. Anh/chị kiểm tra mạng rồi nhắn lại giúp em ạ.';
                notifyReply({ aiData: null, userText: text, bookingHandoff: null, error: 'network' });
            } finally {
                isSending = false;
                if (sendBtn) sendBtn.disabled = false;
            }
        }

        sendBtn.addEventListener('click', sendMessage);
        chatInput.addEventListener('keydown', (e) => {
            // isComposing: bộ gõ tiếng Việt (Telex/VNI) dùng Enter để CHỐT TỪ đang gõ. Không kiểm
            // thì phím đó gửi luôn tin nhắn còn dở dang.
            if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
                e.preventDefault();
                sendMessage();
            }
        });
        // Khách bắt đầu gõ nghĩa là chưa muốn rời trang -> dừng đếm ngược chuyển trang.
        chatInput.addEventListener('input', function() {
            if (chatInput.value.trim()) cancelRedirect('Em đã dừng việc tự mở trang đặt lịch ạ.');
        });
        // [THÊM MỚI] Sự kiện cho nút Làm mới Chat (Tự động xóa và reset phiên)
                const btnNewChat = document.getElementById('btn-new-chat');
                if (btnNewChat) {
                    btnNewChat.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (confirm('Bạn muốn kết thúc ca tư vấn này và bắt đầu hỏi vấn đề mới?')) {
                            safeStorage.remove('meditrust_session_id');
                            safeStorage.remove('meditrust_chat_html');
                            safeStorage.remove('meditrust_last_activity');
                            safeStorage.set('meditrust_chat_state', 'open'); // Ép mở lại sau khi reload
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
        if (!hasSeenTour && safeStorage.get('meditrust_chat_state') !== 'open') {
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