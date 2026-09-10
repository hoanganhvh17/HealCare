# NNL Hospital — Hệ thống đặt lịch khám bệnh

Ứng dụng web quản lý phòng khám và đặt lịch khám bệnh, xây dựng bằng **Spring Boot 3.2.5 / Java 21**,
render phía máy chủ bằng **Thymeleaf**. Thương hiệu hiển thị trên trang bệnh nhân là *HealCare*.

Hệ thống phục vụ **5 vai trò** (bệnh nhân, bác sĩ, trưởng khoa, lễ tân, quản trị) và có một trợ lý AI
tư vấn chuyên khoa, đọc hồ sơ bệnh án cũ, hỗ trợ bác sĩ lúc khám, kèm chế độ trò chuyện bằng giọng nói.

---

## Mục lục

- [Tính năng](#tính-năng)
- [Công nghệ](#công-nghệ)
- [Yêu cầu môi trường](#yêu-cầu-môi-trường)
- [Chạy trên máy cá nhân](#chạy-trên-máy-cá-nhân)
- [Tài khoản mặc định](#tài-khoản-mặc-định)
- [Cấu hình](#cấu-hình)
- [Migration chạy tay](#migration-chạy-tay)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Build và kiểm thử](#build-và-kiểm-thử)
- [Triển khai](#triển-khai)
- [Lưu ý bảo mật](#lưu-ý-bảo-mật)
- [Tài liệu nội bộ](#tài-liệu-nội-bộ)

---

## Tính năng

### Bệnh nhân

- Đặt lịch khám theo **lưới 16 khung giờ 30 phút** trong giờ hành chính (07:30–11:30 và 13:30–17:30).
  Khung giờ đã có người đặt, bác sĩ báo bận, hoặc nằm ngoài ca làm việc đã đăng ký đều bị khóa.
- **Bốn hình thức thanh toán**: VNPay, chuyển khoản VietQR (có webhook đối soát), ví nội bộ,
  và thanh toán tại quầy (không trả trước, giới hạn 2 lịch chờ mỗi tài khoản).
- Tự hủy hoặc dời lịch theo quy định (trước giờ hẹn 24 giờ, dời tối đa 2 lần). Giao diện ẩn nút khi
  thao tác không còn hợp lệ và in rõ lý do.
- Xem hồ sơ bệnh án, đơn thuốc điện tử, tải PDF. Khám xong là nhận email kèm PDF đơn thuốc và
  thông báo vào chuông ngay.
- Tự khai **dị ứng thuốc**, tải lên **hồ sơ bệnh án từ nơi khác** (ảnh hoặc PDF) để AI đọc và tư vấn.
- Đánh giá bác sĩ sau khi khám, xem tin tức y tế, nộp đơn ứng tuyển.
- Chuông thông báo trong ứng dụng: xác nhận lịch, nhắc lịch ngày mai, nhắc tái khám, tin mới.

### Bác sĩ

- Dashboard với 8 ô nhận định (luật Java xác định, bấm vào mới gọi AI), thống kê và biểu đồ.
- Duyệt hoặc hủy yêu cầu đặt lịch, quản lý hàng đợi khám trong ngày.
- Ghi hồ sơ bệnh án: chẩn đoán, mã ICD-10, triệu chứng, chỉ số sinh tồn, đơn thuốc, lời dặn,
  phụ lục bổ sung sau khi chốt.
- **4 trợ lý AI ngay trên form khám**: đối chiếu đơn thuốc với dị ứng thật của bệnh nhân,
  soạn nháp lời dặn, gợi ý mã ICD-10, tóm tắt bệnh sử.
- Đăng ký ca khám cho tuần sau, đăng ký phiên trực, xin nghỉ phép, tìm người thay ca.

### Trưởng khoa

- Duyệt đơn nghỉ phép và phiên trực của khoa mình.
- Xếp ca khám cho cả khoa theo bảng bác sĩ × thứ × buổi, phân công trực.
- Nhắc và chốt đăng ký ca khám của khoa.

### Lễ tân

- Đăng ký khách vãng lai tại quầy, in phiếu thu và đơn thuốc.
- Thu tiền mặt tại quầy, đánh dấu vắng khám.
- Hủy hoặc chuyển lịch hàng loạt khi bác sĩ nghỉ đột xuất.
- Quản lý hàng đợi khám, đánh dấu bệnh nhân đến trễ.

### Quản trị

- Dashboard tài chính và vận hành: tiền đã thu, đã hoàn, thực thu, tiền còn phải thu,
  đối soát độc lập với sổ ví, 4 biểu đồ và khối việc cần xử lý.
- CRUD người dùng, bác sĩ, chuyên khoa, dịch vụ, tin tức, tin tuyển dụng, ứng viên, lịch hẹn.
- Trợ lý AI báo cáo số liệu phòng khám.

### Trợ lý AI

- **Tư vấn chuyên khoa từ triệu chứng**, trả về JSON có cấu trúc để giao diện dựng thẻ bác sĩ
  và mở sẵn form đặt lịch.
- **Tra cứu dữ liệu thật**: lịch làm việc bác sĩ theo tuần, lịch hẹn của chính khách,
  hồ sơ bác sĩ (giá, học vị, đánh giá), lọc bác sĩ theo tiêu chí, hồ sơ bệnh án đã tải lên.
- **Đọc ảnh và PDF**: tự phân loại giấy tờ y tế / ảnh triệu chứng / ảnh khác.
  Ảnh triệu chứng **không bao giờ được lưu** — chỉ phân tích rồi bỏ.
- **Chế độ gọi rảnh tay** dùng Web Speech API (Chrome/Edge, bắt buộc secure context).
- Nhánh cấp cứu in số 115 và dừng hẳn luồng đặt lịch.
- Hạn mức 10 lượt đọc ảnh mỗi người mỗi ngày, đếm trong cơ sở dữ liệu.

---

## Công nghệ

| Lớp | Thành phần |
|---|---|
| Runtime | Java 21, Spring Boot 3.2.5 |
| Web | Spring MVC + Thymeleaf, `thymeleaf-extras-springsecurity6` |
| Bảo mật | Spring Security (form login + OAuth2 Google/Facebook), BCrypt, CSRF bật |
| Dữ liệu | Spring Data JPA / Hibernate, MySQL 8 |
| Phiên | `spring-session-jdbc` (phiên lưu trong MySQL) |
| Lịch chạy | `@Scheduled` + ShedLock (`shedlock-provider-jdbc-template`) |
| AI | OpenRouter (tương thích OpenAI), `openai/gpt-4o-mini` rồi `google/gemini-2.0-flash-exp:free` |
| Giọng nói | Web Speech API, chạy hoàn toàn trong trình duyệt |
| PDF | OpenPDF (ghi), PDFBox 3.0.3 (đọc), font DejaVu nhúng |
| Ảnh | imgscalr |
| QR | ZXing |
| HTML/RSS | Jsoup |
| Email | Spring Mail + template Thymeleaf, gửi `@Async` |
| Build | Maven Wrapper |

Frontend **không có bước build**: CSS/JS nằm trực tiếp trong `src/main/resources/static/`.

---

## Yêu cầu môi trường

- **JDK 21** (không bật preview feature).
- **MySQL 8** đang chạy, có schema `bookinghealthy`.
- Khóa API OpenRouter nếu muốn dùng trợ lý AI (không có khóa thì phần chat báo lỗi, phần còn lại vẫn chạy).
- Tài khoản Gmail app password nếu muốn gửi email thật.

---

## Chạy trên máy cá nhân

```bash
# 1. Tạo schema
mysql -u root -p -e "CREATE DATABASE bookinghealthy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Chạy ứng dụng (Hibernate tự tạo bảng, DataInitializer tự seed dữ liệu)
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```

Ứng dụng lắng nghe ở **http://localhost:8090**.

Lần khởi động đầu tiên, `DataInitializer` tạo 5 vai trò, 22 chuyên khoa, khoảng **132 bác sĩ**
(22 bác sĩ gốc cộng 5 bác sĩ cho mỗi khoa) kèm lịch làm việc, tài khoản quản trị và lễ tân.
Khối seed chính chỉ chạy khi bảng `users` còn rỗng.

Ảnh chân dung bác sĩ nằm trong thư mục `uploads/` ở gốc dự án, nên **phải chạy ứng dụng với
thư mục làm việc là gốc dự án**, nếu không mọi ảnh bác sĩ sẽ hiện hình vỡ.

> **Không bao giờ chạy build ở terminal khác khi `spring-boot:run` đang lên.**
> Lệnh compile, test hay package ghi đè `target/classes` ngay dưới chân tiến trình đang chạy và
> devtools nạp lại giữa lúc ghi, ứng dụng chết với lỗi trông y như lỗi mã nguồn.

---

## Tài khoản mặc định

Chỉ tồn tại ở môi trường phát triển:

| Vai trò | Tài khoản | Mật khẩu |
|---|---|---|
| Quản trị | `admin` | `admin123` |
| Bệnh nhân | `patient_tom` | `123456` |
| Bác sĩ | `doctor_walter` | `123456` |
| Bác sĩ (seed) | `bs_<slug-tên>`, ví dụ `bs_nguyenductoan` | `123456` |
| Lễ tân | `receptionist` | `123456` |

Trưởng khoa là bác sĩ có thêm `ROLE_HEAD_DOCTOR` — đăng nhập bằng tài khoản `bs_*` bình thường,
mục "Phê duyệt của khoa" sẽ xuất hiện trong sidebar.

**Những tài khoản này không được phép tồn tại trên production.** Dùng `SEED_ADMIN_PASSWORD`,
`SEED_DEMO_ACCOUNTS=false`, `SEED_DOCTOR_PASSWORD`, và `SEED_ENABLED=false` sau lần khởi động đầu.

---

## Cấu hình

Toàn bộ cấu hình nằm trong **một file duy nhất**
[`src/main/resources/application.properties`](src/main/resources/application.properties),
theo dạng `${BIEN_MOI_TRUONG:giá-trị-dev}`. Môi trường phát triển chạy không cần đặt biến nào;
production ghi đè bằng biến môi trường.

Một file thay vì `application-prod.properties` là lựa chọn có chủ ý: hai file là hai chỗ phải giữ
đồng bộ, và khi quên một khóa thì nó **im lặng** rơi về giá trị dev. Với một file, việc tìm mọi
chuỗi `${` trong `application.properties` chính là danh sách kiểm tra lúc triển khai.

Các biến quan trọng nhất:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `SERVER_PORT` | `8090` | Cổng lắng nghe |
| `APP_BASE_URL` | `http://localhost:8090` | URL công khai, dùng để VNPay gọi lại |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | localhost | Kết nối MySQL |
| `DDL_AUTO` | `update` | `update` cho lần khởi động đầu, sau đó chuyển `validate` |
| `UPLOAD_DIR` | `uploads` | Ảnh công khai, phục vụ tại `/uploads/**` |
| `PRIVATE_DIR` | `private` | CV ứng viên và hồ sơ bệnh án, **không** phục vụ trực tiếp |
| `AI_API_KEY` | — | Khóa OpenRouter |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | — | Gmail app password |
| `VNPAY_TMN_CODE` / `VNPAY_HASH_SECRET` | sandbox | Cổng VNPay |
| `VIETQR_BANK_ID` / `VIETQR_ACCOUNT_NO` / `VIETQR_MEMO_PREFIX` | — | Thông tin nhận chuyển khoản |
| `PAYMENT_WEBHOOK_SECRET` | rỗng | Bí mật webhook ngân hàng; **để rỗng là đóng hẳn webhook** |
| `SCHEMA_STRICT` | `false` | `true` thì thiếu migration là không khởi động được |
| `MEDICAL_DOC_AI_ENABLED` | `true` | Công tắc AI đọc hồ sơ bệnh án |
| `NEWS_FETCH_ENABLED` | `true` | Công tắc thu thập tin tức y tế |
| `SEED_ENABLED` | `true` | Đặt `false` sau lần khởi động đầu trên production |

Danh sách đầy đủ ở [`deploy/env.example`](deploy/env.example).

> File `.env` được systemd và bash đọc, cả hai **không phải shell đầy đủ**: chú thích phải đứng
> trên dòng riêng, và giá trị có dấu cách hoặc ký tự đặc biệt phải đặt trong nháy kép.

---

## Migration chạy tay

Dự án **không dùng Flyway hay Liquibase**. Hibernate `ddl-auto=update` lo phần lớn, nhưng bốn nhóm
đối tượng nó không diễn đạt được nằm ở [`db/manual/`](db/manual) và phải chạy tay, theo thứ tự:

| File | Nội dung |
|---|---|
| `001_prod_hardening.sql` | Cột sinh `bookings.slot_uk` và unique index chống đặt trùng, `uk_posts_source_url`, bảng `shedlock` |
| `002_spring_session.sql` | `SPRING_SESSION` và `SPRING_SESSION_ATTRIBUTES` |
| `003_external_medical_records.sql` | `external_medical_records` và `ai_image_usage` |
| `004_cash_collection_and_no_show.sql` | Mở rộng ENUM `bookings.status` thêm `NO_SHOW`, ba cột thu tiền tại quầy |

`config/SchemaGuard` kiểm tra các đối tượng này lúc khởi động. Mặc định chỉ ghi log;
đặt `SCHEMA_STRICT=true` trên production để một migration bị quên trở thành lỗi khởi động
thay vì một ca đặt trùng âm thầm.

File `004` **cần chạy cả trên máy phát triển**: `ddl-auto=update` thêm được cột mới nhưng
không bao giờ viết lại danh sách giá trị của một cột `ENUM` đã tồn tại.

---

## Cấu trúc dự án

```
src/main/java/com/bookinghealthy/
├── BookingHealthyApplication.java   # Entry point, @EnableAsync, bean RestTemplate có timeout
├── config/                          # Security, seed, interceptor, ShedLock, SchemaGuard, LeavePolicy
├── controller/                      # Chia theo đối tượng sử dụng
│   ├── admin/  doctor/  head/  receptionist/  user/
│   ├── staff/                       # Logic dùng chung cho bác sĩ và lễ tân
│   └── api/                         # REST trả JSON (chat AI, khung giờ, thông báo)
├── dto/                             # Request/response, dto/ai cho payload OpenAI
├── model/                           # 39 entity JPA và enum
├── repository/                      # 27 Spring Data repository
├── security/                        # UserDetails, OAuth2, userinfo theo nhà cung cấp
├── service/                         # Interface ở đây, triển khai ở service/impl
├── task/                            # 5 cron job, mỗi job một @Component
└── util/                            # PaymentMemo, QRCodeGenerator, VitalSignFormatter

src/main/resources/
├── application.properties           # Toàn bộ cấu hình
├── templates/                       # 89 template Thymeleaf
│   ├── user/ admin/ doctor/ head/ receptionist/ staff/ auth/ email/ error/
│   └── */include/                   # header, footer, sidebar, ai-chat
├── static/assets/                   # Giao diện bệnh nhân
├── static/assets-admin/             # Giao diện nhân viên
└── fonts/                           # DejaVu Sans, nhúng vào PDF để in được tiếng Việt

db/manual/       # SQL chạy tay
deploy/          # Runbook triển khai, systemd unit, nginx, script backup
docs/            # Hướng dẫn sử dụng
.claude/rules/   # Tài liệu kiến trúc chi tiết theo chủ đề
```

Quy ước phân lớp: controller đặt vào thư mục khớp đối tượng sử dụng; service khai interface ở
`service/` và triển khai ở `service/impl/`.

---

## Build và kiểm thử

```bash
./mvnw clean package                                  # Đóng gói fat jar
java -jar target/booking-healthy-0.0.1-SNAPSHOT.jar   # Chạy bản đã đóng gói
./mvnw test                                           # Chạy test
./mvnw test -Dtest=PdfFontTest                        # Chạy một class test
```

Hiện có **2 class test**, cả hai canh đúng loại lỗi mà ứng dụng nuốt vào log — nơi
"không thấy lỗi" và "chạy đúng" trông giống hệt nhau:

- `MedicalRecordMailTemplateTest` — render thật template email hồ sơ bệnh án bằng
  `SpringTemplateEngine`. Lỗi biểu thức SpEL trong template email bị `EmailServiceImpl`
  bắt và bỏ qua, nên không test thì thư hỏng mà log trông như lỗi SMTP.
- `PdfFontTest` — kiểm font DejaVu có thật trong `resources/fonts/`. Thiếu font thì chức năng in
  chỉ báo lỗi đúng lúc lễ tân bấm in, còn email hồ sơ bệnh án lặng lẽ thiếu tệp đính kèm.

Lần chạy `./mvnw test` đầu tiên **phải có mạng**: surefire tải provider
`surefire-junit-platform` theo kiểu lazy.

Không có linter hay formatter nào ngoài maven-compiler-plugin.

---

## Triển khai

- [`deploy/README.md`](deploy/README.md) — runbook không phụ thuộc nhà cung cấp, 8 bước,
  kèm mục 7b cho việc nâng cấp máy đang chạy.
- [`deploy/DEPLOY-ORACLE-FREE.md`](deploy/DEPLOY-ORACLE-FREE.md) — hướng dẫn cụ thể trên
  Oracle Cloud Always Free (máy ảo ARM).
- [`deploy/env.example`](deploy/env.example) — mọi biến môi trường.
- [`deploy/nnlhospital.service`](deploy/nnlhospital.service) — systemd unit.
- [`deploy/nginx.conf.example`](deploy/nginx.conf.example) — reverse proxy.
  **Phải nâng `client_max_body_size` lên 10MB** cho khớp giới hạn multipart, mặc định nginx là 1MB.
- [`deploy/backup.sh`](deploy/backup.sh) — mysqldump cộng nén `uploads/` và `private/`, chạy bằng cron.

Ba việc dễ quên khi triển khai:

1. Chạy `db/manual/*.sql` **trước** khi khởi động jar mới, nhất là khi commit có thêm `@Entity`
   và server đang ở `DDL_AUTO=validate` — `validate` chỉ đối chiếu, không tạo bảng,
   nên thiếu migration là vòng lặp khởi động lại và nginx trả 502.
2. Chép thư mục `uploads/` sang máy chủ — ảnh bác sĩ được git theo dõi nhưng **không** nằm trong jar.
3. Đặt `SEED_ENABLED=false` và đổi mọi mật khẩu seed.

---

## Lưu ý bảo mật

**Năm bí mật bên ngoài đã từng được hardcode và vẫn còn trong lịch sử git.** Đưa chúng ra biến
môi trường không thu hồi được — phải **xoay (rotate)** trước khi mở cho người dùng thật:

- mật khẩu MySQL
- Gmail app password
- Google OAuth client secret
- Facebook OAuth client secret
- khóa API OpenRouter

Cặp khóa VNPay sandbox thì giữ được. Danh sách kiểm tra là bước 0 của `deploy/README.md`.

Những quy tắc bảo mật đang có hiệu lực, cần giữ nguyên khi sửa mã:

- `SecurityConfig` là **nguồn sự thật duy nhất** cho phân quyền URL. Luật hẹp phải khai
  **trên** luật rộng hơn; mọi API bị khóa quyền khai ở khối 0 đầu `filterChain`.
- **CSRF đang bật**. Form Thymeleaf dùng `th:action` trên thẻ `<form>` là tự có token;
  mỗi lời gọi `fetch` POST phải gửi header qua `MediTrustCsrf.headers()` trong `assets/js/csrf.js`.
- **Không bao giờ thêm `@GetMapping` có ghi dữ liệu.** CSRF không xét GET, và cookie phiên vẫn
  được gửi khi điều hướng cấp cao — một đường link là đủ để khai thác.
- Dữ liệu cá nhân (CV ứng viên, hồ sơ bệnh án tải lên) nằm ở `PRIVATE_DIR`, **ngoài** `/uploads`,
  và chỉ đọc được qua endpoint có kiểm quyền.
- **Không bao giờ mở `/actuator/env` hoặc `/actuator/configprops`** — chúng in ra đúng những bí mật
  vừa được đưa vào biến môi trường. Chỉ `/actuator/health` là công khai.

---

## Tài liệu nội bộ

Tài liệu kiến trúc chi tiết nằm ở [`.claude/rules/`](.claude/rules), chia theo chủ đề và được
[`CLAUDE.md`](CLAUDE.md) nạp vào:

| File | Nội dung |
|---|---|
| `project-overview.md` | Tổng quan, entry point |
| `build-and-run.md` | Maven, dependency, lệnh chạy |
| `environment-setup.md` | Cấu hình, cơ sở dữ liệu, seed, upload, font PDF |
| `code-structure.md` | Phân lớp, vị trí đặt class mới |
| `authentication-and-roles.md` | 5 vai trò, phân quyền URL, phiên, CSRF |
| `booking-flow.md` | Khung giờ, thanh toán, tranh chấp chỗ, luật hủy và dời |
| `ai-assistant.md` | Prompt, schema JSON, tra cứu, giọng nói |
| `medical-records.md` | Cụm entity bệnh án, dị ứng, hồ sơ ngoại viện |
| `supporting-subsystems.md` | Lịch trực, nghỉ phép, ví, tin tức, email, cron |
| `coding-conventions.md` | Quy ước, bẫy Thymeleaf/CSS/JS, xử lý lỗi, test |
| `progress-log.md` | Nhật ký công việc, mới nhất ở trên |

`docs/Huong-dan-su-dung-NNL-Hospital.docx` là hướng dẫn sử dụng cho người dùng cuối.

---

## Quy ước đóng góp

- **Tiếng Việt** cho chuỗi hiển thị, chú thích và commit message.
- Commit theo dạng `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` kèm mô tả tiếng Việt,
  ví dụ `fix: lỗi xung đột lịch với phương thức thanh toán bằng ví`.
- Mỗi phần việc hoàn thành phải ghi một dòng vào `.claude/rules/progress-log.md` và cập nhật
  file rule thuộc chủ đề bị ảnh hưởng, **trong cùng một thay đổi**.
- Khi một thao tác không còn hợp lệ, **ẩn nút đi** thay vì cho bấm rồi báo lỗi: viết một hàm
  `whyCannot…(x)` trên service trả `null` nếu còn làm được, ngược lại trả câu tiếng Việt; controller
  dùng nó để chặn thật, template dùng nó để ẩn nút và in đúng câu đó.
