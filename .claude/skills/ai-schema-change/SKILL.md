---
name: ai-schema-change
description: Thêm, đổi tên hoặc bỏ một key trong JSON schema của trợ lý AI phân luồng bệnh nhân (reasoning, ai_reply, speech_reply, suggested_prompts, recommended_departments, is_emergency, patient_summary, booking_intent, booking_target). Dùng khi sửa prompt AiService, khi frontend đọc sai field, hoặc khi cần thêm dữ liệu mới từ model xuống giao diện. Trigger: "prompt AI", "JSON trợ lý", "ai_reply", "booking_target", "speech_reply", "thêm field cho AI", "9 keys".
---

# Đổi schema JSON của trợ lý AI

Model bị ép trả về **đúng 9 keys**. Schema này là **hợp đồng** giữa prompt Java và 4 file JS ở
frontend — không có validation nào bắt lỗi, sai key thì giao diện im lặng mất tính năng.

## 9 keys hiện tại

| Key | Vai trò |
|---|---|
| `reasoning` | Suy luận chọn khoa (không hiển thị) |
| `ai_reply` | Câu trả lời hiển thị, dùng `<br>` xuống dòng |
| `speech_reply` | Bản đọc to ≤2 câu, không emoji/HTML/cảnh báo |
| `suggested_prompts` | **Luôn đúng 3 câu** |
| `recommended_departments` | Mảng ID khoa (21 = Cấp cứu, 22 = Y học gia đình) |
| `is_emergency` | Bật overlay đỏ, bỏ luồng đặt lịch |
| `patient_summary` | Ký ức cộng dồn, được regex trích lại mỗi lượt |
| `booking_intent` | Có muốn đặt lịch không |
| `booking_target` | `doctor_name` / `appointment_date` / `appointment_time` |

## Checklist khi đổi schema

### 1. Prompt trong Java — **2 chỗ đếm số key**
- [ ] [AiService.java:78](../../../src/main/java/com/bookinghealthy/service/AiService.java#L78) — `"BẠN LÀ CỖ MÁY XUẤT JSON. BẠN PHẢI TRẢ VỀ ĐÚNG 9 KEYS..."`
- [ ] [AiService.java:100](../../../src/main/java/com/bookinghealthy/service/AiService.java#L100) — `"...cấu trúc JSON phải có đủ 9 trường y hệt như ví dụ trên"`
- [ ] [AiService.java:81-99](../../../src/main/java/com/bookinghealthy/service/AiService.java#L81-L99) — khối JSON mẫu, thêm/xoá dòng key ở đây

Hai chỗ đếm số dùng **cách viết khác nhau** ("9 KEYS" và "9 trường") nên `grep "9 KEYS"` chỉ ra
một chỗ. Grep cả `"9 trường"`.

### 2. Frontend đọc key — 4 file JS (**không phải template HTML**)
Các template `include/ai-chat*.html` chỉ nạp script; logic đọc JSON nằm ở:

- [ ] [assets/js/ai-chat.js](../../../src/main/resources/static/assets/js/ai-chat.js) — widget bệnh nhân, đọc nhiều key nhất
- [ ] [assets/js/meditrust-voice-call.js](../../../src/main/resources/static/assets/js/meditrust-voice-call.js) — chế độ gọi rảnh tay, đọc `speech_reply` / `is_emergency` / `booking_*`
- [ ] [assets-admin/js/ai-chat-doctor.js](../../../src/main/resources/static/assets-admin/js/ai-chat-doctor.js)
- [ ] [assets-admin/js/doctor-ai-chat.js](../../../src/main/resources/static/assets-admin/js/doctor-ai-chat.js)

Ánh xạ template → script (dùng khi cần biết màn nào ảnh hưởng):

| Template | Script |
|---|---|
| `user/include/ai-chat.html` | `ai-chat.js` + `meditrust-voice-call.js` |
| `doctor/include/ai-chat-doctor.html` | `ai-chat-doctor.js` |
| `doctor/include/ai-chat.html` | `doctor-ai-chat.js` |
| `admin/include/ai-chat.html` | `admin-ai-chat.js` |

### 3. Nếu đụng key liên quan giọng nói
- [ ] `speech_reply` phải giữ fallback `MediTrustVoice.toSpeechText(ai_reply)` — model không tuân
  thủ thì suy giảm chứ không vỡ.
- [ ] Nhãn slot trong `speech_reply` do prompt sinh ra ở dạng lời nói; nếu đổi định dạng phải
  đối chiếu `translateDay()` trong `AiController`.

### 4. Nếu đụng `booking_target`
- [ ] `resolveBookingHandoff()` trong `ai-chat.js` — không được tự ý thay bác sĩ; giữ nguyên
  `pickBestDoctorMatch()` (khớp theo ranh giới từ) và nhánh `doctorNotFound`.
- [ ] So sánh giờ phải qua `slotStartTime()`, **không** dùng `indexOf` (`"10:30"` là substring của
  cả `(10:00 - 10:30)`).

### 5. Quy tắc prompt không được phá
- Mục 0: xưng "em" / gọi khách "anh/chị", áp dụng cho **cả** `ai_reply` lẫn `speech_reply`.
- Mục 5B: model **không được** nói đã ghi nhận / đã giữ / đã đặt chỗ — nó không thấy lịch thật.
- Mục 5C: mỗi lượt tối đa một câu hỏi, không hỏi lại điều khách đã nói.

## Kiểm tra sau khi sửa

1. `grep -rn "<tên_key_cũ>" src/main` — phải sạch (kể cả regex trích `patient_summary` ở
   [AiService.java:262](../../../src/main/java/com/bookinghealthy/service/AiService.java#L262)).
2. Chạy app, chat thử một triệu chứng rõ ràng → thẻ bác sĩ phải bung ra, đúng 3 câu gợi ý.
3. Chat thử "cho tôi đặt lịch với bác sĩ X lúc 10h30 mai" → form mở đúng bác sĩ, đúng giờ.
4. Bật chế độ gọi, xác nhận vẫn đọc được câu trả lời.
5. Ghi nhật ký theo [progress-log.md](../../rules/progress-log.md) và cập nhật
   [ai-assistant.md](../../rules/ai-assistant.md) nếu danh sách key thay đổi.
