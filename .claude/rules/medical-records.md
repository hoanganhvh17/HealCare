# Medical Records

Post-visit clinical data is a connected entity cluster centered on `MedicalRecord` (diagnosis, symptoms, doctor notes, `MedicalRecordStatus`), linked one-to-one with a `Booking`:

- `PrescriptionItem` — prescribed medications
- `VitalSign` — measurements taken at the visit
- `Allergy` — patient allergy list (see below: written from **two** sides)
- `MedicalAddendum` — post-hoc additions to a finalized record
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

## AI assists on the exam form
`doctor/medical-record-form.html` carries four AI buttons backed by `DoctorExamAiController` — allergy/interaction check, draft `doctorNotes`, ICD-10 suggestion, and a summary of the patient's prior records. They only ever *suggest*: nothing writes to `MedicalRecord`, the doctor still presses "Lưu Bệnh Án". Full rules in [ai-assistant.md](ai-assistant.md).

The draft-notes prompt is coupled to `FollowUpReminderTask`: it is required to end with the literal `"Tái khám sau N ngày/tuần/tháng"` because that is what the task's regex extracts. **Change the phrasing in either place and the follow-up reminder stops firing without any error.**

`MedicalRecord.followUpReminderSent` (boolean, default `false`) is **not** clinical data — it's the idempotency flag for `FollowUpReminderTask`, which scans `doctorNotes` for a follow-up instruction ("tái khám sau N ngày/tuần/tháng") and nags the patient by email + in-app notification once the computed date is close. See [ai-assistant.md](ai-assistant.md) for the regex scope and [supporting-subsystems.md](supporting-subsystems.md) for the task pattern. `MedicalRecord` uses `@AllArgsConstructor` but — unlike `User`/`Doctor`/`Department`/`Schedule` — is never constructed positionally by `DataInitializer`, so adding fields to it is safe.

`Booking.reminderSent` is the same shape of flag for `AppointmentReminderTask` (nhắc lịch khám ngày mai) — also not clinical data. `Booking` is safe to extend for the same reason: it declares `@AllArgsConstructor` but every construction in the codebase is `new Booking()` plus setters.
