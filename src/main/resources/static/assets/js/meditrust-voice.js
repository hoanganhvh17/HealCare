/**
 * MediTrust Voice — lớp giọng nói dùng chung cho tất cả khung chat AI.
 *
 * Dùng Web Speech API có sẵn của trình duyệt (không cần API key, không tốn phí):
 *   - SpeechRecognition  : nghe bệnh nhân nói  (STT)
 *   - speechSynthesis    : đọc câu trả lời     (TTS)
 *
 * NGUYÊN TẮC BÁN SONG CÔNG (half-duplex): luôn tắt mic trước khi đọc, chỉ bật lại
 * khi đọc xong. Web Speech API không khử được tiếng vọng từ loa laptop, nếu vừa nghe
 * vừa nói thì trợ lý sẽ tự nghe chính nó và lặp vô tận.
 *
 * Yêu cầu: Chrome / Microsoft Edge, và trang phải chạy trên localhost hoặc HTTPS.
 */
(function (window, document) {
    'use strict';

    var SpeechRecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;
    var synth = window.speechSynthesis;

    var CONSENT_KEY = 'meditrust_voice_consent';
    var CONSENT_TEXT = 'Để nghe được giọng nói, trình duyệt sẽ chuyển lời nói của bạn thành chữ. '
        + 'Bạn đồng ý bật micro chứ?';

    var voicesCache = [];
    var warnedNoVietnameseVoice = false;
    var activeRecognition = null;   // recognition đang chạy (chỉ cho phép 1 tại một thời điểm)
    var keepAliveTimer = null;      // vá lỗi Chrome tự ngắt TTS sau ~15 giây

    // =====================================================================
    // 1. DÒ KHẢ NĂNG HỖ TRỢ CỦA TRÌNH DUYỆT
    // =====================================================================

    function isRecognitionSupported() {
        // window.isSecureContext = true với https:// và cả http://localhost
        return !!SpeechRecognitionCtor && window.isSecureContext !== false;
    }

    function isSpeechSupported() {
        return !!synth && typeof window.SpeechSynthesisUtterance === 'function';
    }

    function isSupported() {
        return isRecognitionSupported() && isSpeechSupported();
    }

    /** Lý do không dùng được, để hiện thông báo tiếng Việt cho người dùng. */
    function unsupportedReason() {
        if (!SpeechRecognitionCtor) {
            return 'Tính năng nói chuyện cần trình duyệt Chrome hoặc Microsoft Edge.';
        }
        if (window.isSecureContext === false) {
            return 'Tính năng nói chuyện chỉ chạy trên localhost hoặc trang có HTTPS.';
        }
        if (!isSpeechSupported()) {
            return 'Trình duyệt của bạn không đọc được văn bản thành giọng nói.';
        }
        return '';
    }

    // =====================================================================
    // 2. CHỌN GIỌNG ĐỌC TIẾNG VIỆT
    // =====================================================================

    function refreshVoices() {
        if (!isSpeechSupported()) return;
        var list = synth.getVoices();
        if (list && list.length) voicesCache = list;
    }

    // Chrome trả mảng rỗng ở lần gọi đầu, phải chờ sự kiện voiceschanged.
    if (isSpeechSupported()) {
        refreshVoices();
        if (typeof synth.addEventListener === 'function') {
            synth.addEventListener('voiceschanged', refreshVoices);
        } else {
            synth.onvoiceschanged = refreshVoices;
        }
    }

    function pickVietnameseVoice() {
        if (!voicesCache.length) refreshVoices();
        if (!voicesCache.length) return null;

        var exact = null, loose = null;
        for (var i = 0; i < voicesCache.length; i++) {
            var lang = (voicesCache[i].lang || '').toLowerCase().replace('_', '-');
            if (lang === 'vi-vn') { exact = voicesCache[i]; break; }
            if (!loose && lang.indexOf('vi') === 0) loose = voicesCache[i];
        }

        var chosen = exact || loose;
        if (!chosen && !warnedNoVietnameseVoice) {
            warnedNoVietnameseVoice = true;
            console.warn('[MediTrustVoice] Máy chưa cài giọng đọc tiếng Việt (vi-VN). '
                + 'Trợ lý sẽ dùng giọng mặc định nên phát âm sẽ không chuẩn. '
                + 'Cài thêm gói giọng nói tiếng Việt trong Windows Settings > Time & Language > Speech.');
        }
        return chosen;
    }

    // =====================================================================
    // 3. CHUẨN HOÁ VĂN BẢN TRƯỚC KHI ĐỌC
    //    Đây là phần quyết định nghe có tự nhiên hay không.
    // =====================================================================

    var DAY_NAMES = {
        't2': 'thứ Hai', 't3': 'thứ Ba', 't4': 'thứ Tư', 't5': 'thứ Năm',
        't6': 'thứ Sáu', 't7': 'thứ Bảy', 'cn': 'Chủ Nhật'
    };

    /** "09:00" -> "9 giờ"; "09:30" -> "9 rưỡi"; "09:15" -> "9 giờ 15 phút" */
    function speakClock(hh, mm) {
        var hour = parseInt(hh, 10);
        var minute = parseInt(mm, 10);
        if (minute === 0) return hour + ' giờ';
        if (minute === 30) return hour + ' rưỡi';
        return hour + ' giờ ' + minute + ' phút';
    }

    /**
     * Đổi các chuỗi máy móc thành lời nói tự nhiên.
     * Bám đúng định dạng do AiController sinh ra: "T5 24/07 (09:00 - 09:30)".
     */
    function humanizeSchedule(text) {
        var out = text;

        // 0. Ngày ISO "2026-07-24" -> "ngày 24 tháng 7"
        //    (resolveBookingHandoff trả nhãn dạng này khi khách nói rõ ngày giờ)
        out = out.replace(/\b(\d{4})-(\d{1,2})-(\d{1,2})\b/g, function (_, y, mo, d) {
            return 'ngày ' + parseInt(d, 10) + ' tháng ' + parseInt(mo, 10);
        });

        // 1. Khung giờ dạng "09:00 - 09:30" -> "9 giờ đến 9 rưỡi"
        out = out.replace(/(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})/g, function (_, h1, m1, h2, m2) {
            return speakClock(h1, m1) + ' đến ' + speakClock(h2, m2);
        });

        // 2. Giờ lẻ còn sót "09:00" -> "9 giờ"
        out = out.replace(/(\d{1,2}):(\d{2})/g, function (_, h, m) {
            return speakClock(h, m);
        });

        // 3. Ngày dạng "24/07" hoặc "24/07/2026" -> "ngày 24 tháng 7"
        out = out.replace(/\b(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?\b/g, function (_, d, mo) {
            return 'ngày ' + parseInt(d, 10) + ' tháng ' + parseInt(mo, 10);
        });

        // 4. Viết tắt thứ "T5 ngày 24..." -> "thứ Năm ngày 24..."
        out = out.replace(/\b(T[2-7]|CN)\b(?=\s|,|\.|$)/g, function (match) {
            return DAY_NAMES[match.toLowerCase()] || match;
        });

        return out;
    }

    /**
     * Biến HTML/Markdown trong bong bóng chat thành câu đọc được.
     * Dùng chung cho cả khung bệnh nhân (JSON -> HTML) lẫn khung bác sĩ/admin (Markdown).
     */
    function toSpeechText(input) {
        if (!input) return '';
        var raw = String(input);

        // Xuống dòng -> dấu chấm, để giọng đọc ngắt nghỉ đúng chỗ
        raw = raw.replace(/<br\s*\/?>/gi, '. ')
                 .replace(/<\/(p|div|li|h[1-6]|tr)>/gi, '. ');

        // Bỏ hẳn phần giao diện không đọc được (thẻ bác sĩ, nút bấm, ảnh)
        raw = raw.replace(/<(script|style)[\s\S]*?<\/\1>/gi, ' ');

        // Gỡ thẻ HTML còn lại bằng DOM cho an toàn với ký tự đặc biệt
        var tmp = document.createElement('div');
        tmp.innerHTML = raw;
        var text = tmp.textContent || tmp.innerText || '';

        // Gỡ cú pháp Markdown mà khung bác sĩ/admin trả về
        text = text.replace(/```[\s\S]*?```/g, ' ')
                   .replace(/[*_`#>|]/g, ' ')
                   .replace(/\[(.*?)\]\(.*?\)/g, '$1');

        // Bỏ câu cảnh báo y khoa — vẫn hiện trên màn hình, nhưng đọc lên thì quá dài dòng
        text = text.replace(/⚠️?\s*Lưu ý[^.]*?(chẩn đoán chính xác\.?)/gi, ' ');

        // Bỏ emoji và ký hiệu trang trí
        try {
            text = text.replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}\u{2190}-\u{21FF}]/gu, ' ');
        } catch (e) {
            text = text.replace(/[☀-➿️]/g, ' ');
        }

        text = humanizeSchedule(text);

        // Dọn khoảng trắng và dấu chấm thừa
        text = text.replace(/\s+/g, ' ')
                   .replace(/\s*\.\s*(\.\s*)+/g, '. ')
                   .trim();

        // Cắt bớt cho khỏi đọc lê thê, ưu tiên cắt ở cuối câu
        if (text.length > 600) {
            var cut = text.lastIndexOf('.', 600);
            text = text.substring(0, cut > 200 ? cut + 1 : 600);
        }
        return text;
    }

    // =====================================================================
    // 4. ĐỌC VĂN BẢN (TTS)
    // =====================================================================

    function stopSpeaking() {
        if (!isSpeechSupported()) return;
        if (keepAliveTimer) { clearInterval(keepAliveTimer); keepAliveTimer = null; }
        try { synth.cancel(); } catch (e) { /* bỏ qua */ }
    }

    /** Chrome cắt ngang utterance dài, nên chia nhỏ theo câu (~180 ký tự). */
    function splitIntoChunks(text) {
        // Tách sau dấu kết câu. Cố tình KHÔNG dùng lookbehind (?<=...) vì Safari cũ
        // coi đó là lỗi cú pháp và sẽ vứt bỏ toàn bộ file này.
        var sentences = text.match(/[^.!?]+[.!?]*\s*/g) || [text];
        var chunks = [], current = '';

        for (var i = 0; i < sentences.length; i++) {
            var s = sentences[i];
            if ((current + ' ' + s).trim().length > 180 && current) {
                chunks.push(current.trim());
                current = s;
            } else {
                current = current ? current + ' ' + s : s;
            }
        }
        if (current.trim()) chunks.push(current.trim());
        return chunks.length ? chunks : [text];
    }

    /**
     * Đọc to một đoạn văn bản.
     * @param {string} text                 nội dung (đã hoặc chưa chuẩn hoá đều được)
     * @param {object} [opts]               { rate, onStart, onEnd, raw }
     *        opts.raw = true  -> không chạy toSpeechText (dùng cho câu do mình tự viết)
     */
    function speak(text, opts) {
        opts = opts || {};
        var content = opts.raw ? String(text || '') : toSpeechText(text);

        if (!isSpeechSupported() || !content) {
            if (opts.onEnd) opts.onEnd();
            return;
        }

        stopSpeaking();

        var voice = pickVietnameseVoice();
        var chunks = splitIntoChunks(content);
        var finished = 0;
        var started = false;

        chunks.forEach(function (chunk) {
            var u = new window.SpeechSynthesisUtterance(chunk);
            u.lang = 'vi-VN';
            u.rate = typeof opts.rate === 'number' ? opts.rate : 0.95; // chậm hơn mặc định cho người lớn tuổi
            u.pitch = 1;
            if (voice) u.voice = voice;

            u.onstart = function () {
                if (!started) {
                    started = true;
                    if (opts.onStart) opts.onStart();
                }
            };
            u.onend = function () {
                finished++;
                if (finished >= chunks.length) {
                    if (keepAliveTimer) { clearInterval(keepAliveTimer); keepAliveTimer = null; }
                    if (opts.onEnd) opts.onEnd();
                }
            };
            u.onerror = function () {
                finished++;
                if (finished >= chunks.length) {
                    if (keepAliveTimer) { clearInterval(keepAliveTimer); keepAliveTimer = null; }
                    if (opts.onEnd) opts.onEnd();
                }
            };

            synth.speak(u);
        });

        // Vá lỗi Chrome: hàng đợi TTS tự "ngủ" sau ~15 giây nếu không được đánh thức
        if (keepAliveTimer) clearInterval(keepAliveTimer);
        keepAliveTimer = setInterval(function () {
            if (!synth.speaking) {
                clearInterval(keepAliveTimer);
                keepAliveTimer = null;
                return;
            }
            synth.pause();
            synth.resume();
        }, 10000);
    }

    function isSpeaking() {
        return isSpeechSupported() && synth.speaking;
    }

    // =====================================================================
    // 5. NGHE GIỌNG NÓI (STT)
    // =====================================================================

    function hasConsent() {
        try { return localStorage.getItem(CONSENT_KEY) === 'yes'; } catch (e) { return false; }
    }

    /** Xin phép một lần duy nhất, vì đây là dữ liệu sức khoẻ được gửi lên dịch vụ nhận dạng. */
    function ensureConsent() {
        if (hasConsent()) return true;
        var ok = window.confirm(CONSENT_TEXT);
        if (ok) {
            try { localStorage.setItem(CONSENT_KEY, 'yes'); } catch (e) { /* bỏ qua */ }
        }
        return ok;
    }

    function stopListening() {
        if (activeRecognition) {
            try { activeRecognition.abort(); } catch (e) { /* bỏ qua */ }
            activeRecognition = null;
        }
    }

    function friendlyRecognitionError(code) {
        switch (code) {
            case 'not-allowed':
            case 'service-not-allowed':
                return 'Bạn chưa cho phép dùng micro. Hãy bấm vào biểu tượng ổ khoá trên thanh địa chỉ để bật lại.';
            case 'no-speech':
                return 'Em chưa nghe thấy gì cả, bạn thử nói lại giúp em nhé.';
            case 'audio-capture':
                return 'Không tìm thấy micro nào trên máy của bạn.';
            case 'network':
                return 'Mất kết nối tới dịch vụ nhận dạng giọng nói.';
            case 'aborted':
                return '';
            default:
                return 'Micro đang gặp trục trặc, bạn thử lại sau ít phút nhé.';
        }
    }

    /**
     * Tạo một recognition đã cấu hình sẵn cho tiếng Việt.
     * @param {object} cfg { continuous, interimResults, onInterim, onFinal, onError, onEnd, onStart }
     */
    function createRecognition(cfg) {
        cfg = cfg || {};
        var rec = new SpeechRecognitionCtor();
        rec.lang = 'vi-VN';
        rec.continuous = !!cfg.continuous;
        rec.interimResults = cfg.interimResults !== false;
        rec.maxAlternatives = 1;

        rec.onstart = function () { if (cfg.onStart) cfg.onStart(); };

        rec.onresult = function (event) {
            var interim = '', final = '';
            for (var i = event.resultIndex; i < event.results.length; i++) {
                var chunk = event.results[i][0].transcript;
                if (event.results[i].isFinal) final += chunk;
                else interim += chunk;
            }
            if (interim && cfg.onInterim) cfg.onInterim(interim);
            if (final && cfg.onFinal) cfg.onFinal(final.trim());
        };

        rec.onerror = function (event) {
            if (cfg.onError) cfg.onError(event.error, friendlyRecognitionError(event.error));
        };

        rec.onend = function () {
            if (activeRecognition === rec) activeRecognition = null;
            if (cfg.onEnd) cfg.onEnd();
        };

        return rec;
    }

    /**
     * Nghe đúng một lượt rồi tự dừng (chế độ bấm-để-nói).
     * Trả về đối tượng recognition để bên gọi có thể dừng sớm.
     */
    function listenOnce(cfg) {
        cfg = cfg || {};
        if (!isRecognitionSupported()) {
            if (cfg.onError) cfg.onError('unsupported', unsupportedReason());
            return null;
        }
        if (!ensureConsent()) {
            if (cfg.onError) cfg.onError('no-consent', '');
            return null;
        }

        stopSpeaking();   // bán song công: không bao giờ vừa nói vừa nghe
        stopListening();

        var rec = createRecognition({
            continuous: false,
            interimResults: true,
            onStart: cfg.onStart,
            onInterim: cfg.onInterim,
            onFinal: cfg.onFinal,
            onError: cfg.onError,
            onEnd: cfg.onEnd
        });

        activeRecognition = rec;
        try {
            rec.start();
        } catch (e) {
            activeRecognition = null;
            if (cfg.onError) cfg.onError('start-failed', 'Không khởi động được micro, bạn thử lại nhé.');
            return null;
        }
        return rec;
    }

    function stopAll() {
        stopListening();
        stopSpeaking();
    }

    // Chuyển tab / khoá máy thì nhả micro và tắt loa, tránh 2 tab tranh nhau micro
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) stopAll();
    });

    // =====================================================================
    // 6. GẮN NÚT MIC + NÚT LOA VÀO MỘT KHUNG CHAT
    // =====================================================================

    var stylesInjected = false;

    function injectStyles() {
        if (stylesInjected) return;
        stylesInjected = true;

        var style = document.createElement('style');
        style.innerHTML =
            '.mtv-mic-btn {' +
            '  background: #ffffff; color: #0d6efd; border: 1px solid #0d6efd;' +
            '  border-radius: 50%; width: 40px; height: 40px; min-width: 40px; cursor: pointer;' +
            '  display: inline-flex; align-items: center; justify-content: center;' +
            '  font-size: 17px; transition: all .2s ease; flex-shrink: 0; padding: 0;' +
            '}' +
            '.mtv-mic-btn:hover { background: #e7f1ff; }' +
            '.mtv-mic-btn.listening {' +
            '  background: #dc3545; color: #fff; border-color: #dc3545;' +
            '  animation: mtv-pulse 1.2s ease-in-out infinite;' +
            '}' +
            '@keyframes mtv-pulse {' +
            '  0%   { box-shadow: 0 0 0 0 rgba(220,53,69,.55); }' +
            '  70%  { box-shadow: 0 0 0 12px rgba(220,53,69,0); }' +
            '  100% { box-shadow: 0 0 0 0 rgba(220,53,69,0); }' +
            '}' +
            '.mtv-speak-btn {' +
            '  background: transparent; border: none; color: #6c757d; cursor: pointer;' +
            '  font-size: 14px; padding: 2px 6px; margin-top: 6px; border-radius: 4px;' +
            '  display: inline-flex; align-items: center; gap: 4px;' +
            '}' +
            '.mtv-speak-btn:hover { background: #eef2f7; color: #0d6efd; }' +
            '.mtv-speak-btn.speaking { color: #0d6efd; font-weight: 600; }' +
            '.mtv-hint {' +
            '  font-size: 11px; color: #8a94a6; padding: 4px 12px 0; line-height: 1.4;' +
            '}';
        document.head.appendChild(style);
    }

    /**
     * Gắn giọng nói vào một khung chat có sẵn.
     * Không đụng tới hàm render của khung chat: nút loa được gắn bằng MutationObserver,
     * và mọi cú bấm đều đi qua event delegation nên vẫn sống sau khi khôi phục
     * lịch sử chat từ sessionStorage.
     *
     * @param {object} cfg { inputId, sendBtnId, messagesId, botSelector, micTitle }
     */
    function attach(cfg) {
        cfg = cfg || {};
        var input = document.getElementById(cfg.inputId);
        var sendBtn = document.getElementById(cfg.sendBtnId);
        var messages = document.getElementById(cfg.messagesId);

        if (!input || !sendBtn || !messages) return null;
        if (input.dataset.mtvAttached === '1') return null;   // tránh gắn hai lần
        input.dataset.mtvAttached = '1';

        injectStyles();

        var botSelector = cfg.botSelector || '.chat-msg.bot';

        // --- 6a. Trình duyệt không hỗ trợ: báo nhẹ nhàng rồi thôi (KB7) ---
        if (!isSupported()) {
            var hint = document.createElement('div');
            hint.className = 'mtv-hint';
            hint.textContent = unsupportedReason();
            if (input.parentElement && input.parentElement.parentElement) {
                input.parentElement.parentElement.appendChild(hint);
            }
            return null;
        }

        // --- 6b. Nút mic cạnh nút gửi ---
        var micBtn = document.createElement('button');
        micBtn.type = 'button';
        micBtn.className = 'mtv-mic-btn';
        micBtn.title = cfg.micTitle || 'Bấm để nói';
        micBtn.setAttribute('aria-label', micBtn.title);
        micBtn.innerHTML = '<i class="bi bi-mic-fill"></i>';
        sendBtn.parentElement.insertBefore(micBtn, sendBtn);

        var listening = false;
        var placeholderBackup = input.placeholder;

        function setListening(on) {
            listening = on;
            micBtn.classList.toggle('listening', on);
            micBtn.innerHTML = on ? '<i class="bi bi-stop-fill"></i>' : '<i class="bi bi-mic-fill"></i>';
            micBtn.title = on ? 'Đang nghe — bấm để dừng' : (cfg.micTitle || 'Bấm để nói');
            input.placeholder = on ? 'Đang nghe, mời bạn nói...' : placeholderBackup;
        }

        micBtn.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();

            if (listening) {
                stopListening();
                setListening(false);
                return;
            }

            listenOnce({
                onStart: function () { setListening(true); },
                // Hiện chữ dần trong ô nhập để người dùng thấy máy đang nghe được
                onInterim: function (text) { input.value = text; },
                onFinal: function (text) {
                    input.value = text;
                    setListening(false);
                    stopListening();
                    if (text) sendBtn.click();     // nói xong là gửi luôn, không phải bấm thêm
                },
                onError: function (code, message) {
                    setListening(false);
                    if (message) input.placeholder = message;
                    setTimeout(function () { input.placeholder = placeholderBackup; }, 6000);
                },
                onEnd: function () { setListening(false); }
            });
        });

        // --- 6c. Nút loa trên mỗi bong bóng của trợ lý ---
        function decorateBotBubbles() {
            var bubbles = messages.querySelectorAll(botSelector);
            for (var i = 0; i < bubbles.length; i++) {
                var bubble = bubbles[i];
                if (bubble.querySelector('.mtv-speak-btn')) continue;
                if (bubble.querySelector('.typing-dots, .admin-typing-dots')) continue; // đang chờ trả lời
                if (!(bubble.textContent || '').trim()) continue;

                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'mtv-speak-btn';
                btn.setAttribute('data-mtv', 'speak');
                btn.title = 'Nghe câu trả lời này';
                btn.innerHTML = '<i class="bi bi-volume-up-fill"></i> Nghe';
                bubble.appendChild(btn);
            }
        }

        var decorateTimer = null;
        var observer = new MutationObserver(function () {
            // Khung bệnh nhân ghi đè innerHTML nhiều lần mỗi lượt
            // (chấm chờ -> câu trả lời -> thẻ bác sĩ -> nút gợi ý) nên phải giãn nhịp.
            clearTimeout(decorateTimer);
            decorateTimer = setTimeout(decorateBotBubbles, 400);
        });
        observer.observe(messages, { childList: true, subtree: true });
        decorateBotBubbles();

        // Uỷ quyền sự kiện: nút loa được khôi phục từ sessionStorage vẫn bấm được
        messages.addEventListener('click', function (e) {
            var btn = e.target.closest ? e.target.closest('.mtv-speak-btn') : null;
            if (!btn) return;
            e.preventDefault();
            e.stopPropagation();

            if (btn.classList.contains('speaking')) {
                stopSpeaking();
                btn.classList.remove('speaking');
                btn.innerHTML = '<i class="bi bi-volume-up-fill"></i> Nghe';
                return;
            }

            // Chỉ đọc nội dung, bỏ chính cái nút loa ra khỏi văn bản
            var bubble = btn.closest(botSelector) || btn.parentElement;
            var clone = bubble.cloneNode(true);
            var innerBtn = clone.querySelector('.mtv-speak-btn');
            if (innerBtn) innerBtn.remove();

            btn.classList.add('speaking');
            btn.innerHTML = '<i class="bi bi-volume-up-fill"></i> Đang đọc';

            speak(clone.innerHTML, {
                onEnd: function () {
                    btn.classList.remove('speaking');
                    btn.innerHTML = '<i class="bi bi-volume-up-fill"></i> Nghe';
                }
            });
        });

        return { micBtn: micBtn, refresh: decorateBotBubbles };
    }

    // =====================================================================
    // 7. XUẤT RA GLOBAL
    // =====================================================================

    window.MediTrustVoice = {
        isSupported: isSupported,
        isRecognitionSupported: isRecognitionSupported,
        isSpeechSupported: isSpeechSupported,
        unsupportedReason: unsupportedReason,
        hasConsent: hasConsent,
        ensureConsent: ensureConsent,
        toSpeechText: toSpeechText,
        humanizeSchedule: humanizeSchedule,
        pickVietnameseVoice: pickVietnameseVoice,
        speak: speak,
        isSpeaking: isSpeaking,
        stopSpeaking: stopSpeaking,
        createRecognition: createRecognition,
        listenOnce: listenOnce,
        stopListening: stopListening,
        stopAll: stopAll,
        attach: attach
    };

})(window, document);
