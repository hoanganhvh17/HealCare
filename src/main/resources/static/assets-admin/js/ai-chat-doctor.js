if (window.aiChatDoctorLoaded) {
    console.warn("AI Chat Doctor script loaded multiple times. Skipping initialization.");
} else {
    window.aiChatDoctorLoaded = true;
    document.addEventListener('DOMContentLoaded', function() {
    // --- 1. KHỞI TẠO BIẾN ---
    const chatBox = document.getElementById('ai-chat-box-doctor');
    const closeBtn = document.getElementById('btn-close-doctor');
    const chatInput = document.getElementById('ai-chat-input-doctor');
    const sendBtn = document.getElementById('ai-chat-send-doctor');
    const messagesContainer = document.getElementById('ai-chat-messages-doctor');
    const btnNewChat = document.getElementById('btn-new-chat-doctor');

    // --- 2. KHÔI PHỤC KÍCH THƯỚC TỪ TRANG TRƯỚC ---
    const savedWidth = sessionStorage.getItem('meditrust_chat_width_doctor');
    if (savedWidth && chatBox) {
        chatBox.style.width = savedWidth;
    }

    // --- 3. HÀM GLOBAL ---
    window.toggleDoctorAI = function() {
        if (chatBox && chatBox.classList.contains('open')) {
            closeChat();
        } else {
            openChat();
        }
    };

    window.sendQuickReplyDoctor = function(text, btnElement) {
        if (chatInput && sendBtn) {
            chatInput.value = text;
            sendMessage();
        }
    };

    // --- 4. QUẢN LÝ PHIÊN & NGỮ CẢNH
    const currentPath = window.location.pathname;
    const savedPath = sessionStorage.getItem('meditrust_chat_last_path_doctor');
    let sessionId = sessionStorage.getItem('meditrust_session_id_doctor');

    // Nếu bác sĩ chuyển URL (từ danh sách sang chi tiết, hoặc từ bệnh nhân A sang bệnh nhân B)
    if (savedPath && savedPath !== currentPath) {
        // Xóa trí nhớ giao diện cũ
        sessionStorage.removeItem('meditrust_chat_html_doctor');
        if (messagesContainer) messagesContainer.innerHTML = '';

        // Cấp phát một ID phiên chat mới tinh để Backend xóa trí nhớ ngầm
        sessionId = 'doc_session_' + Math.random().toString(36).substr(2, 9);
        sessionStorage.setItem('meditrust_session_id_doctor', sessionId);
    }
    // Cập nhật lại đường dẫn hiện tại để lần sau so sánh
    sessionStorage.setItem('meditrust_chat_last_path_doctor', currentPath);

    if (!sessionId) {
        sessionId = 'doc_session_' + Math.random().toString(36).substr(2, 9);
        sessionStorage.setItem('meditrust_session_id_doctor', sessionId);
    }

    // Phục hồi HTML (chỉ khi chưa bị xóa do chuyển trang)
    const savedChatHtml = sessionStorage.getItem('meditrust_chat_html_doctor');
    if (savedChatHtml && messagesContainer) {
        messagesContainer.innerHTML = savedChatHtml;
        setTimeout(() => messagesContainer.scrollTop = messagesContainer.scrollHeight, 100);
    }

    const chatState = sessionStorage.getItem('meditrust_chat_state_doctor');
    if (chatState === 'open') {
        openChat();
    }

    // --- 5. HÀM MỞ / ĐÓNG ---
    function openChat() {
        if(!chatBox) return;
        chatBox.classList.add('open');
        sessionStorage.setItem('meditrust_chat_state_doctor', 'open');
        if(chatInput) chatInput.focus();
        loadWelcomeMessage();
        updateLiveStats();
    }

    function closeChat() {
        if(!chatBox) return;
        chatBox.classList.remove('open');
        sessionStorage.setItem('meditrust_chat_state_doctor', 'closed');
    }

    // --- 6. LẮNG NGHE SỰ KIỆN CLICK CƠ BẢN ---
    if (closeBtn) {
        closeBtn.addEventListener('click', (e) => {
            e.preventDefault();
            closeChat();
        });
    }

    if (btnNewChat) {
        btnNewChat.onclick = function(e) {
            e.preventDefault();
            if (confirm('Làm mới phiên chat?')) {
                sessionStorage.removeItem('meditrust_session_id_doctor');
                sessionStorage.removeItem('meditrust_chat_html_doctor');
                sessionStorage.setItem('meditrust_chat_state_doctor', 'open');
                window.location.reload();
            }
        };
    }

    // --- 7. TÍNH NĂNG KÉO VIỀN ĐỂ THAY ĐỔI KÍCH THƯỚC ---
    const resizeHandle = document.getElementById('doctor-ai-resize-handle');
    let isResizing = false;

    if (resizeHandle && chatBox) {
        resizeHandle.style.width = '12px';
        resizeHandle.style.left = '-6px';

        resizeHandle.addEventListener('mousedown', (e) => {
            isResizing = true;
            chatBox.style.transition = 'none';
            document.body.style.userSelect = 'none';
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            let newWidth = window.innerWidth - e.clientX;
            if (newWidth >= 300 && newWidth <= 900) {
                chatBox.style.width = newWidth + 'px';
            }
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                document.body.style.userSelect = 'auto';
                chatBox.style.transition = 'right 0.4s cubic-bezier(0.05, 0.7, 0.1, 1.0)';
                sessionStorage.setItem('meditrust_chat_width_doctor', chatBox.style.width);
            }
        });
    }

    // --- 8. LOGIC CALL API VÀ HIỂN THỊ TIN NHẮN ---
    function updateLiveStats() {
        if(!messagesContainer) return;
        fetch('/api/doctor/chat/welcome')
            .then(res => res.text())
            .then(newWelcomeHtml => {
                let formattedText = newWelcomeHtml.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                const tempDiv = document.createElement('div');
                tempDiv.innerHTML = formattedText;
                const newStats = tempDiv.querySelector('#live-welcome-stats');
                const existingStatsSpan = document.getElementById('live-welcome-stats');

                if (existingStatsSpan && newStats) {
                    existingStatsSpan.innerHTML = newStats.innerHTML;
                    sessionStorage.setItem('meditrust_chat_html_doctor', messagesContainer.innerHTML);
                }
            })
            .catch(e => console.log("Lỗi cập nhật số liệu ngầm: ", e));
    }

    function loadWelcomeMessage() {
        if (messagesContainer && messagesContainer.innerHTML.trim() === '') {
            const typingMsg = appendMessage('bot', '<div class="typing-dots"><span></span><span></span><span></span></div>');
            fetch('/api/doctor/chat/welcome')
                .then(res => res.text())
                .then(greetingText => {
                    const formattedText = greetingText.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                    typingMsg.innerHTML = formattedText;

                    // --- LOGIC HIỂN THỊ NÚT THÔNG MINH ---
                    const isViewingRecord = document.getElementById("pdf-content") !== null;
                    let quickActionsHtml = '';

                    if (isViewingRecord) {
                        quickActionsHtml = `
                            <div style="margin-top: 15px; padding: 12px; background: #f0f7ff; border-radius: 8px; border-left: 4px solid #0d6efd;">
                                <div style="font-weight: bold; font-size: 13px; color: #0d6efd; margin-bottom: 8px;">
                                    <i class="bi bi-file-medical-fill"></i> Trợ lý Lâm sàng:
                                </div>
                                <div class="quick-replies-container" style="margin-top: 0; padding-top: 0; border: none;">
                                    <button class="quick-reply-btn" onclick="window.sendQuickReplyDoctor('Tóm tắt nhanh bệnh án này cho tôi', this)">📝 Tóm tắt nhanh</button>
                                    <button class="quick-reply-btn" onclick="window.sendQuickReplyDoctor('Phân tích đơn thuốc này, có cảnh báo tác dụng phụ hay tương tác thuốc nào không?', this)">💊 Phân tích Đơn thuốc</button>
                                    <button class="quick-reply-btn" onclick="window.sendQuickReplyDoctor('Gợi ý chế độ ăn uống và sinh hoạt cho bệnh nhân này', this)">🥗 Gợi ý lời dặn dò</button>
                                </div>
                            </div>
                        `;
                    } else {
                        quickActionsHtml = `
                            <div style="margin-top: 15px; padding: 12px; background: #f8f9fa; border-radius: 8px; border-left: 4px solid #198754;">
                                <div style="font-weight: bold; font-size: 13px; color: #333; margin-bottom: 8px;">
                                    <i class="bi bi-lightning-charge-fill text-warning"></i> Thao tác nhanh:
                                </div>
                                <div class="quick-replies-container" style="margin-top: 0; padding-top: 0; border: none;">
                                    <button class="quick-reply-btn" onclick="window.sendQuickReplyDoctor('Thống kê lịch hôm nay', this)">📊 Thống kê lịch hôm nay</button>
                                    <button class="quick-reply-btn" onclick="window.sendQuickReplyDoctor('Kiểm tra bệnh án nợ', this)">📝 Kiểm tra bệnh án nợ</button>
                                    <button class="quick-reply-btn" onclick="window.sendQuickReplyDoctor('Lịch tuần này thế nào?', this)">📅 Lịch tuần này</button>
                                </div>
                            </div>
                        `;
                    }

                    appendMessage('bot', quickActionsHtml);
                })
                .catch(() => {
                    typingMsg.innerHTML = 'Xin chào Bác sĩ! Em là trợ lý AI thống kê lịch khám.';
                });
        }
    }

    function appendMessage(sender, textOrHtml) {
        if(!messagesContainer) return;
        const msgDiv = document.createElement('div');
        msgDiv.classList.add('chat-msg', sender === 'assistant' || sender === 'bot' ? 'bot' : 'user');

        if((sender === 'bot' || sender === 'assistant') && textOrHtml.indexOf('<div') === -1 && typeof marked !== 'undefined') {
            msgDiv.innerHTML = marked.parse(textOrHtml);
        } else {
            msgDiv.innerHTML = textOrHtml;
        }

        messagesContainer.appendChild(msgDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
        sessionStorage.setItem('meditrust_chat_html_doctor', messagesContainer.innerHTML);
        return msgDiv;
    }

    async function sendMessage() {
        if(!chatInput || !messagesContainer) return;
        const text = chatInput.value.trim();
        if (!text) return;

        appendMessage('user', text);
        chatInput.value = '';

        const typingMsg = appendMessage('bot', '<div class="typing-dots"><span></span><span></span><span></span></div>');

        // Lấy nội dung trang màn hình (nếu đang ở trang bệnh án)
        let contextText = "";
        const pdfContent = document.getElementById("pdf-content");
        if (pdfContent) {
            contextText = pdfContent.innerText;
        }

        try {
            const response = await fetch('/api/doctor/chat/ask', {
                method: 'POST',
                headers: MediTrustCsrf.headers({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({
                    sessionId: sessionId,
                    prompt: text,
                    pageContent: contextText
                })
            });

            if (response.ok) {
                const data = await response.json();
                let cleanStr = data.answer || data.ai_reply || "";

                cleanStr = cleanStr.replace(/```json/gi, '').replace(/```/g, '').trim();
                let formattedText = cleanStr;

                try {
                    const aiData = JSON.parse(cleanStr);
                    if (aiData.ai_reply) {
                        formattedText = aiData.ai_reply;
                    }
                } catch(e) {}

                if(typeof marked !== 'undefined') {
                    typingMsg.innerHTML = marked.parse(formattedText);
                } else {
                    typingMsg.innerHTML = formattedText.replace(/\n/g, '<br>').replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                }

                sessionStorage.setItem('meditrust_chat_html_doctor', messagesContainer.innerHTML);
            } else {
                typingMsg.innerHTML = 'Hệ thống bận, vui lòng thử lại sau.';
            }
        } catch (error) {
            typingMsg.innerHTML = 'Lỗi kết nối. Vui lòng kiểm tra mạng.';
            console.error("AI Chat Error:", error);
        }
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    if(sendBtn) sendBtn.addEventListener('click', sendMessage);
    if(chatInput) chatInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') sendMessage(); });

    // --- 9. TOUR GUIDE CẢNH BÁO Y TẾ CHO BÁC SĨ ---
    function checkDoctorEmergencyAlert() {
        if (sessionStorage.getItem('meditrust_chat_state_doctor') === 'open') return;
        if (document.getElementById('chat-tour-guide-doctor')) return;

        fetch('/api/public/news/latest-alert')
            .then(response => {
                if (response.status === 204) return null;
                return response.json();
            })
            .then(data => {
                if (data) {
                    const lastAlertId = localStorage.getItem('meditrust_doctor_last_alert_id');
                    if (lastAlertId === data.id.toString()) return;

                    const style = document.createElement('style');
                    style.innerHTML = `
                        .tour-guide-box-doctor { position: fixed; top: 80px; right: 20px; width: 320px; background: #fff; border: 2px solid #198754; border-radius: 12px; padding: 16px; box-shadow: 0 10px 30px rgba(25, 135, 84, 0.25); z-index: 10000; opacity: 0; visibility: hidden; transform: translateY(-20px) scale(0.9); transition: all 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55); }
                        .tour-guide-box-doctor.show { opacity: 1; visibility: visible; transform: translateY(0) scale(1); }
                        .tour-guide-box-doctor::after { content: ''; position: absolute; top: -12px; right: 150px; border-width: 0 12px 12px; border-style: solid; border-color: transparent transparent #198754 transparent; }
                        .tour-guide-title-doc { font-weight: 800; color: #dc3545; margin-bottom: 8px; font-size: 15px; display: flex; align-items: center; gap: 8px; }
                        .tour-guide-desc-doc { font-size: 13px; color: #444; margin-bottom: 12px; line-height: 1.5; }
                        .tour-guide-btn-doc { background: #198754; color: white; border: none; padding: 6px 16px; border-radius: 20px; font-size: 12px; cursor: pointer; font-weight: bold; transition: 0.2s; }
                        .tour-guide-btn-doc:hover { transform: scale(1.05); }
                    `;
                    document.head.appendChild(style);

                    const tourGuideHtml = `
                        <div id="chat-tour-guide-doctor" class="tour-guide-box-doctor show">
                            <div class="tour-guide-title-doc"><i class="bi bi-exclamation-triangle-fill fs-4"></i> Cập nhật Dịch tễ Khẩn cấp</div>
                            <div class="tour-guide-desc-doc">
                                <strong>${data.title}</strong><br/>
                                <span style="font-size: 0.9em;">${data.summary}</span>
                                <div style="margin-top: 8px; padding-top: 8px; border-top: 1px dashed #ccc; font-style: italic; color: #198754;">
                                    Bác sĩ vui lòng lưu ý các triệu chứng lâm sàng liên quan trong quá trình khám bệnh.
                                </div>
                            </div>
                            <div style="display: flex; gap: 10px; margin-top: 10px; justify-content: flex-end;">
                                <button id="btn-skip-alert-doc" class="tour-guide-btn-doc" style="background-color: #6c757d;">Đã nắm rõ</button>
                                <button id="btn-read-alert-doc" class="tour-guide-btn-doc">Xem chi tiết</button>
                            </div>
                        </div>
                    `;
                    document.body.insertAdjacentHTML('beforeend', tourGuideHtml);

                    const tourGuideBox = document.getElementById('chat-tour-guide-doctor');

                    document.getElementById('btn-read-alert-doc').addEventListener('click', function() {
                        localStorage.setItem('meditrust_doctor_last_alert_id', data.id.toString());
                        window.open('/news/' + data.id, '_blank');
                        tourGuideBox.classList.remove('show');
                    });

                    document.getElementById('btn-skip-alert-doc').addEventListener('click', function() {
                        localStorage.setItem('meditrust_doctor_last_alert_id', data.id.toString());
                        tourGuideBox.classList.remove('show');
                    });
                }
            })
            .catch(err => console.error("Lỗi khi tải tin tức khẩn cấp (Doctor):", err));
    }

    setTimeout(checkDoctorEmergencyAlert, 2000);

    // --- GIỌNG NÓI: nút mic để đọc câu hỏi, nút loa để nghe câu trả lời.
    //     Tiện khi bác sĩ đang khám, tay bận. Module tự ẩn nếu trình duyệt không hỗ trợ. ---
    if (window.MediTrustVoice) {
        window.MediTrustVoice.attach({
            inputId: 'ai-chat-input-doctor',
            sendBtnId: 'ai-chat-send-doctor',
            messagesId: 'ai-chat-messages-doctor',
            botSelector: '.chat-msg.bot',
            micTitle: 'Bấm để đọc câu hỏi'
        });
    }
});
}