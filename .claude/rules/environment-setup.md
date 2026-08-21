# Environment & Configuration

## Database
**MySQL** must be running locally with a `bookinghealthy` schema. Hibernate runs with `ddl-auto=update`, so tables are auto-created and migrated on boot — there is no migration tool (Flyway/Liquibase). Entity changes take effect on restart.

**Gotcha — that only holds while the property line actually exists.** `spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}` was **deleted** from `application.properties` by the 2026-08-15 rewrite into `${VAR:default}` form (it is present at `Init commit`, line 20) and restored on 2026-08-19. Spring Boot's default for a **non-embedded** DataSource is `none`, so with the line gone Hibernate creates nothing — and `DDL_AUTO` in `deploy/env.example` binds to nothing at all. This is invisible on any database that already has its tables, i.e. every dev machine; it surfaces only on the first boot against an empty schema, as `Table 'bookinghealthy.users' doesn't exist` thrown by `DataInitializer`'s own `count()`. **Grep for `ddl-auto` before deploying to a fresh database** — a `DDL_AUTO` line in `.env` is not evidence the property is wired.

**Gotcha — `characterEncoding` in `DB_URL` takes a JAVA charset name, not a MySQL one.** `characterEncoding=utf8mb4` is rejected by Connector/J with `Unsupported character encoding 'utf8mb4'` at the first connection, surfacing as a boot failure with no mention of the URL. The correct value is `UTF-8`, which Connector/J 8 maps to `utf8mb4` server-side anyway. `deploy/env.example` carried the wrong value until 2026-08-19.

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
Everything lives in the single [application.properties](src/main/resources/application.properties), in the form `${ENV_VAR:dev-default}` — dev runs with no environment set, production overrides via environment variables. The app listens on port **8090**.

**One file on purpose, not `application-prod.properties`.** Two files are two places that must be kept in sync, and the failure is silent: forget to add a key to the prod file and it quietly falls back to the dev value, i.e. production running on the dev database password with nothing in the log. With one file, `grep '\${' application.properties` *is* the deployment checklist. [deploy/env.example](../../deploy/env.example) lists every variable; [deploy/README.md](../../deploy/README.md) is the runbook.

**The committed defaults are dev values, and all five external secrets are already in git history.** Externalising them does not un-leak them — MySQL password, Gmail app password, Google + Facebook client secrets and the OpenRouter key must all be **rotated** before the app faces real users. Checklist is step 0 of the deploy README.

Secrets that used to be hardcoded **in Java** now come from config too: `VnPayProperties` (`vnpay.*`) and `VietQrProperties` (`vietqr.*`), the project's first `@ConfigurationProperties` classes. `VNPayConfig` is now a pure static-utility class (`hmacSHA512`, `getRandomNumber`, `getIpAddress`) holding no secrets.

**Never populate those with `@Value` on a static setter** (or `@PostConstruct` assigning a static). It is the first thing suggested online, it appears to work, and it is an initialisation-order landmine: anything reading the static before the bean is constructed gets `null`, with nothing in the code making that visible.

### Schema objects Hibernate cannot express — `db/manual/*.sql`
There is still no migration tool. Three things Hibernate `ddl-auto=update` cannot create live in [db/manual/](../../db/manual/) and are run **by hand, once**, in order:

- `001_prod_hardening.sql` — the `bookings.slot_uk` generated column + `uk_bookings_slot`, `uk_posts_source_url`, and the `shedlock` table.
- `002_spring_session.sql` — `SPRING_SESSION` + `SPRING_SESSION_ATTRIBUTES`.
- `003_external_medical_records.sql` — `external_medical_records` + `ai_image_usage`. Not something Hibernate *cannot* express — it maps both entities fine — but something `validate` **refuses to create**; see the trap below.

`config/SchemaGuard` (an `ApplicationRunner`) checks each object against `information_schema` at boot. By default it only logs loudly — same principle as the lazily-loaded PDF fonts below: a missing artifact must degrade rather than kill startup. Set **`SCHEMA_STRICT=true` in production** so a forgotten migration becomes a boot failure instead of a silent double-booking.

**A generated column is the safe direction of the orphan-column trap** documented above. An orphan *plain* `NOT NULL` column breaks every INSERT because Hibernate stops naming it; an orphan *generated* column is harmless because MySQL computes it and Hibernate's explicit column list never touches it. The corollary: **never add a `slotUk` field to `Booking`** — Hibernate would map it, start naming it in INSERTs, and MySQL rejects any write to a generated column.

`DDL_AUTO` stays `update` for the first boot on an empty database (with no migration tool, `validate` would create nothing); flip it to `validate` afterwards. `validate` ignores extra unmapped columns, so `slot_uk`, `shedlock` and the session tables are all fine.

**Gotcha — once production is on `validate`, ADDING AN ENTITY is a deploy that cannot boot.** This is the mirror image of every other trap on this page: they all fail *silently*, this one fails *loudly and totally*, and it does so **after** the new jar is already in place. `validate` only compares — it creates nothing — so a commit introducing a new `@Entity` dies at startup with `SchemaManagementException: missing table [x]`, Hibernate cancels the context, and systemd's `Restart=always` turns it into a crash-loop serving **502** to every visitor. Happened for real on 2026-08-21 deploying `e6e4ba1` (two new entities: `ExternalMedicalRecord`, `AiImageUsage`).

Three things make it easy to walk into:

- **Dev never sees it.** Dev runs `ddl-auto=update`, so the tables appear the moment you write the entity; the defect exists only on the machine you cannot test on.
- **`deploy/.env` in the repo is NOT what the server runs.** The committed sample still said `DDL_AUTO=update` / `SEED_ENABLED=true`, while `/etc/nnlhospital/.env` had been flipped to `validate` / `false` at the end of the first deploy — exactly as step 4 of the runbook instructs. **Read the server's file, never the repo's, when reasoning about production.**
- **`SchemaGuard` does not catch it.** It checks the four objects listed above against `information_schema`; a brand-new entity is not among them, so it reports "Lược đồ đầy đủ" right up until Hibernate refuses to start.

**The fix is to generate the DDL from Hibernate rather than hand-write it.** `validate` compares column names and types, so a hand-typed guess re-fails the same way. The dev database already holds exactly what Hibernate wants, so `SHOW CREATE TABLE <new_table>` on dev **is** the migration — drop `AUTO_INCREMENT=` and `COLLATE=` (let the table inherit the database default so it matches every other production table) and commit it as the next `db/manual/*.sql`. Do **not** "fix" it by flipping the server back to `update`: that re-opens the whole page of `update` traps for one table, and the flag then silently stays wrong until the next surprise.

**So: any commit that adds or changes an `@Entity` must ship its `db/manual/*.sql` in the same change, and that SQL must be applied BEFORE the new jar is started.** Check with `git diff --stat <old>..<new> -- '*/model/*.java'` before every deploy.

### Hạn mức đọc ảnh bằng AI
`ChatImageService.MAX_IMAGE_ANALYSES_PER_DAY = 10` là **hằng số Java**, không phải khoá cấu hình —
cùng khuôn `BookingService.MAX_PAY_AT_COUNTER_BOOKINGS`. Bộ đếm nằm ở bảng `ai_image_usage`, một dòng
cho mỗi (người dùng, ngày), UNIQUE trên cặp đó. Phải là bảng chứ không phải map trong bộ nhớ như
`AiController.softLockCache`: hạn mức chi phí mà reset mỗi lần khởi động lại thì chỉ cần restart là
lách được, và nó cũng sai khi chạy nhiều instance. Công tắc `medical-doc.ai-enabled` ở dưới tắt cả
đường đọc ảnh này.

### AI đọc hồ sơ bệnh án ngoại viện (`medical-doc.ai-enabled`)
Công tắc duy nhất, khuôn giống `news.fetch.enabled`. Đặt `false` để phát triển mà không đốt lượt gọi
AI: tệp vẫn lưu bình thường, hồ sơ nằm ở `aiStatus = PENDING` và có một dòng log nói rõ vì sao. Xem
[medical-records.md](medical-records.md).

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

**Gotcha — đổi username của một bác sĩ seed làm ứng dụng KHÔNG khởi động được.** Idempotency của `ensureExtraDoctors` là `existsByUsername`, còn ràng buộc UNIQUE của cơ sở dữ liệu là trên **email** — nên đổi `bs_ledinhphuc` thành tên khác khiến lần boot sau tạo lại đúng bác sĩ đó và đâm vào email đã tồn tại. `DataInitializer` là `CommandLineRunner`, nên đây là `Application run failed` chứ không phải một dòng warning; kèm `Restart=always` của systemd thì thành crash-loop và nginx trả **502**. Xảy ra thật trên production ngày 2026-08-19. Cách đúng là đặt **`SEED_ENABLED=false`** sau lần boot đầu — giá trị `deploy/env.example` vẫn khuyến nghị — chứ không phải sửa lại dữ liệu cho vừa lòng bộ seed.

Default logins **in dev**: `admin`/`admin123`, `patient_tom`/`123456`, doctors such as `doctor_walter`/`123456`, and every seeded doctor with `bs_<slug>`/`123456`.

**Those defaults must not reach production** — a guessable admin account on a public healthcare site is a day-one incident. Four `seed.*` keys control it: `SEED_ADMIN_USERNAME` / `SEED_ADMIN_PASSWORD` (the app prints a loud warning while the password is still `admin123`), `SEED_DEMO_ACCOUNTS=false` to skip `patient_tom` / `testsang31`, `SEED_DOCTOR_PASSWORD` for the ~132 doctors and the receptionist, and `SEED_ENABLED=false` to switch the whole runner off after the first boot.

**`data.sql` was deleted on 2026-08-18** — it had been dead since `spring.sql.init.mode=never` was set, and Spring picks `data.sql` up **by filename convention**, so a file sitting there is one flipped property away from running against a database `DataInitializer` already owns. Keep the `never` line: it is the guard against someone re-adding the file.

## Font in PDF — `src/main/resources/fonts/`
`PdfExportServiceImpl` cần **`DejaVuSans.ttf` + `DejaVuSans-Bold.ttf`** ở đó (kèm `LICENSE.txt` của bộ font). Thư mục này từng **rỗng**, và hậu quả rất kín tiếng: font nạp lười ở lần in đầu tiên chứ **không** `@PostConstruct` (cố ý — thiếu tệp thì chỉ chức năng in báo lỗi, không được phép làm sập ứng dụng lúc khởi động), nên `buildReceipt` / `buildPrescription` chỉ ném `IllegalStateException("Thiếu font in PDF…")` đúng lúc lễ tân bấm in, còn thư "đã có hồ sơ bệnh án" thì chỉ lặng lẽ **thiếu tệp đính kèm** mà vẫn gửi bình thường. `PdfFontTest` giờ canh chỗ này: quên commit font là build đỏ.

Chọn DejaVu vì giấy phép cho phép phát hành lại (nhúng thẳng vào PDF phát cho bệnh nhân) — **đừng thay bằng font hệ điều hành** như `arial.ttf`: đó là font có bản quyền của Microsoft, commit vào repo là phát hành lại trái phép.

`IDENTITY_H + EMBEDDED` là bắt buộc: 14 font chuẩn của PDF dùng WinAnsi, không vẽ nổi "ế", "ộ", "ữ".

## Testing the voice agent
The mic only works in a **secure context**. `http://localhost:8090` qualifies, so normal local dev is fine — but reaching the same dev server from another device over `http://192.168.x.x:8090` silently disables every voice feature. Test on `localhost` in Chrome or Edge, and put the app behind HTTPS before demoing voice off-machine.

Reading answers aloud also needs a Vietnamese voice installed on the OS (Windows: *Settings > Time & Language > Speech*). Without one the assistant falls back to the default voice and mispronounces Vietnamese; a console warning is logged once.

## Uploads
**`service/FileStorageService` is the only place that writes an uploaded file.** Seven call sites used to repeat the same four lines with the same three bugs; see [coding-conventions.md](coding-conventions.md). Two knobs:

- `app.upload-dir` (`UPLOAD_DIR`, default `uploads`) — served publicly at `/uploads/**`.
- `app.private-dir` (`PRIVATE_DIR`, default `private`) — **not served by any handler**. Candidate CVs live here.
  Two subdirectories now: `private/cv` (CV ứng viên) và **`private/medical-docs`** (hồ sơ bệnh án ngoại viện bệnh nhân tự tải lên). Cả hai chỉ đọc được qua một endpoint có kiểm quyền — `/admin/candidates/{id}/cv` và `/user/medical-document/file/{id}`. `FileStorageServiceImpl.resolvePrivate(subdir, name)` là chỗ duy nhất biến tên tệp trong DB thành `Path` đã kiểm path traversal; đừng viết bản sao thứ hai của phép kiểm `startsWith(privateRoot)`.

Both are resolved to an **absolute** path at startup (logged as `[Upload]` lines on boot). The old `file:uploads/` was relative to the process working directory — the comment claiming it sat "beside the .jar" was simply wrong, and a container or systemd unit with a different CWD lost every image. Multipart limit is 10MB; **nginx must be raised to match** (`client_max_body_size`, default 1MB) or large uploads are rejected before reaching the app.

**Uploaded CVs are personal data and are deliberately outside `/uploads`.** Filenames are guessable, `/uploads/**` is `permitAll`, and in production nginx serves that directory directly — where Spring Security never runs. Download goes through `GET /admin/candidates/{id}/cv` instead.

**Service images use two resource locations**, so `AdminServiceController` uploads work without migrating any data: `/assets/img/health/**` resolves from `file:<upload-dir>/health/` first, then `classpath:/static/assets/img/health/`. New uploads come off disk, seeded images still come from inside the jar.

**The 133 seeded doctor portraits under `uploads/` are tracked by git but are NOT in the jar.** They must be copied into the production upload volume or every seeded doctor renders a broken image.

Not everything there arrives by upload: `NewsFeedService.downloadImage` **downloads** the illustration of a collected news article into the same folder (named `<millis>_news.<ext>`, capped at 5MB). Its extension comes from the response `Content-Type`, not the URL — Spring picks the served Content-Type from the file extension, so a `.jpg` holding WebP bytes would mislabel the image to every browser.

**`/uploads/**` is resolved only from `file:uploads/`.** A copy placed under `src/main/resources/static/uploads/` is never served — the more specific `/uploads/**` handler wins over the default `/**` static handler and returns 404 instead of falling through, so the copy is not a fallback, it is dead weight. Such a directory existed (26 files, 22 of them byte-identical duplicates of `uploads/`) and was **deleted on 2026-08-18**; do not recreate it. So a doctor avatar must sit in the runtime `uploads/` folder, i.e. the app must be started with the project root as its working directory. Seeded doctor portraits (`doctor-*.jpg`, `bs-*.jpg`) are committed there for that reason.

### Doctor portrait style
Every seeded doctor photo follows **one** look: studio headshot, white coat with stethoscope, plain light-grey background, 512×512 JPEG. They are AI-generated (so no real person's likeness is attached to a fictional doctor), one image per doctor, with only gender and apparent age varied — age brackets follow `experienceYears` (≥18 → 50s, ≥12 → 40s, else 30s). **Keep new doctor photos in this style**, otherwise the doctor list and booking pages go back to looking like a mix of stock photos. `default-doctor.png` stays a neutral silhouette: it is the fallback for a doctor with `avatar == null`.
