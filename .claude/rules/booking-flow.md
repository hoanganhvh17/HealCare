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

## 4. Shared cancel logic
`BookingServiceImpl.cancelWithRefund(bookingId, reason)` is the single place that cancels a booking: sets `CANCELED`, refunds `bookingPrice` to the wallet when `paymentStatus == "PAID"` (marking it `REFUNDED`, otherwise `FAILED`), and sends the cancellation email. `AdminBookingController` and the receptionist bulk-cancel flow both call it — put any change to cancellation behaviour here, not in a controller.

## Data model notes
- `BookingStatus`: `PENDING → CONFIRMED → COMPLETED`, or `CANCELED`.
- `paymentStatus` is a **separate string field** (`UNPAID` / `PAID` / `FAILED`), not an enum.
- A booking references a `Doctor` (not a `Service`) and stores a **price snapshot** in `bookingPrice`.
- Booking-on-behalf is supported via `patientName` / `patientPhone`, which default to the logged-in user's details when blank.
- `queueOrder` / `lateMarkedAt` drive the **waiting queue** the receptionist controls. `null` queueOrder = on time (sorted by `appointmentTime`); a value means the patient was pushed to the back. Sorting lives in `ReceptionServiceImpl.sortByQueue()` and is shared by `/receptionist/queue` and `DoctorExaminationController` — do not re-sort by `appointmentTime` alone in either place.
