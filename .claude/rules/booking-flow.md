# Booking Flow (core domain)

The booking pipeline is the heart of the app and spans several classes.

## 1. Time slots
Availability is computed against a **fixed 30-minute slot grid covering office hours only** — 16 slots: 07:30–11:30 and 13:30–17:30. The canonical slot list is **duplicated three times** — [BookingApi.java](src/main/java/com/bookinghealthy/controller/api/BookingApi.java) (`ALL_SLOTS`), [AiController.java](src/main/java/com/bookinghealthy/controller/api/AiController.java) (`ALL_SLOTS`, used by both the doctor-list and slot-alternatives endpoints), and `TimeSlotService` — **keep all three in sync** if you change operating hours.

**There is deliberately no evening slot.** Outside office hours the hospital runs an on-call rota (`StaffShift`, Thông tư 32/2023/TT-BYT), which never opens bookable slots — see [supporting-subsystems.md](supporting-subsystems.md). The grid used to run to 20:30, which contradicted the public `/working-hours` page and blurred ca khám into phiên trực. The office-hours boundary itself lives in `LeavePolicy.OFFICE_START` / `OFFICE_END`.

Slot buttons are also hardcoded in `user/appointment.html`, `user/booking-edit.html` and `receptionist/walk-in-form.html`, and an `allTimeSlots` JS array is duplicated in `user/doctors.html`, `user/doctor-details.html` and `user/index.html` — all six must match the Java lists too. The `/skills/sync-slot-grid` skill holds the full checklist (11 places including `LeavePolicy.OFFICE_START`/`OFFICE_END`, the AI prompt and `normalizeTimeHint`); use it rather than re-deriving the list.

`GET /api/bookings/booked-slots?doctorId=&date=` returns unavailable slots = existing bookings (any status except `CANCELED`), **plus** doctor-blocked ranges (`DoctorBlockTime`, interval-overlap), **plus** office-hours slots that fall **outside the doctor's working `Schedule`** for that weekday (`BookingService.slotsOutsideWorkingHours`). So a doctor who registered only mornings shows the whole afternoon as unavailable. **Fallback:** a doctor with *no* `Schedule` rows at all is left unrestricted (keeps the old behaviour for seed doctors that never registered).

**`Schedule` is per-week, not a global weekly template.** Each row carries a `weekStart` (Monday), and doctors register only for next week — so the same weekday can have different hours in different weeks. Every read of a doctor's working hours must go through `ScheduleRepository.findEffective(doctorId, date)` (or `findEffectiveOn(date)` for all doctors on one day), never `findByDoctorId`, which mixes every week together. See [supporting-subsystems.md](supporting-subsystems.md) for the resolution order and the registration lock.

**The AI soft-lock is only taken when the patient actually commits.** `AiController.softLockCache` is a 3-minute in-process hold keyed `doctorId_date_slot`. `GET /api/chat/doctors/department/{id}` **reads** it but no longer writes it; the write moved to `POST /api/chat/hold-slot`, which `finishBookingHandoff` calls once a patient has been handed a specific slot. Previously merely opening the chat locked every slot the listing returned, and other sessions had those slots **hidden** — two patients browsing the same department saw different availability, and one could be told "đang có người khác giữ chỗ" about a slot nobody had claimed. Since that endpoint is `permitAll`, a loop with changing `sessionId` could also blank out the whole schedule. The map is now updated via `compute()` so the check and the write are atomic. It is a collision-reducer only — the real guard is still `BookingServiceImpl.reserve()`.

**The AI endpoints honour `Schedule` too.** `AiController` used to rebuild the slot filter itself and skip the table outright, so the assistant offered slots the doctor was not working and the patient hit a greyed-out slot on `/appointment`. `/api/chat/doctors/department/{id}`, `/api/chat/slot-alternatives` and `/api/chat/doctor-availability` all inject `BookingService` and call `slotsOutsideWorkingHours` **once per (doctor, day)**, never per slot. Reuse that path for any new slot-producing code — a fourth private copy of the working-hours comparison is what caused the bug. `getDoctorsByDepartment` carried exactly such a copy (its own hand-rolled 5-filter loop) until it was deleted in favour of `AiController.DaySlots`; **every AI endpoint now goes through `DaySlots.blockReason`**, so the filter and the explanation can never drift apart.

**Cost of `DaySlots`, and the two things that keep it bounded.** Each instance costs ~4 queries (effective schedule, bookings, block times). It used to be ~6 because `hasRegisteredSchedule` re-ran the same `findEffective` that `offDutySlots` had just run — `scheduleKnown` is now computed **lazily**, and the ranking path never asks for it. Second, the department scan is **day-major and stops at the first day any doctor is free**, so the normal cost is `doctors × 1 day` (~24 queries for a 6-doctor department), not `doctors × 7`. If that ever needs batching, the escape hatch is `ScheduleRepository.findEffectiveOn(date)` (one query for every doctor on a day) exposed through a **new `BookingService` method** — never read straight from `AiController`, or it becomes the fourth copy again.

**`BookingService.hasRegisteredSchedule(doctorId, date)` exists for the EXPLANATION layer only.** `slotsOutsideWorkingHours` returning empty is ambiguous — it means both "the doctor works all day" and "the doctor has no `Schedule` rows at all" — which let the AI describe a never-registered doctor as working 07:30–11:30 and 13:30–17:30. The new predicate distinguishes the two so the sentence can say "hệ thống chưa có lịch đăng ký" instead. **The booking rule itself is unchanged: no rows still means unrestricted**, and it must stay that way or the 132 seeded doctors become unbookable. The off-duty check also sits **before** the soft-lock block, so the AI no longer takes 3-minute holds on slots that can never become bookings.

`BookingController.processAppointment` enforces the same rule server-side via `BookingService.isSlotWithinWorkingHours` before reserving — a crafted POST for an off-hours slot is rejected, not just hidden.

Because approved leave auto-generates `DoctorBlockTime` rows, a doctor on leave disappears from availability everywhere — booking pages, `booked-slots`, and the AI slot-alternatives endpoint — with no extra code in any of them.

The public doctors list (`/doctors`) never hides a doctor whose slots are full for the viewed day — it shows "Hết lịch trống" and keeps the card, so a doctor doesn't flicker in and out of the list as the day advances.

## 2. Payment branching
`BookingController.processAppointment` (`POST /appointment`) branches on `paymentMethod`:

- **WALLET** — reserve the slot, then `WalletService.payWithWallet` debits `User.balance`. On success → `CONFIRMED`/`PAID` plus a confirmation email; on insufficient funds → `CANCELED`/`FAILED`.
- **BANK_TRANSFER** — reserve, then redirect to `/checkout-qr?id=` (VietQR page). See the webhook rules in [supporting-subsystems.md](supporting-subsystems.md); the transfer memo is owned by `util/PaymentMemo` and `vietqr.memo-prefix`.
- **VNPAY** (default) — reserve, persist `vnp_TxnRef`, then redirect to the URL built by `PaymentService`. The gateway calls back to `/payment-return`.

### `/payment-return` verifies six things, in this order
It used to verify **none**. The old handler bound only `vnp_ResponseCode` and `vnp_OrderInfo` and trusted them, while the route fell through to `anyRequest().authenticated()` — so **any logged-in user could hand-craft a URL and mark any booking PAID+CONFIRMED**, occupying a real slot and triggering a real confirmation email. Sandbox credentials do not soften that: the asset being stolen is the appointment.

1. **`vnp_SecureHash`** — rebuilt through `PaymentService.buildHashData`, the *same* method used on the outbound side. Keep it shared: a single divergence in encoding rules rejects every genuine transaction, and the usual "fix" is to delete the check.
2. **`vnp_TmnCode`** matches our merchant.
3. **Lookup by `vnp_TxnRef`**, a new nullable column on `Booking`. The old code parsed the id out of `vnp_OrderInfo` by stripping the literal prefix `"Thanh toan lich kham #"` — a free-text string we generated and then re-parsed, failing silently if the gateway altered it at all. `vnp_OrderInfo` is still sent, for the merchant portal only.
4. **Ownership** — the booking must belong to the logged-in user.
5. **Amount** — `bookingPrice × 100`, compared with `compareTo` (`equals` also compares scale).
6. **Idempotency** — only acts while `paymentStatus == "UNPAID"`, so refreshing the result page cannot send a second email.

`vnp_CreateDate` / `vnp_ExpireDate` use `Asia/Ho_Chi_Minh`. They previously used `TimeZone.getTimeZone("Etc/GMT+7")`, which under POSIX sign inversion is **UTC−7** — 14 hours off. The sandbox ignores it; a production gateway would expire every transaction on creation.

## 3. Concurrency
`BookingServiceImpl.reserve()` prevents double-booking with a per-slot `ReentrantLock` keyed by `doctorId|date|time`, held until the surrounding transaction completes via `TransactionSynchronization`. **Do not bypass `reserve()`** when creating bookings from user input — `save()` performs no availability check.

### The lock is a latency optimisation; the DB constraint is the guarantee
The `ReentrantLock` is **in-process only**, and that is not theoretical: the dev database contains bookings **#17 and #18** — two different patients, same doctor, same slot, both `CONFIRMED`+`PAID`, created 0.6 seconds apart. The real guard is a unique index (`db/manual/001_prod_hardening.sql`).

**A plain `UNIQUE(doctor_id, appointment_date, appointment_time)` is wrong here**, even though it matches `MedicalRecord` / `Review` / `AiChatSession`. Cancelled rows are never deleted — every cancel path just flips `status`, and `BookingCleanupTask` manufactures a fresh `CANCELED` row on that key every 3 minutes for each abandoned payment. The first legitimate re-booking of a previously cancelled slot would be rejected. Instead a **stored generated column `slot_uk` is NULL when `status = 'CANCELED'`** and `CONCAT(doctor_id,'|',date,'|',time)` otherwise; MySQL permits many NULLs in a unique index. Never map that column into `Booking` — see [environment-setup.md](environment-setup.md).

**`@Transactional` sits on the three service methods, not on the controllers**, and moving it back would reintroduce a 500. Per the JPA spec a `PersistenceException` marks the transaction rollback-only, so with the boundary on the controller the flow was: constraint fires → controller catches → returns a friendly redirect → the transaction proxy then tries to commit a doomed transaction and throws `UnexpectedRollbackException`. The patient got `error/500.html` instead of the Vietnamese sentence. `BookingController.processAppointment`, `ReceptionistWalkInController.createWalkIn` and `UserBookingEditController.processEdit` therefore carry no `@Transactional`; `reserve`, `reassign` and `rescheduleByUser` do. The wallet flow keeps its integrity because `WalletServiceImpl.payWithWallet` is independently `@Transactional` and the failure branch already compensates.

`saveGuardingSlot(booking, message)` is the single place that translates `DataIntegrityViolationException` into the **existing** Vietnamese sentence for each site. It uses `saveAndFlush`, not `save`: for a new booking `GenerationType.IDENTITY` forces an immediate INSERT, but `reassign` / `rescheduleByUser` are UPDATEs that Hibernate defers to flush — i.e. to commit, outside the try block and outside the lock.

**There is deliberately no `whyCannotReserve()`.** The `whyCannot…()` convention is for stable properties of a row you are rendering a button for; "somebody took this slot 40 ms ago" is a race, not a state. The UI already greys out taken slots via `booked-slots`, and the `existsBy…` pre-check remains the friendly path.

**The past-date floor lives in `reserve()`, and it stops at the DAY on purpose.** It is the single funnel for every creation path (patient self-booking, receptionist walk-in, AI handoff), so one check covers all of them. It does *not* reject a slot that has merely started, because the walk-in desk registers a patient standing at the counter — 13:30 must still be bookable at 13:45. The stricter rule ("the slot must still be in the future") belongs to patient self-booking and sits in `BookingController.processAppointment`, next to the working-hours check. Both exist because the browser's `min` attribute and greyed-out slots are computed **at page load**: a tab opened in the morning still POSTs this morning's 08:00 slot at 16:00, and a tab left open overnight POSTs yesterday's date.

`reassign(bookingId, newDoctorId)` (moving a booking to another doctor) goes through the **same `slotLocks` map** and additionally rejects slots covered by the target doctor's `DoctorBlockTime`. Any new operation that claims a slot must reuse this lock rather than rolling its own.

Note this lock is in-process only; it does not protect against double-booking across multiple app instances.

`rescheduleByUser(bookingId, userId, RescheduleRequestDTO)` (the patient editing their own booking at `/user/booking/edit/{id}`) also reuses `slotLocks`. It skips the lock entirely when doctor+date+time are unchanged — otherwise the booking's own row would be found by `existsBy...` and reported as a conflict with itself.

## 4. Patient self-service edit rules

`whyCannotCancel(booking)` and `whyCannotReschedule(booking)` on `BookingService` are the **single source of truth** for what a patient may still do — each returns `null` when allowed, otherwise the Vietnamese reason shown to the patient. `whyCannotReschedule` delegates to `whyCannotCancel` and adds the reschedule quota on top, so the two can never drift apart.

`ProfileController` calls both to disable the buttons *and* print the reason under the card, and calls `whyCannotCancel` again in `/user/cancel-booking/{id}`; `UserBookingEditController` calls `whyCannotReschedule` on both GET and POST. The UI can therefore never disagree with the server.

`rescheduleByUser` additionally re-checks `isSlotWithinWorkingHours` inside the slot lock, mirroring `BookingController.processAppointment` — the edit page greys out off-duty slots via `booked-slots`, but a crafted POST used to slip straight through into a session the doctor does not work.

The patient's booking list on `/user/profile` is split by `ProfileController` into `upcomingBookings` (still PENDING/CONFIRMED and not yet started, nearest first) and `pastBookings` (everything else, newest first) — `findByUser` itself has no `ORDER BY`, so never render it unsorted.

Constants live on `BookingService`: `MIN_HOURS_BEFORE_CHANGE = 24` and `MAX_RESCHEDULE_TIMES = 2`.

- Changing doctor is restricted to the **same department**, matching `reassign()`.
- `bookingPrice` and `paymentStatus` are deliberately **never touched** — moving to a pricier doctor costs the patient nothing.
- `rescheduleCount` / `lastRescheduledAt` on `Booking` only advance when the slot actually moves; editing notes or the patient's name does not consume a change.
- Moving a booking clears `queueOrder` / `lateMarkedAt`, since the old queue position belongs to the old slot.
- The new slot only has to be in the future — the 24-hour rule gates the *current* appointment, mirroring cancel, since booking a brand-new slot for tomorrow has never been restricted.

## 4b. What staff may still do — `whyStaffCannotChange`
`BookingService.whyStaffCannotChange(booking)` is the doctor/receptionist counterpart of `whyCannotCancel`: `null` = still allowed, otherwise the Vietnamese reason. It rejects `CANCELED`, `COMPLETED`, and any booking whose `appointmentStart` is already in the past — and deliberately **does not** apply `MIN_HOURS_BEFORE_CHANGE`, because a doctor must still be able to cancel a same-day booking ("hủy lịch đột xuất").

It compares the **full `LocalDateTime`**, not just the date, so an 08:00 slot is closed by the afternoon of the same day.

`DoctorDashboardController.confirmBooking` / `cancelBooking` call it before touching anything, and `DoctorBookingManagerController` / `DoctorBookingRequestController` pass an `actionBlockReasons` map (id → reason) into `booking-manager.html` / `booking-requests.html`, which grey out the buttons and print the reason. Same shape as `ProfileController`'s `cancelBlockReasons` for patients, so UI and server can never disagree. Before this, a doctor could "cancel" a `COMPLETED` visit and the patient's wallet was refunded for an exam that actually happened, or "confirm" a booking from last year and an email went out.

`DoctorMedicalRecordController.showCreateForm` also checks `appointmentDate == today` server-side; that rule previously lived **only** in the template, so a hand-typed URL opened the exam form for any day.

**The guard is deliberately NOT inside `cancelWithRefund`.** The receptionist bulk-cancel tool exists exactly for "doctor called in sick mid-day", where some of the day's slots have already passed — a guard there would break that flow.

`whyStaffCannotChange` is therefore applied **per screen**, and every staff screen that acts on a single booking now calls it: `DoctorDashboardController`, `DoctorBookingManagerController`, `DoctorBookingRequestController`, **`AdminBookingController.confirmBooking`/`cancelBooking`**, and **`ReceptionistBookingController.confirm`/`cancel`**. The admin and receptionist paths had no guard at all — admin could "confirm" a booking from last year (email and bell notification included) and cancel a `COMPLETED` visit, refunding the patient for an exam that really happened. Receptionist `confirm` only checked `status == PENDING`, which a stale PENDING row from last week passes. Both list templates receive an `actionBlockReasons` map (id → reason) and render the reason instead of the buttons, so the UI never offers a click that is guaranteed to fail. The reason is printed **only for PENDING/CONFIRMED rows** — for CANCELED/COMPLETED the status column already says it.

`AdminBookingController.deleteBooking` also re-checks the "only CANCELED or PENDING" rule that previously lived **only in the template**: a hand-typed URL permanently deleted a `COMPLETED` visit along with its medical record, unrecoverably.

**Bulk cancel/transfer is blocked for past DATES, not past times.** `ReceptionistScheduleChangeController.whyCannotChangeDate` rejects `date < today` on both `cancel-bulk` and `transfer-bulk`, and the template hides the whole action area (reason box, block-doctor checkbox, both submit buttons) while keeping the patient table visible for lookup. Same-day remains fully functional including slots that have already passed — that is the flow the tool exists for.

**The waiting queue is today-or-later only.** `ReceptionService.whyCannotReorderQueue(booking)` is the single source of truth (`null` = still allowed), used by both `pushToEndOfQueue`/`resetQueuePosition` and `receptionist/queue.html`. Browsing to a past date now shows a "chỉ xem lại được" banner and no action buttons. `resetQueuePosition` previously had **no checks whatsoever** — it could clear the "đến trễ" mark off a visit from last year.

## 4c. Every booking event reaches the patient twice
Each patient-facing `emailService.*` call is now paired with `NotificationService.pushBookingEvent(booking, icon, title)` **on the adjacent line** — email is `@Async` and fails into `System.err`, so it alone was never proof the patient was told. The pairs are: booking confirmed (wallet in `BookingController`, VNPay in `PaymentController`, VietQR in `VietQRController`, walk-in in `ReceptionistWalkInController`), staff confirmation (`DoctorDashboardController`, `AdminBookingController`, `ReceptionistBookingController`), `rescheduleByUser` and `cancelWithRefund` in `BookingServiceImpl`, self-cancel in `ProfileController`, and doctor transfer in `ReceptionServiceImpl`.

**`ProfileController.cancelBooking` needs its own push** because it hand-rolls the refund instead of going through `cancelWithRefund` — the one cancel path the shared method does not cover. See [supporting-subsystems.md](supporting-subsystems.md) for the bell that renders these.

## 5. Shared cancel logic
`BookingServiceImpl.cancelWithRefund(bookingId, reason)` is the single place that cancels a booking: sets `CANCELED`, refunds `bookingPrice` to the wallet when `paymentStatus == "PAID"` (marking it `REFUNDED`, otherwise `FAILED`), and sends the cancellation email. `AdminBookingController`, the receptionist bulk-cancel flow **and `DoctorDashboardController.cancelBooking`** all call it — put any change to cancellation behaviour here, not in a controller. The doctor path used to hand-roll its own copy of the refund, which is how it ended up with none of the guards.

The `reason` is appended to the wallet ledger description, so the ledger says *who* cancelled and not merely that money moved.

## Data model notes
- `BookingStatus`: `PENDING → CONFIRMED → COMPLETED`, or `CANCELED`.
- `paymentStatus` is a **separate string field** (`UNPAID` / `PAID` / `FAILED`), not an enum.
- A booking references a `Doctor` (not a `Service`) and stores a **price snapshot** in `bookingPrice`.
- Booking-on-behalf is supported via `patientName` / `patientPhone`, which default to the logged-in user's details when blank.
- `queueOrder` / `lateMarkedAt` drive the **waiting queue** the receptionist controls. `null` queueOrder = on time (sorted by `appointmentTime`); a value means the patient was pushed to the back. Sorting lives in `ReceptionServiceImpl.sortByQueue()` and is shared by `/receptionist/queue` and `DoctorExaminationController` — do not re-sort by `appointmentTime` alone in either place.
