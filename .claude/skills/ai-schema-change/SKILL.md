---
name: ai-schema-change
description: Thêm, đổi tên hoặc bỏ một key trong JSON schema của trợ lý AI phân luồng bệnh nhân (reasoning, ai_reply, speech_reply, suggested_prompts, recommended_departments, is_emergency, patient_summary, booking_intent, booking_target, lookup). Dùng khi sửa prompt AiService, khi frontend đọc sai field, hoặc khi cần thêm dữ liệu mới từ model xuống giao diện. Trigger: "prompt AI", "JSON trợ lý", "ai_reply", "booking_target", "speech_reply", "lookup", "thêm field cho AI", "10 keys".
---

# Đổi schema JSON của trợ lý AI

Model bị ép trả về **đúng 10 keys**. Schema này là **hợp đồng** giữa prompt Java và 4 file JS ở
frontend — không có validation nào bắt lỗi, sai key thì giao diện im lặng mất tính năng.

## 10 keys hiện tại

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
| `lookup` | Định tuyến câu HỎI: `type` (`none`/`doctor_schedule`/`my_bookings`/`doctor_info`/`doctor_filter`) + `doctor_name` / `date` / `session` / `scope` / `filter` |

## Checklist khi đổi schema

### 1. Prompt trong Java — **2 chỗ đếm số key**
Tất cả nằm trong `PATIENT_BASE_PROMPT` của
[AiService.java](../../../src/main/java/com/bookinghealthy/service/AiService.java) — grep theo
NỘI DUNG, đừng tin số dòng (đã lệch một lần rồi):

- [ ] `grep -n "ĐÚNG 10 KEYS"` — `"BẠN LÀ CỖ MÁY XUẤT JSON. BẠN PHẢI TRẢ VỀ ĐÚNG 10 KEYS..."`
- [ ] `grep -n "đủ 10 trường"` — `"...cấu trúc JSON phải có đủ 10 trường y hệt như ví dụ trên"`
- [ ] Khối JSON mẫu ngay dưới dòng đầu — thêm/xoá dòng key ở đây
- [ ] Mục **5D** — nếu key mới là trường định tuyến, mô tả từng giá trị ở đây
- [ ] Mục **5B** — nếu key mới đụng tới lịch/chỗ trống, PHẢI thêm một gạch đầu dòng nói rõ nó chỉ
  là trường ĐỊNH TUYẾN và `ai_reply`/`speech_reply` vẫn cấm nêu giờ/ngày. Thiếu câu này, model đọc
  key mới như giấy phép tự trả lời — đảo ngược đúng luật sinh ra nó.
- [ ] `MAX_TOKENS` trong cùng file — mỗi key thêm vào là JSON dài ra; cắt giữa chừng thì
  `JSON.parse` hỏng và khách nhìn thấy JSON thô trong bong bóng chat.

Hai chỗ đếm số dùng **cách viết khác nhau** ("10 KEYS" và "10 trường") nên `grep "10 KEYS"` chỉ ra
một chỗ. Grep cả `"10 trường"`.

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

### 5. Nếu đụng `lookup`
- [ ] `resolveLookup()` trong `ai-chat.js` là cửa DUY NHẤT — thêm loại mới thì thêm nhánh ở đó,
  kèm một hàm `looksLike…Question()` vớt khi model bỏ sót.
- [ ] Nhánh tra cứu **KHÔNG** được gán `pendingAlternatives`, gọi `/hold-slot`, chạy
  `startRedirectCountdown`, hay ghi `lastHandoffDate` — xem [ai-assistant.md](../../rules/ai-assistant.md).
- [ ] Loại mới đụng dữ liệu riêng của bệnh nhân thì phải có luật `authenticated()` ở **khối 0** của
  `SecurityConfig` (`/api/chat/**` đang là permitAll).

### 6. Quy tắc prompt không được phá
- Mục 0: xưng "em" / gọi khách "anh/chị", áp dụng cho **cả** `ai_reply` lẫn `speech_reply`.
- Mục 5B: model **không được** nói đã ghi nhận / đã giữ / đã đặt chỗ — nó không thấy lịch thật.
- Mục 5C: mỗi lượt tối đa một câu hỏi, không hỏi lại điều khách đã nói.
- Mục 5D: `lookup.type` phải đi kèm `booking_intent = false` — đây là câu HỎI, không phải yêu cầu đặt.

## Kiểm tra sau khi sửa

1. `grep -rn "<tên_key_cũ>" src/main` — phải sạch (kể cả regex trích `patient_summary` trong
   `AiService`; grep `"patient_summary"\\s*:` chứ đừng tin số dòng).
2. Chạy app, chat thử một triệu chứng rõ ràng → thẻ bác sĩ phải bung ra, đúng 3 câu gợi ý.
3. Chat thử "cho tôi đặt lịch với bác sĩ X lúc 10h30 mai" → form mở đúng bác sĩ, đúng giờ.
4. Chat thử "bác sĩ X chiều nay bận à?" → thẻ XANH có lý do thật, **không** đếm ngược chuyển trang.
5. Bật chế độ gọi, xác nhận vẫn đọc được câu trả lời.
6. Đếm key thật bằng một lượt gọi thật:
   `curl -s -X POST localhost:8090/api/chat/ask -H "Content-Type: application/json" --data-binary @body.json`
   rồi `json.loads(...)` và in `len(d)` — model phải trả đúng số key, không thừa không thiếu.
5. Ghi nhật ký theo [progress-log.md](../../rules/progress-log.md) và cập nhật
   [ai-assistant.md](../../rules/ai-assistant.md) nếu danh sách key thay đổi.
