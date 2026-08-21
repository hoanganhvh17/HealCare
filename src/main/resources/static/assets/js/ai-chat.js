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



                window.sendQuickReply = function(text, btnElement) {
                    const container = btnElement.closest('.quick-replies-container');
                    if (container) container.remove();

                    const chatInput = document.getElementById('ai-chat-input');
                    const sendBtn = document.getElementById('ai-chat-send');
                    if (chatInput && sendBtn) {
                        chatInput.value = text;
                        sendBtn.click();
                    }
                };

                const CHAT_HTML_LIMIT = 300000;
                const safeStorage = {
                    get: function(key) {
                        try { return sessionStorage.getItem(key); } catch (e) { return null; }
                    },
                    set: function(key, value) {
                        try {
                            sessionStorage.setItem(key, value);
                            return true;
                        } catch (e) {
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

                function toIsoDate(date) {
                    return date.getFullYear()
                        + '-' + String(date.getMonth() + 1).padStart(2, '0')
                        + '-' + String(date.getDate()).padStart(2, '0');
                }

                function slotStartTime(slotLabel) {
                    const inside = (slotLabel || '').match(/\((.*?)\)/);
                    const range = inside ? inside[1] : (slotLabel || '');
                    const start = range.match(/(\d{1,2}):(\d{2})/);
                    return start ? String(parseInt(start[1], 10)).padStart(2, '0') + ':' + start[2] : '';
                }

                function normalizeTimeHint(text) {
                    const raw = normalizeText(text);
                    if (!raw) return '';

                    let match = raw.match(/(\d{1,2})\s*(?:h(?![\p{L}])|:|giờ|gio|rưỡi|ruoi)\s*(\d{1,2})?/u);
                    if (!match) match = raw.match(/^(\d{1,2})$/);   // booking_target trả về trần một con số
                    if (!match) return '';

                    let hour = parseInt(match[1], 10);
                    let minute = match[2] !== undefined ? parseInt(match[2], 10) : null;
                    const tail = raw.slice(match.index + match[0].length);

                    const kem = tail.match(/^\s*(?:kém|kem|thiếu|thieu)\s*(\d{1,2})/);
                    if (kem && minute === null) {
                        hour -= 1;
                        minute = 60 - parseInt(kem[1], 10);
                        if (minute >= 60 || minute < 0) return '';
                    }

                    if (minute === null) {
                        minute = /(rưỡi|ruoi)/.test(match[0] + tail.slice(0, 6)) ? 30 : 0;
                    }

                    const nearTail = tail.slice(0, 12);
                    if (hour >= 1 && hour <= 11 && /^[^0-9]{0,10}(chiều|chieu|tối|đêm)/.test(tail)) {
                        hour += 12;
                    } else if (hour >= 1 && hour <= 5 && !/sáng/.test(nearTail)) {
                        hour += 12;
                    }

                    if (hour < 0 || hour > 23 || minute > 59) return '';
                    return String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0');
                }

                function extractSessionHint(text) {
                    const padded = ' ' + normalizeText(text).replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim() + ' ';
                    if (padded === '  ') return '';

                    const hasWord = function(words) {
                        return words.some(function(w) { return padded.indexOf(' ' + w + ' ') !== -1; });
                    };

                    const hasMorning = hasWord(['sáng', 'buổi sang', 'buoi sang']);
                    const hasAfternoon = hasWord(['chiều', 'chieu', 'trưa', 'trua']);

                    if (hasMorning && hasAfternoon && /hoặc|hay|đều được|cũng được|deu duoc|cung duoc/.test(padded)) {
                        return '';
                    }

                    if (hasAfternoon) return 'afternoon';
                    if (hasWord(['tối', 'đêm', 'dem', 'buổi toi', 'buoi toi'])) return 'evening';
                    if (hasMorning) return 'morning';
                    return '';

                }

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

                const NOT_A_DOCTOR_NAME = [
                    'nao', 'nao cung duoc', 'nao cung dc', 'bat ky', 'bat cu', 'gi cung duoc',
                    'khac', 'nu', 'nam', 'gioi', 'tot', 'ranh', 'truc', 'nay', 'do', 'kia',
                    'chuyen khoa', 'chuyen mon', 'truc hom nay', 'truc ca nay', 'phu trach',
                    'danh gia', 'co danh gia', 'duoc danh gia', 'nhieu sao', 'nhieu danh gia',
                    'uy tin', 'noi tieng', 'co tay nghe', 'tay nghe', 'kinh nghiem', 'nhieu kinh nghiem'
                ];

                function extractDoctorName(text) {
                    const normalized = normalizeText(text);
                    const match = normalized.match(/(?:bác sĩ|bs\.?)\s+(.+?)(?:\s+lúc|\s+vào|\s+ngày|\s+thứ|\s+để|\s+đặt|\s+khám|$)/i);
                    if (!match) return '';

                    const FILLER = /(\s+(đi|nhé|nha|nhá|ạ|à|với|luôn|thôi|nhá|nhở|được không|cho tôi|cho mình|giúp tôi|giúp mình|giúp em|cũng được|cung duoc))+$/gi;
                    const name = match[1]
                        .replace(/[.,!?]+$/, '')
                        .replace(FILLER, '')
                        .replace(/[.,!?]+$/, '')
                        .trim();
                    if (!name) return '';

                    const bare = stripDiacritics(name);
                    if (NOT_A_DOCTOR_NAME.indexOf(bare) !== -1) return '';
                    if (NOT_A_DOCTOR_NAME.some(function(w) { return bare === w || bare.indexOf(w + ' ') === 0; })) return '';

                    return name;
                }

                function parseCancelIntent(text) {
                    const padded = ' ' + normalizeText(text).replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim() + ' ';
                    if (padded === '  ') return false;

                    if (/(hủy|huỷ|hủy đi|huỷ đi|hủy lịch|huỷ lịch)/.test(padded)) return true;
                    if (/không đặt nữa|khong dat nua|thôi không đặt|thoi khong dat|khỏi đặt|khoi dat/.test(padded)) return true;
                    if (/để sau|de sau|lúc khác|luc khac|dừng lại|dung lai|không cần nữa|khong can nua/.test(padded)) return true;
                    if (padded.indexOf(' thôi ') !== -1 && /không|khong|đừng|dung lai|khỏi/.test(padded)) return true;
                    return false;
                }

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

                function looksLikeAvailabilityQuestion(text) {
                    const raw = normalizeText(text);
                    const padded = ' ' + raw.replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim() + ' ';

                    if (looksLikeBookingRequest(text)) return false;
                    if (/đặt|dat lich|book|lấy lịch|lay lich/.test(raw)) return false;
                    if (!/bác sĩ|bac si|bs /.test(raw)) return false;
                    if (/(tôi|em|mình|con|cháu|toi|minh)\s+(bận|ban|rảnh|ranh|nghỉ|nghi)/.test(raw)) return false;
                    if (/phí|giá|bao nhiêu tiền|kinh nghiệm|chuyên môn|giỏi|bằng cấp/.test(raw)) return false;

                    const CUES = ['bận', 'ban', 'rảnh', 'ranh', 'nghỉ', 'nghi', 'trực', 'truc',
                        'có làm', 'co lam', 'có khám', 'co kham', 'có ca', 'co ca',
                        'lịch làm việc', 'lich lam viec', 'lịch khám', 'lich kham',
                        'ca khám', 'ca kham', 'làm ngày nào', 'lam ngay nao',
                        'khám ngày nào', 'kham ngay nao', 'khám hôm nào', 'kham hom nao',
                        'làm buổi nào', 'lam buoi nao', 'còn chỗ', 'con cho',
                        'còn trống', 'con trong', 'đi làm', 'di lam'];
                    return CUES.some(function(w) { return padded.indexOf(' ' + w + ' ') !== -1; });
                }

                function looksLikeMyBookingQuestion(text) {
                    const raw = normalizeText(text);
                    if (looksLikeBookingRequest(text)) return false;
                    if (!/của tôi|cua toi|của mình|cua minh|của em|cua em|tôi đã đặt|toi da dat|mình đã đặt|em đã đặt/.test(raw)) {
                        return false;
                    }
                    return /lịch|lich|hẹn|hen|đặt|dat|khám|kham/.test(raw);
                }

                /**
                 * Khách hỏi về HỒ SƠ BỆNH ÁN CŨ họ đã tự tải lên (khám ở viện khác mang sang).
                 *
                 * Hai luật của tệp này, cả hai đều đã có tiền lệ ngay bên cạnh:
                 *
                 * 1. Dùng lối ĐỆM DẤU CÁCH chứ không `\b`. `\b` của JavaScript chỉ hiểu chữ cái
                 *    ASCII nên không bao giờ khớp một từ tiếng Việt có dấu.
                 * 2. Mỗi cụm phải liệt kê CẢ HAI dạng có dấu và không dấu, y như
                 *    `looksLikeMyBookingQuestion` ngay dưới đây. `normalizeText` chỉ hạ chữ thường
                 *    và gộp khoảng trắng — nó KHÔNG bỏ dấu — nên một danh sách chỉ có dạng không
                 *    dấu sẽ trượt sạch những khách gõ đủ dấu, tức phần lớn khách. Và cố ý không
                 *    đưa cả câu qua `stripDiacritics`: làm vậy biến "sáng" thành giới từ "sang"
                 *    và "tôi" thành "toi", đúng hai cái bẫy đã ghi ở coding-conventions.md.
                 */
                const MY_DOCS_ABOUT_OLD = [
                    ' hồ sơ cũ ', ' ho so cu ',
                    ' bệnh án cũ ', ' benh an cu ',
                    ' hồ sơ bệnh án cũ ', ' ho so benh an cu ',
                    ' giấy khám cũ ', ' giay kham cu ',
                    ' kết quả khám cũ ', ' ket qua kham cu ',
                    ' hồ sơ đã tải lên ', ' ho so da tai len ',
                    ' bệnh án đã tải lên ', ' benh an da tai len ',
                    ' hồ sơ ngoại viện ', ' ho so ngoai vien ',
                    ' khám ở viện khác ', ' kham o vien khac ',
                    ' khám ở bệnh viện khác ', ' kham o benh vien khac ',
                    ' khám ở nơi khác ', ' kham o noi khac ',
                    ' tuyến dưới ', ' tuyen duoi '
                ];

                const MY_DOCS_POSSESSIVE = [
                    ' của tôi ', ' cua toi ', ' của mình ', ' cua minh ', ' của em ', ' cua em ',
                    ' tôi đã tải ', ' toi da tai ', ' em đã tải ', ' em da tai ',
                    ' mình đã tải ', ' minh da tai ', ' tôi vừa tải ', ' toi vua tai ',
                    ' em vừa tải ', ' em vua tai ', ' tôi vừa gửi ', ' toi vua gui ',
                    ' em vừa gửi ', ' em vua gui ', ' tôi tải lên ', ' toi tai len ',
                    ' em tải lên ', ' em tai len ', ' tôi đã gửi ', ' toi da gui '
                ];

                const MY_DOCS_NOUN = [
                    ' hồ sơ ', ' ho so ', ' bệnh án ', ' benh an ',
                    ' giấy khám ', ' giay kham ', ' kết quả khám ', ' ket qua kham ',
                    ' phiếu khám ', ' phieu kham ', ' đơn thuốc cũ ', ' don thuoc cu ',
                    ' tải lên ', ' tai len '
                ];

                function looksLikeMyDocumentsQuestion(text) {
                    // Bỏ dấu câu trước khi đệm, nếu không "hồ sơ cũ?" không khớp ' hồ sơ cũ '.
                    const raw = normalizeText(text).replace(/[.,!?;:]/g, ' ').replace(/\s+/g, ' ').trim();
                    const padded = ' ' + raw + ' ';
                    const has = function(list) {
                        return list.some(function(k) { return padded.indexOf(k) >= 0; });
                    };

                    // Cụm nói thẳng về hồ sơ CŨ thì nhận ngay, không cần thêm dấu hiệu sở hữu.
                    if (has(MY_DOCS_ABOUT_OLD)) return true;

                    // Còn lại phải có dấu hiệu SỞ HỮU: "hồ sơ" trần cũng là "hồ sơ năng lực",
                    // "hồ sơ bác sĩ" — những thứ hoàn toàn khác.
                    if (!has(MY_DOCS_POSSESSIVE)) return false;
                    return has(MY_DOCS_NOUN);
                }

                function looksLikeDoctorInfoQuestion(text) {
                    const raw = normalizeText(text);
                    if (looksLikeBookingRequest(text)) return false;
                    if (!/bác sĩ|bac si|bs /.test(raw)) return false;
                    return /bao nhiêu tiền|bao nhieu tien|giá khám|gia kham|phí khám|phi kham|chi phí|chi phi|có tốt|co tot|đánh giá|danh gia|mấy sao|may sao|kinh nghiệm|kinh nghiem|bằng cấp|bang cap|giỏi không|gioi khong/.test(raw);
                }

                const DOCTOR_SUPERLATIVE = new RegExp(
                    '(đánh giá|danh gia|nhiều sao|nhieu sao|uy tín|uy tin|nổi tiếng|noi tieng'
                    + '|chuyên môn|chuyen mon|kinh nghiệm|kinh nghiem|tay nghề|tay nghe|giỏi|gioi)'
                    + '[^.!?]{0,24}(nhất|nhat)'
                    + '|rẻ nhất|re nhat|giá thấp nhất|gia thap nhat');

                function looksLikeDoctorFilterQuestion(text) {
                    const raw = normalizeText(text);
                    if (!/bác sĩ|bac si/.test(raw)) return false;
                    if (DOCTOR_SUPERLATIVE.test(raw)) return true;
                    if (looksLikeBookingRequest(text)) return false;
                    return /bác sĩ nữ|bac si nu|bác sĩ nam|bac si nam|nhiều kinh nghiệm|nhieu kinh nghiem|giỏi nhất|gioi nhat|rẻ nhất|re nhat|giá thấp|gia thap|có bác sĩ nào|co bac si nao|bác sĩ nào tốt|gợi ý bác sĩ|goi y bac si/.test(raw);
                }

                const WEEKDAY_WORDS = [
                    { words: ['chủ nhật', 'chu nhat', 'cn'], day: 0 },
                    { words: ['thứ hai', 'thu hai', 'thứ 2', 'thu 2', 't2'], day: 1 },
                    { words: ['thứ ba', 'thu ba', 'thứ 3', 'thu 3', 't3'], day: 2 },
                    { words: ['thứ tư', 'thu tu', 'thứ 4', 'thu 4', 't4'], day: 3 },
                    { words: ['thứ năm', 'thu nam', 'thứ 5', 'thu 5', 't5'], day: 4 },
                    { words: ['thứ sáu', 'thu sau', 'thứ 6', 'thu 6', 't6'], day: 5 },
                    { words: ['thứ bảy', 'thu bay', 'thứ 7', 'thu 7', 't7'], day: 6 }
                ];

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
                    for (const entry of WEEKDAY_WORDS) {
                        const hit = entry.words.some(function(w) { return padded.indexOf(' ' + w + ' ') !== -1; });
                        if (!hit) continue;

                        let delta;
                        if (nextWeek) {
                            const daysToNextMonday = (8 - today.getDay()) % 7 || 7;
                            delta = daysToNextMonday + (entry.day + 6) % 7;
                        } else {
                            delta = (entry.day - today.getDay() + 7) % 7;
                            if (delta === 0 && sessionAlreadyPassed(extractSessionHint(text))) {
                                delta = 7;
                            }
                        }
                        return isoAfterDays(delta);
                    }
                    const TODAY_WORDS = ['hôm nay', 'hom nay', 'sáng nay', 'sang nay',
                        'chiều nay', 'chieu nay', 'trưa nay', 'trua nay', 'tối nay', 'toi nay'];
                    if (TODAY_WORDS.some(function(w) { return padded.indexOf(' ' + w + ' ') !== -1; })) {
                        return toIsoDate(today);
                    }
                    if (/ngày mai|sáng mai|chiều mai|trưa mai|tối mai|hôm sau/.test(normalized)) {
                        return isoAfterDays(1);
                    }
                    if (/ngày kia|ngay kia|ngày mốt|ngay mot/.test(normalized)) {
                        return isoAfterDays(2);
                    }
                    const inDays = normalized.match(/(\d{1,2})\s*ngày\s*(?:nữa|sau)/);
                    if (inDays) {
                        const n = parseInt(inDays[1], 10);
                        if (n >= 1 && n <= 90) return isoAfterDays(n);
                    }
                    if (/cuối tuần|cuoi tuan/.test(normalized)) {
                        const toSaturday = (6 - today.getDay() + 7) % 7;
                        return isoAfterDays(nextWeek ? toSaturday + 7 : (toSaturday === 0 ? 0 : toSaturday));
                    }
                    if (nextWeek) {
                        return isoAfterDays((8 - today.getDay()) % 7 || 7);
                    }

                    const wordDate = normalized.match(/(?:ngày|mùng|mồng|mung|mong)\s*(\d{1,2})\s*(?:tháng|thang)\s*(\d{1,2})/);
                    if (wordDate) {
                        return buildDateHint(parseInt(wordDate[1], 10), parseInt(wordDate[2], 10), null, today);
                    }

                    const slashMatch = normalized.match(/(^|[^\d])(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?([^\d]|$)/);
                    if (slashMatch) {
                        return buildDateHint(parseInt(slashMatch[2], 10), parseInt(slashMatch[3], 10),
                            slashMatch[4] ? parseInt(slashMatch[4], 10) : null, today);
                    }

                    return '';
                }
                function buildDateHint(day, month, year, today) {
                    if (!(day >= 1 && day <= 31) || !(month >= 1 && month <= 12)) return '';

                    let resolvedYear = year === null ? today.getFullYear() : (year < 100 ? 2000 + year : year);
                    const iso = function(y) {
                        return y + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
                    };
                    if (year === null && iso(resolvedYear) < toIsoDate(today)) {
                        resolvedYear += 1;
                    }
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

                function describeLoad(nearbyLoad) {
                    if (nearbyLoad === 0) return 'quanh giờ đó bác sĩ chưa có ca nào nên anh/chị gần như không phải chờ';
                    if (nearbyLoad <= 2) return 'quanh giờ đó bác sĩ chỉ có ' + nearbyLoad + ' ca nên anh/chị ít phải chờ';
                    return 'quanh giờ đó bác sĩ có ' + nearbyLoad + ' ca khám';
                }
                let pendingAlternatives = null;

                function lastNameWord(fullName) {
                    const words = normalizeText(fullName).split(' ').filter(Boolean);
                    return words.length ? words[words.length - 1] : '';
                }
                function resolveAlternativeChoice(text, handoff) {
                    const alt = (handoff && handoff.alternatives) || {};
                    const sameTime = Array.isArray(alt.sameTimeDoctors) ? alt.sameTimeDoctors : [];
                    const otherTimes = Array.isArray(alt.otherTimes) ? alt.otherTimes : [];
                    if (!sameTime.length && !otherTimes.length) return null;

                    const raw = normalizeText(text).replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim();
                    if (!raw) return null;
                    const padded = ' ' + raw + ' ';

                    const directions = [];
                    if (sameTime.length) directions.push('doctor');
                    if (otherTimes.length) directions.push('time');

                    let want = null;
                    if (/^(1|một|mot|đầu tiên|dau tien|cái đầu|cai dau|cái đầu tiên|cai dau tien)$/.test(raw)) want = directions[0];
                    else if (/^(2|hai|cái sau|cai sau|cái thứ hai|cai thu hai)$/.test(raw)) want = directions[1];
                    if (!want) {
                        const ordinal = raw.match(/(?:hướng|huong|cách|cach|phương án|phuong an|option|số|so|chọn|chon|lấy|lay|cái|cai)\s*(\d)(?!\s*(?:\d|:|h|giờ|gio|rưỡi|ruoi))/);
                        if (ordinal) want = directions[parseInt(ordinal[1], 10) - 1] || null;
                    }
                    if (!want && /^(ok|okay|oke|vâng|vang|dạ|da|ừ|u|uh|được|duoc|đồng ý|dong y|nhất trí)( ạ| a| em| nhé| nhe)?$/.test(raw)) {
                        want = directions[0];
                    }
                    if (!want && /(nào cũng được|nao cung duoc|cái nào cũng|sao cũng được|sao cung duoc|sớm nhất|som nhat|gần nhất|gan nhat|tùy em|tuy em|em chọn giúp|em chon giup)/.test(raw)) {
                        want = directions[0];
                    }
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
                    if (!want) {
                        const spokenTime = normalizeTimeHint(text);
                        if (spokenTime) {
                            const hit = otherTimes.find(function(o) { return slotStartTime(o.slot) === spokenTime; });
                            if (hit) return { kind: 'time', option: hit };
                        }
                    }
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

                function buildDoctorChoiceRow(doc, fallbackDate, buttonLabel, reasonText) {
                    return `<div class="d-flex align-items-center gap-2 mb-2 p-2" style="background:#fff;border-radius:6px;">
                        <img src="${doc.avatar}" alt="" onerror="this.src='/assets/img/default-doctor.png'"
                             style="width:38px;height:38px;border-radius:50%;object-fit:cover;">
                        <div style="flex:1;font-size:13px;color:#334155;">
                            <div><strong>${doc.fullName}</strong>${doc.degree ? ' — ' + doc.degree : ''}</div>
                            <div style="color:#64748b;">${doc.slotLabel || ''}${reasonText ? ' — ' + reasonText + '.' : ''}</div>
                        </div>
                        <a href="${buildAppointmentUrl(doc.id, doc.date || fallbackDate, doc.slot)}"
                           class="btn btn-sm btn-outline-primary">${buttonLabel}</a>
                    </div>`;
                }

                function pickOtherDoctors(list, selfId, limit) {
                    return (Array.isArray(list) ? list : [])
                        .filter(function(d) { return d && d.slot && String(d.id) !== String(selfId); })
                        .slice(0, limit || 2);
                }

                function loadOfPicked(alternatives, doctorId, key) {
                    const list = (alternatives && alternatives.sameTimeDoctors) || [];
                    for (let i = 0; i < list.length; i++) {
                        if (String(list[i].id) === String(doctorId)) {
                            const v = list[i][key];
                            return (v === undefined || v === null) ? null : v;
                        }
                    }
                    return null;
                }

                function describeDayLoad(dayLoad) {
                    if (!dayLoad) return 'hôm đó bác sĩ chưa có ca nào nên anh/chị gần như không phải chờ';
                    if (dayLoad <= 3) return 'hôm đó bác sĩ mới có ' + dayLoad + ' ca nên anh/chị ít phải chờ';
                    return 'hôm đó bác sĩ có ' + dayLoad + ' ca khám';
                }

                function describeWanted(handoff) {
                    if (handoff.requestedTime) return 'Khung giờ ' + handoff.requestedTime;
                    if (handoff.requestedSession === 'morning') return 'Buổi sáng';
                    if (handoff.requestedSession === 'afternoon') return 'Buổi chiều';
                    return 'Khung giờ anh/chị yêu cầu';
                }

                function buildSlotFullHtml(handoff) {
                    const alt = handoff.alternatives || {};
                    const wantedTime = handoff.requestedTime || 'khung giờ anh/chị yêu cầu';
                    const sameTime = Array.isArray(alt.sameTimeDoctors) ? alt.sameTimeDoctors : [];
                    const otherTimes = Array.isArray(alt.otherTimes) ? alt.otherTimes : [];

                    const reasonLine = alt.reasonText
                        || (describeWanted(handoff) + ' ngày ' + formatDayMonth(handoff.appointmentDate) + ' đã kín lịch rồi ạ.');
                    const headline = alt.otherTimesMovedDay
                        ? reasonLine + ' ' + (alt.otherTimesText || 'Em phải tìm sang ngày khác ạ.')
                        : reasonLine;

                    let html = `<div class="mt-3 p-3" style="background:#fff8e1;border-left:4px solid #ffc107;border-radius:8px;">
                        <div class="fw-bold mb-2" style="color:#b8860b;">
                            <i class="bi bi-clock-history"></i> ${headline}
                        </div>`;

                    if (sameTime.length > 0) {
                        html += `<div style="font-size:13px;color:#334155;margin-bottom:6px;">
                            Em gợi ý anh/chị mấy bác sĩ cùng chuyên khoa vẫn còn nhận <strong>${wantedTime}</strong>:
                        </div>`;
                        sameTime.forEach(function(doc) {
                            html += buildDoctorChoiceRow(doc, handoff.appointmentDate, 'Chọn',
                                'em gợi ý bác sĩ này vì ' + describeLoad(doc.nearbyLoad));
                        });
                    }

                    if (otherTimes.length > 0) {
                        const keepName = alt.requestedDoctorName || handoff.doctorName || 'bác sĩ hiện tại';
                        const movedDay = (alt.otherTimesMovedDay !== undefined)
                            ? alt.otherTimesMovedDay
                            : (otherTimes[0].date && otherTimes[0].date !== handoff.appointmentDate);
                        const movedTo = alt.otherTimesDate || otherTimes[0].date;
                        html += `<div style="font-size:13px;color:#334155;margin:8px 0 6px;">
                            Hoặc anh/chị vẫn giữ <strong>${keepName}</strong> và
                            ${movedDay
                                ? 'chuyển sang <strong>' + formatDayMonth(movedTo) + '</strong> (ngày làm việc gần nhất của bác sĩ)'
                                : 'dời sang khung giờ gần nhất'}:
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

                function buildAvailabilityHtml(av) {
                    const anchor = av.anchor || {};
                    const week = Array.isArray(av.week) ? av.week : [];
                    const headline = anchor.reasonText || av.summaryText || '';

                    let html = `<div class="mt-3 p-3" style="background:#e0f2fe;border-left:4px solid #0ea5e9;border-radius:8px;">
                        <div class="fw-bold mb-2" style="color:#075985;">
                            <i class="bi bi-calendar-week"></i> ${headline}
                        </div>`;

                    if (week.length > 0) {
                        html += `<div style="font-size:13px;color:#334155;margin-bottom:6px;">
                            Lịch làm việc của <strong>${av.doctorName}</strong>:
                        </div><div class="d-flex flex-wrap gap-2 mb-2">`;
                        week.forEach(function(day) {
                            let note;
                            if (day.dayState === 'NO_SCHEDULE') {
                                note = 'chưa có lịch đăng ký';
                            } else if (day.dayState === 'OFF_ALL_DAY') {
                                note = 'nghỉ';
                            } else {
                                note = (day.workingRanges || []).join(' và ');
                                note += (day.freeCount > 0)
                                    ? ' — còn ' + day.freeCount + ' khung'
                                    : ' — đã kín';
                            }
                            const bookable = day.freeCount > 0;
                            const inner = `<div style="font-weight:600;">${day.dayLabel}</div>
                                           <div style="color:#64748b;font-size:12px;">${note}</div>`;
                            html += bookable
                                ? `<a href="${buildAppointmentUrl(av.doctorId, day.date, day.firstFreeSlot)}"
                                      class="text-decoration-none p-2"
                                      style="background:#fff;border:1px solid #bae6fd;border-radius:6px;font-size:13px;color:#334155;min-width:120px;">${inner}</a>`
                                : `<div class="p-2" style="background:#f1f5f9;border-radius:6px;font-size:13px;color:#94a3b8;min-width:120px;">${inner}</div>`;
                        });
                        html += `</div>`;
                    }

                    if (av.summaryText && anchor.reasonText && av.wantsWeek) {
                        html += `<div style="font-size:13px;color:#334155;">${av.summaryText}</div>`;
                    }
                    return html + `</div>`;
                }

                /**
                 * Thoát ký tự HTML. Các thẻ tra cứu khác nội suy thẳng vì dữ liệu là tên bác sĩ và
                 * tên khoa lấy từ DB của chính mình; thẻ hồ sơ ngoại viện thì KHÔNG — nội dung ở đó
                 * là chữ model đọc ra từ một tệp NGƯỜI LẠ tải lên, tức dữ liệu không tin được đi
                 * thẳng vào innerHTML của một trang đã đăng nhập.
                 */
                function escapeHtml(value) {
                    if (value === null || value === undefined) return '';
                    return String(value)
                        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
                }

                function lookupCard(inner) {
                    return `<div class="mt-3 p-3" style="background:#e0f2fe;border-left:4px solid #0ea5e9;border-radius:8px;">${inner}</div>`;
                }

                function buildMyBookingsHtml(data) {
                    if (data.needLogin) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Anh/chị <a href="/login">đăng nhập</a> giúp em để em xem lịch hẹn ạ.
                        </div>`);
                    }
                    if (data.error) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Em chưa tra được lịch hẹn lúc này ạ. Anh/chị thử lại, hoặc xem
                            <a href="/user/profile#booking-history">lịch sử đặt lịch</a> nhé.
                        </div>`);
                    }

                    const upcoming = Array.isArray(data.upcoming) ? data.upcoming : [];
                    const past = Array.isArray(data.past) ? data.past : [];
                    if (upcoming.length === 0 && past.length === 0) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Anh/chị chưa có lịch hẹn nào ạ. Em mở giúp
                            <a href="/appointment">trang đặt lịch</a> nhé?
                        </div>`);
                    }

                    let inner = `<div class="fw-bold mb-2" style="color:#075985;">
                        <i class="bi bi-calendar-check"></i> ${upcoming.length > 0
                            ? 'Anh/chị đang có ' + data.upcomingCount + ' lịch hẹn sắp tới ạ'
                            : 'Anh/chị không còn lịch hẹn nào sắp tới ạ'}
                    </div>`;

                    upcoming.concat(past).forEach(function(b) {
                        const blocked = b.cancelBlockReason;
                        inner += `<div class="mb-2 p-2" style="background:#fff;border-radius:6px;font-size:13px;color:#334155;">
                            <div><strong>BS. ${b.doctorName}</strong> — ${b.departmentName}</div>
                            <div style="color:#64748b;">${b.time || ''} ${b.date ? '· ' + formatDayMonth(b.date) : ''} · ${b.statusLabel}</div>
                            ${blocked
                                ? `<div style="color:#94a3b8;font-size:12px;margin-top:4px;">${blocked}</div>`
                                : `<div class="mt-1 d-flex gap-2">
                                       ${b.rescheduleBlockReason ? '' : `<a href="/user/booking/edit/${b.id}" class="btn btn-sm btn-outline-primary">Dời lịch</a>`}
                                       <a href="/user/profile#booking-history" class="btn btn-sm btn-outline-secondary">Xem chi tiết</a>
                                   </div>`}
                        </div>`;
                    });
                    return lookupCard(inner);
                }

                /**
                 * Thẻ CẤP CỨU — dùng chung cho cả hai đường: khách gõ chữ mô tả triệu chứng nguy
                 * hiểm, và ảnh triệu chứng cho ra urgency = EMERGENCY.
                 *
                 * Trước đây nhánh này chỉ prepend một dòng chữ đỏ rồi CHẠY TIẾP: vẫn dựng thẻ bác
                 * sĩ có link đặt lịch, vẫn vào resolveBookingHandoff, vẫn bật đếm ngược tự nhảy
                 * sang /appointment sau 5 giây. Người đang cần gọi 115 mà trang tự chuyển sang form
                 * đặt lịch là hành vi phải biến mất. Chế độ gọi (`enterEmergency`) đã làm đúng từ
                 * đầu — đây là kéo khung chat gõ chữ về ngang với nó.
                 *
                 * KHÔNG hardcode id khoa Cấp cứu vào đây: cả dự án không có chỗ nào special-case
                 * 21/22, chúng chỉ là chữ trong prompt và id đến từ thứ tự seed. Vả lại không ai
                 * "đặt lịch" cấp cứu — thứ cần là số điện thoại và lời chỉ đường.
                 */
                function buildEmergencyCardHtml(bodyHtml) {
                    return `<div class="p-3" style="background:#fef2f2;border-left:4px solid #dc2626;border-radius:8px;">
                        <div class="fw-bold mb-2" style="color:#b91c1c;">
                            <i class="bi bi-exclamation-triangle-fill"></i> CẢNH BÁO KHẨN CẤP
                        </div>
                        <div style="font-size:14px;color:#334155;">${bodyHtml}</div>
                        <div class="mt-3 d-flex flex-wrap gap-2">
                            <a href="tel:115" class="btn btn-sm btn-danger fw-bold">
                                <i class="bi bi-telephone-fill"></i> Gọi 115 ngay
                            </a>
                            <a href="/departments" class="btn btn-sm btn-outline-danger">Khoa Cấp cứu</a>
                        </div>
                        <div style="font-size:12px;color:#7f1d1d;margin-top:8px;">
                            Anh/chị tới cơ sở y tế gần nhất ngay ạ. Em tạm dừng việc đặt lịch khám ở đây,
                            vì trường hợp này không nên chờ tới lịch hẹn.
                        </div>
                    </div>`;
                }

                /**
                 * Thẻ kết quả đọc ẢNH TRIỆU CHỨNG.
                 *
                 * `escapeHtml` là bắt buộc ở mọi trường: đây là chữ model đọc ra từ một tấm ảnh
                 * người lạ vừa tải lên, đi thẳng vào innerHTML của một trang đã đăng nhập.
                 */
                function buildSymptomResultHtml(sym) {
                    const findings = Array.isArray(sym.findings) ? sym.findings : [];
                    const departments = Array.isArray(sym.departments) ? sym.departments : [];

                    let inner = `<div style="font-size:13px;color:#334155;">
                        Em xem ảnh anh/chị gửi thì thấy${sym.bodyPart ? ' ở <strong>' + escapeHtml(sym.bodyPart) + '</strong>' : ''}:
                    </div>`;

                    if (findings.length > 0) {
                        inner += '<ul class="mb-2 mt-1" style="font-size:13px;color:#334155;padding-left:18px;">';
                        findings.forEach(function(f) { inner += `<li>${escapeHtml(f)}</li>`; });
                        inner += '</ul>';
                    }

                    if (sym.advice) {
                        inner += `<div style="font-size:13px;color:#334155;margin-bottom:6px;">
                            <strong>Trong lúc chờ đi khám:</strong> ${escapeHtml(sym.advice)}
                        </div>`;
                    }

                    if (departments.length > 0) {
                        inner += '<div class="mt-2 d-flex flex-wrap gap-2">';
                        departments.forEach(function(d) {
                            inner += `<a href="/department-details/${d.id}" class="btn btn-sm btn-outline-primary">
                                Xem bác sĩ khoa ${escapeHtml(d.name)}
                            </a>`;
                        });
                        inner += '</div>';
                    }

                    // Hai câu này KHÔNG được bỏ: một câu nói rõ đây không phải chẩn đoán, một câu
                    // nói rõ ảnh đã bị xoá — khách gửi ảnh cơ thể mình có quyền biết nó đi đâu.
                    inner += `<div style="font-size:12px;color:#64748b;margin-top:8px;">
                        ⚠️ Đây chỉ là mô tả những gì nhìn thấy trên ảnh, KHÔNG phải chẩn đoán.
                        Anh/chị cần bác sĩ khám trực tiếp mới kết luận được ạ.
                        <br>Ảnh của anh/chị đã được xoá ngay sau khi em xem, hệ thống không lưu lại.
                    </div>`;
                    return lookupCard(inner);
                }

                function buildMyDocumentsHtml(data) {
                    if (data.needLogin) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Anh/chị <a href="/login">đăng nhập</a> giúp em để em xem hồ sơ đã tải lên ạ.
                        </div>`);
                    }
                    if (data.error) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Em chưa đọc được hồ sơ của anh/chị lúc này ạ. Anh/chị thử lại, hoặc mở
                            <a href="/user/profile#medical-records">tab Hồ sơ y tế</a> nhé.
                        </div>`);
                    }

                    const docs = Array.isArray(data.documents) ? data.documents : [];
                    if (docs.length === 0) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Anh/chị chưa tải hồ sơ bệnh án cũ nào lên ạ. Nếu đã khám ở nơi khác,
                            anh/chị tải ảnh chụp hoặc tệp PDF lên ở
                            <a href="/user/profile#medical-records">tab Hồ sơ y tế</a> để em đọc và
                            tư vấn đúng chuyên khoa hơn nhé.
                        </div>`);
                    }

                    let inner = `<div class="fw-bold mb-2" style="color:#075985;">
                        <i class="bi bi-folder2-open"></i> Anh/chị đang có ${docs.length} hồ sơ bệnh án cũ ạ
                    </div>`;

                    docs.forEach(function(d) {
                        // Bốn trạng thái, không trạng thái nào im lặng — xem ExternalMedicalRecord.
                        let body;
                        if (d.aiStatus === 'DONE') {
                            body = `<div style="color:#334155;white-space:pre-line;">${escapeHtml(d.aiSummary)}</div>`;
                            if (d.departmentId) {
                                // Trỏ tới TRANG KHOA chứ không phải /appointment: form đặt lịch chỉ
                                // nhận doctorId + appointmentDate, một tham số departmentId gắn vào
                                // đó sẽ bị bỏ qua lặng lẽ và khách mở ra thấy form trống trơn.
                                body += `<div class="mt-2">
                                    <a href="/department-details/${d.departmentId}" class="btn btn-sm btn-outline-primary">
                                        Xem bác sĩ khoa ${escapeHtml(d.departmentName || 'được gợi ý')}
                                    </a>
                                </div>`;
                            }
                        } else if (d.aiStatus === 'UNREADABLE' || d.aiStatus === 'FAILED') {
                            body = `<div style="color:#92400e;">${escapeHtml(d.aiSummary || 'Em chưa đọc được tệp này ạ.')}</div>`;
                        } else {
                            body = `<div style="color:#64748b;">Hồ sơ này chưa được phân tích ạ.</div>`;
                        }

                        inner += `<div class="mb-2 p-2" style="background:#fff;border-radius:6px;font-size:13px;">
                            <div><strong>${escapeHtml(d.title || 'Hồ sơ')}</strong>
                                 <span style="color:#64748b;">${d.createdAt ? '· ' + escapeHtml(d.createdAt) : ''}</span></div>
                            ${body}
                            <div class="mt-1"><a href="${escapeHtml(d.fileUrl)}" target="_blank" rel="noopener">Xem tệp gốc</a></div>
                        </div>`;
                    });

                    // Câu miễn trừ BẮT BUỘC: đây là chữ máy đọc từ giấy tờ, không phải chẩn đoán.
                    inner += `<div style="font-size:12px;color:#64748b;margin-top:6px;">
                        Đây là phần em đọc tự động từ giấy tờ anh/chị tải lên nên có thể sai sót.
                        Bác sĩ sẽ đối chiếu bản gốc khi khám ạ.
                    </div>`;
                    return lookupCard(inner);
                }

                /**
                 * Thẻ kết quả sau khi khách đính kèm hồ sơ ngay trong khung chat.
                 *
                 * Dùng lại đúng bốn nhánh trạng thái của `buildMyDocumentsHtml` — DONE / UNREADABLE
                 * / FAILED / PENDING đều phải có câu riêng, không nhánh nào im lặng.
                 */
                function buildUploadResultHtml(doc) {
                    let inner = `<div class="fw-bold mb-2" style="color:#075985;">
                        <i class="bi bi-paperclip"></i> Em đã nhận hồ sơ của anh/chị ạ
                    </div>
                    <div style="font-size:13px;"><strong>${escapeHtml(doc.title || 'Hồ sơ')}</strong></div>`;

                    if (doc.aiStatus === 'DONE') {
                        inner += `<div style="font-size:13px;color:#334155;white-space:pre-line;margin-top:4px;">${escapeHtml(doc.aiSummary)}</div>`;
                        if (doc.departmentId) {
                            inner += `<div class="mt-2">
                                <a href="/department-details/${doc.departmentId}" class="btn btn-sm btn-outline-primary">
                                    Xem bác sĩ khoa ${escapeHtml(doc.departmentName || 'được gợi ý')}
                                </a>
                            </div>`;
                        }
                    } else if (doc.aiStatus === 'UNREADABLE' || doc.aiStatus === 'FAILED') {
                        inner += `<div style="font-size:13px;color:#92400e;margin-top:4px;">${escapeHtml(doc.aiSummary || 'Em chưa đọc được tệp này ạ.')}</div>`;
                    } else {
                        inner += `<div style="font-size:13px;color:#64748b;margin-top:4px;">
                            Em đã lưu hồ sơ nhưng chưa đọc được nội dung ạ. Anh/chị mở
                            <a href="/user/profile#medical-records">tab Hồ sơ y tế</a> bấm "Phân tích lại" giúp em nhé.
                        </div>`;
                    }

                    inner += `<div class="mt-1" style="font-size:12px;">
                        <a href="${escapeHtml(doc.fileUrl)}" target="_blank" rel="noopener">Xem tệp gốc</a>
                        · <a href="/user/profile#medical-records">Quản lý hồ sơ đã tải</a>
                    </div>
                    <div style="font-size:12px;color:#64748b;margin-top:6px;">
                        Đây là phần em đọc tự động từ giấy tờ nên có thể sai sót. Bác sĩ sẽ đối chiếu
                        bản gốc khi anh/chị tới khám ạ.
                    </div>`;
                    return lookupCard(inner);
                }

                function buildDoctorProfileHtml(p) {
                    if (p.error) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Em chưa tra được thông tin bác sĩ lúc này ạ. Anh/chị thử lại giúp em nhé.
                        </div>`);
                    }
                    if (p.doctorNotFound) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Em chưa tìm thấy bác sĩ "${p.requestedDoctorName}" ạ. Anh/chị xem
                            <a href="/doctors">danh sách bác sĩ</a> giúp em nhé.
                        </div>`);
                    }
                    if (p.doctorAmbiguous) {
                        const names = (p.candidates || []).map(function(d) {
                            return `<button class="quick-reply-btn" onclick="window.sendQuickReply('Khám bác sĩ ${String(d.fullName).replace(/'/g, "\\'")} bao nhiêu tiền?', this)">${d.fullName}</button>`;
                        }).join('');
                        return lookupCard(`<div style="font-size:13px;color:#334155;margin-bottom:6px;">
                            Bên em có mấy bác sĩ cùng tên "${p.requestedDoctorName}" ạ, anh/chị chọn giúp em nhé:
                        </div><div class="quick-replies-container">${names}</div>`);
                    }

                    const ratingLine = (p.reviewCount > 0)
                        ? `⭐ ${Number(p.avgRating).toFixed(1)}/5 · ${p.reviewCount} đánh giá`
                        : 'Chưa có đánh giá nào';
                    const priceLine = (p.price !== null && p.price !== undefined)
                        ? Number(p.price).toLocaleString('vi-VN') + ' đ/lần khám'
                        : 'Liên hệ phòng khám';

                    return lookupCard(`
                        <div class="d-flex align-items-center gap-2 mb-2">
                            <img src="${p.avatar}" onerror="this.src='/assets/img/default-doctor.png'"
                                 style="width:48px;height:48px;border-radius:50%;object-fit:cover;">
                            <div style="font-size:13px;color:#334155;">
                                <div><strong>BS. ${p.fullName}</strong>${p.degree ? ' — ' + p.degree : ''}</div>
                                <div style="color:#64748b;">${p.departmentName} · ${p.experienceYears || 0} năm kinh nghiệm</div>
                            </div>
                        </div>
                        <div style="font-size:13px;color:#334155;">
                            <div><i class="bi bi-cash-coin"></i> <strong>${priceLine}</strong></div>
                            <div><i class="bi bi-star"></i> ${ratingLine}</div>
                            ${p.bio ? `<div style="color:#64748b;margin-top:4px;">${p.bio}</div>` : ''}
                        </div>
                        <div class="mt-2 d-flex gap-2">
                            <a href="/doctors/${p.id}" class="btn btn-sm btn-outline-primary">Xem hồ sơ</a>
                            <a href="${buildAppointmentUrl(p.id, '', '')}" class="btn btn-sm btn-primary">Đặt lịch</a>
                        </div>`);
                }

                function buildDoctorFilterHtml(data) {
                    if (data.error) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Em chưa tra được danh sách bác sĩ lúc này ạ. Anh/chị thử lại giúp em nhé.
                        </div>`);
                    }
                    const doctors = Array.isArray(data.doctors) ? data.doctors : [];
                    if (doctors.length === 0) {
                        return lookupCard(`<div style="font-size:13px;color:#334155;">
                            Em chưa tìm được bác sĩ nào khớp tiêu chí đó ạ. Anh/chị xem
                            <a href="/doctors">toàn bộ danh sách bác sĩ</a> giúp em nhé.
                        </div>`);
                    }

                    const criteria = [];
                    if (data.gender) criteria.push('bác sĩ ' + data.gender.toLowerCase());
                    if (data.sortBy === 'price') criteria.push('mức giá thấp nhất');
                    else if (data.sortBy === 'rating') criteria.push('điểm đánh giá thật cao nhất');
                    else if (data.sortBy === 'experience') criteria.push('nhiều kinh nghiệm nhất');

                    const byRating = (data.sortBy === 'rating');
                    const ratedCount = doctors.filter(function(d) { return d.reviewCount > 0; }).length;

                    let inner = `<div class="fw-bold mb-2" style="color:#075985;">
                        <i class="bi bi-person-badge"></i> Em gợi ý anh/chị${criteria.length ? ' ' + criteria.join(', ') : ''} ạ
                    </div>`;
                    if (byRating && ratedCount === 0) {
                        inner += `<div style="font-size:12px;color:#b45309;margin-bottom:6px;">
                            Hiện chưa bác sĩ nào ở đây có lượt đánh giá thật của người bệnh ạ, nên em
                            xếp giúp anh/chị theo số năm kinh nghiệm.
                        </div>`;
                    }

                    let dividerDone = false;
                    doctors.forEach(function(d) {
                        const hasReal = d.reviewCount > 0;
                        const rating = hasReal
                            ? '⭐ ' + Number(d.avgRating).toFixed(1) + ' (' + d.reviewCount + ' đánh giá)'
                            : 'chưa có đánh giá';
                        const price = (d.price !== null && d.price !== undefined)
                            ? Number(d.price).toLocaleString('vi-VN') + ' đ' : '';

                        if (byRating && ratedCount > 0 && !hasReal && !dividerDone) {
                            dividerDone = true;
                            inner += `<div style="font-size:12px;color:#64748b;margin:6px 0 4px;">
                                Các bác sĩ dưới đây chưa có lượt đánh giá nào ạ:
                            </div>`;
                        }
                        inner += `<div class="d-flex align-items-center gap-2 mb-2 p-2" style="background:#fff;border-radius:6px;">
                            <img src="${d.avatar}" onerror="this.src='/assets/img/default-doctor.png'"
                                 style="width:38px;height:38px;border-radius:50%;object-fit:cover;">
                            <div style="flex:1;font-size:13px;color:#334155;">
                                <div><strong>${d.fullName}</strong>${d.degree ? ' — ' + d.degree : ''}</div>
                                <div style="color:#64748b;">${d.experienceYears || 0} năm KN · ${price} · ${rating}</div>
                                ${d.worksOnDate ? '' : '<div style="color:#94a3b8;font-size:12px;">Không có ca làm việc ngày anh/chị hỏi</div>'}
                            </div>
                            <a href="${d.appointmentUrl || buildAppointmentUrl(d.id, d.date, '')}" class="btn btn-sm btn-primary">Chọn</a>
                        </div>`;
                    });
                    return lookupCard(inner);
                }

                function buildAppointmentUrl(doctorId, appointmentDate, appointmentTime) {
                    const url = new URL('/appointment', window.location.origin);
                    if (doctorId) url.searchParams.set('doctorId', doctorId);
                    if (appointmentDate) url.searchParams.set('appointmentDate', appointmentDate);
                    if (appointmentTime) url.searchParams.set('appointmentTime', appointmentTime);
                    return url.toString();
                }

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

                    const exact = doctors.filter(function(doc) {
                        return stripDiacritics(doc.fullName) === want;
                    });
                    if (exact.length === 1) return exact[0];
                    if (exact.length > 1) return ambiguous(exact);

                    const byGivenName = doctors.filter(function(doc) {
                        const words = wordsOf(doc);
                        return words.length > 0 && words[words.length - 1] === wantLast;
                    });
                    if (byGivenName.length === 1) return byGivenName[0];

                    const byAllWords = doctors.filter(function(doc) {
                        const words = wordsOf(doc);
                        return wantWords.every(function(w) { return words.indexOf(w) !== -1; });
                    });
                    if (byAllWords.length === 1) return byAllWords[0];

                    if (byGivenName.length > 1) return ambiguous(byGivenName);
                    if (byAllWords.length > 1) return ambiguous(byAllWords);
                    return null;
                }

                let lastHandoffDate = '';

                function parseWishes(userText, bookingTarget) {
                    bookingTarget = bookingTarget || {};
                    const textTime = normalizeTimeHint(userText);
                    const textSession = extractSessionHint(userText);

                    const time = textTime
                        || (textSession ? '' : normalizeTimeHint(bookingTarget.appointment_time || ''));
                    const session = textTime ? '' : textSession;

                    const textDate = extractDateHint(userText);
                    let date = textDate || '';
                    const dateExplicit = !!date;

                    if (!date && lastHandoffDate && lastHandoffDate >= toIsoDate(new Date())) {
                        date = lastHandoffDate;
                    }
                    if (!date) date = bookingTarget.appointment_date || '';

                    return { time: time, session: session, date: date, dateExplicit: dateExplicit };
                }

                /**
                 * Gắn mong muốn của khách vào URL danh sách bác sĩ, để SERVER xếp hạng theo chỗ
                 * trống thật thay vì trả về thứ tự bảng.
                 *
                 * CỐ Ý KHÔNG gửi `date` khi khách chỉ nêu giờ: lúc đó ngày mới được quyết định bên
                 * trong nhánh A, dựa vào chính response này — vòng tròn. Để server tự quét từ hôm
                 * nay rồi trả `matchedDate` cho biết nó đã chấm cả khoa trên ngày nào.
                 */
                function buildDeptUrl(departmentId, wishes, doctorId) {
                    const url = new URL('/api/chat/doctors/department/' + departmentId, window.location.origin);
                    if (sessionId) url.searchParams.set('sessionId', sessionId);
                    if (doctorId) url.searchParams.set('doctorId', doctorId);
                    if (wishes && wishes.time) url.searchParams.set('time', wishes.time);
                    if (wishes && wishes.session) url.searchParams.set('session', wishes.session);
                    if (wishes && wishes.dateExplicit && wishes.date) url.searchParams.set('date', wishes.date);
                    return url.toString();
                }

                /**
                 * Bác sĩ vừa được HỎI THĂM ở lượt trước, để hiểu câu tiếp "thế mai thì sao?".
                 *
                 * Cố ý là biến RIÊNG, không dùng chung lastHandoffDate: hỏi thăm một bác sĩ không
                 * phải là chốt một ngày khám, ghi đè vào đó sẽ khiến lượt sửa lịch tiếp theo mượn
                 * nhầm ngày khách mới chỉ HỎI chứ chưa hề chọn.
                 */
                let lastAvailabilityDoctor = null;

                /**
                 * Bác sĩ khách đã CHỌN từ danh sách gợi ý (khác hẳn "vừa hỏi thăm" ở trên).
                 *
                 * Đây là một QUYẾT ĐỊNH của khách, nên nó phải sống qua các lượt sau: câu tiếp theo
                 * gần như không bao giờ nhắc lại tên ("chọn luôn giờ cho tôi chiều nay"), mà model
                 * cũng hay quên chép tên vào booking_target. Thiếu biến này thì resolveBookingHandoff
                 * không có ai để ghim và rơi về availableDoctors[0] — tức tự đổi sang bác sĩ khác
                 * ngay sau khi khách vừa mất công so sánh để chọn người.
                 */
                let lastChosenDoctor = null;

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
                        // lý do đó ("hôm đó bác sĩ không đăng ký ca làm việc") mới là thứ khách cần nghe.
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
                        alternatives: opts.alternatives || null,
                        // Bác sĩ cùng khoa cũng trống đúng thứ khách xin — để thẻ đã chốt mời đổi.
                        otherDoctors: opts.otherDoctors || [],
                        // Căn cứ để nói RÕ vì sao chọn người này. null = không có số liệu -> không nêu lý do.
                        pickNearbyLoad: (opts.pickNearbyLoad === undefined) ? null : opts.pickNearbyLoad,
                        pickDayLoad: (opts.pickDayLoad === undefined) ? null : opts.pickDayLoad
                    };
                }

                async function resolveBookingHandoff(aiData, userText) {
                    // Câu HỎI về lịch thì dừng ở đây, kể cả khi booking_intent = true.
                    //
                    // booking_intent DÍNH từ lượt trước — mục 5 của prompt bắt model giữ nguyên mọi
                    // trường khách không nhắc tới — nên giữa một cuộc hội thoại đặt lịch, một câu
                    // hỏi thăm vẫn mang booking_intent = true. Chạy tiếp là đặt soft-lock 3 phút
                    // và bật đếm ngược chuyển trang, cho một CÂU HỎI.
                    //
                    // An toàn vì looksLikeAvailabilityQuestion trả false với mọi câu có chữ "đặt".
                    if (looksLikeAvailabilityQuestion(userText)) return null;
                    // Khách nêu TIÊU CHÍ so sánh nhất ("đặt lịch với bác sĩ đánh giá tốt nhất") thì
                    // nhánh gợi ý bác sĩ trả lời. Chạy tiếp ở đây là vừa in bảng so sánh vừa đặt
                    // chỗ 3 phút và bật đếm ngược cho MỘT người trong bảng — chốt hộ khách đúng cái
                    // việc khách vừa nhờ so sánh. Không như câu hỏi lịch, câu này CÓ chữ "đặt lịch",
                    // nên cửa thoát bên dưới (vốn nhường model và bỏ qua khi có chữ "đặt") không đỡ được.
                    if (looksLikeDoctorFilterQuestion(userText)) return null;
                    // Model cũng nhận ra đây là câu tra cứu -> nhường cho nhánh tra cứu.
                    const lookupType = aiData && aiData.lookup && aiData.lookup.type;
                    if (lookupType && lookupType !== 'none' && !looksLikeBookingRequest(userText)) {
                        return null;
                    }

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

                    // Mong muốn của khách — xem parseWishes để biết vì sao model là nguồn CUỐI CÙNG.
                    const wishes = parseWishes(userText, bookingTarget);
                    const requestedTime = wishes.time;
                    const requestedSession = wishes.session;
                    const requestedDate = wishes.date;
                    const dateExplicit = wishes.dateExplicit;

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

                    // KHÁCH ĐÃ CHỌN BÁC SĨ Ở LƯỢT TRƯỚC THÌ GIỮ NGUYÊN NGƯỜI ĐÓ.
                    //
                    // Câu tiếp theo của khách hầu như không bao giờ nhắc lại tên ("chọn luôn giờ cho
                    // tôi chiều nay"), mà model cũng thường quên chép tên vào booking_target. Không có
                    // cái ghim này thì requestedDoctorName rỗng -> không ai được chỉ đích danh ->
                    // selectedDoctor rơi về availableDoctors[0], tức HỆ THỐNG TỰ ĐỔI SANG BÁC SĨ KHÁC
                    // mà không nói một lời. Khách vừa mất công so đánh giá để chọn người xong.
                    //
                    // Ghim xong, mọi thứ còn lại tự đúng: nhánh /slot-alternatives nhận doctorId nên
                    // khi bác sĩ đó không có ca buổi khách xin, nó trả về reason = OFF_DUTY kèm câu
                    // giải thích, và thẻ "khung giờ kín" nói rõ vì sao thay vì lặng lẽ đổi người.
                    //
                    // Chỉ ghim khi CÙNG KHOA (hoặc chưa biết khoa): khách đổi hẳn triệu chứng sang
                    // khoa khác thì bác sĩ cũ không còn liên quan.
                    if (!candidateDoctorId && lastChosenDoctor && lastChosenDoctor.id) {
                        const sameDept = !candidateDepartmentId
                            || String(candidateDepartmentId) === String(lastChosenDoctor.departmentId);
                        if (sameDept) {
                            candidateDoctorId = lastChosenDoctor.id;
                            candidateDoctorName = lastChosenDoctor.fullName;
                            if (!candidateDepartmentId) candidateDepartmentId = lastChosenDoctor.departmentId;
                        }
                    }

                    if (candidateDoctorId && doctorSearchResult && doctorSearchResult.departmentId) {
                        candidateDepartmentId = doctorSearchResult.departmentId;
                    }

                    let deptLookupReached = false;
                    if (candidateDepartmentId) {
                        try {
                            // Gửi kèm doctorId để backend ghim bác sĩ được chỉ đích danh lên đầu,
                            // không bị cắt mất; và gửi giờ/buổi khách xin để server XẾP HẠNG theo
                            // chỗ trống thật. Thiếu mấy tham số này thì danh sách về theo thứ tự
                            // bảng và availableDoctors[0] luôn là cùng một người, bất kể khách xin giờ nào.
                            const deptRes = await fetch(buildDeptUrl(candidateDepartmentId, wishes, candidateDoctorId));
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
                    // Bác sĩ khách CHỌN TỪ DANH SÁCH ở lượt trước cũng được bảo vệ y như vậy: server
                    // xếp hạng chỉ đổi thứ tự chứ không loại ai, nên không tìm thấy nghĩa là dữ liệu
                    // lệch — im lặng rơi về availableDoctors[0] chính là con bug này.
                    if (!selectedDoctor && (requestedDoctorName || candidateDoctorName)) {
                        return { doctorNotFound: true,
                                 requestedDoctorName: requestedDoctorName || candidateDoctorName };
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
                            // GẮN alternatives cả ở nhánh THÀNH CÔNG. Server đã xếp hạng sẵn mọi bác
                            // sĩ cùng khoa còn trống đúng khung này, lâu nay giao diện vứt hết đi nên
                            // khách không hề biết mình có lựa chọn nào khác.
                            return makeHandoff(Object.assign({}, baseOpts, {
                                date: date, slot: alternatives.slot, fallback: false,
                                alternatives: alternatives,
                                otherDoctors: pickOtherDoctors(alternatives.sameTimeDoctors, selectedDoctor.id, 2),
                                pickNearbyLoad: loadOfPicked(alternatives, selectedDoctor.id, 'nearbyLoad'),
                                pickDayLoad: loadOfPicked(alternatives, selectedDoctor.id, 'dayLoad')
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
                        // Không có ngày thì lấy NGÀY SERVER VỪA CHẤM CẢ KHOA (matchedDate) — chính
                        // xác hơn hẳn parseSlotLabel, vốn phải đoán năm từ nhãn "T5 24/07".
                        // Giữ previewInfo làm lớp đỡ cho payload cũ còn nằm trong cache.
                        const date = requestedDate
                            || selectedDoctor.matchedDate
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
                                date: date, slot: alternatives.slot, fallback: false, suggested: true,
                                alternatives: alternatives,
                                otherDoctors: pickOtherDoctors(alternatives.sameTimeDoctors, selectedDoctor.id, 2),
                                pickNearbyLoad: loadOfPicked(alternatives, selectedDoctor.id, 'nearbyLoad'),
                                pickDayLoad: loadOfPicked(alternatives, selectedDoctor.id, 'dayLoad')
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
                    // Nhánh này KHÔNG có payload /slot-alternatives, và cố ý không gọi thêm một
                    // lượt mạng nữa (đây là nhánh chạy nhiều nhất). Lấy từ availableDoctors — nay
                    // đã được SERVER xếp hạng — và lọc `matchedDate === suggestedDate`: bác sĩ rảnh
                    // thứ Ba tuần sau không phải là "cũng còn trống khung này".
                    const sameDayOthers = availableDoctors
                        .filter(function(d) { return d.matchedDate === suggestedDate && d.matchedSlot; })
                        .map(function(d) {
                            return { id: d.id, fullName: d.fullName, avatar: d.avatar, degree: d.degree,
                                     slot: d.matchedSlot, date: d.matchedDate, slotLabel: d.matchedSlotLabel,
                                     nearbyLoad: d.nearbyLoad, dayLoad: d.dayLoad };
                        });
                    return makeHandoff(Object.assign({}, baseOpts, {
                        date: suggestedDate,
                        slot: suggestedSlot,
                        label: suggestedLabel,
                        fallback: false,
                        suggested: true,
                        otherDoctors: pickOtherDoctors(sameDayOthers, selectedDoctor.id, 2),
                        pickNearbyLoad: (selectedDoctor.nearbyLoad === undefined) ? null : selectedDoctor.nearbyLoad,
                        pickDayLoad: (selectedDoctor.dayLoad === undefined) ? null : selectedDoctor.dayLoad
                    }));
                }

                /**
                 * Khách HỎI về lịch làm việc của một bác sĩ -> trả lời bằng DỮ LIỆU THẬT.
                 *
                 * Trả `null` khi câu này không phải câu hỏi lịch. Ngược lại trả về payload của
                 * /api/chat/doctor-availability, hoặc {doctorAmbiguous} / {doctorNotFound} /
                 * {error:'NETWORK'} — cùng ba kết cục mà nhánh đặt lịch đã dùng, vì cùng một lý do:
                 * im lặng còn tệ hơn nói sai.
                 *
                 * KHÔNG bao giờ tự chọn bác sĩ khi tên khớp nhiều người (xem pickBestDoctorMatch).
                 */
                async function resolveAvailabilityQuestion(aiData, userText) {
                    const lookup = (aiData && aiData.lookup) || {};
                    const askedByModel = lookup.type === 'doctor_schedule';
                    if (!askedByModel && !looksLikeAvailabilityQuestion(userText)) return null;

                    const bookingTarget = (aiData && aiData.booking_target) || {};
                    const requestedName = (lookup.doctor_name
                        || extractDoctorName(userText)
                        || bookingTarget.doctor_name
                        || '').trim();

                    let doctor = null;
                    if (requestedName) {
                        let lookupReached = false;
                        try {
                            const url = new URL('/api/doctors/search', window.location.origin);
                            url.searchParams.set('keyword', requestedName);
                            const res = await fetch(url.toString());
                            if (res.ok) {
                                const doctors = await res.json();
                                if (Array.isArray(doctors)) {
                                    lookupReached = true;
                                    if (doctors.length > 0) doctor = pickBestDoctorMatch(doctors, requestedName);
                                }
                            }
                        } catch (err) {
                            console.error(err);
                        }
                        // Lỗi hạ tầng KHÔNG được báo thành lỗi của khách: nuốt nó rồi kết luận
                        // "chưa tìm thấy bác sĩ X" khiến khách gõ lại đúng tên hàng chục lần vô ích.
                        if (!lookupReached) return { error: 'NETWORK' };
                        if (doctor && doctor.ambiguous) {
                            return { doctorAmbiguous: true, requestedDoctorName: requestedName,
                                     candidates: doctor.candidates };
                        }
                        if (!doctor) {
                            return { doctorNotFound: true, requestedDoctorName: requestedName };
                        }
                    } else if (lastAvailabilityDoctor) {
                        // "thế mai thì sao?" — không có tên nào trong câu, dùng lại người vừa hỏi.
                        doctor = lastAvailabilityDoctor;
                    } else {
                        // Không biết đang hỏi ai thì không đoán bừa; để model trả lời chung chung.
                        return null;
                    }

                    const wantedDate = extractDateHint(userText) || lookup.date || '';
                    const wantedSession = normalizeTimeHint(userText)
                        ? '' : (extractSessionHint(userText) || lookup.session || '');
                    // "tuần này/tuần sau bác sĩ làm ngày nào" -> cần cả dải, không chỉ một ngày.
                    const wantsWeek = lookup.scope === 'week'
                        || /tuần|tuan|ngày nào|ngay nao|hôm nào|hom nao|những ngày|nhung ngay/.test(normalizeText(userText));

                    try {
                        const url = new URL('/api/chat/doctor-availability', window.location.origin);
                        url.searchParams.set('doctorId', doctor.id);
                        if (wantedDate) url.searchParams.set('date', wantedDate);
                        if (wantedSession && wantedSession !== 'evening') {
                            url.searchParams.set('session', wantedSession);
                        }
                        url.searchParams.set('days', wantsWeek ? 7 : 3);
                        if (sessionId) url.searchParams.set('sessionId', sessionId);

                        const res = await fetch(url.toString());
                        if (!res.ok) return { error: 'NETWORK' };
                        const data = await res.json();
                        if (!data || !data.anchor) return { error: 'NETWORK' };

                        lastAvailabilityDoctor = {
                            id: data.doctorId, fullName: data.doctorName,
                            departmentId: data.departmentId, avatar: data.avatar, degree: data.degree
                        };
                        data.wantsWeek = wantsWeek;
                        return data;
                    } catch (err) {
                        console.error(err);
                        return { error: 'NETWORK' };
                    }
                }

                /**
                 * Bộ điều phối cho MỌI câu hỏi TRA CỨU — cửa duy nhất mà sendMessage gọi.
                 *
                 * Trả `null` nếu lượt này không phải câu tra cứu, ngược lại là `{kind, ...}` với
                 * `kind` thuộc availability / my_bookings / doctor_info / doctor_filter.
                 *
                 * KHÔNG nhánh nào ở đây được đặt chỗ, đếm ngược hay ghi lastHandoffDate: khách mới
                 * chỉ HỎI. Muốn đặt thì có LINK để tự bấm.
                 */
                async function resolveLookup(aiData, userText, suppressDocumentsCard) {
                    const lookup = (aiData && aiData.lookup) || {};
                    const type = lookup.type;

                    if (type === 'my_bookings' || looksLikeMyBookingQuestion(userText)) {
                        return await resolveMyBookings();
                    }
                    if (type === 'my_documents' || looksLikeMyDocumentsQuestion(userText)) {
                        // Thẻ hồ sơ vừa in cách đây hai bóng chat — xem skipNextDocumentsCard.
                        // Trả null chứ không rơi tiếp xuống nhánh lịch làm việc: lượt này nói về
                        // hồ sơ, không phải về lịch của bác sĩ nào.
                        if (suppressDocumentsCard) return null;
                        return await resolveMyDocuments();
                    }
                    if (type === 'doctor_filter' || looksLikeDoctorFilterQuestion(userText)) {
                        return await resolveDoctorFilter(aiData, userText);
                    }
                    if (type === 'doctor_info' || looksLikeDoctorInfoQuestion(userText)) {
                        const profile = await resolveDoctorInfo(aiData, userText);
                        if (profile) return profile;
                    }
                    const availability = await resolveAvailabilityQuestion(aiData, userText);
                    return availability ? Object.assign({ kind: 'availability' }, availability) : null;
                }

                async function resolveMyBookings() {
                    // Chưa đăng nhập thì KHÔNG gọi API (nó trả 401): mời đăng nhập ngay tại chỗ.
                    if (window.MEDITRUST_IS_LOGGED_IN !== true) {
                        return { kind: 'my_bookings', needLogin: true };
                    }
                    try {
                        const res = await fetch('/api/chat/my-bookings');
                        if (res.status === 401) return { kind: 'my_bookings', needLogin: true };
                        if (!res.ok) return { kind: 'my_bookings', error: 'NETWORK' };
                        const data = await res.json();
                        return Object.assign({ kind: 'my_bookings' }, data);
                    } catch (err) {
                        console.error(err);
                        return { kind: 'my_bookings', error: 'NETWORK' };
                    }
                }

                async function resolveMyDocuments() {
                    // Chưa đăng nhập thì KHÔNG gọi API (nó trả 401): mời đăng nhập ngay tại chỗ.
                    if (window.MEDITRUST_IS_LOGGED_IN !== true) {
                        return { kind: 'my_documents', needLogin: true };
                    }
                    try {
                        const res = await fetch('/api/chat/my-documents');
                        if (res.status === 401) return { kind: 'my_documents', needLogin: true };
                        if (!res.ok) return { kind: 'my_documents', error: 'NETWORK' };
                        const data = await res.json();
                        return Object.assign({ kind: 'my_documents' }, data);
                    } catch (err) {
                        console.error(err);
                        return { kind: 'my_documents', error: 'NETWORK' };
                    }
                }

                /** Tra một bác sĩ theo tên, dùng chung cho hồ sơ và lịch làm việc. */
                async function findDoctorByName(name) {
                    try {
                        const url = new URL('/api/doctors/search', window.location.origin);
                        url.searchParams.set('keyword', name);
                        const res = await fetch(url.toString());
                        if (!res.ok) return { error: 'NETWORK' };
                        const doctors = await res.json();
                        if (!Array.isArray(doctors) || doctors.length === 0) return null;
                        return pickBestDoctorMatch(doctors, name);
                    } catch (err) {
                        console.error(err);
                        return { error: 'NETWORK' };
                    }
                }

                async function resolveDoctorInfo(aiData, userText) {
                    const lookup = (aiData && aiData.lookup) || {};
                    const name = (lookup.doctor_name || extractDoctorName(userText) || '').trim();
                    let doctor = name ? await findDoctorByName(name) : lastAvailabilityDoctor;

                    if (doctor && doctor.error) return { kind: 'doctor_info', error: 'NETWORK' };
                    // Trùng tên thì HỎI LẠI, không bao giờ tự chọn (xem pickBestDoctorMatch).
                    if (doctor && doctor.ambiguous) {
                        return { kind: 'doctor_info', doctorAmbiguous: true,
                                 requestedDoctorName: name, candidates: doctor.candidates };
                    }
                    if (!doctor) {
                        return name ? { kind: 'doctor_info', doctorNotFound: true, requestedDoctorName: name } : null;
                    }

                    try {
                        const res = await fetch('/api/chat/doctor-profile?doctorId=' + encodeURIComponent(doctor.id));
                        if (!res.ok) return { kind: 'doctor_info', error: 'NETWORK' };
                        const data = await res.json();
                        lastAvailabilityDoctor = { id: data.id, fullName: data.fullName,
                            departmentId: data.departmentId, avatar: data.avatar, degree: data.degree };
                        return Object.assign({ kind: 'doctor_info' }, data);
                    } catch (err) {
                        console.error(err);
                        return { kind: 'doctor_info', error: 'NETWORK' };
                    }
                }

                async function resolveDoctorFilter(aiData, userText) {
                    const raw = normalizeText(userText);
                    const lookup = (aiData && aiData.lookup) || {};
                    const filter = lookup.filter || {};
                    const bookingTarget = (aiData && aiData.booking_target) || {};
                    const deptIds = Array.isArray(aiData && aiData.recommended_departments)
                        ? aiData.recommended_departments : [];

                    let gender = filter.gender || '';
                    if (/bác sĩ nữ|bac si nu/.test(raw)) gender = 'Nữ';
                    else if (/bác sĩ nam|bac si nam/.test(raw)) gender = 'Nam';

                    // Nhận diện tiêu chí ở trình duyệt ĐÈ LÊN model, cùng lý do đã ghi cho
                    // looksLikeAvailabilityQuestion: model bị chính prompt cấm nói về đánh giá và
                    // lịch, nên tín hiệu của nó ở hai chủ đề này yếu hơn.
                    //
                    // "đánh giá" và "chuyên môn" là HAI tiêu chí khác nhau và không được gộp:
                    // đánh giá là điểm bệnh nhân khác chấm, chuyên môn là số năm kinh nghiệm +
                    // học vị. Thẻ trả về nói rõ đã xếp theo tiêu chí nào.
                    let sortBy = filter.sort_by || '';
                    if (/rẻ nhất|re nhat|giá thấp|gia thap/.test(raw)) sortBy = 'price';
                    else if (/đánh giá|danh gia|nhiều sao|nhieu sao|mấy sao|may sao|uy tín|uy tin|nổi tiếng|noi tieng/.test(raw)) sortBy = 'rating';
                    else if (/kinh nghiệm|kinh nghiem|giỏi nhất|gioi nhat|chuyên môn|chuyen mon|tay nghề|tay nghe/.test(raw)) sortBy = 'experience';

                    try {
                        const url = new URL('/api/chat/doctors/filter', window.location.origin);
                        const deptId = bookingTarget.department_id || deptIds[0] || null;
                        if (deptId) url.searchParams.set('departmentId', deptId);
                        if (gender) url.searchParams.set('gender', gender);
                        if (sortBy) url.searchParams.set('sortBy', sortBy);
                        const wantedDate = extractDateHint(userText) || lookup.date || '';
                        if (wantedDate) url.searchParams.set('date', wantedDate);

                        const res = await fetch(url.toString());
                        if (!res.ok) return { kind: 'doctor_filter', error: 'NETWORK' };
                        const doctors = await res.json();
                        if (!Array.isArray(doctors) || doctors.length === 0) {
                            return { kind: 'doctor_filter', doctors: [], gender: gender, sortBy: sortBy };
                        }
                        // Gắn sẵn link đặt lịch vào TỪNG dòng, để nút "Chọn" trên thẻ chat và câu
                        // "em mở trang đặt lịch với bác sĩ X nhé" của chế độ gọi dùng CHUNG một URL.
                        // Dựng riêng ở hai nơi là sớm muộn cũng lệch tham số.
                        doctors.forEach(function(d) {
                            d.appointmentUrl = buildAppointmentUrl(d.id, d.date, '');
                        });
                        return { kind: 'doctor_filter', doctors: doctors, gender: gender, sortBy: sortBy };
                    } catch (err) {
                        console.error(err);
                        return { kind: 'doctor_filter', error: 'NETWORK' };
                    }
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
            // Nơi DUY NHẤT ghi nhớ bác sĩ đang được chốt. Mọi thẻ đã chốt đều đi qua đây (luồng
            // đặt lịch thường, khách chọn hướng thay thế, khách chọn từ danh sách gợi ý), nên các
            // lượt sau bám đúng người vừa hiện trên màn hình thay vì lấy lại phần tử đầu danh sách.
            if (handoff && handoff.doctor && handoff.doctor.id) {
                lastChosenDoctor = {
                    id: handoff.doctor.id,
                    fullName: handoff.doctorName || handoff.doctor.fullName,
                    departmentId: handoff.doctor.departmentId
                };
            }

            // Câu "khung giờ anh/chị vừa chọn" CHỈ được nói khi khách thật sự đã chọn. Khách chưa
            // nêu giờ mà hệ thống tự lấy khung sớm nhất thì phải nhận là EM chọn — nói vống lên
            // chính là thứ khiến khách tưởng đã đặt đúng ý rồi mở trang ra mới thấy khác.
            const inSession = handoff.requestedSession === 'morning' ? ' trong buổi sáng'
                : (handoff.requestedSession === 'afternoon' ? ' trong buổi chiều' : '');
            const picked = handoff.doctorName || handoff.doctor.fullName;
            const askedTime = !!(handoff.requestedTime || handoff.requestedSession);

            // LUẬT: MỌI CÂU SO SÁNH NHẤT PHẢI NÊU RÕ PHẠM VI.
            // Câu cũ "khung giờ trống sớm nhất" được in mỗi khi suggested = true, với ĐÚNG 0 dữ
            // liệu về những bác sĩ khác — trong khi bác sĩ lại do trình duyệt lấy đại phần tử đầu
            // danh sách. Khách xin 9h30 mà đọc "sớm nhất" rồi thấy bác sĩ khác cùng khoa rảnh sớm
            // hơn thì đó là nói sai, không phải nói gọn.
            //
            // Không có số liệu -> KHÔNG nêu lý do. Thà nói ngắn còn hơn nói một câu chưa ai kiểm chứng.
            const why = askedTime
                ? (handoff.pickNearbyLoad !== null ? describeLoad(handoff.pickNearbyLoad) : '')
                : (handoff.pickDayLoad !== null ? describeDayLoad(handoff.pickDayLoad) : '');

            const headline = handoff.suggested
                ? (askedTime
                    ? 'Em chọn giúp anh/chị bác sĩ ' + picked + (why ? ' vì ' + why : '') + ' ạ.'
                    // "sớm nhất CỦA BÁC SĨ" — nêu rõ phạm vi, vì đó đúng là thứ đã kiểm chứng.
                    : 'Em chọn giúp anh/chị bác sĩ ' + picked + ' và khung trống sớm nhất của bác sĩ'
                      + inSession + (why ? ', vì ' + why : '') + ' ạ.')
                : 'Em đã mở đúng bác sĩ và khung giờ anh/chị vừa chọn.';
            const note = handoff.suggested
                ? `<div style="font-size:12px;color:#64748b;margin-top:4px;">
                       Anh/chị đổi sang khung giờ khác ngay trên trang đặt lịch được ạ.
                   </div>`
                : '';

            // Bác sĩ cùng khoa cũng còn trống. CHỈ hiện khi EM tự chọn giúp (suggested): khách đã
            // tự chọn bác sĩ thì mời đổi là đang nghi ngờ lựa chọn của họ.
            // Rỗng thì KHÔNG in gì — câu "trong khoa chỉ còn mỗi bác sĩ X" là một khẳng định phủ
            // định mới mà chưa ai kiểm chứng.
            const others = handoff.suggested ? (handoff.otherDoctors || []) : [];
            let othersHtml = '';
            if (others.length > 0) {
                // Số lượng lấy từ chính danh sách, KHÔNG viết cứng "2": cắt top 3 trừ chính mình
                // có thể chỉ còn 1 người.
                const lead = handoff.requestedTime
                    ? 'Cùng khung <strong>' + handoff.requestedTime + '</strong> còn ' + others.length + ' bác sĩ khác đang trống ạ:'
                    : (handoff.requestedSession
                        // Khách nêu BUỔI thì mỗi người một khung khác nhau — nói "cùng buổi", tuyệt
                        // đối không nói "cùng khung 07:30".
                        ? 'Cùng' + inSession + ' hôm đó còn ' + others.length + ' bác sĩ khác đang trống ạ:'
                        : 'Hôm đó còn ' + others.length + ' bác sĩ khác trong khoa đang trống ạ:');
                othersHtml = `<div style="font-size:13px;color:#334155;margin-top:8px;">${lead}</div>`;
                others.forEach(function(doc) {
                    const reason = handoff.requestedTime || handoff.requestedSession
                        ? describeLoad(doc.nearbyLoad) : describeDayLoad(doc.dayLoad);
                    othersHtml += buildDoctorChoiceRow(doc, handoff.appointmentDate, 'Đổi', reason);
                });
            }

            typingMsg.innerHTML += `
                <div class="mt-3 p-3" style="background: #eef6ff; border-left: 4px solid #0d6efd; border-radius: 8px;">
                    <div class="fw-bold mb-1" style="color: #0d6efd;"><i class="bi bi-calendar-check"></i> ${headline}</div>
                    <div style="font-size: 13px; color: #334155;">
                        <div><strong>Bác sĩ:</strong> ${picked}</div>
                        <div><strong>Lịch hẹn:</strong> ${handoff.selectedSlotLabel}</div>
                    </div>
                    ${note}
                    ${othersHtml}
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

        /**
         * Vừa in thẻ kết quả tải hồ sơ xong thì lượt NGAY SAU đó không in lại danh sách hồ sơ.
         *
         * Câu tự gửi sau khi tải lên BẮT BUỘC phải nhắc tới hồ sơ ("Bạn xem hồ sơ tôi vừa gửi...")
         * — đo bằng model thật: bỏ vế đó đi, hỏi trống "Tôi nên khám chuyên khoa nào?", thì model
         * coi là mô tả mông lung và rơi về khoa 22 (Y học gia đình) thay vì đọc khối tiền sử đã
         * tiêm. Nhưng chính vế đó lại khớp `looksLikeMyDocumentsQuestion`, nên thẻ danh sách hồ sơ
         * in ra ngay dưới thẻ vừa in cách đó hai bóng chat. Cờ này cắt đúng một lần lặp đó.
         *
         * Chỉ sống ĐÚNG MỘT lượt: `sendMessage` đọc rồi xoá ngay, nếu không một câu hỏi thật về hồ
         * sơ ở lượt sau sẽ bị nuốt mất thẻ.
         */
        let skipNextDocumentsCard = false;

        async function sendMessage() {
            if (isSending) return;
            const text = chatInput.value.trim();
            if (!text) return;

            // Khách gõ tiếp nghĩa là họ chưa muốn rời trang.
            cancelRedirect('Em đã dừng việc tự mở trang đặt lịch ạ.');

            isSending = true;
            if (sendBtn) sendBtn.disabled = true;

            // Đọc và xoá NGAY: cờ chỉ có hiệu lực cho đúng lượt này.
            const suppressDocumentsCard = skipNextDocumentsCard;
            skipNextDocumentsCard = false;

            appendMessage('user', text);
            chatInput.value = '';

            // Khách xin DỪNG. Phải xử lý TRƯỚC buildAlternativeContext: ngữ cảnh đó ra lệnh cho
            // model "TUYỆT ĐỐI KHÔNG hỏi lại khách chọn hướng nào", nên gửi câu từ chối kèm nó là
            // ép model chốt một hướng và hệ thống mở trang đặt lịch ngay sau khi khách nói thôi.
            if (parseCancelIntent(text)) {
                pendingAlternatives = null;
                lastHandoffDate = '';
                // Khách dừng hẳn thì bác sĩ đã chọn cũng hết hiệu lực, kẻo lần đặt lịch sau lại
                // bị ghim vào người của cuộc trò chuyện cũ.
                lastChosenDoctor = null;
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
                                                // DỪNG HẲN tại đây: không thẻ bác sĩ, không chốt
                                                // lịch, không đếm ngược chuyển trang. Xem
                                                // buildEmergencyCardHtml để biết vì sao.
                                                typingMsg.innerHTML = buildEmergencyCardHtml(typingMsg.innerHTML);
                                                cancelRedirect('');
                                                pendingAlternatives = null;
                                                safeStorage.setChatHtml(messagesContainer.innerHTML);
                                                notifyReply({ aiData: aiData, userText: text, bookingHandoff: null });
                                                return;
                                            }

                                            // 3. Xử lý đa ý định (Vòng lặp quét mảng recommended_departments)
                                            const deptIds = aiData.recommended_departments;
                                            if (deptIds && Array.isArray(deptIds) && deptIds.length > 0) {
                                                // KHÔNG quảng cáo "đang tạm giữ lịch": việc xem danh sách
                                                // không còn đặt khoá nữa (xem softLockCache ở AiController),
                                                // nên câu đó vừa sai vừa hối thúc khách một cách vô cớ.
                                                let allActionHtml = `<div class="mt-3">`;

                                                // Cùng bộ mong muốn với nhánh chốt lịch bên dưới. Hai nơi mà gửi khác
                                                // nhau thì khách đọc 3 thẻ ở đây rồi thẻ xác nhận bên dưới lại chốt
                                                // một bác sĩ thứ tư không hề có trên màn hình.
                                                const cardWishes = parseWishes(text, aiData.booking_target);
                                                for (let i = 0; i < deptIds.length; i++) {
                                                    const deptId = deptIds[i];
                                                    try {
                                                        const docRes = await fetch(buildDeptUrl(deptId, cardWishes, null));
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

                                            // ===== KHÁCH HỎI TRA CỨU (lịch làm việc, lịch hẹn, hồ sơ bác sĩ) =====
                                            // In câu trả lời THẬT ngay dưới câu của model — đúng cái mà mục 5B của
                                            // prompt đã hứa ("hệ thống lo phần lịch") và cho tới nay chưa hề làm khi
                                            // booking_intent = false.
                                            //
                                            // BA ĐIỀU CÁC NHÁNH NÀY KHÔNG ĐƯỢC LÀM: gán pendingAlternatives, gọi
                                            // /hold-slot, chạy startRedirectCountdown. Cả ba chỉ nằm trong
                                            // finishBookingHandoff, nên chỉ cần KHÔNG gọi hàm đó là đủ. Cũng KHÔNG
                                            // ghi lastHandoffDate: khách mới chỉ HỎI về một ngày, chưa chọn nó.
                                            const lookupResult = await resolveLookup(aiData, text, suppressDocumentsCard);
                                            const availability = (lookupResult && lookupResult.kind === 'availability')
                                                ? lookupResult : null;

                                            if (lookupResult && lookupResult.kind === 'my_bookings') {
                                                typingMsg.innerHTML += buildMyBookingsHtml(lookupResult);
                                            } else if (lookupResult && lookupResult.kind === 'my_documents') {
                                                typingMsg.innerHTML += buildMyDocumentsHtml(lookupResult);
                                            } else if (lookupResult && lookupResult.kind === 'doctor_info') {
                                                typingMsg.innerHTML += buildDoctorProfileHtml(lookupResult);
                                            } else if (lookupResult && lookupResult.kind === 'doctor_filter') {
                                                typingMsg.innerHTML += buildDoctorFilterHtml(lookupResult);
                                            } else if (availability && availability.doctorAmbiguous) {
                                                const names = (availability.candidates || [])
                                                    .map(function(d) {
                                                        return `<button class="quick-reply-btn" onclick="window.sendQuickReply('Bác sĩ ${String(d.fullName).replace(/'/g, "\\'")} có lịch khám hôm nào?', this)">${d.fullName}</button>`;
                                                    }).join('');
                                                typingMsg.innerHTML += `
                                                    <div class="mt-3 p-3" style="background:#e0f2fe;border-left:4px solid #0ea5e9;border-radius:8px;">
                                                        <div class="fw-bold mb-1" style="color:#075985;">
                                                            <i class="bi bi-people"></i> Bên em có mấy bác sĩ cùng tên "${availability.requestedDoctorName}" ạ
                                                        </div>
                                                        <div style="font-size:13px;color:#334155;margin-bottom:6px;">Anh/chị chọn giúp em một người nhé:</div>
                                                        <div class="quick-replies-container">${names}</div>
                                                    </div>`;
                                            } else if (availability && availability.doctorNotFound) {
                                                typingMsg.innerHTML += `
                                                    <div class="mt-3 p-3" style="background:#e0f2fe;border-left:4px solid #0ea5e9;border-radius:8px;">
                                                        <div style="font-size:13px;color:#334155;">
                                                            Em chưa tìm thấy bác sĩ "${availability.requestedDoctorName}" ạ. Anh/chị
                                                            kiểm tra lại tên giúp em, hoặc xem <a href="/doctors">danh sách bác sĩ</a> nhé.
                                                        </div>
                                                    </div>`;
                                            } else if (availability && availability.error) {
                                                // Hỏng hạ tầng thì nói là hỏng hạ tầng, đừng để khách tưởng bác sĩ rảnh.
                                                typingMsg.innerHTML += `
                                                    <div class="mt-3 p-3" style="background:#e0f2fe;border-left:4px solid #0ea5e9;border-radius:8px;">
                                                        <div style="font-size:13px;color:#334155;">
                                                            Em chưa tra được lịch làm việc của bác sĩ lúc này ạ. Anh/chị thử lại
                                                            giúp em, hoặc xem <a href="/doctor-schedule">lịch khám</a> nhé.
                                                        </div>
                                                    </div>`;
                                            } else if (availability) {
                                                typingMsg.innerHTML += buildAvailabilityHtml(availability);
                                            }

                                            // Lưu lại khung HTML (Đã chạy ngầm memory JSON)
                                            safeStorage.setChatHtml(messagesContainer.innerHTML);
                                            // `lookup` mang CẢ NĂM nhánh tra cứu sang lớp gọi thoại.
                                            // Trước đây payload chỉ có `availability`, nên ba nhánh còn lại
                                            // (lịch hẹn của khách, hồ sơ bác sĩ, danh sách gợi ý bác sĩ) in
                                            // ra thẻ chat nhưng KHÔNG BAO GIỜ được đọc lên trong chế độ gọi —
                                            // khách đang gọi rảnh tay thì không nhìn màn hình, coi như câu hỏi
                                            // của họ rơi vào im lặng. `availability` giữ nguyên để không phải
                                            // sửa nhánh đã chạy tốt bên kia.
                                            notifyReply({ aiData: aiData, userText: text, bookingHandoff: null,
                                                          availability: availability, lookup: lookupResult });

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

        // ================= ĐÍNH KÈM HỒ SƠ NGAY TRONG KHUNG CHAT =================
        // Hướng thứ hai bên cạnh trang "Hồ sơ y tế": khách gửi thẳng ảnh/PDF vào khung chat rồi
        // hỏi luôn. Cùng một service, cùng chỗ lưu riêng tư, cùng bộ luật quyền xem — chỉ khác
        // chỗ bắt đầu, nên hồ sơ gửi ở đây vẫn hiện trong trang hồ sơ và vẫn được bác sĩ nhìn thấy.
        const attachBtn = document.getElementById('ai-chat-attach');
        const fileInput = document.getElementById('ai-chat-file');

        // 10MB — khớp spring.servlet.multipart.max-file-size. Chặn ở đây chỉ để khách khỏi ngồi
        // chờ một lượt tải chắc chắn hỏng; máy chủ vẫn là chỗ quyết định.
        const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

        async function uploadImageOrDocumentFromChat(file) {
            if (!file) return;

            // Chưa đăng nhập thì KHÔNG gọi API (nó trả 401 và hồ sơ cũng chẳng biết gắn cho ai).
            if (window.MEDITRUST_IS_LOGGED_IN !== true) {
                appendMessage('bot', lookupCard(`<div style="font-size:13px;color:#334155;">
                    Hồ sơ bệnh án là dữ liệu riêng của anh/chị nên em cần anh/chị
                    <a href="/login">đăng nhập</a> trước khi gửi lên ạ.
                </div>`));
                return;
            }
            if (file.size > MAX_UPLOAD_BYTES) {
                appendMessage('bot', lookupCard(`<div style="font-size:13px;color:#334155;">
                    Tệp này nặng quá 10MB nên em chưa nhận được ạ. Anh/chị chụp lại ở kích thước nhỏ hơn giúp em nhé.
                </div>`));
                return;
            }

            // Dùng CHUNG cờ isSending với sendMessage: đang đọc hồ sơ mà khách gửi tiếp một câu là
            // hai lượt cùng ghi vào một sessionId, đúng lỗi mà cờ này sinh ra để chặn.
            if (isSending) return;
            isSending = true;
            if (sendBtn) sendBtn.disabled = true;
            if (attachBtn) attachBtn.disabled = true;
            cancelRedirect('Em đã dừng việc tự mở trang đặt lịch ạ.');

            appendMessage('user', '<i class="bi bi-paperclip"></i> ' + escapeHtml(file.name));
            const waiting = appendMessage('bot', 'Dạ em đang đọc hồ sơ của anh/chị, chờ em một chút ạ...');

            let doc = null;
            let symptom = null;
            try {
                const form = new FormData();
                form.append('file', file);
                // sessionId để máy chủ ghi được ghi chú vào lịch sử hội thoại — ảnh triệu chứng
                // không lưu ở đâu cả, nên đó là thứ duy nhất giúp model nhớ ở lượt sau.
                if (sessionId) form.append('sessionId', sessionId);
                const res = await fetch('/user/medical-document/chat-upload', { method: 'POST', body: form });

                // PHIÊN HẾT HẠN, không phải lỗi mạng. Spring Security chặn TRƯỚC khi request tới
                // controller và trả 302 sang /login; fetch đi theo redirect đó rồi trả về trang
                // đăng nhập với status 200, nên chỉ xét `res.ok` là rơi vào nhánh catch và báo
                // "không kết nối được" — sai hẳn nguyên nhân, khách sẽ đi kiểm tra wifi.
                // Cùng cái bẫy mà trang /checkout-qr đã phải xử lý.
                if (res.redirected || (res.url && res.url.indexOf('/login') >= 0)) {
                    waiting.innerHTML = lookupCard(`<div style="font-size:13px;color:#334155;">
                        Phiên đăng nhập của anh/chị đã hết hạn ạ. Anh/chị
                        <a href="/login">đăng nhập lại</a> rồi gửi hồ sơ giúp em nhé.
                    </div>`);
                } else if (res.status === 401) {
                    waiting.innerHTML = lookupCard(`<div style="font-size:13px;color:#334155;">
                        Phiên đăng nhập của anh/chị đã hết hạn ạ. Anh/chị
                        <a href="/login">đăng nhập lại</a> rồi gửi hồ sơ giúp em nhé.
                    </div>`);
                } else if (res.status === 400) {
                    // Câu từ chối của FileStorageService (sai định dạng, tệp rỗng) in nguyên văn —
                    // nó đã nói rõ nhận những đuôi nào.
                    const data = await res.json().catch(function() { return {}; });
                    waiting.innerHTML = lookupCard(`<div style="font-size:13px;color:#92400e;">
                        ${escapeHtml(data.message || 'Em chưa nhận được tệp này ạ.')}
                    </div>`);
                } else if (!res.ok) {
                    waiting.innerHTML = lookupCard(`<div style="font-size:13px;color:#334155;">
                        Em chưa tải được hồ sơ lên lúc này ạ. Anh/chị thử lại giúp em nhé.
                    </div>`);
                } else {
                    const data = await res.json();
                    if (data.kind === 'SYMPTOM') {
                        symptom = data.symptom || {};
                        // Ảnh cho thấy dấu hiệu cấp cứu thì đi vào ĐÚNG thẻ đỏ của luồng gõ chữ,
                        // không dựng thẻ đỏ thứ hai trông khác đi.
                        waiting.innerHTML = symptom.isEmergency
                            ? buildEmergencyCardHtml(buildSymptomResultHtml(symptom))
                            : buildSymptomResultHtml(symptom);
                    } else if (data.kind === 'OTHER') {
                        waiting.innerHTML = lookupCard(`<div style="font-size:13px;color:#334155;">
                            ${escapeHtml(data.message || 'Ảnh này em chưa đọc được ạ.')}
                        </div>`);
                    } else {
                        doc = data.document;
                        waiting.innerHTML = buildUploadResultHtml(doc);
                    }
                }
            } catch (err) {
                console.error(err);
                waiting.innerHTML = lookupCard(`<div style="font-size:13px;color:#334155;">
                    Em không kết nối được với hệ thống ạ. Anh/chị kiểm tra mạng rồi gửi lại giúp em nhé.
                </div>`);
            } finally {
                safeStorage.setChatHtml(messagesContainer.innerHTML);
                isSending = false;
                if (sendBtn) sendBtn.disabled = false;
                if (attachBtn) attachBtn.disabled = false;
                // Xoá giá trị để khách chọn LẠI ĐÚNG tệp vừa rồi vẫn kích hoạt được `change`.
                if (fileInput) fileInput.value = '';
            }

            // Đọc xong thì hỏi luôn — đó là lý do khách gửi hồ sơ vào đây thay vì vào trang hồ sơ.
            // Chỉ hỏi khi thật sự có nội dung: hồ sơ không đọc được mà vẫn hỏi "tư vấn khoa nào"
            // là bắt trợ lý trả lời về một thứ nó không có.
            if (doc && doc.aiStatus === 'DONE' && chatInput) {
                skipNextDocumentsCard = true;
                chatInput.value = 'Bạn xem hồ sơ tôi vừa gửi và tư vấn giúp tôi nên khám chuyên khoa nào';
                sendMessage();
                return;
            }

            // Ảnh triệu chứng: hỏi tiếp để trợ lý tư vấn sâu hơn. Câu này xưng "tôi" vì nó được
            // gửi ĐI như lời của khách và hiện trong bóng chat màu xanh của họ.
            //
            // KHÔNG tự hỏi tiếp khi đang cấp cứu: thẻ đỏ đã nói việc cần làm là gọi 115, thêm một
            // lượt tư vấn chuyên khoa bên dưới là kéo sự chú ý ra khỏi đúng thứ đang gấp.
            if (symptom && !symptom.isEmergency && chatInput) {
                skipNextDocumentsCard = true;
                chatInput.value = 'Tôi vừa gửi ảnh chỗ đang bị đau, bạn tư vấn thêm giúp tôi với';
                sendMessage();
            }
        }

        if (attachBtn && fileInput) {
            attachBtn.addEventListener('click', function() { fileInput.click(); });
            fileInput.addEventListener('change', function() {
                uploadImageOrDocumentFromChat(fileInput.files && fileInput.files[0]);
            });
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
            // Chế độ gọi cho khách chọn bác sĩ BẰNG LỜI, không đi qua finishBookingHandoff, nên
            // phải tự báo về đây. Thiếu lời báo này thì lượt sau ("chọn luôn giờ chiều nay")
            // không có ai để ghim và hệ thống lặng lẽ đổi sang bác sĩ khác.
            rememberChosenDoctor: function(doc) {
                lastChosenDoctor = (doc && doc.id)
                    ? { id: doc.id, fullName: doc.fullName, departmentId: doc.departmentId }
                    : null;
            },
            // Dùng chung một hàm dựng URL cho nút "Chọn" trên thẻ và câu mời của loa.
            buildAppointmentUrl: buildAppointmentUrl,
            // Chế độ gọi bật cờ này để tự lo phần xác nhận bằng giọng nói
            // thay vì để khung chat tự nhảy trang sau 900ms.
            suppressAutoRedirect: false,
            onReply: []
        };
    });