if (window.adminAiChatLoaded) {
    console.warn("Admin AI Chat script loaded multiple times. Skipping initialization.");
} else {
    window.adminAiChatLoaded = true;

// HÀM MỞ/ĐÓNG SIÊU ĐƠN GIẢN - TOÀN CỤC
window.toggleMediTrustAI = function() {
    const panel = document.getElementById('admin-ai-sidepanel');
    if (panel) {
        const isOpen = panel.classList.toggle('open');
        localStorage.setItem('admin_ai_sidebar_user_choice', isOpen ? 'open' : 'closed');
        console.log("🚀 Lệnh điều khiển AI: " + (isOpen ? "MỞ" : "ĐÓNG"));
        
        if (isOpen) {
            const input = document.getElementById('admin-ai-input-sidebar');
            if (input) setTimeout(() => input.focus(), 300);
            if (window.checkAndLoadWelcome) window.checkAndLoadWelcome();
        }
    }
};

document.addEventListener('DOMContentLoaded', function() {
    console.log("🚀 MediTrust AI: Script đã sẵn sàng.");

    const panel = document.getElementById('admin-ai-sidepanel');
    const closeBtn = document.getElementById('admin-btn-close-sidebar');
    const messagesBox = document.getElementById('admin-ai-messages-sidebar');
    const input = document.getElementById('admin-ai-input-sidebar');
    const clearBtn = document.getElementById('admin-btn-new-chat-sidebar');

    // 1. Khôi phục lịch sử
    const savedHtml = sessionStorage.getItem('admin_ai_chat_cache');
    if (savedHtml && messagesBox) {
        messagesBox.innerHTML = savedHtml;
        messagesBox.scrollTop = messagesBox.scrollHeight;
    }

    // Khôi phục trạng thái đóng/mở
    const userChoice = localStorage.getItem('admin_ai_sidebar_user_choice');
    if (userChoice === 'open') {
        panel.classList.add('open');
        setTimeout(() => { if (window.checkAndLoadWelcome) window.checkAndLoadWelcome(); }, 500);
    }

    // 2. Gán sự kiện cho nút đóng (--)
    if (closeBtn) {
        closeBtn.onclick = function(e) {
            e.preventDefault();
            panel.classList.remove('open');
            localStorage.setItem('admin_ai_sidebar_user_choice', 'closed');
            console.log("➡️ Đã đóng Sidebar bằng nút DASH");
        };
    }

    // 3. Hàm tải tin chào mừng (Đảm bảo chạy đúng 1 lần)
    let isFetchingWelcome = false;
    window.checkAndLoadWelcome = function() {
        if (!messagesBox || isFetchingWelcome) return;
        
        // Kiểm tra xem thực sự có tin nhắn nào chưa (trừ khoảng trắng)
        const hasContent = messagesBox.innerText.trim().length > 0;
        if (!hasContent) {
            isFetchingWelcome = true;
            messagesBox.innerHTML = '<div class="admin-chat-msg bot"><div class="admin-typing-dots"><span></span><span></span><span></span></div></div>';
            
            fetch('/api/admin/chat/welcome')
                .then(res => res.json())
                .then(data => {
                    messagesBox.innerHTML = ''; // Xóa dots
                    
                    // Render câu chào
                    const markdown = data.message;
                    const htmlContent = (typeof marked !== 'undefined') ? marked.parse(markdown) : markdown;
                    appendMessage('bot', htmlContent);
                    
                    // Khởi tạo container nút bấm
                    let quickBtnsHTML = '<div class="quick-replies-container">';
                    
                    // Nếu có bản nháp, chèn nút Cảnh báo đỏ lên ĐẦU TIÊN
                    if (data.draftNewsCount && data.draftNewsCount > 0) {
                        quickBtnsHTML += `<button class="quick-reply-btn" style="border-color: #dc3545; color: #dc3545; font-weight: bold; background-color: #fff8f8;" onclick="window.sendQuickReplyAdmin('Có bao nhiêu bản nháp tin tức y tế khẩn cấp cần duyệt?')">🚨 Tin khẩn cấp (${data.draftNewsCount})</button>`;
                    }
                    
                    // Thêm các nút mặc định
                    quickBtnsHTML += `
                            <button class="quick-reply-btn" onclick="window.sendQuickReplyAdmin('Doanh thu tháng này?')">💰 Doanh thu</button>
                            <button class="quick-reply-btn" onclick="window.sendQuickReplyAdmin('Tỷ lệ hủy lịch?')">📉 Hủy lịch</button>
                            <button class="quick-reply-btn" onclick="window.sendQuickReplyAdmin('Bác sĩ tốt nhất?')">👨‍⚕️ Top Bác sĩ</button>
                            <button class="quick-reply-btn" onclick="window.sendQuickReplyAdmin('Xem đánh giá tiêu cực')">⚠️ Đánh giá xấu</button>
                        </div>`;
                        
                    messagesBox.insertAdjacentHTML('beforeend', quickBtnsHTML);
                    saveCache();
                })
                .catch(err => {
                    console.error("Lỗi welcome:", err);
                    messagesBox.innerHTML = '<div class="admin-chat-msg bot">Chào sếp, em đã sẵn sàng hỗ trợ!</div>';
                })
                .finally(() => { isFetchingWelcome = false; });
        }
    };

    // 4. Xử lý gửi tin nhắn
    function appendMessage(role, content) {
        if (!messagesBox) return;
        const msgDiv = document.createElement('div');
        msgDiv.className = `admin-chat-msg ${role}`;
        msgDiv.innerHTML = content;
        messagesBox.appendChild(msgDiv);
        messagesBox.scrollTop = messagesBox.scrollHeight;
        saveCache();
    }

    function saveCache() {
        if (messagesBox) sessionStorage.setItem('admin_ai_chat_cache', messagesBox.innerHTML);
    }

    window.sendQuickReplyAdmin = function(text) {
        if (input) { input.value = text; sendMessage(); }
    };

    async function sendMessage() {
        if (!input || !input.value.trim() || !messagesBox) return;

        const userText = input.value.trim();
        appendMessage('user', userText);
        input.value = '';

        const typingDiv = document.createElement('div');
        typingDiv.className = 'admin-chat-msg bot';
        typingDiv.innerHTML = '<div class="admin-typing-dots"><span></span><span></span><span></span></div>';
        messagesBox.appendChild(typingDiv);
        messagesBox.scrollTop = messagesBox.scrollHeight;

        try {
            const res = await fetch('/api/admin/chat/ask', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({prompt: userText, sessionId: 'admin_session'})
            });
            const data = await res.json();
            const aiHtml = (typeof marked !== 'undefined') ? marked.parse(data.answer) : data.answer;
            typingDiv.innerHTML = aiHtml;
            saveCache();
            messagesBox.scrollTop = messagesBox.scrollHeight;
        } catch (e) {
            typingDiv.innerHTML = "Lỗi kết nối AI.";
        }
    }

    const sendBtn = document.getElementById('admin-ai-send-sidebar');
    if (sendBtn) sendBtn.onclick = sendMessage;
    if (input) input.onkeypress = (e) => { if(e.key === 'Enter') sendMessage(); };

    // 5. Làm mới chat
    if (clearBtn) {
        clearBtn.onclick = function() {
            if (confirm("Làm mới đoạn chat?")) {
                sessionStorage.removeItem('admin_ai_chat_cache');
                messagesBox.innerHTML = '';
                window.checkAndLoadWelcome();
            }
        };
    }

    // 6. Resize
    const resizeHandle = document.getElementById('admin-ai-resize-handle');
    let isResizing = false;
    if (resizeHandle) {
        resizeHandle.onmousedown = () => { isResizing = true; document.body.style.userSelect = 'none'; };
        document.onmousemove = (e) => {
            if (!isResizing) return;
            let newWidth = window.innerWidth - e.clientX;
            if (newWidth > 300 && newWidth < 900) panel.style.width = newWidth + 'px';
        };
        document.onmouseup = () => { isResizing = false; document.body.style.userSelect = 'auto'; };
    }

    // 7. Giọng nói: nút mic để đọc câu hỏi, nút loa để nghe câu trả lời.
    //    Module tự ẩn nếu trình duyệt không hỗ trợ.
    if (window.MediTrustVoice) {
        window.MediTrustVoice.attach({
            inputId: 'admin-ai-input-sidebar',
            sendBtnId: 'admin-ai-send-sidebar',
            messagesId: 'admin-ai-messages-sidebar',
            botSelector: '.admin-chat-msg.bot',
            micTitle: 'Bấm để đọc câu hỏi'
        });
    }
});
}
