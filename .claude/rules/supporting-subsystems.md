# Supporting Subsystems

## Lịch làm việc, lịch trực & nghỉ phép

Shared by **doctors and receptionists** at `/doctor/work-schedule` and `/receptionist/work-schedule` — one template `templates/staff/work-schedule.html`, one controller superclass `controller/staff/StaffWorkScheduleController`, two thin subclasses that only declare `basePath()` and `sidebarFragment()`. The sidebar is injected via Thymeleaf preprocessing (`th:replace="~{__${sidebarFragment}__ :: sidebar}"`).

### The central distinction — ca khám vs. phiên trực
This is the whole point of the subsystem, and it is easy to break:

- **Ca khám** (`ShiftType.CA_SANG` / `CA_CHIEU`, `isClinic()`) is **office hours** and **does** open bookable slots. It lives **only in `Schedule`** (read by `/doctor-schedule`, `TimeSlotService`, and `booked-slots`) — doctor-only. Registered in one shot via `StaffScheduleService.saveClinicTemplate(user, morningDays, afternoonDays)` (`POST {base}/clinic/save`) from a weekday×session checkbox grid. **The registration applies to next week only and requires every one of the 7 weekdays to have ≥1 session** (`missingDaysMessage` rejects gaps). **Clinic no longer creates a `StaffShift` row** — `registerShift` rejects clinic types. This is also why `getEvents` renders clinic from `Schedule` and skips clinic `StaffShift`s: storing it in both places was what made each clinic block render twice, stacked.

  **`Schedule` is scoped to one week — `Schedule.weekStart`** (the Monday) says which. `saveClinicTemplate` deletes and rewrites **only the rows of the week being registered**, so the current week (patients have already booked into it) and every past week are untouched. Before this field, `Schedule` held only `dayOfWeek`, so one save silently rewrote every week including the past.

  **Never read `Schedule` with `findByDoctorId`** — that mixes all weeks together. `ScheduleRepository.findEffective(doctorId, date)` and `findEffectiveOn(date)` (all doctors, one day) are the **single source of truth** for week resolution, and resolve in order: rows for that exact week → rows of the doctor's most recent registration *before* it (the last pattern carries forward until they register again) → `weekStart IS NULL` rows (the seed's default recurring schedule) → empty, which booking treats as unrestricted. `BookingServiceImpl.isSlotWithinWorkingHours` / `slotsOutsideWorkingHours`, `/doctor-schedule`, the doctor detail page, and `getEvents` all go through them.

  **Only the next-week registration can write `Schedule`.** `DoctorService.registerSchedule` and `deleteSchedule` (plus the `GET /doctor/schedule/delete/{id}` route) were removed — they were orphaned when `schedule-register.html` was deleted, and each bypassed both the week lock and the "≥1 ca mỗi ngày" rule.

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

### Approved leave blocks the booking calendar
`LeaveServiceImpl.approve()` generates `DoctorBlockTime` rows tagged with `leaveRequestId` for every date in range (full day, or the half-day window from `HalfDaySession`). Rejecting or cancelling deletes them by that tag, so a doctor's own manual blocks are never touched. **This is the only integration point with booking — reuse it rather than teaching booking code about leave.** Emergency requests ("Báo bận đột xuất", `emergency = true`) create the blocks immediately at submit time and are approved afterwards.

`LeaveService.countAffectedBookings` feeds the warning on the approval screen, which links to the existing receptionist bulk cancel/transfer tool.

### Entities
`StaffProfile` (hireDate, workCondition, BHXH years, `headOfDepartment`), `StaffShift`, `LeaveRequest`, `ShiftCoverRequest`, all keyed on **`User`** so receptionists work too. `StaffProfile` is a separate table on purpose: `User`/`Doctor`/`Department` use positional `@AllArgsConstructor` in `DataInitializer`, so adding fields there would break seeding.

Services follow the interface + `impl` pattern: `StaffScheduleService`, `LeaveService`, `ShiftCoverService`. `CurrentUserService` resolves the principal (UserDetails or OAuth2User) for all of them.

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
`EmailServiceImpl` sends HTML mail rendered from Thymeleaf templates in `templates/email/`: booking confirmation, booking cancellation, candidate confirmation, and a general notification template. Sending is asynchronous (`@EnableAsync`), so failures surface in logs rather than in the request.

## Dashboards
`AdminDashboardService` + `DashboardApiController` aggregate booking statistics (`DailyBookingStatsDTO`, `AdminDashboardSummaryDTO`) consumed by charts on the admin dashboard.
