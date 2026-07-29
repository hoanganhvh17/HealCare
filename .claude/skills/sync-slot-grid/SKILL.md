---
name: sync-slot-grid
description: Đồng bộ lưới khung giờ khám (30 phút, giờ hành chính) trên toàn bộ dự án. Dùng khi thay đổi giờ làm việc, thêm/bớt/đổi khung giờ đặt lịch, đổi độ dài slot, hoặc khi một khung giờ hiển thị sai/lệch giữa trang đặt lịch, trang bác sĩ, trợ lý AI và lễ tân. Trigger: "khung giờ", "giờ khám", "slot", "ALL_SLOTS", "giờ hành chính", "thêm ca tối", "đổi giờ làm việc".
---

# Đồng bộ lưới khung giờ khám

Danh sách 16 khung giờ (07:30–11:30 và 13:30–17:30) bị **lặp lại ở 11 nơi**. Sửa thiếu một nơi
là sinh bug lệch giờ — đã xảy ra khi bỏ ca tối 17:30–20:30. Skill này liệt kê đủ các nơi đó và
thứ tự sửa.

## Nguyên tắc

- **Ca khám chỉ trong giờ hành chính.** Ngoài giờ là phiên trực (`StaffShift`, TT 32/2023/TT-BYT)
  và **không bao giờ** mở slot đặt khám. Đừng thêm khung giờ tối vào lưới này.
- Ranh giới giờ hành chính là `LeavePolicy.OFFICE_START` / `OFFICE_END` — sửa lưới thì phải
  sửa hằng số này trước, vì `checkOfficeHoursClash` dùng nó để chặn đăng ký trực.
- Định dạng nhãn slot cố định là `"HH:mm - HH:mm"`. Đổi định dạng sẽ phá `slotStartTime()`
  trong `ai-chat.js` và `toSpeechText()` trong `meditrust-voice.js`.

## Checklist (sửa hết, không bỏ nơi nào)

### 1. Java — nguồn dữ liệu
- [ ] [LeavePolicy.java:36-37](../../../src/main/java/com/bookinghealthy/config/LeavePolicy.java#L36-L37) — `OFFICE_START` / `OFFICE_END`
- [ ] [TimeSlotService.java:34-42](../../../src/main/java/com/bookinghealthy/service/TimeSlotService.java#L34-L42) — `MORNING_SLOTS` / `AFTERNOON_SLOTS`
- [ ] [BookingApi.java:37](../../../src/main/java/com/bookinghealthy/controller/api/BookingApi.java#L37) — `ALL_SLOTS` (endpoint `/api/bookings/booked-slots`)
- [ ] [AiController.java:86](../../../src/main/java/com/bookinghealthy/controller/api/AiController.java#L86) — `ALL_SLOTS` (dùng cho cả danh sách bác sĩ lẫn `/slot-alternatives`)

### 2. Template có nút giờ hardcode
- [ ] [user/appointment.html:359](../../../src/main/resources/templates/user/appointment.html#L359) — 2 lưới `.time-slot-grid` (sáng + chiều)
- [ ] [user/booking-edit.html](../../../src/main/resources/templates/user/booking-edit.html) — cùng cấu trúc, bệnh nhân tự dời lịch
- [ ] [receptionist/walk-in-form.html](../../../src/main/resources/templates/receptionist/walk-in-form.html) — đặt lịch tại quầy

Mỗi nút gồm `value="HH:mm - HH:mm"` (giá trị gửi lên server, **phải khớp chính xác** chuỗi trong
Java) và nhãn chỉ hiện giờ bắt đầu. `id` theo dạng `t_HHmm`.

### 3. Template có mảng JS `allTimeSlots`
- [ ] [user/doctors.html:383](../../../src/main/resources/templates/user/doctors.html#L383)
- [ ] [user/doctor-details.html:472](../../../src/main/resources/templates/user/doctor-details.html#L472)
- [ ] [user/index.html:272](../../../src/main/resources/templates/user/index.html#L272)

### 4. Trang hiển thị giờ cho khách (không phải lưới đặt, nhưng sai là mâu thuẫn công khai)
- [ ] [user/working-hours.html](../../../src/main/resources/templates/user/working-hours.html)
- [ ] [user/doctor-schedule.html](../../../src/main/resources/templates/user/doctor-schedule.html)

### 5. Lớp AI (nếu không sửa, model quảng cáo giờ mà lưới không có → mọi yêu cầu rơi vào nhánh "khung giờ kín")
- [ ] [AiService.java:59-60](../../../src/main/java/com/bookinghealthy/service/AiService.java#L59-L60) — mục 1 của prompt nêu giờ khám và câu "ngoài giờ chỉ có kíp trực"
- [ ] [ai-chat.js](../../../src/main/resources/static/assets/js/ai-chat.js) — `normalizeTimeHint()` suy luận giờ 1–5 là buổi chiều **dựa trên** khoảng giờ hành chính hiện tại; và ngay dưới nó `sessionAlreadyPassed()` mốc "sáng hết sau 11:30 / chiều hết sau 17:30"
- [ ] [AiController.java](../../../src/main/java/com/bookinghealthy/controller/api/AiController.java) — `OUTSIDE_HOURS_TEXT` (câu báo cho khách xin giờ ngoài lưới) phải khớp giờ mới

> Ánh xạ **buổi → khung giờ** (`sessionOf` / `slotsOfSession` trong `AiController`) cố ý chỉ dựa vào
> mốc `LocalTime.NOON`, không liệt kê lại lưới, nên **không phải** một nơi khai báo nữa. `extractSessionHint()`
> ở `ai-chat.js` cũng chỉ trả về chữ `morning`/`afternoon` và không biết gì về giờ — giữ nguyên như vậy,
> đừng map buổi ra giờ ở phía trình duyệt kẻo lưới có thêm nơi khai báo thứ 12.
>
> Cùng lý do đó, **trình duyệt không tự quyết định một giờ có nằm trong lưới hay không**. Nhánh "khách
> nêu giờ cụ thể" của `resolveBookingHandoff` gọi `/api/chat/slot-alternatives` và để `resolveCanonicalSlot`
> ở server trả lời. `isSlotBookable()` (hỏi `booked-slots`) **không** kiểm tra lưới — `booked-slots` chỉ
> liệt kê các khung TRONG lưới nên mọi giờ ngoài lưới đều trông như còn trống — và chỉ được dùng cho
> khung giờ do chính server sinh ra.

> `DoctorAiController` **từng** là nơi khai báo thứ 12 (và bản sao đó còn sót `17:30`/`18:00`/`18:30`/`19:00`
> — các khung ca tối đã bỏ từ 24/07 — nên trợ lý báo bác sĩ "rảnh lúc 18:30", giờ mà bệnh nhân không
> đặt được ở đâu cả). Nay nó gọi `TimeSlotService.allSlots()` và lọc tiếp qua
> `BookingService.slotsOutsideWorkingHours`, nên **không còn là một nơi khai báo**. Giữ nguyên như vậy:
> nơi nào cần đọc cả lưới thì gọi `allSlots()`, đừng liệt kê lại.

## Sau khi sửa

1. `grep -rn "07:30\|17:30" --include=*.java --include=*.html --include=*.js src/main` — rà sót.
2. Kiểm tra thủ công: mở `/appointment`, chọn một bác sĩ chỉ đăng ký ca sáng → toàn bộ buổi chiều
   phải hiện disabled (`BookingService.slotsOutsideWorkingHours`).
3. Hỏi trợ lý AI một giờ vừa thêm/vừa bỏ, xác nhận nó trả lời đúng.
4. Ghi nhật ký theo [progress-log.md](../../rules/progress-log.md) và cập nhật
   [booking-flow.md](../../rules/booking-flow.md) nếu số lượng khung giờ hoặc danh sách file thay đổi.
