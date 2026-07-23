# Environment & Configuration

## Database
**MySQL** must be running locally with a `bookinghealthy` schema. Hibernate runs with `ddl-auto=update`, so tables are auto-created and migrated on boot — there is no migration tool (Flyway/Liquibase). Entity changes take effect on restart.

## Configuration file
Connection settings, credentials, Gmail SMTP, Google/Facebook OAuth2 client secrets, and the OpenRouter AI key all live in [application.properties](src/main/resources/application.properties). The app listens on port **8090**.

## Seed data
[DataInitializer.java](src/main/java/com/bookinghealthy/config/DataInitializer.java) is a `CommandLineRunner` that populates roles, users, 22 departments, doctors, and schedules — but **only when the `users` table is empty**. To re-seed, drop the schema and restart.

Default logins: `admin`/`admin123`, `patient_tom`/`123456`, doctors such as `doctor_walter`/`123456`.

`data.sql` also exists but is disabled (`spring.sql.init.mode=never`).

## Uploads
Uploaded images are written to an `uploads/` directory beside the running process and served at `/uploads/**` (see [WebConfig.java](src/main/java/com/bookinghealthy/config/WebConfig.java)). This folder is not in the repo — it is created at runtime. Multipart limit is 10MB.
