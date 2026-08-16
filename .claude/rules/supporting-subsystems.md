# Supporting Subsystems

## Lịch làm việc, lịch trực & nghỉ phép

Shared by **doctors and receptionists** at `/doctor/work-schedule` and `/receptionist/work-schedule` — one template `templates/staff/work-schedule.html`, one controller superclass `controller/staff/StaffWorkScheduleController`, two thin subclasses that only declare `basePath()` and `sidebarFragment()`. The sidebar is injected via Thymeleaf preprocessing (`th:replace="~{__${sidebarFragment}__ :: sidebar}"`).

### The central distinction — ca khám vs. phiên trực
This is the whole point of the subsystem, and it is easy to break:

- **Ca khám** (`ShiftType.CA_SANG` / `CA_CHIEU`, `isClinic()`) is **office hours** and **does** open bookable slots. It lives **only in `Schedule`** (read by `/doctor-schedule`, `TimeSlotService`, and `booked-slots`) — doctor-only. Registered in one shot via `StaffScheduleService.saveClinicTemplate(user, morningDays, afternoonDays)` (`POST {base}/clinic/save`) from a weekday×session checkbox grid. **The registration applies to next week only and requires every one of the 7 weekdays to have ≥1 session** (`missingDaysMessage` rejects gaps). **Clinic no longer creates a `StaffShift` row** — `registerShift` rejects clinic types. This is also why `getEvents` renders clinic from `Schedule` and skips clinic `StaffShift`s: storing it in both places was what made each clinic block render twice, stacked.

  **`Schedule` is scoped to one week — `Schedule.weekStart`** (the Monday) says which. `saveClinicTemplate` deletes and rewrites **only the rows of the week being registered**, so the current week (patients have already booked into it) and every past week are untouched. Before this field, `Schedule` held only `dayOfWeek`, so one save silently rewrote every week including the past.

  **Never read `Schedule` with `findByDoctorId`** — that mixes all weeks together. `ScheduleRepository.findEffective(doctorId, date)` and `findEffectiveOn(date)` (all doctors, one day) are the **single source of truth** for week resolution, and resolve in order: rows for that exact week → rows of the doctor's most recent registration *before* it (the last pattern carries forward until they register again) → `weekStart IS NULL` rows (the seed's default recurring schedule) → empty, which booking treats as unrestricted. `BookingServiceImpl.isSlotWithinWorkingHours` / `slotsOutsideWorkingHours`, `/doctor-schedule`, the doctor detail page, and `getEvents` all go through them.

  **Only the next-week registration can write `Schedule`.** `DoctorService.registerSchedule` and `deleteSchedule` (plus the `GET /doctor/schedule/delete/{id}` route) were removed — they were orphaned when `schedule-register.html` was deleted, and each bypassed both the week lock and the "≥1 ca mỗi ngày" rule.

  **Trưởng khoa xếp ca cho cả khoa** — `/head/clinic-roster` (`HeadRosterController`), a doctors × 7 weekdays × (Sáng/Chiều) checkbox grid saved in one shot via `StaffScheduleService.assignClinicWeek`. This is the *active* counterpart to the old "Chốt & tự xếp lịch" button, which only ran `autoRegisterUnregisteredDoctors` and chose nobody. It reuses `replaceClinicSchedules` + `markRegisteredForNextWeek`, so the week lock still holds. Three rules, all load-bearing:

  - **Coverage is checked per DEPARTMENT, not per doctor**: every weekday needs ≥1 doctor on ca sáng and ≥1 on ca chiều (`missingCoverageMessage`). `missingDaysMessage` (every doctor, every day) is the rule for *self*-registration only — applying it here would make it impossible for a head to give anyone a day off.
  - **But every doctor still needs ≥1 session somewhere in the week** (`emptyWeekMessage`), unless approved leave covers all 7 days. A completely empty week is *unrepresentable*: `replaceClinicSchedules` deletes that week's rows, and `findEffective` reads "no rows for this week" as "not registered" and falls back to the doctor's previous pattern — so the saved roster would silently differ from what patients can book, and the grid would re-tick the old pattern next time.
  - The head is **not** bound by `LeavePolicy.CLINIC_DEADLINE_*` (Chủ nhật 22:00) since they are the one who closes the schedule; they are still limited to **next week**.

  Cells for days the doctor has blocking leave render as "Nghỉ" and are re-checked server-side. Doctors whose effective schedule actually changed get an email **and** a `Notification`; opening the grid and saving it unchanged notifies nobody (`changesEffectiveSchedule` compares against `findEffective`, not against that week's rows).

  **Trưởng khoa phân công trực** — `POST /head/duty-roster/assign` → `assignDutyShift`, a modal on `/head/duty-roster` (the "chưa có ai trực" badges prefill the date). It runs the **same** `validateShift` as self-registration, so every legal rule survives; the only difference is the shift is created `APPROVED` with the head as approver, since the person assigning is the person who would approve. Clinic shift types are rejected here, as in `registerShift`.

  **Weekly registration lifecycle.** `StaffProfile.clinicRegisteredForWeek` (a Monday date) records which week a doctor last registered for; `saveClinicTemplate` sets it to `nextWeekStart()`. Registration closes at the deadline in `LeavePolicy.CLINIC_DEADLINE_DAY`/`_TIME` (**Chủ nhật 22:00**, the moment the cron finalizes the week): after it `saveClinicTemplate` rejects, the checkbox grid renders disabled with no submit button, and the bell stops nagging. `ClinicRegistrationTask` runs two Sunday crons: 08:00 emails doctors who haven't registered for next week (`sendNextWeekRegistrationReminders`), 22:00 auto-fills a full week (every day ≥1 morning) for anyone still unregistered (`autoRegisterUnregisteredDoctors`) and emails them. The bell (`/api/staff/notifications`) shows the same reminder from Thursday onward. A trưởng khoa can trigger both manually per department from `/head/dashboard` (`POST /head/clinic/remind` and `/head/clinic/finalize`).

  On the calendar, clinic blocks outside next week come back with `readOnly = true` and a `statusLabel` of "Ca khám đã chốt" / "Ca khám dự kiến"; `work-schedule.js` adds `.ws-locked` (dimmed, padlock) so a doctor can see at a glance which week they may still edit.
- **Phiên trực** (`TRUC_NGOAI_GIO`, `TRUC_12H_DEM`, `TRUC_24H`) and **hội chẩn** (`HOI_CHAN`) are the only things in `StaffShift` now. Trực (`isDuty()`) is **outside office hours** per Thông tư 32/2023/TT-BYT and must **never** open a bookable slot; `StaffShift` is read by no booking or AI code, so this holds by construction. Duty can be registered for several weeks at once via `repeatWeeks` on `POST {base}/shift/register` (same weekday, N consecutive weeks). Do not wire `StaffShift` into slot availability.

`TRUC_24H` covers a whole day, so it is rejected on weekdays; `StaffScheduleServiceImpl.checkOfficeHoursClash` also rejects any duty starting before `LeavePolicy.OFFICE_END` on a weekday.

Duty start times must therefore be **≥ 17:30**. This is why the weekday full-cover shift is `TRUC_NGOAI_GIO` (17:30 → 07:30, 14 giờ) rather than a 16:00-start "trực 16/24": with office hours ending at 17:30, a 16:00 start is rejected by that very rule and the shift type would be impossible to register on any weekday.

### Legal quotas — `config/LeavePolicy`
**Single source of truth** for every number; do not hardcode days anywhere else.

| Chế độ | Mức | Căn cứ |
|---|---|---|
| Phép năm | 12 / 14 / 16 ngày theo `WorkCondition` | BLLĐ 2019, Điều 113.1 |
| Thâm niên | +1 ngày mỗi 5 năm | Điều 114 |
| Nghỉ ốm | 30/40/60 (bình thường), 40/50/70 (nặng nhọc) theo năm đóng BHXH | Luật BHXH 2024, Điều 43 |
| Việc riêng | 3 / 1 / 3 ngày có lương; 1 ngày không lương | Điều 115.1 và 115.2 |
| Thai sản | 180 ngày | Luật BHXH |
| Nghỉ bù sau trực 24/24 | 1 ngày (thường), 2 ngày (lễ, Tết) | QĐ 73/2011/QĐ-TTg, Điều 2.4 |
| Nghỉ sau trực 12/24, 16/24 | ≥ 12 giờ | QĐ 73/2011/QĐ-TTg, Điều 2.4 |
| Công bố lịch trực trước | ≥ 7 ngày (cảnh báo, không chặn) | TT 32/2023/TT-BYT |
| Chốt đăng ký ca khám tuần sau | Chủ nhật 22:00 (chặn) | quy định nội bộ |

`LeavePolicy.weekStartOf(date)` is also the one place that decides a work week starts on Monday — `Schedule.weekStart`, the registration lock, and the calendar all call it rather than re-deriving the Monday.

`LeavePolicy.HEAVY_DEPARTMENTS` lists the departments seeded as `WorkCondition.HEAVY` (14 ngày): Cấp cứu, Gây mê hồi sức, Ung bướu, Chẩn đoán hình ảnh, Tâm thần, Huyết học.

### Đơn / ca đã hết hiệu lực không còn ra quyết định được
`LeaveService.whyCannotDecide(request)` and `StaffScheduleService.whyCannotDecideShift(shift)` are the single source of truth for "can the head still act on this": `null` = yes, otherwise the Vietnamese reason. Both reject a **CANCELED** item and one whose period has already elapsed (`endDate < today`, `getEndsAt() < now`). `approve`/`reject` call them first, and the two head screens use them to hide the buttons and show an "Đã hết hiệu lực" badge — so a crafted POST is refused too. Approving a finished leave would generate `DoctorBlockTime` for past dates and wrongly consume the year's quota; approving a finished duty shift would generate compensatory leave nobody can take.

**Only the shared conditions live in those methods.** "Already APPROVED" belongs to `approve`/`approveShift` alone, because **rejecting an already-approved item is how a head revokes a decision** (`reject` calls `removeBlockTimes`). `rejectShift` also gained the status guard it never had — it used to overwrite an APPROVED or CANCELED shift.

Because both decisions are now blocked, an elapsed PENDING request would sit in the queue forever, so `HeadApprovalController` splits the list into `requests` and `expiredRequests` (a collapsed table at the bottom), and `LeaveService.countPendingInDepartment` **counts only what is still actionable** (`AND l.endDate >= :today` in the repository query) — the dashboard tiles and the notification bell both read that number, and a badge that can never be cleared is worse than no badge. No new `ApprovalStatus` constant was added on purpose: it is a native MySQL `ENUM(...)` column (see [environment-setup.md](environment-setup.md)).

### Thông báo trong ứng dụng (`Notification`)
Every head-doctor decision now writes a row to `notifications` **in addition to** the email, because `EmailServiceImpl` is `@Async` and swallows failures into `System.err` — a doctor had no reliable way to learn whether their request was approved.

- `NotificationService.push(recipient, icon, title, message, link)` is called right beside the existing `emailService.sendStaffNotification` in `LeaveServiceImpl.notifyDecision`, `StaffScheduleServiceImpl.notifyShiftDecision`, `autoRegisterUnregisteredDoctors`, and both new head assignment paths. Keep the pair together so the bell and the inbox never tell different stories.
- `GET /api/staff/notifications` merges the stored rows (with `id`, `link`, `read`, `time`) **first**, then the still-computed reminders (unregistered week, cover invitations, own shifts needing cover, head's pending queue) — those must stay computed because they have to disappear on their own when the underlying work is done. It also returns `unreadCount`; `POST /api/staff/notifications/read` clears it.
- The old *computed* "đơn nghỉ đã có quyết định trong 7 ngày" contributor was **removed**: with decisions persisted it would list every decision twice.
- The bell markup moved into the shared `doctor/include/header :: header-nav` fragment (see [coding-conventions.md](coding-conventions.md)) so it appears on every doctor / head / receptionist page, not only the calendar page.

#### The bell serves PATIENTS too
The same `Notification` table backs a second bell in `user/include/header :: header-nav`, rendered under `sec:authorize="isAuthenticated()"` and driven by `assets/js/user-notifications.js` (a port of `staff-notifications.js`; ids are `userNotif*`, **never** `staffNotif*`, because `user/medical-record-detail.html` embeds both the patient header and the admin footer).

- **`GET /api/notifications` + `POST /api/notifications/read`** (`UserNotificationApiController`) serve it. Deliberately **not** `/api/staff/notifications`: that endpoint bolts on four staff-only computed reminders, and the path prefix is a lie when the caller is a patient. It returns stored rows only — every patient event is persisted when it happens, so there is nothing to recompute.
- Before this the patient bell was a dead `<a href="#">`. `FollowUpReminderTask` had been writing `Notification` rows for patients all along, so **the nhắc-tái-khám notification existed but was unreachable** — the reason to check both ends whenever a `push` target is not a staff member.
- **`NotificationService.pushBookingEvent(booking, icon, title)`** is the one place that formats a booking event ("BS. X — 09:00 - 09:30, dd/MM/yyyy", link `/user/profile#booking-history`). Eleven call sites use it; do not hand-roll a twelfth summary string. The exception is **`MedicalRecordDeliveryServiceImpl`**, which calls plain `push` on purpose: its link must be `/user/medical-record/view/{bookingId}`, and `pushBookingEvent`'s fixed link would drop the patient on the booking list to hunt for the visit themselves.
- **`pushToAllPatients(...)`** fans a published article out to every `ROLE_USER` (`@Async`, `saveAll` in batches of 500). It carries **no** de-duplication — `AdminPostController` owns that, reading the previous status before the write in both `publishPost` and `savePost`, since `/publish/{id}` is a GET that a refresh or a prefetch can fire twice. It is deliberately **not** `@Transactional`: paired with `@Async` the proxy order is unspecified, and the losing order opens the transaction on the caller's thread while the work runs on another.

### Approved leave blocks the booking calendar
`LeaveServiceImpl.approve()` generates `DoctorBlockTime` rows tagged with `leaveRequestId` for every date in range (full day, or the half-day window from `HalfDaySession`). Rejecting or cancelling deletes them by that tag, so a doctor's own manual blocks are never touched. **This is the only integration point with booking — reuse it rather than teaching booking code about leave.** Emergency requests ("Báo bận đột xuất", `emergency = true`) create the blocks immediately at submit time and are approved afterwards.

`LeaveService.countAffectedBookings` feeds the warning on the approval screen, which links to the existing receptionist bulk cancel/transfer tool.

### Entities
`StaffProfile` (hireDate, workCondition, BHXH years, `headOfDepartment`), `StaffShift`, `LeaveRequest`, `ShiftCoverRequest`, `Notification`, all keyed on **`User`** so receptionists work too. `StaffProfile` is a separate table on purpose: `User`/`Doctor`/`Department` use positional `@AllArgsConstructor` in `DataInitializer`, so adding fields there would break seeding.

Services follow the interface + `impl` pattern: `StaffScheduleService`, `LeaveService`, `ShiftCoverService`, `NotificationService`. `CurrentUserService` resolves the principal (UserDetails or OAuth2User) for all of them.

## Wallet & transactions
`User.balance` (a `BigDecimal` on the user) plus a `WalletTransaction` ledger typed by `TransactionType`. `WalletService` handles debit (`payWithWallet`, returns `false` on insufficient funds rather than throwing) and `refundToWallet`. Booking cancellations refund to the wallet.

## Recruitment
`JobPosting` + `Candidate` (with `CandidateStatus`) — public careers pages plus admin management (`AdminJobController`, `AdminCandidateController`). Applicants receive a confirmation email.

## Content
- `Post` — news/blog articles, authored in admin with TinyMCE rich text (vendored under `static/assets-admin/vendor/tinymce`), **or collected automatically from real newspapers** by `MedicalNewsTask` (see above), in which case `sourceUrl` / `sourceName` are set and `news-details.html` renders a "Nguồn: … — Đọc bản gốc" block. Both columns are nullable, so an admin-written post simply has none.

  **`admin/post-form.html` binds `@ModelAttribute Post`, so any field without an input on the form is written back as empty.** That is why `image` is patched by hand in `AdminPostController.savePost`, and why `sourceUrl` / `sourceName` need hidden inputs. The form also carries a **`status` select**: without it `Post.status`'s field initialiser (`"PUBLISHED"`) meant that opening a draft, changing one word and pressing Lưu silently published it *and* fanned a notification out to every patient.
- `Review` — patient ratings of doctors, submitted via `UserReviewController`. One review per booking, enforced by `ReviewServiceImpl.saveReview`.

  **A doctor nobody has rated is advertised as 5.0, and only the public page may do that.** `user/doctors.html` renders `doc.rating || 5.0` (in JS `0.0` is falsy, and `getAverageRating` returns `0.0` — not `null` — for zero reviews); `DoctorDTO(Doctor)` defaults the same way. That is a deliberate marketing choice on `/doctors`. Anything that **ranks or recommends** must instead go through `ReviewService.getRatingStats(ids)`, which returns `RatingStats(average, count)` for many doctors in one aggregate query and **omits** unrated doctors entirely rather than reporting them as `0.0` — see the `sortBy=rating` rules in [ai-assistant.md](ai-assistant.md). **`ReviewService.hasReview(bookingId)` exists so the UI can agree with that rule**: `ProfileController` passes a `reviewedBookingIds` set and `user/profile.html` renders an "Đã đánh giá" badge instead of the button. Before this the button showed for *every* `COMPLETED` booking, so a patient could open the modal, pick stars, type a comment, press Gửi — and only then be told "Bạn đã đánh giá dịch vụ này rồi", losing what they wrote.
- `Service` and `Department` — catalog entities surfaced on public pages.

## QR codes
`util/QRCodeGenerator` uses ZXing to render QR images — used for the VietQR bank-transfer payment page and booking tickets.

## Email
`EmailServiceImpl` sends HTML mail rendered from Thymeleaf templates in `templates/email/`: booking confirmation, booking cancellation, candidate confirmation, a follow-up-reminder template, **the medical-record/e-prescription mail**, and a general notification template. Sending is asynchronous (`@EnableAsync`), so failures surface in logs rather than in the request.

**Because sending happens on another thread, a mail method must never touch a lazy association.** `Booking.user`, `Booking.doctor` and `Doctor.user` are all `@ManyToOne(fetch = LAZY)`; open-in-view only binds a session to the *request* thread, so an un-initialised proxy read inside an `@Async` method throws `LazyInitializationException` — and it lands in the very try/catch that swallows every mail failure, so the log looks exactly like an SMTP error while the patient gets nothing. `sendMedicalRecordReady` therefore takes a `MedicalRecordMailDTO` of plain strings, built by `MedicalRecordDeliveryServiceImpl` while still on the request thread. **Follow that shape for any new mail that needs more than the fields already loaded**; the older methods get away with passing `Booking` only as long as the request is still in flight.

`sendMedicalRecordReady` is also the one mail with an **attachment** (`don-thuoc-<id>.pdf` from `PdfExportService`). The attachment is optional by design — a null `prescriptionPdf` still sends, since the prescription is in the body too. See [medical-records.md](medical-records.md).

`general-notification.html` is rendered by a private `EmailServiceImpl.sendGeneral(...)` shared by `sendStaffNotification` and `sendAppointmentReminder` — two callers, one renderer; add a third the same way rather than copying the MimeMessage boilerplate.

**No template under `templates/email/` may use a `@{...}` link expression.** `EmailServiceImpl` renders through a plain `org.thymeleaf.context.Context`, not an `IWebContext` — `@{...}` needs the latter and throws `TemplateProcessingException` at send time, silently swallowed by the same try/catch that catches every other mail-sending failure (easy to miss: it looks exactly like an SMTP error in the log). Every existing template routes a call-to-action through plain text ("truy cập website để...") instead, since the app has no configured base URL to build an absolute link from even if `IWebContext` were available.

## Chuyển khoản ngân hàng & webhook (`VietQRController`)

`util/PaymentMemo` là **nguồn sự thật duy nhất** cho nội dung chuyển khoản: `format(prefix, id)` sinh ra nó, `parseBookingId(prefix, description)` đọc lại, và tiền tố đến từ `vietqr.memo-prefix`. Ba nơi tiêu thụ: mã QR, webhook, và `checkout-qr.html`.

Nó tồn tại vì hai hằng số hardcode ở hai file đã lệch nhau: QR in `"HEALCARE <id>"` còn webhook so khớp `"MDTRUST"`, nên **nhánh xử lý của webhook chưa từng chạy lần nào**. Khách quét mã, chuyển tiền thật, webhook nhận rồi lặng lẽ trả `SUCCESS`, và ba phút sau `BookingCleanupTask` huỷ lịch vì "chưa thanh toán".

`parseBookingId` dùng regex `<prefix>\s*0*(\d+)`, **không** `replaceAll("[^0-9]", "")`: nội dung chuyển khoản thật do ngân hàng gửi sang còn kèm số tài khoản người gửi, mã tham chiếu và ngày tháng — gom hết chữ số lại là ghi nhận tiền cho nhầm lịch hẹn của người khác.

Bốn luật của webhook, mỗi luật vá một lỗ hổng riêng:

- **Bí mật trong header là bắt buộc** (`payment.webhook.secret`), so bằng `MessageDigest.isEqual`. Endpoint là `permitAll` và CSRF tắt toàn cục, nên trước đó bất kỳ ai trên internet cũng xác nhận được lịch bất kỳ. **Để rỗng là đóng hẳn webhook** — an toàn hơn mở toang.
- **Chống trùng bằng `Booking.bankTxnRef`.** Casso/SePay gửi lại cùng một giao dịch là hành vi bình thường của chúng; mỗi lần gửi lại là một email xác nhận nữa tới bệnh nhân.
- **Đối chiếu số tiền** với `bookingPrice`. Thiếu tiền thì ghi log cho lễ tân xử lý tay, không tự động hoàn/huỷ — đó là quyết định về tiền của người khác.
- **Parser chịu được cả hai shape** vì chưa chốt nhà cung cấp: SePay phẳng (`content`/`transferAmount`/`referenceCode`) và Casso lồng trong `data[]` (`description`/`amount`/`tid`). Bản cũ deref thẳng `payload.get("description")` nên payload Casso ném NPE, rơi vào catch-all và trả HTTP 400 — trông y hệt một lỗi mạng.

Trang `/checkout-qr` **đếm ngược 3 phút** khớp `BookingCleanupTask` thay vì poll vô hạn, và nhận diện cả trường hợp phiên hết hạn (Spring trả HTML trang đăng nhập kèm status 200, nên chỉ xét `response.ok` là lặp mãi).

## Scheduled tasks (`task/`)

**Mọi job đều gắn `@SchedulerLock` (ShedLock) — trừ đúng một cái.** Không có khoá thì mỗi instance chạy mỗi job một lần: bệnh nhân nhận email nhắc lịch nhân đôi, `MedicalNewsTask` lấy trùng bài (mỗi bài trùng tốn thêm một lượt gọi AI), `ClinicRegistrationTask` xếp lịch chồng nhau.

Các cờ boolean sẵn có **không thay thế được**, và không theo ba kiểu khác nhau: `AppointmentReminderTask` ghi `reminderSent` **sau** khi gửi (hai instance cùng đọc trước khi ai kịp ghi → cùng gửi), `FollowUpReminderTask` ghi **trước** khi gửi (an toàn hơn nhưng vẫn là đọc-rồi-ghi), còn `ClinicRegistrationTask.remindDoctorsToRegister` **không ghi cờ nào cả** — chạy hai lần là gửi hai lần, luôn luôn.

`config/ShedLockConfig` dùng `JdbcTemplateLockProvider` trên `DataSource` sẵn có (không cần Redis). Hai tham số bắt buộc:
- **`usingDbTime()`** — thiếu nó ShedLock so đồng hồ của từng máy chủ ứng dụng với nhau, và lệch giờ là cách kinh điển để nó âm thầm cho chạy hai lần.
- **`lockAtLeastFor`** trên các job cron — một job xong trong 2 giây sẽ nhả khoá kịp cho instance kia (trigger lệch vài trăm mili-giây) chạy lại toàn bộ.

**`AiController.cleanUpExpiredLocks` TUYỆT ĐỐI không được gắn `@SchedulerLock`** — nó dọn `softLockCache`, một map nằm trong bộ nhớ của chính JVM đó, nên mỗi instance phải tự chạy. Gắn khoá phân tán vào là cache của các instance còn lại phình lên và không bao giờ được dọn. Có comment tại chỗ nói rõ điều này, để một lần quét "gắn annotation cho mọi @Scheduled" không phá nó.

**`spring.task.scheduling.pool.size=4`.** Pool mặc định của Spring là **1 luồng** cho cả 8 job. `MedicalNewsTask` chiếm luồng hàng phút (5 RSS + 2 bài, mỗi bài một lượt HTTP 10s + một lượt LLM tối đa 2×25s + tải ảnh), và trong suốt thời gian đó `BookingCleanupTask` không chạy — lịch chưa thanh toán giữ chỗ lâu hơn 3 phút rất nhiều. **Đừng "sửa" bằng `@Async` trên job tin tức**: nó phá `@SchedulerLock` (khoá nhả ngay khi luồng scheduler trả về, còn việc thật chạy không khoá).
Each cron job is its own `@Component` in `task/` (`ClinicRegistrationTask`, `BookingCleanupTask`, `MedicalNewsTask`, `FollowUpReminderTask`, `AppointmentReminderTask`) — the one exception is the AI chat-session cleanup, a `@Scheduled` method living directly inside `AiService`. `SchedulerConfig` just flips on `@EnableScheduling`.

`AppointmentReminderTask` (daily, `0 30 7 * * ?`) reminds patients of **tomorrow's** appointment by email + bell, over `PENDING`/`CONFIRMED` bookings only, once each (`Booking.reminderSent`). It runs half an hour before `FollowUpReminderTask` so the two mail bursts do not overlap. Its finder carries an `@EntityGraph` for `user` / `doctor.user` on purpose: a cron thread has no open-in-view, and both `pushBookingEvent` and the mail body read those.

`FollowUpReminderTask` (daily, `0 0 8 * * ?`) reads a doctor's "tái khám sau N ngày/tuần/tháng" instruction out of `MedicalRecord.doctorNotes` and reminds the patient once the computed date is close — see [ai-assistant.md](ai-assistant.md) for the regex scope (deliberately narrow — no absolute dates) and [medical-records.md](medical-records.md) for the `followUpReminderSent` flag it uses for idempotency. Follows the same "email + `NotificationService.push` together" convention documented above under "Thông báo trong ứng dụng".

`MedicalNewsTask` (`news.fetch.cron`, default `0 0 6,18 * * ?`) collects real medical news — see the section below; it is the only task whose schedule is configurable.

## Tin tức y tế tự thu thập (`MedicalNewsTask` + `NewsFeedService`)

**The AI summarises; it does not write.** The old version of this task simply told the model *"hãy viết một bản tin cảnh báo về một dịch bệnh đang bùng phát"* and saved the result — no URL, no date, no source, so every case count and place name in the article was invented and published under the hospital's name. Now `NewsFeedServiceImpl` downloads the real article first and the model is only allowed to summarise what it was given.

The pipeline: RSS (`NewsSourceCatalog.SOURCES`) → filter by age + `existsBySourceUrl` → `fetchArticleText` → `AiService.getStatelessResponse` → sanitize → save as **DRAFT**. Admin reviews at `/admin/manage-news` and presses "Duyệt", which is what fans the notification out to patients.

Rules that must survive any edit:

- **`NewsSourceCatalog` is an allow-list, not a suggestion.** The task never fetches outside it. Adding a source means verifying the RSS URL from the machine that runs the app first — a feed that 404s or returns HTML only shows up as "lấy được 0 bài". `NewsFeedServiceImpl` wraps **each source in its own try/catch** so one dead feed cannot empty the whole batch.
- **`Post.sourceUrl` is the dedup key**, via `PostRepository.existsBySourceUrl`. The old check was `findByTitleContainingIgnoreCase(<the whole generated title>)`, a substring match that essentially never fired.
- **The model's HTML is passed through `Jsoup.clean(content, Safelist.basic())`.** `news-details.html` renders `content` with `th:utext` — live HTML on a public page. Without this, anything the model emits becomes real markup in the patient's browser.
- **Outbreak articles get the `[Cảnh báo y tế] ` prefix** (keywords in `NewsSourceCatalog.OUTBREAK_KEYWORDS`). That prefix is the *only* thing `PostServiceImpl.findLatestEmergencyAlert()` matches on to feed the alert banner in the AI chat widget (`GET /api/public/news/latest-alert`). Real headlines never contain it, so dropping this step kills the banner silently.

  **`looksLikeOutbreak` matches whole words via the space-padded idiom, never a bare `contains`.** A plain substring test labelled *"Điều tra dấu hiệu lừa đảo tại thẩm mỹ viện Lucy, tìm khách mua **dịch vụ**…"* as an epidemic alert, because `"dịch"` sits inside `"dịch vụ"`. Same trap for `"tả"` in "mô tả" and `"dại"` in "dại dột" — so the list holds only unambiguous multi-word terms (`dịch bệnh`, `ổ dịch`, `dịch tả`, `bệnh dại`). It also drops `"cảnh báo"` / `"bộ y tế"` as far too broad: "Bộ Y tế hướng dẫn khám sàng lọc ung thư vú" is ordinary news, and labelling it an alert empties the banner of meaning. This is the same rule already documented for `extractSessionHint` in [coding-conventions.md](coding-conventions.md) — `\b` is ASCII-only and can never match a Vietnamese word with diacritics.
- **Two things about outbound HTTP were found by testing against the live feeds and are easy to undo:**
  - The `USER_AGENT` must stay a **plain browser string**. Appending `...NewsBot/1.0` made VnExpress's image CDN return HTTP 403 for every image while the identical URL with a browser UA returned 200.
  - The saved file's extension comes from the response **Content-Type**, not the URL. That CDN serves WebP from a URL ending in `.jpg`, and Spring decides the served Content-Type from the file extension — so trusting the URL writes WebP bytes into a `.jpg` and mislabels the image to the browser. The image URL's **query string must be kept**: the `s=` signature is required, and the bare URL returns 401.
- **RSS date formats differ per source and both are handled.** VnExpress uses RFC-1123; Tuổi Trẻ uses `M/d/yyyy h:mm:ss AM` with a **U+202F narrow no-break space** before AM/PM, which looks exactly like a space and silently dropped every one of its articles until it was normalised. An item whose date will not parse is skipped, and the first offending raw value is logged per source so the format can be added.
- **`AiService.getStatelessResponse`** is used rather than `getConversationalResponse`: no `AiChatSession`, no 6-message replay of a previous article, and it returns `null` instead of the Vietnamese string `"Hệ thống AI đang bận..."` that the old code fed straight into `readTree`.
- `POST /admin/manage-news/fetch-now` runs the same method on demand (POST, not GET — it writes). It creates drafts only; it publishes nothing.

## Dashboards
`AdminDashboardService` + `DashboardApiController` aggregate booking statistics (`DailyBookingStatsDTO`, `AdminDashboardSummaryDTO`) consumed by charts on the admin dashboard.
