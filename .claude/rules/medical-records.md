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

Records are also read by the AI layer — the patient's latest completed record is injected into triage chat context, so changes to `MedicalRecord` field names should be checked against `AiService`.
