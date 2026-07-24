# Code Structure & Layering

Standard Spring MVC layering under `com.bookinghealthy`:

- **`controller/`** — split by audience: root-level plus `controller/user`, `controller/admin`, `controller/doctor`, `controller/receptionist`, and `controller/api` (JSON). This split mirrors the URL and role structure, so place a new controller in the folder matching its audience.
- **`service/`** — interfaces live directly in `service/`; implementations in `service/impl/`. A few services (`AiService`, `ImageService`, `GlobalHelper`) are concrete classes. **When adding a service, follow the interface + `impl` pattern** used by `BookingService`, `WalletService`, `DoctorService`, `MedicalRecordService`.
- **`repository/`** — Spring Data JPA repositories.
- **`model/`** — JPA entities plus enums (`Role`, `BookingStatus`, `MedicalRecordStatus`, `TransactionType`, `AuthProvider`, `CandidateStatus`).
- **`dto/`** — request/response shapes, including `dto/ai/` for the OpenAI-compatible payloads.
- **`security/`**, **`config/`**, **`util/`**.

Entities use Lombok (`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor`) — note that `@AllArgsConstructor` is used positionally in `DataInitializer` (in both the main seed block and `ensureExtraDoctors`), so **adding a field to `User` or `Doctor` breaks that seeding code** and it must be updated in the same change.

Bulk seed data is kept out of `DataInitializer`: `config/DoctorSeedData` holds only the `SeedDoctor` table (department → 5 doctors), while the logic that turns it into `User`/`Doctor`/`Schedule` rows stays in `DataInitializer`. It is called from `run()` rather than being its own `CommandLineRunner`, because a separate runner could execute *before* `DataInitializer` and create users, which would make `count() == 0` false and skip the entire main seed (no departments, no roles).
