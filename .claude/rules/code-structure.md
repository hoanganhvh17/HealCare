# Code Structure & Layering

Standard Spring MVC layering under `com.bookinghealthy`:

- **`controller/`** — split by audience: root-level plus `controller/user`, `controller/admin`, `controller/doctor`, `controller/receptionist`, `controller/head` (trưởng khoa), `controller/staff` (logic dùng chung cho bác sĩ + lễ tân), and `controller/api` (JSON). This split mirrors the URL and role structure, so place a new controller in the folder matching its audience.

  `controller/staff/StaffWorkScheduleController` is an **abstract superclass**, not a mapped controller: `DoctorWorkScheduleController` and `ReceptionistWorkScheduleController` extend it and only supply `basePath()` and `sidebarFragment()`. Use this shape when a screen is identical for two roles — duplicating the mappings instead would double every future fix.
- **`service/`** — interfaces live directly in `service/`; implementations in `service/impl/`. A few services (`AiService`, `ImageService`, `GlobalHelper`) are concrete classes. **When adding a service, follow the interface + `impl` pattern** used by `BookingService`, `WalletService`, `DoctorService`, `MedicalRecordService`.
- **`repository/`** — Spring Data JPA repositories.
- **`model/`** — JPA entities plus enums (`Role`, `BookingStatus`, `MedicalRecordStatus`, `TransactionType`, `AuthProvider`, `CandidateStatus`).
- **`dto/`** — request/response shapes, including `dto/ai/` for the OpenAI-compatible payloads.
- **`security/`**, **`config/`**, **`util/`**.

Entities use Lombok (`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor`) — note that `@AllArgsConstructor` is used positionally in `DataInitializer` (in both the main seed block and `ensureExtraDoctors`), so **adding a field to `User`, `Doctor`, `Department` or `Schedule` breaks that seeding code** and it must be updated in the same change.

That trap is why per-employee HR data (ngày vào làm, điều kiện lao động, trưởng khoa) lives in a separate `StaffProfile` entity keyed on `User` rather than as new columns on `User`/`Doctor`. The newer entities (`StaffProfile`, `StaffShift`, `LeaveRequest`, `ShiftCoverRequest`) deliberately **omit `@AllArgsConstructor`** so they can never acquire the same problem.

Bulk seed data is kept out of `DataInitializer`: `config/DoctorSeedData` holds only the `SeedDoctor` table (department → 5 doctors), while the logic that turns it into `User`/`Doctor`/`Schedule` rows stays in `DataInitializer`. It is called from `run()` rather than being its own `CommandLineRunner`, because a separate runner could execute *before* `DataInitializer` and create users, which would make `count() == 0` false and skip the entire main seed (no departments, no roles).
