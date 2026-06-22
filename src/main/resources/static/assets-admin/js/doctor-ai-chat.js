document.addEventListener('DOMContentLoaded', function() {
    const toggleBtn = document.getElementById('doctor-ai-chat-toggle');
    const closeBtn = document.getElementById('doctor-btn-close');
    const chatBox = document.getElementById('doctor-ai-chat-box');
    const chatInput = document.getElementById('doctor-ai-chat-input');
    const sendBtn = document.getElementById('doctor-ai-chat-send');
    const messagesContainer = document.getElementById('doctor-ai-chat-messages');
    const newChatBtn = document.getElementById('doctor-btn-new-chat');

    // 1. Mở / Đóng Chat
    toggleBtn.addEventListener('click', function() {
        chatBox.classList.remove('d-none');
        toggleBtn.classList.add('d-none');
        chatInput.focus();
    });

    closeBtn.addEventListener('click', function(e) {
        e.preventDefault();
        chatBox.classList.add('d-none');
        toggleBtn.classList.remove('d-none');
    });

    // 2. Thêm tin nhắn vào khung chat
    function appendMessage(sender, textOrHtml) {
        const msgDiv = document.createElement('div');
        msgDiv.classList.add('chat-msg', sender === 'assistant' || sender === 'bot' ? 'bot' : 'user');

        // Nếu là text từ AI, parse Markdown
        if(sender === 'bot' && textOrHtml.indexOf('<div') === -1) {
            msgDiv.innerHTML = marked.parse(textOrHtml);
        } else {
            msgDiv.innerHTML = textOrHtml;
        }

        messagesContainer.appendChild(msgDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
        return msgDiv;
    }

    // 3. Gửi tin nhắn
    async function sendMessage() {
        const text = chatInput.value.trim();
        if (!text) return;

        appendMessage('user', text);
        chatInput.value = '';

        // Hiển thị hiệu ứng typing
        const typingMsg = appendMessage('bot', '<div class="typing-dots"><span></span><span></span><span></span></div>');

        try {
            const response = await fetch('/api/doctor/assistant/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: text })
            });

            if (response.ok) {
                const data = await response.json();
                typingMsg.innerHTML = marked.parse(data.ai_reply);
            } else {
                typingMsg.innerHTML = 'Hệ thống bận, vui lòng thử lại sau.';
            }
        } catch (error) {
            typingMsg.innerHTML = 'Lỗi kết nối. Vui lòng kiểm tra mạng.';
            console.error("AI Chat Error:", error);
        }
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    sendBtn.addEventListener('click', sendMessage);
    chatInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });

    // 4. Nút làm mới chat
    newChatBtn.addEventListener('click', function(e) {
        e.preventDefault();
        messagesContainer.innerHTML = '<div class="chat-msg bot">Xin chào Bác sĩ! Em là trợ lý AI. Bác sĩ có thể hỏi em về lịch làm việc, bệnh nhân hôm nay nhé!</div>';
    });
});