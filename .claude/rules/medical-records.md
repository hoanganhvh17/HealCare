# Medical Records

Post-visit clinical data is a connected entity cluster centered on `MedicalRecord` (diagnosis, symptoms, doctor notes, `MedicalRecordStatus`), linked one-to-one with a `Booking`:

- `PrescriptionItem` — prescribed medications
- `VitalSign` — measurements taken at the visit
- `Allergy` — patient allergy list (see below: written from **two** sides)
- `MedicalAddendum` — post-hoc additions to a finalized record. **`DoctorMedicalRecordController.addMedicalAddendum` gates on ownership**: the booking must belong to the logged-in doctor, and the submitted `recordId` must be the record of that `bookingId`. It previously took `recordId` straight from the form with no check at all, so changing one number in the request let any doctor write into any patient's closed record — the one write path in the app that reaches finalized clinical data. Empty notes are rejected too. **Cả ba đường của bác sĩ nay đều gác quyền** (2026-08-25): `viewRecordForDoctor` từng bị comment mất phép kiểm nên bất kỳ tài khoản `ROLE_DOCTOR` nào duyệt id là đọc được bệnh án cả bệnh viện; `saveAdvancedMedicalRecord` nhận `Authentication` rồi **không dùng**, tức ghi được bệnh án + đơn thuốc vào ca của đồng nghiệp rồi hệ thống gửi email kèm PDF tới bệnh nhân của họ; và `viewPatientHistoryRecords` chỉ kiểm "có phải bác sĩ không" nên trả về trọn bệnh sử + hồ sơ ngoại viện của bệnh nhân bất kỳ, đi vòng qua đúng `whyCannotView` mà `ExternalMedicalRecordService` đã dựng. Đường thứ ba nay dùng `BookingRepository.existsByDoctorIdAndUserId` — cùng nguyên tắc need-to-know.
- `MedicalAttachment` — uploaded files (see the uploads path in the environment rules)

## Access paths
- **Doctors** author and edit records through `controller/doctor` (`DoctorMedicalRecordController`, `DoctorExaminationController`) with templates under `templates/doctor/`.
- **Patients** view their own records via `UserMedicalRecordController`.
- **Admins** have a read/manage view via `AdminMedicalRecordController`.

Records are also read by the AI layer — the patient's latest completed record is injected into triage chat context, so changes to `MedicalRecord` field names should be checked against `AiService`. A patient can also ask AI to explain **any one of their own records** in plain language via `POST /api/chat/medical-record/{bookingId}/explain` (a small Q&A box on `medical-record-detail.html`, not the floating triage widget) — see [ai-assistant.md](ai-assistant.md).

## Allergies are written from two sides
`AllergyService` (interface + impl) owns every write to `allergies`. Two entry points, and the `source` column records which one:

- **Patient** — the "Hồ sơ y tế" tab on `/user/profile`, via `UserAllergyController` (`POST /user/allergy/add`, `/delete/{id}`). That tab used to be a placeholder sentence.
- **Doctor** — a modal on the exam form, via `DoctorMedicalRecordController.addAllergyDuringExam` (`POST /doctor/medical-record/allergy/add`), gated by the same booking-ownership check as `showCreateForm`.

Rules that must survive any edit:

- **The doctor's path returns JSON and the browser calls it with `fetch`.** The exam form is one giant `<form>`; a normal POST reloads the page and destroys the symptoms, diagnosis, prescription rows and notes the doctor is part-way through typing.
- **`whyCannotDelete(allergy, user)` is the single source of truth for deletion** — `null` = allowed, otherwise the Vietnamese reason. The template hides the button with it and the controller re-checks it, so a hand-made POST is refused with the same sentence the UI shows. Same shape as `BookingService.whyCannotCancel`. A patient may delete only their own `SOURCE_PATIENT` rows: a doctor-recorded allergy is clinical data.
- **`source` is a plain `String`** (`Allergy.SOURCE_PATIENT` / `SOURCE_DOCTOR`), never `@Enumerated` — see the MySQL `ENUM` trap in [environment-setup.md](environment-setup.md) — and is deliberately **nullable** so `ddl-auto=update` cannot add a `NOT NULL` column with no DEFAULT.
- **`existsByUserIdAndAllergenIgnoreCase` blocks duplicates.** Two rows reading "Penicillin" and "penicillin" look like two separate allergies to a doctor skimming the red alert card.
- `MedicalRecordService.addPatientAllergy` was **removed**: it had never been called, skipped the duplicate check and set no `source`, so leaving it beside the new path meant two ways into one table with only one of them applying the rules.

Before this existed there was **no write path at all** — the red "CẢNH BÁO DỊ ỨNG" card and the AI prescription cross-check both ran against a permanently empty table.

## Khám xong là bệnh nhân nhận được hồ sơ ngay
`MedicalRecordDeliveryService.deliver(bookingId)` gửi bệnh nhân **email hồ sơ bệnh án + đơn thuốc điện tử** (kèm PDF đơn thuốc) **và** một `Notification` vào chuông, cùng một lượt. `DoctorMedicalRecordController.saveAdvancedMedicalRecord` là chỗ gọi duy nhất. Trước đó bệnh án lưu xong là nằm im trong DB: bệnh nhân chỉ biết nếu tự vào `/user/profile` bấm xem.

Bốn điều phải sống sót qua mọi lần sửa:

- **Gọi SAU KHI `createAdvancedMedicalRecord` trả về**, tức sau commit. Đặt vào trong service kia (`@Transactional`) là gửi thư cho một bệnh án còn có thể rollback — thư đã đi thì không rút lại được.
- **Nằm NGOÀI khối `try/catch` của lời gọi lưu bệnh án** trong controller. Rơi vào nhánh catch đó là bác sĩ bị đá ngược về form khám, và lần bấm Lưu tiếp theo chắc chắn báo *"Lịch hẹn này đã có hồ sơ bệnh án!"* — ca khám kẹt cứng vì một lỗi gửi thư. Bản thân `deliver` cũng tự nuốt lỗi vào log, đây là lớp chặn thứ hai.
- **`deliver` cố ý KHÔNG `@Async` và KHÔNG `@Transactional`.** Nó phải chạy trên luồng phục vụ request, nơi open-in-view còn mở session — cả `buildPrescription` lẫn `booking.getDoctor().getUser()` đều cần nạp quan hệ LAZY. Phần chậm thật (SMTP) đã `@Async` sẵn bên `EmailServiceImpl`.
- **Thư nhận `MedicalRecordMailDTO` (chuỗi thuần), không nhận entity.** `EmailServiceImpl` gửi ở luồng khác; `Booking.user` / `Booking.doctor` / `Doctor.user` đều LAZY nên proxy chưa nạp sẽ ném `LazyInitializationException` **rơi đúng vào khối catch nuốt lỗi** của nó — thư im lặng không tới, log trông y hệt lỗi SMTP. Xem [supporting-subsystems.md](supporting-subsystems.md).

Chuông dùng `NotificationService.push` chứ **không** `pushBookingEvent`, vì link phải trỏ thẳng `/user/medical-record/view/{bookingId}`; `pushBookingEvent` luôn gắn link về mục lịch sử đặt lịch, bắt bệnh nhân tự dò lại đúng ca vừa khám.

PDF đơn thuốc dựng qua `PdfExportService.buildPrescription`, bọc try/catch **riêng**: hàm đó ném khi thiếu font Unicode trong `resources/fonts` (xem [environment-setup.md](environment-setup.md)). Thiếu tệp đính kèm thì chấp nhận được vì toàn bộ đơn thuốc đã nằm trong thân thư — mất luôn cả thư thì không. Giữ nguyên khối try/catch đó kể cả khi font đã có: nó là thứ giữ cho một sự cố in ấn không nuốt mất lá thư.

## Hồ sơ bệnh án bệnh nhân mang từ NƠI KHÁC tới

`ExternalMedicalRecord` (bảng `external_medical_records`) giữ giấy tờ bệnh nhân đã khám ở bệnh viện
khác / tuyến dưới rồi tự tải lên. Ba nơi tiêu thụ: khối tiêm ngữ cảnh của chatbot, thẻ tra cứu
`my_documents` trong khung chat, và thẻ cho bác sĩ trên form khám + trang hồ sơ bệnh nhân.

**Tuyệt đối không dùng lại `MedicalAttachment` cho việc này.** Entity đó khai
`@JoinColumn(name = "medical_record_id", nullable = false)`, tức bắt buộc phải có một `MedicalRecord` —
thứ chỉ tồn tại SAU khi bác sĩ của viện này khám xong. Hồ sơ cũ tồn tại TRƯỚC mọi lịch hẹn, nên nó
khoá thẳng vào `User`. (`MedicalAttachment` vẫn là mã chết: chỗ ghi duy nhất được gọi với `null`.)

**ẢNH TRIỆU CHỨNG KHÔNG PHẢI HỒ SƠ BỆNH ÁN và KHÔNG BAO GIỜ được lưu.** Khách gửi ảnh chụp mắt
sưng / nốt ban vào khung chat thì máy chủ phân loại ra `SYMPTOM`, tư vấn chuyên khoa rồi **vứt bytes
đi** — không tệp trên đĩa, không dòng trong `external_medical_records`. Đây là lựa chọn về quyền
riêng tư: ảnh một phần cơ thể nhạy cảm hơn giấy tờ nhiều, và bảng này thì **bác sĩ có lịch hẹn đọc
được**. Xem [ai-assistant.md](ai-assistant.md).

Trước khi có phân loại, mọi ảnh đều đi qua prompt "đọc hồ sơ bệnh án": ảnh mắt sưng → model trả
*"đây không phải giấy tờ y tế"* → `applyAiResult` vẫn lưu `aiStatus = DONE` → dòng đó được tiêm vào
system prompt **mọi lượt chat** dưới tiêu đề "HỒ SƠ BỆNH ÁN TỪ NƠI KHÁC" và hiện trên form khám của
bác sĩ. `applyAnalysis` nay từ chối đặt `DONE` cho bất cứ thứ gì không phải `DOCUMENT`.

**HAI đường vào, một đường xử lý.** Bệnh nhân tải lên từ tab "Hồ sơ y tế" ở `/user/profile`, **hoặc**
đính kèm thẳng trong khung chat AI (nút kẹp giấy). Cả hai đều gọi cùng `upload` + `analyze`, lưu cùng
chỗ, chịu cùng `whyCannotView` — khác mỗi hình thức trả về: trang hồ sơ redirect kèm flash message,
khung chat trả JSON (`POST /chat-upload`) vì một lần tải lại trang sẽ xoá sạch đoạn hội thoại đang dở.
Xem [ai-assistant.md](ai-assistant.md).

Luồng: `UserMedicalDocumentController` (`/user/medical-document/**`, khuôn `UserAllergyController`)
→ `ExternalMedicalRecordService.upload` lưu tệp qua `FileStorageService.storeMedicalDocument` →
`analyze(id)` đọc nội dung rồi gọi AI → lưu `aiSummary` + `aiDepartmentId` + `aiStatus`.

**Hai đường đọc nội dung, và cái thứ hai là chỗ dễ nói dối nhất:**
- **Ảnh** → `DocumentTextExtractor.toImageDataUrl` (thu nhỏ 1600px, JPEG, base64) →
  `AiService.analyzeDocumentImage`. **Thu nhỏ là bắt buộc**: base64 phình ~33%, một ảnh 10MB thành
  request ~13MB và đốt token vô ích.
- **PDF** → `extractPdfText` (PDFBox) → `AiService.getStatelessResponse`. **PDF bản scan trả chuỗi
  RỖNG** (không có lớp chữ), và ngưỡng là `MIN_MEANINGFUL_CHARS = 60` chứ không phải `isBlank()` —
  bản scan hay còn vài ký tự watermark, đủ qua phép thử rỗng nhưng không đủ để tóm tắt gì. Rỗng thì
  ghi `UNREADABLE` và mời bệnh nhân chụp ảnh từng trang; **đưa chuỗi rỗng cho model là in ra một bản
  "tóm tắt" bịa đặt dưới tên hồ sơ bệnh án**.

**Bốn trạng thái `aiStatus`, không trạng thái nào im lặng**: `PENDING` (chưa chạy, hoặc
`medical-doc.ai-enabled=false`), `DONE`, `UNREADABLE` (PDF scan), `FAILED` (model hỏng hoặc JSON
không đọc được). Cả bốn đều có câu tiếng Việt riêng trên màn hình bệnh nhân và màn hình bác sĩ —
riêng `PENDING` phải nói "chưa phân tích", **không** được gộp với `FAILED`: nói với bác sĩ rằng tệp
hỏng trong khi nó bình thường là làm bác sĩ thôi bấm vào xem bản gốc.

Luật phải sống sót qua mọi lần sửa:

- **`analyze()` KHÔNG `@Transactional`.** Có lời gọi mạng giữa hàm; một transaction ở đây giam một
  connection HikariCP (pool 10) suốt thời gian chờ. Cùng lý do khiến `MedicalRecordDeliveryService`
  được tách ra.
- **`upload()` cũng không `@Transactional`** vì nó ghi tệp ra đĩa — thứ rollback không thu hồi được.
  Ghi tệp trước, lưu dòng sau, và **xoá tệp nếu lưu hỏng**, nếu không mỗi lỗi để lại một tệp mồ côi.
- **`whyCannotView(record, viewer)` là nguồn sự thật duy nhất về quyền xem**: chủ hồ sơ, hoặc bác sĩ
  **đã có lịch hẹn** với bệnh nhân đó (`BookingRepository.existsByDoctorIdAndUserId`). Nó dò qua bảng
  `doctors` chứ **không đọc `User.roles`** — `roles` là `@ManyToMany` LAZY, một lần chạm ngoài session
  là `LazyInitializationException` ở đúng chỗ đang gác quyền.
- **Chỉ MỘT endpoint tải tệp** (`GET /user/medical-document/file/{id}`), dùng chung cho bệnh nhân và
  bác sĩ, dù đường dẫn mang tiền tố `/user`. Tách làm hai theo đối tượng nghe gọn hơn nhưng thành hai
  bản kiểm quyền có thể lệch nhau. Trả cùng mã 404 cho "không phải của bạn" và "không tồn tại".
- **Tệp nằm ở `app.private-dir/medical-docs`, KHÔNG phải `app.upload-dir`** — dữ liệu sức khoẻ, cùng
  lập luận đã dùng cho CV ứng viên. `privateRoot()` không được đăng ký với `ResourceHandler` nào.
- **`aiStatus` / `docType` là `String`, không `@Enumerated`** — bẫy cột `ENUM(...)` native của MySQL.
- **`aiDepartmentId` phải đối chiếu `DepartmentRepository.findById` trước khi ghi.** Model bịa id là
  chuyện đã xảy ra nhiều lần trong dự án này; id lạ thành một đường dẫn chết.
- **Bản tóm tắt AI KHÔNG phải chẩn đoán.** Câu miễn trừ bắt buộc có ở cả ba màn hình. Thẻ của bác sĩ
  còn phải tách hẳn khỏi timeline bệnh án nội viện (viền cảnh báo riêng) — trộn hai thứ vào một danh
  sách là mời bác sĩ tin một dòng chữ chưa ai kiểm chứng ngang với bệnh án đồng nghiệp đã ký.

Xem [ai-assistant.md](ai-assistant.md) cho nhánh chat và [supporting-subsystems.md](supporting-subsystems.md)
cho phần lưu trữ.

## AI assists on the exam form
`doctor/medical-record-form.html` carries four AI buttons backed by `DoctorExamAiController` — allergy/interaction check, draft `doctorNotes`, ICD-10 suggestion, and a summary of the patient's prior records. They only ever *suggest*: nothing writes to `MedicalRecord`, the doctor still presses "Lưu Bệnh Án". Full rules in [ai-assistant.md](ai-assistant.md).

The draft-notes prompt is coupled to `FollowUpReminderTask`: it is required to end with the literal `"Tái khám sau N ngày/tuần/tháng"` because that is what the task's regex extracts. **Change the phrasing in either place and the follow-up reminder stops firing without any error.**

`MedicalRecord.followUpReminderSent` (boolean, default `false`) is **not** clinical data — it's the idempotency flag for `FollowUpReminderTask`, which scans `doctorNotes` for a follow-up instruction ("tái khám sau N ngày/tuần/tháng") and nags the patient by email + in-app notification once the computed date is close. See [ai-assistant.md](ai-assistant.md) for the regex scope and [supporting-subsystems.md](supporting-subsystems.md) for the task pattern. `MedicalRecord` uses `@AllArgsConstructor` but — unlike `User`/`Doctor`/`Department`/`Schedule` — is never constructed positionally by `DataInitializer`, so adding fields to it is safe.

`Booking.reminderSent` is the same shape of flag for `AppointmentReminderTask` (nhắc lịch khám ngày mai) — also not clinical data. `Booking` is safe to extend for the same reason: it declares `@AllArgsConstructor` but every construction in the codebase is `new Booking()` plus setters.
