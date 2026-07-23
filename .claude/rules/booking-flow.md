# Booking Flow (core domain)

The booking pipeline is the heart of the app and spans several classes.

## 1. Time slots
Availability is computed against a **fixed 30-minute slot grid** running 07:30–20:30. The canonical slot list is **duplicated** in [BookingApi.java](src/main/java/com/bookinghealthy/controller/api/BookingApi.java) (`ALL_SLOTS`) and in `TimeSlotService` — **keep both in sync** if you change operating hours.

`GET /api/bookings/booked-slots?doctorId=&date=` returns unavailable slots = existing bookings (any status except `CANCELED`) **plus** doctor-blocked ranges (`DoctorBlockTime`, matched with interval-overlap logic).

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
