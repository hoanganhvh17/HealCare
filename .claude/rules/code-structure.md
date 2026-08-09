# Code Structure & Layering

Standard Spring MVC layering under `com.bookinghealthy`:

- **`controller/`** — split by audience: root-level plus `controller/user`, `controller/admin`, `controller/doctor`, `controller/receptionist`, `controller/head` (trưởng khoa), `controller/staff` (logic dùng chung cho bác sĩ + lễ tân), and `controller/api` (JSON). This split mirrors the URL and role structure, so place a new controller in the folder matching its audience.

  `controller/head` holds two controllers on purpose: `HeadApprovalController` (reactive — duyệt đơn nghỉ, duyệt ca trực) and `HeadRosterController` (proactive — xếp ca khám cho khoa, phân công trực). Splitting them keeps either from growing into a 400-line class of unrelated screens.

  The doctor AI is split the same way: `DoctorAiController` (floating chat widget) and `DoctorExamAiController` (the four assists on the exam form). Both stay under `/api/doctor/chat/` so one `SecurityConfig` block-0 rule covers them — **a new doctor-facing AI endpoint belongs under that prefix**, otherwise it needs its own matcher.

  `controller/staff/StaffWorkScheduleController` is an **abstract superclass**, not a mapped controller: `DoctorWorkScheduleController` and `ReceptionistWorkScheduleController` extend it and only supply `basePath()` and `sidebarFragment()`. Use this shape when a screen is identical for two roles — duplicating the mappings instead would double every future fix.
  `controller/user/UserAllergyController` is split out of `ProfileController` for the same reason — that class already carries the profile, booking history and password screens.

  `controller/api/PatientChatLookupApiController` is split out of `AiController` on the same principle: the patient chat's **lookup** endpoints (`/api/chat/my-bookings`, `/doctor-profile`, `/doctors/filter`) answer questions with real DB rows, while `AiController` already carries the prompt plumbing, the soft-lock cache and every slot rule. It keeps the `/api/chat/` prefix so the existing matchers apply — but `/my-bookings` still needs its own block-0 rule, see [authentication-and-roles.md](authentication-and-roles.md).

- **`service/`** — interfaces live directly in `service/`; implementations in `service/impl/`. A few services (`AiService`, `ImageService`, `GlobalHelper`) are concrete classes. **When adding a service, follow the interface + `impl` pattern** used by `BookingService`, `WalletService`, `DoctorService`, `MedicalRecordService`, `AllergyService`, `MedicalRecordDeliveryService`.

  `MedicalRecordDeliveryService` shows why a *delivery* step gets its own service rather than living inside `MedicalRecordServiceImpl`: it must run **after** that class's `@Transactional` method has committed, and it must stay on the request thread to read lazy associations before handing plain strings to the `@Async` mail sender. See [medical-records.md](medical-records.md).
- **`repository/`** — Spring Data JPA repositories.
- **`model/`** — JPA entities plus enums (`Role`, `BookingStatus`, `MedicalRecordStatus`, `TransactionType`, `AuthProvider`, `CandidateStatus`).
- **`dto/`** — request/response shapes, including `dto/ai/` for the OpenAI-compatible payloads.
- **`security/`**, **`config/`**, **`util/`**. `util/VitalSignFormatter` renders one bộ chỉ số sinh tồn as a single Vietnamese line and is shared by `PdfExportServiceImpl` (đơn thuốc in ra giấy) and `MedicalRecordDeliveryServiceImpl` (thư gửi bệnh nhân) — it was extracted the moment the second consumer appeared, so the paper the patient holds and the mail in their inbox cannot print blood pressure two different ways.

Entities use Lombok (`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor`) — note that `@AllArgsConstructor` is used positionally in `DataInitializer` (in both the main seed block and `ensureExtraDoctors`), so **adding a field to `User`, `Doctor`, `Department` or `Schedule` breaks that seeding code** and it must be updated in the same change.

That trap is why per-employee HR data (ngày vào làm, điều kiện lao động, trưởng khoa) lives in a separate `StaffProfile` entity keyed on `User` rather than as new columns on `User`/`Doctor`. The newer entities (`StaffProfile`, `StaffShift`, `LeaveRequest`, `ShiftCoverRequest`, `Notification`) deliberately **omit `@AllArgsConstructor`** so they can never acquire the same problem.

`Notification` + `NotificationService` (interface + impl) carry in-app notifications; see [supporting-subsystems.md](supporting-subsystems.md) for why they exist alongside email and where `push` must be called.

`config/NewsSourceCatalog` follows the same data/logic split for a different feature: it holds only the allow-list of newspapers (and the outbreak keywords), while `NewsFeedServiceImpl` does the fetching and `MedicalNewsTask` turns the result into `Post` rows. See [supporting-subsystems.md](supporting-subsystems.md).

Bulk seed data is kept out of `DataInitializer`: `config/DoctorSeedData` holds only the `SeedDoctor` table (department → 5 doctors), while the logic that turns it into `User`/`Doctor`/`Schedule` rows stays in `DataInitializer`. It is called from `run()` rather than being its own `CommandLineRunner`, because a separate runner could execute *before* `DataInitializer` and create users, which would make `count() == 0` false and skip the entire main seed (no departments, no roles).
