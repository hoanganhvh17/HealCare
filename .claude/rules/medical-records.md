# Medical Records

Post-visit clinical data is a connected entity cluster centered on `MedicalRecord` (diagnosis, symptoms, doctor notes, `MedicalRecordStatus`), linked one-to-one with a `Booking`:

- `PrescriptionItem` — prescribed medications
- `VitalSign` — measurements taken at the visit
- `Allergy` — patient allergy list
- `MedicalAddendum` — post-hoc additions to a finalized record
- `MedicalAttachment` — uploaded files (see the uploads path in the environment rules)

## Access paths
- **Doctors** author and edit records through `controller/doctor` (`DoctorMedicalRecordController`, `DoctorExaminationController`) with templates under `templates/doctor/`.
- **Patients** view their own records via `UserMedicalRecordController`.
- **Admins** have a read/manage view via `AdminMedicalRecordController`.

Records are also read by the AI layer — the patient's latest completed record is injected into triage chat context, so changes to `MedicalRecord` field names should be checked against `AiService`. A patient can also ask AI to explain **any one of their own records** in plain language via `POST /api/chat/medical-record/{bookingId}/explain` (a small Q&A box on `medical-record-detail.html`, not the floating triage widget) — see [ai-assistant.md](ai-assistant.md).

## AI assists on the exam form
`doctor/medical-record-form.html` carries four AI buttons backed by `DoctorExamAiController` — allergy/interaction check, draft `doctorNotes`, ICD-10 suggestion, and a summary of the patient's prior records. They only ever *suggest*: nothing writes to `MedicalRecord`, the doctor still presses "Lưu Bệnh Án". Full rules in [ai-assistant.md](ai-assistant.md).

The draft-notes prompt is coupled to `FollowUpReminderTask`: it is required to end with the literal `"Tái khám sau N ngày/tuần/tháng"` because that is what the task's regex extracts. **Change the phrasing in either place and the follow-up reminder stops firing without any error.**

`MedicalRecord.followUpReminderSent` (boolean, default `false`) is **not** clinical data — it's the idempotency flag for `FollowUpReminderTask`, which scans `doctorNotes` for a follow-up instruction ("tái khám sau N ngày/tuần/tháng") and nags the patient by email + in-app notification once the computed date is close. See [ai-assistant.md](ai-assistant.md) for the regex scope and [supporting-subsystems.md](supporting-subsystems.md) for the task pattern. `MedicalRecord` uses `@AllArgsConstructor` but — unlike `User`/`Doctor`/`Department`/`Schedule` — is never constructed positionally by `DataInitializer`, so adding fields to it is safe.

`Booking.reminderSent` is the same shape of flag for `AppointmentReminderTask` (nhắc lịch khám ngày mai) — also not clinical data. `Booking` is safe to extend for the same reason: it declares `@AllArgsConstructor` but every construction in the codebase is `new Booking()` plus setters.
