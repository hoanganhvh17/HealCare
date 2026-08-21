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
    // Danh sách bác sĩ vừa ĐỌC LÊN, đang chờ khách chọn một người ("người thứ hai", "bác sĩ Bình").
    // Khác hẳn awaitingConfirm: ở đây chưa có lịch hẹn nào, nên một tiếng "vâng" KHÔNG được
    // chốt gì cả — xem describeDoctorFilter.
    var pendingChoiceList = null;
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
        // Vừa đọc xong danh sách bác sĩ -> câu này rất có thể là "người thứ hai" / "bác sĩ Bình".
        //
        // Phải xử lý TẠI CHỖ, trước khi gửi cho model: model KHÔNG hề biết em vừa đọc những ai
        // (danh sách do hệ thống tra ra, không nằm trong hội thoại), nên hỏi nó thì nó chỉ có thể
        // hỏi ngược lại khách — đúng cái vòng lặp mà resolveAlternativeChoice bên ai-chat.js sinh
        // ra để cắt.
        //
        // Không nhận ra thì trả null và chuyển tiếp cho model. TUYỆT ĐỐI không đoán bừa: đoán sai
        // là mở trang đặt lịch với một bác sĩ khách không hề chọn.
        if (pendingChoiceList) {
            var picked = resolveDoctorChoice(text, pendingChoiceList);
            if (picked) {
                pendingChoiceList = null;
                setTranscript('', text);
                offerNearestSlot(picked);
                return;
            }
            pendingChoiceList = null;
        }

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

        // Mọi lượt trả lời MỚI đều làm danh sách vừa đọc hết hiệu lực; nhánh doctor_filter bên
        // dưới sẽ gán lại nếu lượt này lại là một danh sách. Không xoá thì lượt sau khách nói
        // "người thứ hai" và bị chọn vào một danh sách đã bỏ qua từ lâu — đúng cái bẫy đã ghi
        // cho pendingAlternatives bên ai-chat.js.
        pendingChoiceList = null;

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

        // Việc đặt lịch hỏng vì hệ thống. Handoff dạng này KHÔNG có bác sĩ lẫn khung giờ, nên nếu
        // để rơi xuống nhánh xác nhận bên dưới thì một tiếng "vâng" sẽ chốt một lịch hẹn rỗng.
        if (payload.bookingHandoff && payload.bookingHandoff.error) {
            var WHY_VOICE = {
                NETWORK: 'Dạ em chưa kết nối được tới danh sách bác sĩ. Anh/chị thử lại giúp em ạ.',
                NO_DOCTORS: 'Dạ chuyên khoa này hiện chưa có bác sĩ nào nhận lịch ạ.',
                NO_DEPARTMENT: 'Dạ em chưa rõ anh/chị muốn khám chuyên khoa nào. Anh/chị mô tả thêm triệu chứng giúp em ạ.'
            };
            awaitingConfirm = null;
            say(WHY_VOICE[payload.bookingHandoff.error] || WHY_VOICE.NETWORK,
                function () { startListening(); });
            return;
        }

        // Nhiều bác sĩ trùng tên: đọc danh sách ra để khách chọn, tuyệt đối không tự quyết.
        if (payload.bookingHandoff && payload.bookingHandoff.doctorAmbiguous) {
            var names = (payload.bookingHandoff.candidates || []).map(function (d) { return d.fullName; });
            awaitingConfirm = null;
            say('Dạ bên em có ' + names.length + ' bác sĩ cùng tên '
                + payload.bookingHandoff.requestedDoctorName + ': ' + names.join(', ')
                + '. Anh/chị chọn giúp em một người ạ?',
                function () { startListening(); });
            return;
        }

        // Khách nêu đích danh bác sĩ nhưng hệ thống không tìm ra: phải nói thật,
        // không được lặng lẽ chốt sang bác sĩ khác.
        if (payload.bookingHandoff && payload.bookingHandoff.doctorNotFound) {
            awaitingConfirm = null;
            say('Dạ em chưa tìm thấy bác sĩ ' + payload.bookingHandoff.requestedDoctorName
                + '. Anh/chị đọc lại tên giúp em, hoặc để em giữ bác sĩ đang gợi ý ạ?',
                function () { startListening(); });
            return;
        }

        // Không đặt được khung giờ khách xin: báo NGAY từ đầu, kèm LÝ DO, rồi đưa hướng thay thế.
        // KHÔNG đọc lại câu của model ở đây — model không nhìn thấy lịch làm việc nên nó hay
        // nói "em đã ghi nhận giờ khám" rồi mâu thuẫn ngay với thực tế.
        if (payload.bookingHandoff && payload.bookingHandoff.fallback) {
            // Nhánh này LUÔN là câu hỏi MỞ ("đổi bác sĩ hay đổi giờ ạ?", "tìm bác sĩ khác nhé?"),
            // nên câu trả lời phải chuyển cho AI chứ không đưa vào parseYesNo. Nhất là khi hết
            // hướng thay thế: handoff lúc đó KHÔNG có khung giờ nào, để khách "vâng" một cái là
            // confirmBooking() điều hướng sang một lịch hẹn trống rỗng.
            awaitingConfirm = null;
            say(describeSlotFull(payload.bookingHandoff), function () { startListening(); });
            return;
        }

        // Khách HỎI về lịch làm việc của bác sĩ (không phải xin đặt lịch).
        //
        // BẮT BUỘC awaitingConfirm = null và BẮT BUỘC kết bằng câu hỏi MỞ: ở đây không có lịch hẹn
        // nào để một tiếng "vâng" chốt cả. Thiếu nhánh này thì lớp gọi rơi thẳng xuống say(spoken)
        // và câu trả lời về lịch — thứ duy nhất khách hỏi — không bao giờ được đọc lên.
        if (payload.availability) {
            awaitingConfirm = null;
            say(spoken + ' ' + describeAvailability(payload.availability),
                function () { startListening(); });
            return;
        }

        // Bốn nhánh tra cứu còn lại. Cùng luật với nhánh lịch làm việc ở trên: awaitingConfirm = null
        // và kết bằng câu hỏi MỞ. Thiếu chúng thì thẻ chat có nội dung mà chế độ gọi im lặng —
        // đúng lúc khách rảnh tay, không nhìn màn hình.
        var lookup = payload.lookup;
        if (lookup && lookup.kind === 'doctor_filter') {
            awaitingConfirm = null;
            var offer = describeDoctorFilter(lookup);
            // Giữ ĐÚNG những người vừa đọc lên, không nhiều hơn.
            pendingChoiceList = offer.offered.length ? offer.offered : null;
            // CỐ Ý KHÔNG đọc lại câu của model ở nhánh này, y như nhánh "khung giờ kín": model bị
            // chính prompt cấm nói về đánh giá và lịch, nên câu của nó chỉ là một lời dẫn rỗng
            // ("Dạ em xem giúp anh/chị ngay ạ") — đọc thêm là tốn mất một phần ngân sách hơi đọc
            // cho chữ không mang tin, và có nguy cơ mâu thuẫn với chính danh sách ngay sau đó.
            say(offer.text, function () { startListening(); });
            return;
        }
        if (lookup && lookup.kind === 'doctor_info') {
            awaitingConfirm = null;
            say(spoken + ' ' + describeDoctorProfile(lookup), function () { startListening(); });
            return;
        }
        if (lookup && lookup.kind === 'my_bookings') {
            awaitingConfirm = null;
            say(spoken + ' ' + describeMyBookings(lookup), function () { startListening(); });
            return;
        }
        if (lookup && lookup.kind === 'my_documents') {
            awaitingConfirm = null;
            say(spoken + ' ' + describeMyDocuments(lookup), function () { startListening(); });
            return;
        }

        // KB1 — đã chốt được bác sĩ + khung giờ: hỏi xác nhận rồi mới điều hướng
        if (payload.bookingHandoff) {
            awaitingConfirm = payload.bookingHandoff;
            var doctorName = payload.bookingHandoff.doctorName
                || (payload.bookingHandoff.doctor && payload.bookingHandoff.doctor.fullName)
                || 'bác sĩ';
            var slot = V().humanizeSchedule(payload.bookingHandoff.selectedSlotLabel || '');
            // Khách chưa nêu giờ nào mà hệ thống tự chọn giúp thì phải nói rõ là EM xếp, y như
            // thẻ chat — nói "em đặt lịch ... anh/chị xác nhận" là ngầm bảo khách đã chọn giờ đó.
            //
            // Câu cũ đọc "là khung trống sớm nhất ạ" mà chưa hề so với bác sĩ nào khác. Nay nêu
            // LÝ DO THẬT lấy từ số liệu server; không có số liệu thì không nêu lý do.
            // CỐ Ý KHÔNG đọc danh sách bác sĩ khác thành tiếng: nó biến câu hỏi có/không thành một
            // menu, mà menu là việc của nhánh fallback.
            var askedTime = !!(payload.bookingHandoff.requestedTime || payload.bookingHandoff.requestedSession);
            var whyVoice = askedTime
                ? (payload.bookingHandoff.pickNearbyLoad !== null && payload.bookingHandoff.pickNearbyLoad !== undefined
                    ? describeLoadVoice(payload.bookingHandoff.pickNearbyLoad) : '')
                : (payload.bookingHandoff.pickDayLoad !== null && payload.bookingHandoff.pickDayLoad !== undefined
                    ? describeDayLoadVoice(payload.bookingHandoff.pickDayLoad) : '');

            var question = payload.bookingHandoff.suggested
                ? 'Em chọn giúp anh/chị bác sĩ ' + doctorName + ', ' + slot
                    + (whyVoice ? ', vì ' + whyVoice : '') + '. Anh/chị thấy được không ạ?'
                : 'Em đặt lịch với ' + doctorName + ', ' + slot + '. Anh/chị xác nhận giúp em nhé?';

            say(spoken + ' ' + question, function () { startListening(); });
            return;
        }

        say(spoken, function () { startListening(); });
    }

    /** Vì sao em gợi ý bác sĩ này — khách cần nghe lý do, không chỉ nghe cái tên. */
    function describeLoadVoice(nearbyLoad) {
        if (!nearbyLoad) return 'quanh giờ đó bác sĩ chưa có ca nào nên anh/chị gần như không phải chờ';
        if (nearbyLoad <= 2) return 'quanh giờ đó bác sĩ chỉ có ' + nearbyLoad + ' ca nên anh/chị ít phải chờ';
        return 'quanh giờ đó bác sĩ có ' + nearbyLoad + ' ca khám';
    }

    /**
     * Bản dùng khi khách KHÔNG nêu giờ nào. "quanh giờ đó" lúc ấy trỏ vào một mốc khách chưa hề
     * nhắc tới. Bản song sinh của describeDayLoad trong ai-chat.js — sửa một bên thì sửa cả hai,
     * kẻo thẻ chat và câu đọc nói khác nhau.
     */
    function describeDayLoadVoice(dayLoad) {
        if (!dayLoad) return 'hôm đó bác sĩ chưa có ca nào nên anh/chị gần như không phải chờ';
        if (dayLoad <= 3) return 'hôm đó bác sĩ mới có ' + dayLoad + ' ca nên anh/chị ít phải chờ';
        return 'hôm đó bác sĩ có ' + dayLoad + ' ca khám';
    }

    /**
     * Câu đọc khi không đặt được khung giờ khách xin: nói thật LÝ DO, rồi đưa đúng hai hướng chọn.
     *
     * Lý do lấy nguyên văn `reasonText` do server dựng — chuỗi đó cố ý viết theo định dạng máy
     * ("T3 28/07", "13:30 - 17:30") nên humanizeSchedule đọc thành lời được, khỏi cần bản riêng.
     */
    function describeSlotFull(handoff) {
        var alt = handoff.alternatives || {};
        var wanted = V().humanizeSchedule(handoff.requestedTime || '');
        var sameTime = alt.sameTimeDoctors || [];
        var otherTimes = alt.otherTimes || [];

        var text = alt.reasonText
            ? 'Dạ ' + V().humanizeSchedule(alt.reasonText) + ' '
            : 'Dạ khung giờ ' + wanted + ' đã kín lịch rồi ạ. ';

        // Đổi NGÀY phải nói ngay sau lý do, TRƯỚC mọi hướng chọn: nghe mỗi giờ mà không nghe ngày
        // là khách gật đầu rồi đến sai hôm.
        if (alt.otherTimesMovedDay && alt.otherTimesText) {
            text += V().humanizeSchedule(alt.otherTimesText) + ' ';
        }

        if (sameTime.length > 0) {
            text += 'Em gợi ý anh/chị bác sĩ ' + sameTime[0].fullName
                + ' cùng chuyên khoa, còn nhận ' + V().humanizeSchedule(sameTime[0].slotLabel || wanted)
                + ', vì ' + describeLoadVoice(sameTime[0].nearbyLoad) + '. ';
        }
        if (otherTimes.length > 0) {
            // slotLabel có kèm thứ/ngày: gợi ý có thể đã phải rơi sang ngày khác vì hôm khách xin
            // bác sĩ nghỉ. Đọc mỗi giờ mà giấu ngày là khách đến sai hôm.
            text += 'Hoặc anh/chị giữ bác sĩ ' + (alt.requestedDoctorName || handoff.doctorName || 'hiện tại')
                + ' và chuyển sang ' + V().humanizeSchedule(otherTimes[0].slotLabel || otherTimes[0].slot) + '. ';
        }

        if (sameTime.length === 0 && otherTimes.length === 0) {
            return text + 'Em chưa tìm được khung giờ nào thay thế trong tuần này ạ. '
                + 'Anh/chị muốn em tìm bác sĩ khác cùng chuyên khoa không ạ?';
        }
        return text + 'Anh/chị chọn hướng nào ạ?';
    }

    /**
     * Câu đọc khi khách HỎI về lịch làm việc của một bác sĩ.
     *
     * Kết bằng câu hỏi MỞ, không bao giờ câu có/không: khách chưa xin đặt gì, một tiếng "vâng" ở
     * đây mà bị hiểu thành xác nhận là tạo lịch hẹn khách không hề yêu cầu.
     *
     * Lý do lấy nguyên văn reasonText/summaryText của server — viết theo định dạng máy nên
     * humanizeSchedule đọc thành lời được, không cần bản riêng cho loa.
     */
    function describeAvailability(av) {
        if (av.doctorAmbiguous) {
            var names = (av.candidates || []).map(function (d) { return d.fullName; });
            return 'Dạ bên em có ' + names.length + ' bác sĩ cùng tên ' + av.requestedDoctorName
                + ': ' + names.join(', ') + '. Anh/chị hỏi về bác sĩ nào ạ?';
        }
        if (av.doctorNotFound) {
            return 'Dạ em chưa tìm thấy bác sĩ ' + av.requestedDoctorName
                + '. Anh/chị đọc lại tên giúp em ạ?';
        }
        if (av.error) {
            return 'Dạ em chưa tra được lịch làm việc lúc này ạ. Anh/chị thử lại giúp em nhé?';
        }

        var anchor = av.anchor || {};
        var text = anchor.reasonText ? 'Dạ ' + V().humanizeSchedule(anchor.reasonText) + ' ' : '';

        // Ngày khách hỏi bác sĩ không làm -> đọc thêm vài ngày bác sĩ CÓ làm, để khách không phải
        // hỏi lại từng ngày một.
        var working = (av.week || []).filter(function (d) {
            return d.freeCount > 0 && d.date !== anchor.date;
        }).slice(0, 2);
        if (!anchor.freeCount && working.length > 0) {
            text += 'Bác sĩ còn nhận khám ' + working.map(function (d) {
                return V().humanizeSchedule(d.dayLabel);
            }).join(' và ') + '. ';
        }

        return text + 'Anh/chị muốn em xem lịch ngày nào ạ?';
    }

    // =====================================================================
    // 5b. ĐỌC DANH SÁCH GỢI Ý VÀ CHO KHÁCH CHỌN BẰNG LỜI
    // =====================================================================

    // Đọc TỐI ĐA 3 người, và cắt bớt nữa nếu câu vượt NGÂN SÁCH KÝ TỰ bên dưới.
    var MAX_DOCTORS_READ = 3;
    var ORDINAL_WORDS = ['nhất', 'hai', 'ba'];

    // Một hơi đọc của trình duyệt chỉ chịu được MAX_CHUNK_CHARS = 300 ký tự (~13 giây, xem
    // meditrust-voice.js). Vượt ngưỡng là splitIntoChunks xé thành nhiều utterance, mà giữa hai
    // utterance trình duyệt LUÔN chèn một quãng nghỉ thật — đúng tiếng "đứng hình giữa câu" đã
    // phải đi sửa một lần rồi. Chừa 30 ký tự cho những cái tên dài bất thường.
    var SPEECH_BUDGET = 270;

    var CRITERION_VOICE = {
        rating: 'theo điểm đánh giá thật của người bệnh',
        experience: 'theo số năm kinh nghiệm',
        price: 'theo mức giá khám từ thấp lên'
    };

    /**
     * "4.8" -> "4 phẩy 8". Giọng Việt đọc dấu chấm thập phân thành "chấm" nghe rất máy móc,
     * mà số sao là con số khách nghe để so sánh nên phải rõ.
     */
    function speakNumber(value) {
        // Number(null) là 0 chứ không phải NaN, nên phải loại rỗng TRƯỚC khi ép kiểu — bằng không
        // một bác sĩ chưa ai chấm (avgRating = null) sẽ được đọc lên là "0 sao".
        if (value === null || value === undefined || value === '') return '';
        var n = Number(value);
        if (!isFinite(n)) return '';
        var rounded = Math.round(n * 10) / 10;
        var whole = Math.floor(rounded);
        var decimal = Math.round((rounded - whole) * 10);
        return decimal === 0 ? String(whole) : whole + ' phẩy ' + decimal;
    }

    /** Từ cuối trong họ tên — cách người Việt gọi nhau. Bản song sinh của lastNameWord bên ai-chat.js. */
    function lastNameWord(fullName) {
        var words = String(fullName || '').toLowerCase().trim().split(/\s+/).filter(Boolean);
        return words.length ? words[words.length - 1] : '';
    }

    /**
     * Đọc danh sách bác sĩ gợi ý rồi MỜI KHÁCH CHỌN.
     *
     * Ba điều bắt buộc, đều là bản sao bằng lời của luật đã áp cho thẻ chat:
     *  - Nói rõ ĐANG XẾP THEO TIÊU CHÍ GÌ. Đọc trống một dãy tên thì khách không có gì để so,
     *    mà "tốt nhất" đo bằng đánh giá hay bằng kinh nghiệm là hai chuyện khác hẳn.
     *  - Bác sĩ chưa ai chấm phải nói thẳng "chưa có lượt đánh giá nào". Trang /doctors cố ý
     *    quảng cáo 5 sao cho họ; khung chat và loa đều không được nhắc lại con số đó.
     *  - Đọc kèm SỐ LƯỢT đánh giá, vì đó là thứ duy nhất phân biệt điểm thật với điểm mặc định.
     *
     * Luôn kết bằng câu hỏi MỞ ("chọn người nào ạ?"), không bao giờ câu có/không: ở đây chưa
     * có lịch hẹn nào để một tiếng "vâng" chốt.
     */
    function describeDoctorFilter(data) {
        if (data.error) {
            return { text: 'Dạ em chưa tra được danh sách bác sĩ lúc này ạ. Anh/chị thử lại giúp em nhé?',
                     offered: [] };
        }
        var all = data.doctors || [];
        if (!all.length) {
            return { text: 'Dạ em chưa tìm được bác sĩ nào khớp tiêu chí đó ạ. '
                         + 'Anh/chị cho em biết thêm chuyên khoa hoặc yêu cầu khác nhé?',
                     offered: [] };
        }

        var byRating = data.sortBy === 'rating';
        var ratedCount = all.filter(function (d) { return d.reviewCount > 0; }).length;

        var intro = (byRating && ratedCount === 0)
            ? 'Dạ chưa bác sĩ nào có lượt đánh giá thật của người bệnh, nên em xếp theo số năm kinh nghiệm. '
            : 'Dạ em xếp ' + (CRITERION_VOICE[data.sortBy] || CRITERION_VOICE.experience) + '. ';

        // MỖI BÁC SĨ ĐÚNG MỘT DỮ KIỆN, và dữ kiện đó chính là tiêu chí đang xếp. Đọc kèm cả
        // kinh nghiệm lẫn giá lẫn số sao là vừa dài (vỡ ngân sách một hơi đọc) vừa khiến khách
        // không biết đang so bằng cái gì — nghe thì không lướt lại được như nhìn thẻ trên màn hình.
        // MỌI DÒNG PHẢI CÙNG MỘT ĐƠN VỊ. Đọc người này "5 sao" người kia "18 năm kinh nghiệm" là
        // khách không so được gì cả — nghe thì không lướt ngược lên được như nhìn thẻ trên màn hình.
        function factOf(d) {
            if (byRating) {
                return d.reviewCount > 0
                    ? speakNumber(d.avgRating) + ' sao sau ' + d.reviewCount + ' lượt đánh giá'
                    : 'chưa có lượt đánh giá nào';
            }
            if (data.sortBy === 'price' && d.price !== null && d.price !== undefined) {
                return Math.round(Number(d.price) / 1000) + ' nghìn đồng';
            }
            return (d.experienceYears || 0) + ' năm kinh nghiệm';
        }

        function assemble(list) {
            var lines = list.map(function (d, i) {
                return 'Thứ ' + ORDINAL_WORDS[i] + ', bác sĩ ' + d.fullName + ', ' + factOf(d);
            });
            var left = all.length - list.length;
            return intro + lines.join('. ') + '.'
                + (left > 0 ? ' Em còn ' + left + ' bác sĩ nữa.' : '')
                + ' Anh/chị chọn người nào ạ?';
        }

        // Cắt dần cho tới khi lọt một hơi đọc. Danh sách trả về PHẢI đúng bằng những người vừa
        // đọc lên: giữ lại nhiều hơn thì "người thứ ba" sẽ chọn trúng một cái tên khách chưa
        // từng nghe.
        var offered = all.slice(0, MAX_DOCTORS_READ);
        var text = assemble(offered);
        while (offered.length > 1 && text.length > SPEECH_BUDGET) {
            offered = offered.slice(0, offered.length - 1);
            text = assemble(offered);
        }
        return { text: text, offered: offered };
    }

    /** Hồ sơ MỘT bác sĩ: giá khám và đánh giá thật. */
    function describeDoctorProfile(p) {
        if (p.error) return 'Dạ em chưa tra được thông tin bác sĩ lúc này ạ. Anh/chị thử lại giúp em nhé?';
        if (p.doctorNotFound) {
            return 'Dạ em chưa tìm thấy bác sĩ ' + p.requestedDoctorName + '. Anh/chị đọc lại tên giúp em ạ?';
        }
        if (p.doctorAmbiguous) {
            var names = (p.candidates || []).map(function (d) { return d.fullName; });
            return 'Dạ bên em có ' + names.length + ' bác sĩ cùng tên ' + p.requestedDoctorName
                + ': ' + names.join(', ') + '. Anh/chị hỏi về bác sĩ nào ạ?';
        }

        var parts = ['Dạ bác sĩ ' + p.fullName];
        if (p.experienceYears) parts.push(p.experienceYears + ' năm kinh nghiệm');
        if (p.price !== null && p.price !== undefined) {
            parts.push('giá khám ' + Math.round(Number(p.price) / 1000) + ' nghìn đồng một lần');
        }
        // Điều kiện là SỐ LƯỢT chứ không phải điểm: getAverageRating trả 0.0 chứ không null.
        parts.push(p.reviewCount > 0
            ? 'được ' + speakNumber(p.avgRating) + ' sao sau ' + p.reviewCount + ' lượt đánh giá'
            : 'chưa có lượt đánh giá nào');

        return parts.join(', ') + '. Anh/chị muốn em xem lịch khám của bác sĩ không ạ?';
    }

    /** Lịch hẹn của CHÍNH khách. Chỉ đọc 2 lịch gần nhất — phần còn lại đã có trên màn hình. */
    function describeMyBookings(data) {
        if (data.needLogin) {
            return 'Dạ anh/chị đăng nhập giúp em thì em mới tra được lịch hẹn của mình ạ. '
                + 'Anh/chị muốn em mở trang đăng nhập không ạ?';
        }
        if (data.error) return 'Dạ em chưa tra được lịch hẹn lúc này ạ. Anh/chị thử lại giúp em nhé?';

        var upcoming = data.upcoming || [];
        if (!upcoming.length) {
            return 'Dạ anh/chị chưa có lịch hẹn nào sắp tới ạ. Anh/chị muốn em đặt lịch giúp không ạ?';
        }
        var lines = upcoming.slice(0, 2).map(function (b) {
            return 'bác sĩ ' + b.doctorName + ', ' + V().humanizeSchedule((b.time || '') + ', ' + (b.date || ''));
        });
        var extra = upcoming.length > 2 ? ' Anh/chị còn ' + (upcoming.length - 2) + ' lịch nữa.' : '';
        return 'Dạ anh/chị có ' + upcoming.length + ' lịch hẹn sắp tới: '
            + lines.join('. ') + '.' + extra + ' Anh/chị cần em giúp gì thêm ạ?';
    }

    /**
     * Hồ sơ bệnh án cũ khách đã tải lên. Cùng luật với ba nhánh tra cứu kia: kết bằng câu hỏi MỞ,
     * và người gọi đã đặt awaitingConfirm = null — không có lịch hẹn nào để một tiếng "vâng" chốt.
     *
     * Đọc TỐI ĐA MỘT hồ sơ và cắt bản tóm tắt cho lọt SPEECH_BUDGET. Vượt ngân sách là
     * splitIntoChunks xé thành hai utterance và trình duyệt chèn một quãng nghỉ thật ngay giữa
     * câu — đúng lỗi "trợ lý đứng hình" đã sửa ở meditrust-voice.js.
     */
    function describeMyDocuments(data) {
        if (data.needLogin) {
            return 'Dạ anh/chị đăng nhập giúp em thì em mới xem được hồ sơ đã tải lên ạ. '
                + 'Anh/chị muốn em mở trang đăng nhập không ạ?';
        }
        if (data.error) {
            return 'Dạ em chưa đọc được hồ sơ của anh/chị lúc này ạ. Anh/chị thử lại giúp em nhé?';
        }

        var docs = data.documents || [];
        if (!docs.length) {
            return 'Dạ anh/chị chưa tải hồ sơ bệnh án cũ nào lên ạ. Nếu anh/chị đã khám ở nơi khác, '
                + 'anh/chị vào mục Hồ sơ y tế tải ảnh chụp giấy khám lên, em sẽ đọc giúp. '
                + 'Giờ anh/chị muốn em hỗ trợ gì ạ?';
        }

        var done = docs.filter(function (d) { return d.aiStatus === 'DONE'; });
        if (!done.length) {
            // Không có bản nào đọc được: nói ĐÚNG lý do của hồ sơ mới nhất thay vì một câu chung
            // chung — bản scan và lỗi hệ thống cần hai hành động khác nhau từ khách.
            var first = docs[0];
            if (first.aiStatus === 'UNREADABLE') {
                return 'Dạ hồ sơ ' + first.title + ' là bản scan nên em chưa đọc được chữ ạ. '
                    + 'Anh/chị chụp ảnh từng trang rồi tải lên giúp em nhé?';
            }
            return 'Dạ em chưa phân tích xong hồ sơ ' + first.title + ' ạ. '
                + 'Anh/chị vào mục Hồ sơ y tế bấm Phân tích lại giúp em nhé?';
        }

        var doc = done[0];
        var head = 'Dạ anh/chị có ' + docs.length + ' hồ sơ cũ. Hồ sơ ' + doc.title + ': ';
        var tail = doc.departmentName
            ? ' Theo hồ sơ này thì khoa ' + doc.departmentName + ' là phù hợp nhất ạ. '
              + 'Anh/chị muốn em xem bác sĩ khoa đó không ạ?'
            : ' Anh/chị muốn em tư vấn thêm gì ạ?';

        // Cắt bản tóm tắt cho vừa một hơi đọc. Xuống dòng thành dấu phẩy: toSpeechText biến mỗi
        // lần xuống dòng thành một dấu chấm, mà giọng Việt nghỉ ở dấu chấm lâu hơn hẳn.
        var summary = String(doc.aiSummary || '').replace(/\s*\n+\s*/g, ', ').trim();
        var room = SPEECH_BUDGET - head.length - tail.length;
        if (room > 40 && summary.length > room) {
            summary = summary.slice(0, room - 3).trim() + '...';
        }
        return head + summary + tail;
    }

    /**
     * Khách vừa nghe danh sách và nói người mình chọn: theo THỨ TỰ ("người thứ hai", "số 1")
     * hoặc theo TÊN ("bác sĩ Bình").
     *
     * Trả về bác sĩ, hoặc null để câu đó đi tiếp tới AI — im lặng đoán bừa ở đây là mở trang
     * đặt lịch với một người khách không hề chọn.
     *
     * Đệm khoảng trắng chứ KHÔNG dùng \b: \b của JS chỉ hiểu chữ cái ASCII nên không bao giờ
     * khớp một từ tiếng Việt có dấu (xem coding-conventions.md).
     */
    function resolveDoctorChoice(text, list) {
        var raw = String(text || '').toLowerCase().replace(/[.,!?]/g, ' ').replace(/\s+/g, ' ').trim();
        if (!raw || !list || !list.length) return null;
        var padded = ' ' + raw + ' ';

        // 1. Gọi đích danh. Xét TRƯỚC thứ tự: "bác sĩ Hai" là tên người, không phải "người thứ hai".
        for (var i = 0; i < list.length; i++) {
            var given = lastNameWord(list[i].fullName);
            if (given && padded.indexOf(' ' + given + ' ') !== -1) return list[i];
        }

        // 2. Thứ tự trong danh sách vừa đọc.
        var ORDINALS = [
            { words: [' thứ nhất ', ' thu nhat ', ' đầu tiên ', ' dau tien ', ' người đầu ', ' nguoi dau ',
                      ' số 1 ', ' so 1 ', ' số một ', ' so mot ', ' cái đầu ', ' cai dau '], index: 0 },
            { words: [' thứ hai ', ' thu hai ', ' thứ 2 ', ' thu 2 ', ' số 2 ', ' so 2 ', ' số hai ', ' so hai '], index: 1 },
            { words: [' thứ ba ', ' thu ba ', ' thứ 3 ', ' thu 3 ', ' số 3 ', ' so 3 ', ' số ba ', ' so ba '], index: 2 }
        ];
        for (var k = 0; k < ORDINALS.length; k++) {
            for (var w = 0; w < ORDINALS[k].words.length; w++) {
                if (padded.indexOf(ORDINALS[k].words[w]) !== -1) return list[ORDINALS[k].index] || null;
            }
        }

        // 3. Cả câu chỉ là một con số / một từ chỉ thứ tự.
        if (/^(1|một|mot)$/.test(raw)) return list[0] || null;
        if (/^(2|hai)$/.test(raw)) return list[1] || null;
        if (/^(3|ba)$/.test(raw)) return list[2] || null;

        // 4. "người đầu tiên cũng được", "ai cũng được" -> lấy người em vừa xếp đầu.
        if (/(nào cũng được|nao cung duoc|ai cũng được|ai cung duoc|người đầu|nguoi dau|em chọn giúp|em chon giup|tùy em|tuy em)/.test(raw)) {
            return list[0] || null;
        }
        return null;
    }

    /**
     * Khách vừa chọn được bác sĩ -> TỰ tra khung trống gần nhất của CHÍNH người đó rồi mời luôn.
     *
     * Trước đây chỗ này chỉ nói "em mở trang đặt lịch để anh/chị chọn khung giờ nhé?", nên khách
     * phải nói tiếp "chọn luôn giờ cho tôi chiều nay" — và lượt đó đi qua model, nơi tên bác sĩ
     * thường bị bỏ rơi, khiến hệ thống lặng lẽ chốt sang một bác sĩ khác. Tra ngay tại đây thì
     * khách có giờ luôn, và cái tên vừa chọn cũng được ghim lại cho các lượt sau.
     */
    function offerNearestSlot(doc) {
        setState('thinking');
        // Ghim bác sĩ sang ai-chat.js: các lượt sau ("chiều nay thì sao") phải bám đúng người này.
        if (chat() && chat().rememberChosenDoctor) chat().rememberChosenDoctor(doc);

        fetch('/api/chat/doctor-availability?doctorId=' + encodeURIComponent(doc.id) + '&days=7')
            .then(function (r) { return r.ok ? r.json() : null; })
            .catch(function () { return null; })
            .then(function (av) {
                var offer = describeNearestSlot(doc, av);
                // null = câu hỏi MỞ ("tìm bác sĩ khác không ạ?"), không có gì để một tiếng "vâng" chốt.
                awaitingConfirm = offer.handoff;
                say(offer.text, function () { startListening(); });
            });
    }

    /** Dựng câu mời khung gần nhất + LÝ DO nếu phải dời sang ngày khác. */
    function describeNearestSlot(doc, av) {
        function urlFor(date, slot) {
            return (chat() && chat().buildAppointmentUrl)
                ? chat().buildAppointmentUrl(doc.id, date || '', slot || '')
                : (doc.appointmentUrl || '/appointment?doctorId=' + doc.id);
        }

        // Tra hỏng thì nói là tra hỏng. Im lặng ở đây khiến khách tưởng bác sĩ kín lịch.
        if (!av || av.error) {
            return {
                text: 'Dạ bác sĩ ' + doc.fullName + '. Em chưa tra được khung trống lúc này ạ. '
                    + 'Em mở trang đặt lịch để anh/chị tự chọn giờ nhé?',
                handoff: { doctorName: doc.fullName, appointmentUrl: urlFor('', ''),
                           selectedSlotLabel: 'Bác sĩ ' + doc.fullName }
            };
        }

        var week = av.week || [];
        var free = null;
        for (var i = 0; i < week.length; i++) {
            if (week[i].freeCount > 0 && week[i].firstFreeSlot) { free = week[i]; break; }
        }

        // Cả tuần không còn khung nào: HỎI khách, TUYỆT ĐỐI không tự nhảy sang bác sĩ khác —
        // đó đúng là hành vi đang phải sửa. Đổi người là quyết định của khách, không phải của em.
        if (!free) {
            return {
                text: 'Dạ 7 ngày tới bác sĩ ' + doc.fullName + ' chưa còn khung nào trống ạ. '
                    + 'Anh/chị muốn em tìm bác sĩ khác cùng khoa không ạ?',
                handoff: null
            };
        }

        var slot = V().humanizeSchedule(free.firstFreeSlot);
        var isFirstDay = week.length > 0 && free.date === week[0].date;

        // Khung gần nhất KHÔNG phải hôm nay thì BẮT BUỘC nói vì sao, chứ không đọc trống một ngày
        // khác rồi để khách tự đoán. reasonText do server dựng và phân biệt rõ "không đăng ký ca
        // làm việc" với "đã có người đặt" — hai chuyện ngược nhau.
        //
        // Không lặp lại tên bác sĩ ở vế sau: reasonText đã nêu tên rồi, nhắc lại là tốn ngân sách
        // một hơi đọc cho chữ thừa.
        if (!isFirstDay && av.anchor && av.anchor.reasonText) {
            return {
                text: 'Dạ ' + V().humanizeSchedule(av.anchor.reasonText)
                    + ' Ngày sớm nhất bác sĩ còn trống là ' + V().humanizeSchedule(free.dayLabel)
                    + ', khung ' + slot + '. Em mở trang đặt lịch khung đó nhé?',
                handoff: {
                    doctorName: doc.fullName,
                    appointmentUrl: urlFor(free.date, free.firstFreeSlot),
                    selectedSlotLabel: free.dayLabel + ' (' + free.firstFreeSlot + ')'
                }
            };
        }

        return {
            text: 'Dạ bác sĩ ' + doc.fullName + ' còn trống sớm nhất '
                + V().humanizeSchedule(free.dayLabel) + ', khung ' + slot
                + '. Em mở trang đặt lịch khung đó nhé?',
            handoff: {
                doctorName: doc.fullName,
                appointmentUrl: urlFor(free.date, free.firstFreeSlot),
                selectedSlotLabel: free.dayLabel + ' (' + free.firstFreeSlot + ')'
            }
        };
    }

    /** KB3 — chuyển overlay sang chế độ cảnh báo cấp cứu. */
    function enterEmergency(aiData) {
        stopListening();
        setState('emergency');
        awaitingConfirm = null;
        pendingChoiceList = null;

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
        pendingChoiceList = null;
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
        // Danh sách của cuộc gọi vừa rồi không được sống sang cuộc gọi sau, kẻo "người thứ hai"
        // chọn trúng một người của lần gọi trước.
        pendingChoiceList = null;
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
