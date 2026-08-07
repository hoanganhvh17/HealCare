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
- **`NotificationService.pushBookingEvent(booking, icon, title)`** is the one place that formats a booking event ("BS. X — 09:00 - 09:30, dd/MM/yyyy", link `/user/profile#booking-history`). Eleven call sites use it; do not hand-roll a twelfth summary string.
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
- `Post` — news/blog articles, authored in admin with TinyMCE rich text (vendored under `static/assets-admin/vendor/tinymce`).
- `Review` — patient ratings of doctors, submitted via `UserReviewController`.
- `Service` and `Department` — catalog entities surfaced on public pages.

## QR codes
`util/QRCodeGenerator` uses ZXing to render QR images — used for the VietQR bank-transfer payment page and booking tickets.

## Email
`EmailServiceImpl` sends HTML mail rendered from Thymeleaf templates in `templates/email/`: booking confirmation, booking cancellation, candidate confirmation, a follow-up-reminder template, and a general notification template. Sending is asynchronous (`@EnableAsync`), so failures surface in logs rather than in the request.

`general-notification.html` is rendered by a private `EmailServiceImpl.sendGeneral(...)` shared by `sendStaffNotification` and `sendAppointmentReminder` — two callers, one renderer; add a third the same way rather than copying the MimeMessage boilerplate.

**No template under `templates/email/` may use a `@{...}` link expression.** `EmailServiceImpl` renders through a plain `org.thymeleaf.context.Context`, not an `IWebContext` — `@{...}` needs the latter and throws `TemplateProcessingException` at send time, silently swallowed by the same try/catch that catches every other mail-sending failure (easy to miss: it looks exactly like an SMTP error in the log). Every existing template routes a call-to-action through plain text ("truy cập website để...") instead, since the app has no configured base URL to build an absolute link from even if `IWebContext` were available.

## Scheduled tasks (`task/`)
Each cron job is its own `@Component` in `task/` (`ClinicRegistrationTask`, `BookingCleanupTask`, `MedicalNewsTask`, `FollowUpReminderTask`, `AppointmentReminderTask`) — the one exception is the AI chat-session cleanup, a `@Scheduled` method living directly inside `AiService`. `SchedulerConfig` just flips on `@EnableScheduling`.

`AppointmentReminderTask` (daily, `0 30 7 * * ?`) reminds patients of **tomorrow's** appointment by email + bell, over `PENDING`/`CONFIRMED` bookings only, once each (`Booking.reminderSent`). It runs half an hour before `FollowUpReminderTask` so the two mail bursts do not overlap. Its finder carries an `@EntityGraph` for `user` / `doctor.user` on purpose: a cron thread has no open-in-view, and both `pushBookingEvent` and the mail body read those.

`FollowUpReminderTask` (daily, `0 0 8 * * ?`) reads a doctor's "tái khám sau N ngày/tuần/tháng" instruction out of `MedicalRecord.doctorNotes` and reminds the patient once the computed date is close — see [ai-assistant.md](ai-assistant.md) for the regex scope (deliberately narrow — no absolute dates) and [medical-records.md](medical-records.md) for the `followUpReminderSent` flag it uses for idempotency. Follows the same "email + `NotificationService.push` together" convention documented above under "Thông báo trong ứng dụng".

## Dashboards
`AdminDashboardService` + `DashboardApiController` aggregate booking statistics (`DailyBookingStatsDTO`, `AdminDashboardSummaryDTO`) consumed by charts on the admin dashboard.
