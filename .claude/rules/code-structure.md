# Code Structure & Layering

Standard Spring MVC layering under `com.bookinghealthy`:

- **`controller/`** — split by audience: root-level plus `controller/user`, `controller/admin`, `controller/doctor`, `controller/receptionist`, and `controller/api` (JSON). This split mirrors the URL and role structure, so place a new controller in the folder matching its audience.
- **`service/`** — interfaces live directly in `service/`; implementations in `service/impl/`. A few services (`AiService`, `ImageService`, `GlobalHelper`) are concrete classes. **When adding a service, follow the interface + `impl` pattern** used by `BookingService`, `WalletService`, `DoctorService`, `MedicalRecordService`.
- **`repository/`** — Spring Data JPA repositories.
- **`model/`** — JPA entities plus enums (`Role`, `BookingStatus`, `MedicalRecordStatus`, `TransactionType`, `AuthProvider`, `CandidateStatus`).
- **`dto/`** — request/response shapes, including `dto/ai/` for the OpenAI-compatible payloads.
- **`security/`**, **`config/`**, **`util/`**.

Entities use Lombok (`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor`) — note that `@AllArgsConstructor` is used positionally in `DataInitializer`, so **adding a field to `User` or `Doctor` breaks that seeding code** and it must be updated in the same change.
