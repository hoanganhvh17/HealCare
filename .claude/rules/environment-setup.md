# Environment & Configuration

## Database
**MySQL** must be running locally with a `bookinghealthy` schema. Hibernate runs with `ddl-auto=update`, so tables are auto-created and migrated on boot — there is no migration tool (Flyway/Liquibase). Entity changes take effect on restart.

**Gotcha — `ddl-auto=update` never widens a column either.** It only *adds* tables and columns. Declaring `@Column(length = 500)` on a field whose column already exists as `varchar(255)` compiles and boots fine, then fails at runtime with "Data truncated" the first time a long value arrives — and only on databases that already had the table. `Notification.message` is pinned to 255 for exactly this reason: the dev database already contained a leftover `notifications` table from an earlier iteration of the project, with narrower columns and two now-unused ones (`user_id`, `target_url`) that Hibernate left in place.

**Gotcha — a column left behind by a field you deleted will break every INSERT.** The mirror image of the one above, and it does not announce itself. `ddl-auto=update` adds a `NOT NULL` column with **no DEFAULT**; delete or revert the field later and Hibernate simply stops naming it in the INSERT, so MySQL rejects the row with `Field 'x' doesn't have a default value` — surfacing as a plain HTTP 500 with the real cause buried under `UnexpectedRollbackException` (the controller's catch-all swallows the original and the *commit* is what finally throws). The dev database carries two such orphans on `bookings`, `review_reminder_sent` and `upcoming_reminder_sent`, from an experiment that never reached git — while they are there **no booking can be created at all**. Fix either way:

```sql
-- keep the columns, just let inserts through
ALTER TABLE bookings ALTER COLUMN review_reminder_sent SET DEFAULT 0;
ALTER TABLE bookings ALTER COLUMN upcoming_reminder_sent SET DEFAULT 0;
-- or drop them, since no entity maps them
ALTER TABLE bookings DROP COLUMN review_reminder_sent, DROP COLUMN upcoming_reminder_sent;
```

Check with `SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='…'` whenever an insert starts failing right after a field was removed or a commit reverted.

This is why `Allergy.source` is declared **nullable** even though Java always sets it: a new `NOT NULL` column on an existing table is exactly the shape that breaks every INSERT later. Prefer nullable + a Java-side default when adding a column to a table that already exists.

**Gotcha — renaming an `@Enumerated(EnumType.STRING)` value.** Hibernate 6 maps such a field to a **native MySQL `ENUM(...)` column** whose value list is fixed at table-creation time. `ddl-auto=update` **never rewrites that list**, so after you rename or remove an enum constant, inserting the new value fails with `Data truncated for column '…'` (surfaces as an HTTP 500). Fix it once by hand, e.g. `ALTER TABLE staff_shifts MODIFY COLUMN shift_type ENUM('CA_SANG','CA_CHIEU','TRUC_NGOAI_GIO','TRUC_12H_DEM','TRUC_24H','HOI_CHAN') NOT NULL;` — or drop the table and let it re-create. Adding a *new* constant to the end is safe; only renames/removals on an existing dev DB need this.

## Configuration file
Connection settings, credentials, Gmail SMTP, Google/Facebook OAuth2 client secrets, and the OpenRouter AI key all live in [application.properties](src/main/resources/application.properties). The app listens on port **8090**.

### Thu thập tin tức (`news.fetch.*`)
Four keys drive `MedicalNewsTask`: `enabled` (kill switch), `cron` (default `0 0 6,18 * * ?`), `max-per-run` (2 — each article costs one AI call), `max-age-days` (3). The **list of newspapers is not here** — it is `config/NewsSourceCatalog.SOURCES`, kept in Java for the same reason as `DoctorSeedData`. Set `news.fetch.enabled=false` to develop without the task firing. See [supporting-subsystems.md](supporting-subsystems.md).

## Seed data
[DataInitializer.java](src/main/java/com/bookinghealthy/config/DataInitializer.java) is a `CommandLineRunner` that populates roles, users, 22 departments, doctors, and schedules — but **only when the `users` table is empty**. To re-seed that block, drop the schema and restart.

Four blocks run **outside** that guard and are idempotent, so they also apply to an existing dev database:
- `ensureReceptionistAccount()` — `ROLE_RECEPTIONIST` + the `receptionist` account.
- `ensureStaffProfiles()` — a `StaffProfile` for every doctor and receptionist: `hireDate = today − experienceYears`, `workCondition` = `HEAVY` for the departments in `LeavePolicy.HEAVY_DEPARTMENTS` (14 ngày phép) else `NORMAL` (12 ngày).
- `ensureHeadDoctors()` — `ROLE_HEAD_DOCTOR` plus one trưởng khoa per department (the doctor with the highest `experienceYears`), recorded via `StaffProfile.headOfDepartment`. They **keep** `ROLE_DOCTOR`, so log in with their normal `bs_<slug>` / `123456` account and the "Phê duyệt của khoa" item appears in the doctor sidebar.

  Both must run **after** `ensureExtraDoctors()` so the 110 seeded doctors are included.
- `ensureExtraDoctors()` — 5 extra doctors for **every** department (22 × 5 = 110), with bio, degree, price, phone and 3 weekly `Schedule` rows each. The data table lives in [DoctorSeedData.java](src/main/java/com/bookinghealthy/config/DoctorSeedData.java) keyed by **department name**, so a renamed department silently skips its five doctors (a warning is printed). Username / email / avatar filename are all derived from the full name via `slugify()` (`Nguyễn Đức Toàn` → `bs_nguyenductoan`, `nguyenductoan@nnlhospital.vn`, `bs-nguyenductoan.jpg`), which is what makes re-running safe — the check is `existsByUsername`.

  **The email domain is a machine string — no spaces, ever.** `User.email` carries `@Email`, and `DataInitializer` is a `CommandLineRunner`, so a bad address does not merely skip a doctor: the `ConstraintViolationException` kills the whole boot with `Application run failed`. A brand rename done by find-and-replace turned this into `"@NNL Hospital.vn"` and the app would not start at all. Doctors seeded **before** that rename keep their old `@meditrust.vn` address — the idempotency check is `existsByUsername`, not email, so the domain change only applies to newly created rows. Harmless in dev; `UPDATE users SET email = REPLACE(email,'@meditrust.vn','@nnlhospital.vn')` if you want them uniform. The BCrypt hash is computed **once** and reused; encoding per doctor would add ~10s to every boot.

Default logins: `admin`/`admin123`, `patient_tom`/`123456`, doctors such as `doctor_walter`/`123456`, and every seeded doctor with `bs_<slug>`/`123456`.

`data.sql` also exists but is disabled (`spring.sql.init.mode=never`).

## Font in PDF — `src/main/resources/fonts/`
`PdfExportServiceImpl` cần **`DejaVuSans.ttf` + `DejaVuSans-Bold.ttf`** ở đó (kèm `LICENSE.txt` của bộ font). Thư mục này từng **rỗng**, và hậu quả rất kín tiếng: font nạp lười ở lần in đầu tiên chứ **không** `@PostConstruct` (cố ý — thiếu tệp thì chỉ chức năng in báo lỗi, không được phép làm sập ứng dụng lúc khởi động), nên `buildReceipt` / `buildPrescription` chỉ ném `IllegalStateException("Thiếu font in PDF…")` đúng lúc lễ tân bấm in, còn thư "đã có hồ sơ bệnh án" thì chỉ lặng lẽ **thiếu tệp đính kèm** mà vẫn gửi bình thường. `PdfFontTest` giờ canh chỗ này: quên commit font là build đỏ.

Chọn DejaVu vì giấy phép cho phép phát hành lại (nhúng thẳng vào PDF phát cho bệnh nhân) — **đừng thay bằng font hệ điều hành** như `arial.ttf`: đó là font có bản quyền của Microsoft, commit vào repo là phát hành lại trái phép.

`IDENTITY_H + EMBEDDED` là bắt buộc: 14 font chuẩn của PDF dùng WinAnsi, không vẽ nổi "ế", "ộ", "ữ".

## Testing the voice agent
The mic only works in a **secure context**. `http://localhost:8090` qualifies, so normal local dev is fine — but reaching the same dev server from another device over `http://192.168.x.x:8090` silently disables every voice feature. Test on `localhost` in Chrome or Edge, and put the app behind HTTPS before demoing voice off-machine.

Reading answers aloud also needs a Vietnamese voice installed on the OS (Windows: *Settings > Time & Language > Speech*). Without one the assistant falls back to the default voice and mispronounces Vietnamese; a console warning is logged once.

## Uploads
Uploaded images are written to an `uploads/` directory beside the running process and served at `/uploads/**` (see [WebConfig.java](src/main/java/com/bookinghealthy/config/WebConfig.java)). Multipart limit is 10MB.

Not everything there arrives by upload: `NewsFeedService.downloadImage` **downloads** the illustration of a collected news article into the same folder (named `<millis>_news.<ext>`, capped at 5MB). Its extension comes from the response `Content-Type`, not the URL — Spring picks the served Content-Type from the file extension, so a `.jpg` holding WebP bytes would mislabel the image to every browser.

**`/uploads/**` is resolved only from `file:uploads/`.** The copies under `src/main/resources/static/uploads/` are never served — the more specific `/uploads/**` handler wins over the default `/**` static handler and returns 404 instead of falling through. So a doctor avatar must sit in the runtime `uploads/` folder, i.e. the app must be started with the project root as its working directory. Seeded doctor portraits (`doctor-*.jpg`, `bs-*.jpg`) are committed there for that reason.

### Doctor portrait style
Every seeded doctor photo follows **one** look: studio headshot, white coat with stethoscope, plain light-grey background, 512×512 JPEG. They are AI-generated (so no real person's likeness is attached to a fictional doctor), one image per doctor, with only gender and apparent age varied — age brackets follow `experienceYears` (≥18 → 50s, ≥12 → 40s, else 30s). **Keep new doctor photos in this style**, otherwise the doctor list and booking pages go back to looking like a mix of stock photos. `default-doctor.png` stays a neutral silhouette: it is the fallback for a doctor with `avatar == null`.
