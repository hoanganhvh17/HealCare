/**
 * MediTrust Voice Call — chế độ "gọi điện" rảnh tay cho bệnh nhân.
 *
 * Bệnh nhân lớn tuổi bấm một nút rồi nói chuyện bình thường: trợ lý tự nghe,
 * tự phát hiện lúc khách nói xong, tự trả lời bằng giọng nói, và tự hỏi câu
 * xác nhận trước khi mở trang đặt lịch.
 *
 * Phụ thuộc:
 *   - meditrust-voice.js  (nạp trước)
 *   - ai-chat.js          (nạp trước, cung cấp window.MediTrustChat)
 *
 * Vòng đời: idle -> listening -> thinking -> speaking -> listening -> ...
 */
(function (window, document) {
    'use strict';

    var SILENCE_MS = 1100;          // im lặng bao lâu thì coi như khách đã nói xong
    var MAX_UNCLEAR = 2;            // nghe hụt quá số lần này thì mời khách gõ chữ (KB4)
    var MAX_RESTART_BURST = 5;      // chặn vòng lặp khởi động lại vô hạn
    var PENDING_URL_KEY = 'meditrust_pending_booking_url';
    var PENDING_LABEL_KEY = 'meditrust_pending_booking_label';

    var state = 'idle';             // idle | listening | thinking | speaking | emergency
    var recognition = null;
    var silenceTimer = null;
    var finalTranscript = '';
    var interimTranscript = '';
    var shouldListen = false;
    var unclearCount = 0;
    var restartBurst = 0;
    var restartBurstResetAt = 0;
    var awaitingConfirm = null;     // bookingHandoff đang chờ khách nói có/không
    var micMuted = false;
    var els = {};                   // các nút/vùng trên overlay

    function V() { return window.MediTrustVoice; }
    function chat() { return window.MediTrustChat; }

    // =====================================================================
    // 1. NHẬN DIỆN CÂU ĐỒNG Ý / TỪ CHỐI
    // =====================================================================

    var NO_WORDS = /(^|\s)(không|khong|ko|thôi|thoi|khoan|chưa|chua|hủy|huy|dừng)(\s|$|,|\.)/i;
    var YES_WORDS = /(^|\s)(có|co|đồng ý|dong y|vâng|vang|ừ|ù|ok|okay|okie|được|duoc|đúng|dung roi|chốt|chot|xác nhận|xac nhan|nhất trí|tiếp tục|đặt đi|dat di|làm đi)(\s|$|,|\.)/i;

    // Câu có nội dung chỉ dẫn cụ thể ("đổi sang bác sĩ B", "chuyển sang 3 giờ chiều").
    // Những câu này KHÔNG phải lời từ chối suông — phải chuyển thẳng cho AI xử lý,
    // nếu không trợ lý sẽ hỏi lại "đổi bác sĩ hay đổi giờ ạ?" dù khách vừa nói rõ rồi.
    var INSTRUCTION_WORDS = /(đổi|doi|chuyển|chuyen|sang|khác|khac|bác sĩ|bac si|bs |giờ|gio |ngày|ngay |sáng|chiều|tối|thứ|mai)/i;

    /**
     * Trả về 'yes' | 'no' | 'unknown'.
     * 'no' chỉ dành cho lời từ chối suông; câu nào có chỉ dẫn cụ thể thì trả 'unknown'
     * để bên gọi đẩy sang cho AI, vì AI mới đủ ngữ cảnh để làm theo.
     */
    function parseYesNo(text) {
        var raw = (text || '').toLowerCase().trim();
        var t = ' ' + raw + ' ';

        if (NO_WORDS.test(t)) {
            // "thôi, đổi sang bác sĩ B" -> vừa từ chối vừa ra chỉ dẫn: để AI lo
            return INSTRUCTION_WORDS.test(raw) ? 'unknown' : 'no';
        }
        if (YES_WORDS.test(t)) {
            // "được, nhưng đổi sang buổi chiều" -> có chỉ dẫn kèm theo thì cũng để AI lo
            return INSTRUCTION_WORDS.test(raw) ? 'unknown' : 'yes';
        }
        return 'unknown';
    }

    // =====================================================================
    // 2. GIAO DIỆN CUỘC GỌI
    // =====================================================================

    function injectStyles() {
        if (document.getElementById('mtvc-styles')) return;
        var style = document.createElement('style');
        style.id = 'mtvc-styles';
        style.innerHTML =
            '#mtvc-overlay {' +
            '  position: fixed; inset: 0; z-index: 20000; display: none;' +
            '  background: linear-gradient(160deg, #0b2c5c, #0d6efd 55%, #0b5ed7);' +
            '  color: #fff; font-family: Roboto, system-ui, sans-serif;' +
            '  flex-direction: column; align-items: center; justify-content: center;' +
            '  padding: 24px; text-align: center;' +
            '}' +
            '#mtvc-overlay.show { display: flex; }' +
            '#mtvc-overlay.emergency { background: linear-gradient(160deg, #6b0f14, #dc3545 60%, #a71d2a); }' +
            '#mtvc-avatar {' +
            '  width: 132px; height: 132px; border-radius: 50%; object-fit: cover;' +
            '  border: 4px solid rgba(255,255,255,.85); background: #fff; margin-bottom: 18px;' +
            '}' +
            '#mtvc-avatar.pulse { animation: mtvc-pulse 1.5s ease-in-out infinite; }' +
            '@keyframes mtvc-pulse {' +
            '  0%   { box-shadow: 0 0 0 0 rgba(255,255,255,.6); }' +
            '  70%  { box-shadow: 0 0 0 28px rgba(255,255,255,0); }' +
            '  100% { box-shadow: 0 0 0 0 rgba(255,255,255,0); }' +
            '}' +
            '#mtvc-status { font-size: 20px; font-weight: 700; margin-bottom: 8px; letter-spacing: .3px; }' +
            '#mtvc-transcript {' +
            '  font-size: 25px; line-height: 1.45; font-weight: 500; max-width: 760px;' +
            '  min-height: 116px; margin-bottom: 20px; text-shadow: 0 1px 3px rgba(0,0,0,.25);' +
            '  overflow-y: auto; max-height: 42vh; padding: 0 8px;' +
            '}' +
            '#mtvc-transcript .mtvc-you { opacity: .8; font-size: 20px; display: block; margin-top: 10px; }' +
            '#mtvc-actions { display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; }' +
            '.mtvc-btn {' +
            '  border: 2px solid rgba(255,255,255,.75); background: rgba(255,255,255,.12);' +
            '  color: #fff; border-radius: 40px; padding: 14px 26px; font-size: 17px;' +
            '  font-weight: 600; cursor: pointer; transition: all .18s ease;' +
            '  display: inline-flex; align-items: center; gap: 9px; min-height: 54px;' +
            '}' +
            '.mtvc-btn:hover { background: rgba(255,255,255,.26); }' +
            '.mtvc-btn.danger { background: #dc3545; border-color: #dc3545; }' +
            '.mtvc-btn.danger:hover { background: #bb2d3b; }' +
            '.mtvc-btn.solid { background: #fff; color: #0d6efd; border-color: #fff; }' +
            '.mtvc-btn.solid:hover { background: #e7f1ff; }' +
            '.mtvc-btn[hidden] { display: none; }' +
            '#mtvc-note { margin-top: 18px; font-size: 14px; opacity: .8; max-width: 620px; }' +
            '#mtvc-call-btn {' +
            '  background: #198754; color: #fff; border: none; border-radius: 50%;' +
            '  width: 40px; height: 40px; min-width: 40px; cursor: pointer; padding: 0;' +
            '  display: inline-flex; align-items: center; justify-content: center;' +
            '  font-size: 17px; flex-shrink: 0; transition: transform .18s ease;' +
            '}' +
            '#mtvc-call-btn:hover { background: #157347; transform: scale(1.06); }' +
            '#mtvc-resume-bar {' +
            '  position: fixed; left: 50%; transform: translateX(-50%); bottom: 24px; z-index: 19000;' +
            '  background: #fff; border: 1px solid #d6e2f5; border-left: 5px solid #0d6efd;' +
            '  border-radius: 10px; box-shadow: 0 8px 24px rgba(0,0,0,.18); padding: 14px 18px;' +
            '  display: flex; align-items: center; gap: 14px; font-size: 15px; max-width: 92vw;' +
            '}' +
            '@media (max-width: 576px) {' +
            '  #mtvc-transcript { font-size: 21px; }' +
            '  .mtvc-btn { padding: 12px 18px; font-size: 15px; }' +
            '}';
        document.head.appendChild(style);
    }

    function buildOverlay() {
        if (document.getElementById('mtvc-overlay')) return;

        var overlay = document.createElement('div');
        overlay.id = 'mtvc-overlay';
        overlay.innerHTML =
            '<img id="mtvc-avatar" src="/assets/img/health/11zon_cropped.png" alt="Trợ lý MediTrust"' +
            '     onerror="this.style.visibility=\'hidden\'">' +
            '<div id="mtvc-status">Đang kết nối...</div>' +
            '<div id="mtvc-transcript"></div>' +
            '<div id="mtvc-actions">' +
            '  <button type="button" class="mtvc-btn" id="mtvc-interrupt" hidden>' +
            '    <i class="bi bi-hand-index-thumb"></i> Ngắt lời</button>' +
            '  <button type="button" class="mtvc-btn" id="mtvc-mute">' +
            '    <i class="bi bi-mic-mute"></i> Tắt tiếng</button>' +
            '  <button type="button" class="mtvc-btn solid" id="mtvc-keyboard">' +
            '    <i class="bi bi-keyboard"></i> Gõ chữ</button>' +
            '  <a class="mtvc-btn solid" id="mtvc-call-115" href="tel:115" hidden>' +
            '    <i class="bi bi-telephone-fill"></i> Gọi 115</a>' +
            '  <button type="button" class="mtvc-btn danger" id="mtvc-hangup">' +
            '    <i class="bi bi-telephone-x-fill"></i> Kết thúc</button>' +
            '</div>' +
            '<div id="mtvc-note"></div>';
        document.body.appendChild(overlay);

        els = {
            overlay: overlay,
            avatar: document.getElementById('mtvc-avatar'),
            status: document.getElementById('mtvc-status'),
            transcript: document.getElementById('mtvc-transcript'),
            interrupt: document.getElementById('mtvc-interrupt'),
            mute: document.getElementById('mtvc-mute'),
            keyboard: document.getElementById('mtvc-keyboard'),
            call115: document.getElementById('mtvc-call-115'),
            hangup: document.getElementById('mtvc-hangup'),
            note: document.getElementById('mtvc-note')
        };

        els.interrupt.addEventListener('click', function () {
            V().stopSpeaking();
            startListening();
        });

        els.mute.addEventListener('click', function () {
            micMuted = !micMuted;
            els.mute.innerHTML = micMuted
                ? '<i class="bi bi-mic-fill"></i> Bật tiếng'
                : '<i class="bi bi-mic-mute"></i> Tắt tiếng';
            if (micMuted) {
                stopListening();
                setStatus('Đã tắt micro');
            } else if (state !== 'speaking') {
                startListening();
            }
        });

        els.keyboard.addEventListener('click', function () { hangUp(true); });
        els.hangup.addEventListener('click', function () { hangUp(false); });
    }

    function setStatus(text) {
        if (els.status) els.status.textContent = text;
    }

    function setTranscript(assistantText, userText) {
        if (!els.transcript) return;
        var html = '';
        if (assistantText) html += escapeHtml(assistantText);
        if (userText) html += '<span class="mtvc-you">Anh/chị: ' + escapeHtml(userText) + '</span>';
        els.transcript.innerHTML = html;
        els.transcript.scrollTop = els.transcript.scrollHeight;
    }

    function escapeHtml(s) {
        var d = document.createElement('div');
        d.textContent = s == null ? '' : String(s);
        return d.innerHTML;
    }

    function setState(next) {
        state = next;
        if (!els.avatar) return;

        els.avatar.classList.toggle('pulse', next === 'listening' || next === 'speaking');
        els.interrupt.hidden = next !== 'speaking';

        if (next === 'listening') setStatus(micMuted ? 'Đã tắt micro' : 'Em đang nghe anh/chị nói...');
        else if (next === 'thinking') setStatus('Em đang suy nghĩ...');
        else if (next === 'speaking') setStatus('Em đang trả lời...');
    }

    // =====================================================================
    // 3. NGHE (có tự phát hiện khách nói xong)
    // =====================================================================

    function clearSilenceTimer() {
        if (silenceTimer) { clearTimeout(silenceTimer); silenceTimer = null; }
    }

    function armSilenceTimer() {
        clearSilenceTimer();
        silenceTimer = setTimeout(function () {
            var said = (finalTranscript + ' ' + interimTranscript).trim();
            stopListening();
            if (said) {
                unclearCount = 0;
                handleUserSpeech(said);
            } else {
                handleUnclear();
            }
        }, SILENCE_MS);
    }

    function stopListening() {
        shouldListen = false;
        clearSilenceTimer();
        if (recognition) {
            try { recognition.abort(); } catch (e) { /* bỏ qua */ }
            recognition = null;
        }
    }

    function startListening() {
        if (state === 'emergency' || micMuted) return;

        V().stopSpeaking();          // bán song công: không bao giờ vừa nói vừa nghe
        stopListening();

        finalTranscript = '';
        interimTranscript = '';
        shouldListen = true;
        setState('listening');
        setTranscript('', '');

        recognition = V().createRecognition({
            continuous: true,
            interimResults: true,
            onInterim: function (text) {
                interimTranscript = text;
                setTranscript('', (finalTranscript + ' ' + text).trim());
                armSilenceTimer();
            },
            onFinal: function (text) {
                finalTranscript = (finalTranscript + ' ' + text).trim();
                interimTranscript = '';
                setTranscript('', finalTranscript);
                armSilenceTimer();
            },
            onError: function (code, message) {
                if (code === 'not-allowed' || code === 'service-not-allowed') {
                    shouldListen = false;
                    setStatus('Không dùng được micro');
                    els.note.textContent = message;
                    setTimeout(function () { hangUp(true); }, 4000);
                } else if (code === 'no-speech') {
                    // để onend tự khởi động lại
                }
            },
            onEnd: function () {
                // Chrome tự tắt sau khoảng 60 giây im lặng -> bật lại,
                // nhưng phải chặn vòng lặp vô hạn khi micro bị chặn.
                if (!shouldListen || state === 'emergency' || micMuted) return;

                var now = Date.now();
                if (now > restartBurstResetAt) { restartBurst = 0; restartBurstResetAt = now + 3000; }
                restartBurst++;
                if (restartBurst > MAX_RESTART_BURST) {
                    shouldListen = false;
                    setStatus('Micro không ổn định');
                    els.note.textContent = 'Micro liên tục ngắt kết nối. Anh/chị gõ chữ giúp em nhé.';
                    setTimeout(function () { hangUp(true); }, 4000);
                    return;
                }
                setTimeout(function () { if (shouldListen) startListening(); }, 250);
            }
        });

        if (!recognition) return;
        try {
            recognition.start();
        } catch (e) {
            recognition = null;
        }
    }

    /** KB4 — nghe hụt. Thử lại tối đa 2 lần rồi mời khách gõ chữ. */
    function handleUnclear() {
        unclearCount++;
        if (unclearCount === 1) {
            say('Dạ em nghe chưa rõ, anh/chị nói lại giúp em nhé.', function () { startListening(); });
            return;
        }
        if (unclearCount >= MAX_UNCLEAR) {
            say('Em xin lỗi, em nghe chưa được rõ. Anh/chị gõ giúp em vào khung chat nhé.', function () {
                hangUp(true);
            });
            return;
        }
        startListening();
    }

    // =====================================================================
    // 4. NÓI
    // =====================================================================

    function say(text, onDone, opts) {
        opts = opts || {};
        stopListening();
        setState('speaking');
        setTranscript(text, '');

        V().speak(text, {
            raw: opts.raw === true,
            rate: opts.rate,
            onEnd: function () {
                if (state === 'emergency') return;
                if (onDone) onDone();
            }
        });
    }

    // =====================================================================
    // 5. XỬ LÝ MỘT LƯỢT HỘI THOẠI
    // =====================================================================

    function handleUserSpeech(text) {
        // Đang chờ khách chốt lịch thì câu nói này là câu trả lời có/không (KB1, KB9)
        if (awaitingConfirm) {
            var answer = parseYesNo(text);
            if (answer === 'yes') { confirmBooking(); return; }
            if (answer === 'no') {
                awaitingConfirm = null;
                say('Dạ vâng ạ. Anh/chị muốn đổi sang giờ khác hay đổi bác sĩ ạ?', function () { startListening(); });
                return;
            }
            // Nói gì đó khác hẳn ("đổi sang buổi chiều") -> để AI xử lý tiếp
            awaitingConfirm = null;
        }

        setState('thinking');
        setTranscript('', text);

        var input = document.getElementById('ai-chat-input');
        if (!input || !chat()) {
            say('Dạ hệ thống đang bận, anh/chị thử lại sau ít phút giúp em nhé.', function () { hangUp(false); });
            return;
        }
        input.value = text;
        chat().sendMessage();     // kết quả quay lại qua subscriber onReply bên dưới
    }

    /** Được ai-chat.js gọi sau mỗi lượt trả lời, kể cả khi lỗi. */
    function onChatReply(payload) {
        if (state === 'idle' || state === 'emergency') return;

        if (payload.error) {
            say('Dạ hệ thống đang bận. Anh/chị nói lại giúp em nhé.', function () { startListening(); });
            return;
        }

        var aiData = payload.aiData;

        // Model trả về text thường thay vì JSON -> vẫn đọc được
        if (!aiData) {
            say(payload.rawText || 'Dạ em chưa hiểu ý anh/chị, anh/chị nói lại giúp em nhé.',
                function () { startListening(); });
            return;
        }

        // KB3 — cấp cứu: dừng hẳn luồng đặt lịch
        if (aiData.is_emergency) {
            enterEmergency(aiData);
            return;
        }

        var spoken = aiData.speech_reply || V().toSpeechText(aiData.ai_reply);

        // Khách nêu đích danh bác sĩ nhưng hệ thống không tìm ra: phải nói thật,
        // không được lặng lẽ chốt sang bác sĩ khác.
        if (payload.bookingHandoff && payload.bookingHandoff.doctorNotFound) {
            awaitingConfirm = null;
            say('Dạ em chưa tìm thấy bác sĩ ' + payload.bookingHandoff.requestedDoctorName
                + '. Anh/chị đọc lại tên giúp em, hoặc để em giữ bác sĩ đang gợi ý ạ?',
                function () { startListening(); });
            return;
        }

        // KB1 — đã chốt được bác sĩ + khung giờ: hỏi xác nhận rồi mới điều hướng
        if (payload.bookingHandoff) {
            awaitingConfirm = payload.bookingHandoff;
            var doctorName = payload.bookingHandoff.doctorName
                || (payload.bookingHandoff.doctor && payload.bookingHandoff.doctor.fullName)
                || 'bác sĩ';
            var slot = V().humanizeSchedule(payload.bookingHandoff.selectedSlotLabel || '');
            var question = 'Em đặt lịch với ' + doctorName + ', ' + slot + '. Anh/chị xác nhận giúp em nhé?';

            say(spoken + ' ' + question, function () { startListening(); });
            return;
        }

        say(spoken, function () { startListening(); });
    }

    /** KB3 — chuyển overlay sang chế độ cảnh báo cấp cứu. */
    function enterEmergency(aiData) {
        stopListening();
        setState('emergency');
        awaitingConfirm = null;

        els.overlay.classList.add('emergency');
        els.avatar.classList.remove('pulse');
        els.call115.hidden = false;
        els.mute.hidden = true;
        els.interrupt.hidden = true;
        setStatus('CẢNH BÁO KHẨN CẤP');

        var spoken = aiData.speech_reply || V().toSpeechText(aiData.ai_reply);
        var warning = 'Đây có thể là dấu hiệu cấp cứu. Anh/chị hãy gọi ngay số một một năm, '
            + 'hoặc tới phòng cấp cứu gần nhất.';

        setTranscript(warning + ' ' + spoken, '');
        els.note.textContent = 'Trợ lý đã tạm dừng để anh/chị liên hệ cấp cứu.';
        // Đọc chậm hơn hẳn cho rõ từng chữ
        V().speak(warning + ' ' + spoken, { rate: 1.0 });
    }

    // =====================================================================
    // 6. CHỐT LỊCH
    // =====================================================================

    function confirmBooking() {
        var handoff = awaitingConfirm;
        awaitingConfirm = null;
        if (!handoff) { startListening(); return; }

        // KB5 — chưa đăng nhập thì /appointment sẽ đá về trang login mà khách không hiểu vì sao
        if (window.MEDITRUST_IS_LOGGED_IN === false) {
            try {
                sessionStorage.setItem(PENDING_URL_KEY, handoff.appointmentUrl);
                sessionStorage.setItem(PENDING_LABEL_KEY, handoff.selectedSlotLabel || '');
            } catch (e) { /* bỏ qua */ }

            say('Dạ anh/chị đăng nhập giúp em một lát để em giữ chỗ khung giờ này ạ.', function () {
                window.location.href = '/login';
            });
            return;
        }

        say('Dạ, em mở trang đặt lịch cho anh/chị ngay ạ.', function () {
            window.location.href = handoff.appointmentUrl;
        });
    }

    /** KB5 (phần sau) — quay lại sau khi đăng nhập thì hỏi có muốn đặt tiếp không. */
    function offerPendingBooking() {
        var url, label;
        try {
            url = sessionStorage.getItem(PENDING_URL_KEY);
            label = sessionStorage.getItem(PENDING_LABEL_KEY);
        } catch (e) { return; }

        if (!url || window.MEDITRUST_IS_LOGGED_IN !== true) return;
        try {
            sessionStorage.removeItem(PENDING_URL_KEY);
            sessionStorage.removeItem(PENDING_LABEL_KEY);
        } catch (e) { /* bỏ qua */ }

        var bar = document.createElement('div');
        bar.id = 'mtvc-resume-bar';
        bar.innerHTML =
            '<div><strong>Tiếp tục đặt lịch?</strong><br>' +
            '<span style="color:#5b6b82;font-size:14px;">' + escapeHtml(label || 'Lịch hẹn anh/chị vừa chọn') + '</span></div>' +
            '<button type="button" class="btn btn-primary btn-sm" id="mtvc-resume-yes">Đặt tiếp</button>' +
            '<button type="button" class="btn btn-light btn-sm" id="mtvc-resume-no">Để sau</button>';
        document.body.appendChild(bar);

        document.getElementById('mtvc-resume-yes').addEventListener('click', function () {
            window.location.href = url;
        });
        document.getElementById('mtvc-resume-no').addEventListener('click', function () {
            bar.remove();
        });
    }

    // =====================================================================
    // 7. BẮT ĐẦU / KẾT THÚC CUỘC GỌI
    // =====================================================================

    function startCall() {
        if (!V() || !V().isSupported()) {
            window.alert(V() ? V().unsupportedReason() : 'Trình duyệt không hỗ trợ giọng nói.');
            return;
        }
        if (!V().ensureConsent()) return;
        if (state !== 'idle') return;

        buildOverlay();
        els.overlay.classList.remove('emergency');
        els.overlay.classList.add('show');
        els.call115.hidden = true;
        els.mute.hidden = false;
        els.note.textContent = '';
        micMuted = false;
        unclearCount = 0;
        awaitingConfirm = null;
        restartBurst = 0;

        // Mở khung chat phía sau để kết thúc cuộc gọi là thấy nguyên đoạn hội thoại
        if (chat() && chat().openChat) chat().openChat();
        if (chat()) chat().suppressAutoRedirect = true;

        setState('speaking');
        setStatus('Đang kết nối...');

        // Dùng lại câu chào cá nhân hoá sẵn có (biết tên và bệnh cũ của khách)
        fetch('/api/chat/welcome')
            .then(function (r) { return r.ok ? r.text() : ''; })
            .catch(function () { return ''; })
            .then(function (greeting) {
                var text = (greeting || '').trim()
                    || 'Dạ em chào anh/chị, em là trợ lý MediTrust. Anh/chị đang thấy trong người thế nào ạ?';
                say(text, function () { startListening(); });
            });
    }

    /**
     * @param {boolean} toKeyboard true = chuyển sang gõ chữ (mở sẵn khung chat),
     *                             false = tắt hẳn.
     */
    function hangUp(toKeyboard) {
        stopListening();
        V().stopAll();
        state = 'idle';
        awaitingConfirm = null;
        micMuted = false;

        if (chat()) chat().suppressAutoRedirect = false;
        if (els.overlay) {
            els.overlay.classList.remove('show', 'emergency');
            els.mute.innerHTML = '<i class="bi bi-mic-mute"></i> Tắt tiếng';
        }

        if (toKeyboard) {
            var input = document.getElementById('ai-chat-input');
            if (input) { if (chat() && chat().openChat) chat().openChat(); input.focus(); }
        }
    }

    // =====================================================================
    // 8. KHỞI TẠO
    // =====================================================================

    function addCallButton() {
        var sendBtn = document.getElementById('ai-chat-send');
        if (!sendBtn || document.getElementById('mtvc-call-btn')) return;

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.id = 'mtvc-call-btn';
        btn.title = 'Gọi cho trợ lý — nói chuyện không cần gõ';
        btn.setAttribute('aria-label', btn.title);
        btn.innerHTML = '<i class="bi bi-telephone-fill"></i>';
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            startCall();
        });
        sendBtn.parentElement.insertBefore(btn, sendBtn);
    }

    function init() {
        if (!V()) return;

        // Luôn chạy, kể cả khi trình duyệt không hỗ trợ giọng nói,
        // vì phần "đặt tiếp sau khi đăng nhập" là thao tác bấm chuột bình thường.
        offerPendingBooking();

        if (!V().isSupported()) return;              // KB7 — không hiện nút gọi
        if (!document.getElementById('ai-chat-input')) return;   // trang không có khung chat bệnh nhân

        injectStyles();
        addCallButton();

        if (chat() && chat().onReply) chat().onReply.push(onChatReply);
    }

    // ai-chat.js cũng chạy trong DOMContentLoaded, nên đợi thêm một nhịp
    // để chắc chắn window.MediTrustChat đã tồn tại.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { setTimeout(init, 0); });
    } else {
        setTimeout(init, 0);
    }

    window.MediTrustVoiceCall = {
        start: startCall,
        hangUp: hangUp,
        parseYesNo: parseYesNo,
        getState: function () { return state; }
    };

})(window, document);
