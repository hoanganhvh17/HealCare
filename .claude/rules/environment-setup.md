# Environment & Configuration

## Database
**MySQL** must be running locally with a `bookinghealthy` schema. Hibernate runs with `ddl-auto=update`, so tables are auto-created and migrated on boot — there is no migration tool (Flyway/Liquibase). Entity changes take effect on restart.

## Configuration file
Connection settings, credentials, Gmail SMTP, Google/Facebook OAuth2 client secrets, and the OpenRouter AI key all live in [application.properties](src/main/resources/application.properties). The app listens on port **8090**.

## Seed data
[DataInitializer.java](src/main/java/com/bookinghealthy/config/DataInitializer.java) is a `CommandLineRunner` that populates roles, users, 22 departments, doctors, and schedules — but **only when the `users` table is empty**. To re-seed that block, drop the schema and restart.

Two blocks run **outside** that guard and are idempotent, so they also apply to an existing dev database:
- `ensureReceptionistAccount()` — `ROLE_RECEPTIONIST` + the `receptionist` account.
- `ensureExtraDoctors()` — 5 extra doctors for **every** department (22 × 5 = 110), with bio, degree, price, phone and 3 weekly `Schedule` rows each. The data table lives in [DoctorSeedData.java](src/main/java/com/bookinghealthy/config/DoctorSeedData.java) keyed by **department name**, so a renamed department silently skips its five doctors (a warning is printed). Username / email / avatar filename are all derived from the full name via `slugify()` (`Nguyễn Đức Toàn` → `bs_nguyenductoan`, `nguyenductoan@meditrust.vn`, `bs-nguyenductoan.jpg`), which is what makes re-running safe — the check is `existsByUsername`. The BCrypt hash is computed **once** and reused; encoding per doctor would add ~10s to every boot.

Default logins: `admin`/`admin123`, `patient_tom`/`123456`, doctors such as `doctor_walter`/`123456`, and every seeded doctor with `bs_<slug>`/`123456`.

`data.sql` also exists but is disabled (`spring.sql.init.mode=never`).

## Testing the voice agent
The mic only works in a **secure context**. `http://localhost:8090` qualifies, so normal local dev is fine — but reaching the same dev server from another device over `http://192.168.x.x:8090` silently disables every voice feature. Test on `localhost` in Chrome or Edge, and put the app behind HTTPS before demoing voice off-machine.

Reading answers aloud also needs a Vietnamese voice installed on the OS (Windows: *Settings > Time & Language > Speech*). Without one the assistant falls back to the default voice and mispronounces Vietnamese; a console warning is logged once.

## Uploads
Uploaded images are written to an `uploads/` directory beside the running process and served at `/uploads/**` (see [WebConfig.java](src/main/java/com/bookinghealthy/config/WebConfig.java)). Multipart limit is 10MB.

**`/uploads/**` is resolved only from `file:uploads/`.** The copies under `src/main/resources/static/uploads/` are never served — the more specific `/uploads/**` handler wins over the default `/**` static handler and returns 404 instead of falling through. So a doctor avatar must sit in the runtime `uploads/` folder, i.e. the app must be started with the project root as its working directory. Seeded doctor portraits (`doctor-*.jpg`, `bs-*.jpg`) are committed there for that reason.

### Doctor portrait style
Every seeded doctor photo follows **one** look: studio headshot, white coat with stethoscope, plain light-grey background, 512×512 JPEG. They are AI-generated (so no real person's likeness is attached to a fictional doctor), one image per doctor, with only gender and apparent age varied — age brackets follow `experienceYears` (≥18 → 50s, ≥12 → 40s, else 30s). **Keep new doctor photos in this style**, otherwise the doctor list and booking pages go back to looking like a mix of stock photos. `default-doctor.png` stays a neutral silhouette: it is the fallback for a doctor with `avatar == null`.
