# Booking Flow (core domain)

The booking pipeline is the heart of the app and spans several classes.

## 1. Time slots
Availability is computed against a **fixed 30-minute slot grid covering office hours only** — 16 slots: 07:30–11:30 and 13:30–17:30. The canonical slot list is **duplicated three times** — [BookingApi.java](src/main/java/com/bookinghealthy/controller/api/BookingApi.java) (`ALL_SLOTS`), [AiController.java](src/main/java/com/bookinghealthy/controller/api/AiController.java) (`ALL_SLOTS`, used by both the doctor-list and slot-alternatives endpoints), and `TimeSlotService` — **keep all three in sync** if you change operating hours.

**There is deliberately no evening slot.** Outside office hours the hospital runs an on-call rota (`StaffShift`, Thông tư 32/2023/TT-BYT), which never opens bookable slots — see [supporting-subsystems.md](supporting-subsystems.md). The grid used to run to 20:30, which contradicted the public `/working-hours` page and blurred ca khám into phiên trực. The office-hours boundary itself lives in `LeavePolicy.OFFICE_START` / `OFFICE_END`.

Slot buttons are also hardcoded in `user/appointment.html`, `user/booking-edit.html`, `receptionist/walk-in-form.html` and the `allTimeSlots` array in `user/doctors.html` — those four must match the Java lists too.

`GET /api/bookings/booked-slots?doctorId=&date=` returns unavailable slots = existing bookings (any status except `CANCELED`), **plus** doctor-blocked ranges (`DoctorBlockTime`, interval-overlap), **plus** office-hours slots that fall **outside the doctor's working `Schedule`** for that weekday (`BookingService.slotsOutsideWorkingHours`). So a doctor who registered only mornings shows the whole afternoon as unavailable. **Fallback:** a doctor with *no* `Schedule` rows at all is left unrestricted (keeps the old behaviour for seed doctors that never registered).

**`Schedule` is per-week, not a global weekly template.** Each row carries a `weekStart` (Monday), and doctors register only for next week — so the same weekday can have different hours in different weeks. Every read of a doctor's working hours must go through `ScheduleRepository.findEffective(doctorId, date)` (or `findEffectiveOn(date)` for all doctors on one day), never `findByDoctorId`, which mixes every week together. See [supporting-subsystems.md](supporting-subsystems.md) for the resolution order and the registration lock.

`BookingController.processAppointment` enforces the same rule server-side via `BookingService.isSlotWithinWorkingHours` before reserving — a crafted POST for an off-hours slot is rejected, not just hidden.

Because approved leave auto-generates `DoctorBlockTime` rows, a doctor on leave disappears from availability everywhere — booking pages, `booked-slots`, and the AI slot-alternatives endpoint — with no extra code in any of them.

The public doctors list (`/doctors`) never hides a doctor whose slots are full for the viewed day — it shows "Hết lịch trống" and keeps the card, so a doctor doesn't flicker in and out of the list as the day advances.

## 2. Payment branching
`BookingController.processAppointment` (`POST /appointment`) branches on `paymentMethod`:

- **WALLET** — reserve the slot, then `WalletService.payWithWallet` debits `User.balance`. On success → `CONFIRMED`/`PAID` plus a confirmation email; on insufficient funds → `CANCELED`/`FAILED`.
- **BANK_TRANSFER** — reserve, then redirect to `/checkout-qr?id=` (VietQR page).
- **VNPAY** (default) — reserve, then build a VNPay sandbox URL via `PaymentService`. The gateway calls back to `/payment-return`, handled by [PaymentController](src/main/java/com/bookinghealthy/controller/user/PaymentController.java), which **parses the booking id out of the `vnp_OrderInfo` string** (`"Thanh toan lich kham #<id>"`) and marks PAID+CONFIRMED or FAILED+CANCELED. That string format is load-bearing — changing it on the outbound side breaks the callback.

## 3. Concurrency
`BookingServiceImpl.reserve()` prevents double-booking with a per-slot `ReentrantLock` keyed by `doctorId|date|time`, held until the surrounding transaction completes via `TransactionSynchronization`. **Do not bypass `reserve()`** when creating bookings from user input — `save()` performs no availability check.

`reassign(bookingId, newDoctorId)` (moving a booking to another doctor) goes through the **same `slotLocks` map** and additionally rejects slots covered by the target doctor's `DoctorBlockTime`. Any new operation that claims a slot must reuse this lock rather than rolling its own.

Note this lock is in-process only; it does not protect against double-booking across multiple app instances.

`rescheduleByUser(bookingId, userId, RescheduleRequestDTO)` (the patient editing their own booking at `/user/booking/edit/{id}`) also reuses `slotLocks`. It skips the lock entirely when doctor+date+time are unchanged — otherwise the booking's own row would be found by `existsBy...` and reported as a conflict with itself.

## 4. Patient self-service edit rules

`whyCannotCancel(booking)` and `whyCannotReschedule(booking)` on `BookingService` are the **single source of truth** for what a patient may still do — each returns `null` when allowed, otherwise the Vietnamese reason shown to the patient. `whyCannotReschedule` delegates to `whyCannotCancel` and adds the reschedule quota on top, so the two can never drift apart.

`ProfileController` calls both to disable the buttons *and* print the reason under the card, and calls `whyCannotCancel` again in `/user/cancel-booking/{id}`; `UserBookingEditController` calls `whyCannotReschedule` on both GET and POST. The UI can therefore never disagree with the server.

The patient's booking list on `/user/profile` is split by `ProfileController` into `upcomingBookings` (still PENDING/CONFIRMED and not yet started, nearest first) and `pastBookings` (everything else, newest first) — `findByUser` itself has no `ORDER BY`, so never render it unsorted.

Constants live on `BookingService`: `MIN_HOURS_BEFORE_CHANGE = 24` and `MAX_RESCHEDULE_TIMES = 2`.

- Changing doctor is restricted to the **same department**, matching `reassign()`.
- `bookingPrice` and `paymentStatus` are deliberately **never touched** — moving to a pricier doctor costs the patient nothing.
- `rescheduleCount` / `lastRescheduledAt` on `Booking` only advance when the slot actually moves; editing notes or the patient's name does not consume a change.
- Moving a booking clears `queueOrder` / `lateMarkedAt`, since the old queue position belongs to the old slot.
- The new slot only has to be in the future — the 24-hour rule gates the *current* appointment, mirroring cancel, since booking a brand-new slot for tomorrow has never been restricted.

## 5. Shared cancel logic
`BookingServiceImpl.cancelWithRefund(bookingId, reason)` is the single place that cancels a booking: sets `CANCELED`, refunds `bookingPrice` to the wallet when `paymentStatus == "PAID"` (marking it `REFUNDED`, otherwise `FAILED`), and sends the cancellation email. `AdminBookingController` and the receptionist bulk-cancel flow both call it — put any change to cancellation behaviour here, not in a controller.

## Data model notes
- `BookingStatus`: `PENDING → CONFIRMED → COMPLETED`, or `CANCELED`.
- `paymentStatus` is a **separate string field** (`UNPAID` / `PAID` / `FAILED`), not an enum.
- A booking references a `Doctor` (not a `Service`) and stores a **price snapshot** in `bookingPrice`.
- Booking-on-behalf is supported via `patientName` / `patientPhone`, which default to the logged-in user's details when blank.
- `queueOrder` / `lateMarkedAt` drive the **waiting queue** the receptionist controls. `null` queueOrder = on time (sorted by `appointmentTime`); a value means the patient was pushed to the back. Sorting lives in `ReceptionServiceImpl.sortByQueue()` and is shared by `/receptionist/queue` and `DoctorExaminationController` — do not re-sort by `appointmentTime` alone in either place.
