# Quy trình deploy NNL Hospital

Làm theo đúng thứ tự. Mỗi bước có cách kiểm chứng ngay bên dưới nó.

Tệp này là runbook **chung**, không phụ thuộc nhà cung cấp. Bản cụ thể cho một hạ tầng miễn phí có sẵn: **[DEPLOY-ORACLE-FREE.md](DEPLOY-ORACLE-FREE.md)** (Oracle Cloud Always Free — máy ảo ARM 2 OCPU/12GB, MySQL + nginx + certbot trên cùng một máy, 0đ vĩnh viễn).

> **Hiện trạng production (cập nhật 2026-08-24).** Tên miền đang chạy là **`hoanganhvh17.online`**, không phải `nnlhospital.io.vn` như `DEPLOY-ORACLE-FREE.md` còn ghi — tên cũ **không còn phân giải được** từ cả máy dev lẫn chính máy chủ, còn chứng chỉ Let's Encrypt mà nginx đang nạp là của tên mới. Máy chủ `ubuntu@134.185.87.112`, jar ở `/opt/nnlhospital/app.jar`, dịch vụ `nnlhospital`, đứng sau Cloudflare. **Đừng tin tên miền in trong tài liệu — hỏi `sudo nginx -T | grep server_name` trước mỗi lần kiểm chứng**, bằng không mọi lệnh `curl` sẽ báo lỗi DNS và rất dễ bị đọc nhầm thành "trang chết".

Các tệp đi kèm trong thư mục này:

| Tệp | Dùng ở bước |
|---|---|
| [env.example](env.example) | 3 — mọi biến môi trường |
| [nnlhospital.service](nnlhospital.service) | 3 — unit systemd, nạp `.env` và tự khởi động lại |
| [nginx.conf.example](nginx.conf.example) | 5 — reverse proxy + phục vụ `/uploads/` |
| [backup.sh](backup.sh) | 6b — sao lưu DB + tệp tải lên, chạy bằng cron |

---

## 0. XOAY BÍ MẬT — làm TRƯỚC, không phải làm sau

Toàn bộ bí mật cũ **đã nằm trong lịch sử git**. Đưa chúng ra biến môi trường **không** thu hồi được — ai clone repo cũng đọc lại được bằng `git log -p`.

Phải cấp mới, không tái sử dụng:

- [ ] Mật khẩu MySQL
- [ ] Gmail app password (Google Account → Security → App passwords → xoá cái cũ)
- [ ] Google OAuth client secret (Google Cloud Console → Credentials → Reset)
- [ ] Facebook App secret (Meta for Developers → Settings → Basic → Reset)
- [ ] OpenRouter API key (openrouter.ai/keys → xoá cái cũ)

VNPay vẫn dùng sandbox nên cặp `TMN_CODE`/`HASH_SECRET` giữ nguyên được. Chuyển sang merchant thật thì cấp mới cả hai.

---

## 0b. Đưa `deploy/`, `db/` và `src/test/` vào git

Ba thư mục này từng **chưa hề được commit** (`git ls-files deploy db src/test` trả về 0), nên `git clone` trên máy chủ sẽ không có gì trong đó. Kiểm lại trước khi làm tiếp:

```bash
git ls-files deploy db src/test | wc -l      # phải > 0
```

Hậu quả nếu quên, theo thứ tự mức độ:

- **`db/manual/*.sql` không tồn tại trên máy chủ.** Bước 4 không chạy được, và `SchemaGuard` với `SCHEMA_STRICT=true` sẽ chặn khởi động — nhưng chỉ sau khi bạn đã dựng xong cả máy chủ.
- **Chính runbook này cũng không có ở đó.**
- `src/test/` mất hai bài test canh đúng hai lỗi mà ứng dụng **nuốt vào log**: thiếu font in PDF (`PdfFontTest`) và template email hỏng (`MedicalRecordMailTemplateTest`). Không có chúng thì "không thấy lỗi" và "chạy đúng" trông giống hệt nhau.

Ảnh tin tức do `NewsFeedService` tải về (`uploads/*_news.*`) thì **không** commit — chúng tự sinh lại trên máy chủ.

---

## 1. Đóng gói

```bash
./mvnw clean package
```

Kết quả: `target/booking-healthy-0.0.1-SNAPSHOT.jar` — jar **béo**, chạy được bằng `java -jar`.

> Trước đợt này `pom.xml` thiếu `spring-boot-maven-plugin`, nên `mvn package` vẫn báo BUILD SUCCESS nhưng ra một jar mỏng không có `Main-Class`. Lỗi không bao giờ lộ trên máy dev vì ở đó luôn chạy `./mvnw spring-boot:run`.

**Kiểm chứng:**
```bash
unzip -p target/booking-healthy-*.jar META-INF/MANIFEST.MF | grep Main-Class
# phải in ra: Main-Class: org.springframework.boot.loader.launch.JarLauncher
```

---

## 2. Chuẩn bị máy chủ

```bash
sudo mkdir -p /var/lib/nnlhospital/uploads /var/lib/nnlhospital/private
sudo chown -R nnlhospital:nnlhospital /var/lib/nnlhospital
sudo chmod 750 /var/lib/nnlhospital/private     # CV ứng viên
```

**Chép 133 ảnh bác sĩ** từ thư mục `uploads/` của repo sang `/var/lib/nnlhospital/uploads/`. Chúng được git theo dõi nhưng **không** nằm trong jar — quên bước này là toàn bộ bác sĩ hiện ảnh vỡ.

```bash
rsync -av uploads/ /var/lib/nnlhospital/uploads/
sudo chown -R nnlhospital:nnlhospital /var/lib/nnlhospital/uploads
ls /var/lib/nnlhospital/uploads | wc -l      # phải ≥ 133
```

> Con số là **133** (`git ls-files uploads | wc -l`), không phải 142 — chênh lệch là ảnh tin tức `NewsFeedService` đã tải về trên máy dev, không được commit và không cần chép sang.
>
> Máy dev chạy Windows thì không có `rsync` sẵn; dùng `scp -r uploads ubuntu@<IP>:/tmp/uploads` rồi `sudo cp -a /tmp/uploads/. /var/lib/nnlhospital/uploads/`.

---

## 3. Cấu hình

```bash
cp deploy/env.example /etc/nnlhospital/.env
# điền hết các ô trống, đặc biệt SEED_ADMIN_PASSWORD và PAYMENT_WEBHOOK_SECRET
sudo chmod 600 /etc/nnlhospital/.env
```

**Kiểm chứng — không được sót biến nào:**
```bash
grep -o '\${[A-Z_]*' src/main/resources/application.properties | tr -d '${' | sort -u
```
So danh sách đó với `.env`.

### Hai luật về định dạng `.env` — không phải chuyện thẩm mỹ

Tệp này được nạp bởi `EnvironmentFile=` của systemd và bởi `.` của bash trong [backup.sh](backup.sh). Cả hai đều **không** phải shell đầy đủ:

1. **Không có bình luận cuối dòng.** systemd chỉ bỏ qua dòng *bắt đầu* bằng `#`. Viết `SCHEMA_STRICT=true   # ghi chú` là gán nguyên chuỗi `"true   # ghi chú"`, Spring ném lỗi ép kiểu boolean và app không khởi động — mà thông báo lỗi chỉ nói về boolean, không hề nhắc tới dấu `#`.
2. **Giá trị có dấu cách hoặc `& ? *` phải đặt trong nháy kép.** App password của Gmail có dấu cách; `NEWS_FETCH_CRON` có cả dấu cách lẫn `*` và `?`; `DB_URL` có `&` — và dấu `&` không đặt nháy sẽ khiến bash trong `backup.sh` cắt dòng làm đôi rồi chạy nền, tức mật khẩu DB biến mất mà không báo gì.

`env.example` đã tuân thủ cả hai; giữ nguyên khuôn đó khi sửa.

### Cài dịch vụ

```bash
sudo cp deploy/nnlhospital.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now nnlhospital
sudo journalctl -u nnlhospital -f
```

Unit đã có `Restart=always` (app chết hoặc máy reboot là tự lên lại) và `SuccessExitStatus=143` — thiếu dòng cuối thì mỗi lần `systemctl stop` đều bị ghi nhận là thất bại, vì JVM thoát với mã 143 khi nhận SIGTERM.

---

## 4. Lược đồ cơ sở dữ liệu

```bash
mysql -u root -p -e "CREATE DATABASE bookinghealthy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Boot **lần đầu** với `DDL_AUTO=update` **và `SCHEMA_STRICT=false`** để Hibernate dựng bảng và `DataInitializer` seed dữ liệu.

> `SCHEMA_STRICT=false` ở lần boot đầu là **bắt buộc**, không phải tuỳ chọn. Lúc đó DB còn rỗng nên cả bốn đối tượng `SchemaGuard` kiểm (`uk_bookings_slot`, `uk_posts_source_url`, `shedlock`, `SPRING_SESSION`) đều chưa tồn tại — để `true` là nó chặn khởi động đúng lúc cần Hibernate dựng bảng, và bước này không bao giờ chạy được.

Sau đó dừng app và chạy DDL tay:

```bash
mysql -u root -p bookinghealthy < db/manual/001_prod_hardening.sql
mysql -u root -p bookinghealthy < db/manual/002_spring_session.sql
```

Rồi đặt `DDL_AUTO=validate` và `SCHEMA_STRICT=true`, khởi động lại.

> `SchemaGuard` kiểm ba index/bảng này lúc khởi động. Với `SCHEMA_STRICT=true` thì thiếu là app **không lên** — cố ý: quên chạy SQL trông y hệt lúc mọi thứ bình thường, cho tới ngày hai bệnh nhân đặt trúng một khung giờ.

---

## 5. nginx + HTTPS

```bash
sudo cp deploy/nginx.conf.example /etc/nginx/sites-available/nnlhospital
# sửa server_name và đường dẫn chứng chỉ
sudo certbot --nginx -d nnlhospital.example.com
sudo nginx -t && sudo systemctl reload nginx
```

---

## 6. Đăng ký lại URL ở bên thứ ba

- [ ] Google Cloud Console → Credentials → Authorized redirect URIs: `https://<domain>/login/oauth2/code/google`
- [ ] Meta for Developers → Facebook Login → Valid OAuth Redirect URIs: `https://<domain>/login/oauth2/code/facebook`
- [ ] Casso/SePay → URL webhook: `https://<domain>/api/payment/webhook`, kèm header bí mật đúng bằng `PAYMENT_WEBHOOK_SECRET`
- [ ] VNPay merchant → Return URL: `https://<domain>/payment-return`

---

## 6b. Sao lưu & giám sát

```bash
sudo cp deploy/backup.sh /usr/local/bin/nnlhospital-backup
sudo chmod 700 /usr/local/bin/nnlhospital-backup
sudo /usr/local/bin/nnlhospital-backup          # chạy thử ngay, đừng đợi tới lúc cần
sudo crontab -e
# 15 3 * * * /usr/local/bin/nnlhospital-backup >> /var/log/nnlhospital-backup.log 2>&1
```

Script dump MySQL bằng `--single-transaction` (không khoá bảng, lễ tân vẫn đặt lịch được trong lúc chạy) và `--default-character-set=utf8mb4` — **thiếu tham số thứ hai là hỏng thầm**: tên bệnh nhân và chẩn đoán tiếng Việt bị mã hoá sai trong tệp dump, và chỉ lộ ra đúng lúc phục hồi, tức lúc không còn bản nào khác để đối chiếu.

**Phục hồi thử một lần vào database tạm ngay sau khi cài.** Bản sao lưu chưa từng phục hồi thử thì chưa phải bản sao lưu — bệnh án và đơn thuốc là dữ liệu không dựng lại được.

Giám sát: [UptimeRobot](https://uptimerobot.com) free, monitor HTTP tới `https://<domain>/actuator/health` mỗi 5 phút. Đó chính là lý do route này được đặt `permitAll` (xem bước 7).

---

## 7. Kiểm chứng sau khi chạy

### Bắt buộc
```bash
# Healthcheck không được redirect
curl -I https://<domain>/actuator/health          # 200, không phải 302

# Webhook phải từ chối khi không có bí mật
curl -X POST https://<domain>/api/payment/webhook \
     -H 'Content-Type: application/json' -d '{"description":"HEALCARE 1"}'
# → 401 UNAUTHORIZED

# Gửi lại đúng một giao dịch hai lần → lần hai KHÔNG được gửi email lần nữa
```

- [x] ~~Đăng nhập thường~~ — **đã xảy ra thật và đã sửa.** `CustomUserDetails` ôm entity `User`, nên bật `spring-session-jdbc` là **mọi lượt đăng nhập trả HTTP 500** (`NotSerializableException: com.bookinghealthy.model.User` khi ghi `SPRING_SESSION_ATTRIBUTES`). Cả hai principal nay chỉ giữ chuỗi thuần; đã kiểm chứng lại từ jar đóng gói với đủ 4 vai trò.
- [ ] **Đăng nhập Google và Facebook** — vẫn phải thử bằng trình duyệt thật. `CustomOAuth2User` dính đúng lỗi đó (nặng hơn: `OAuth2User` không kế thừa `Serializable` như `UserDetails`, nên lớp này trước giờ không tuần tự hoá được chút nào) và đã sửa cùng lúc, nhưng đường này không bắn được bằng `curl`.
- [ ] Sau khi đổi hình dạng principal: `DELETE FROM SPRING_SESSION_ATTRIBUTES; DELETE FROM SPRING_SESSION;` — phiên cũ giải tuần tự hoá vào lớp mới sẽ hỏng. Chỉ là bắt mọi người đăng nhập lại.
- [ ] Đặt lịch trọn vòng bằng VNPay sandbox. Sửa tay `vnp_SecureHash` trong URL trả về → phải bị **từ chối**.
- [ ] Đăng nhập tài khoản A, mở `/checkout-qr?id=<lịch của B>` → phải bị chặn.
- [ ] Tải ảnh đại diện, ảnh tin tức, ảnh dịch vụ, CV ứng viên — cả bốn phải hiện/tải được **từ jar đã đóng gói**.
- [ ] Bấm nút micro trong khung chat → phải hiện (chứng minh secure context đã đúng).

### Khi nào scale lên nhiều instance
- [ ] Bắn hai POST đặt cùng khung giờ vào hai instance → đúng **một** thành công, cái còn lại nhận câu tiếng Việt *"Khung giờ này đã có người giữ chỗ…"*, **không phải trang 500**.
- [ ] Chạy qua mốc 07:30 → bệnh nhân nhận **một** email nhắc lịch.
- [ ] Đăng nhập ở instance 1, gọi request tới instance 2 → vẫn còn đăng nhập.

---

## 7b. NÂNG CẤP một máy chủ ĐANG CHẠY (khác hẳn bước 1-7)

Bước 1-7 dựng máy mới. Việc thường làm hơn là đẩy commit mới lên máy đã chạy, và nó có một cái bẫy
mà bước 1-7 không hề nhắc tới.

**Kiểm TRƯỚC KHI đóng gói — commit này có thêm/sửa `@Entity` nào không:**

```bash
git diff --stat <commit_dang_chay>..<commit_moi> -- '*/model/*.java'
```

Có dòng nào hiện ra thì **phải viết `db/manual/00N_*.sql` và chạy nó TRƯỚC khi khởi động jar mới**.
Production chạy `DDL_AUTO=validate` (bước 4 đã bảo lật sang như vậy), mà `validate` **không tạo bảng** —
nó chỉ đối chiếu. Thiếu bảng là Hibernate ném `SchemaManagementException: missing table [x]`, ứng dụng
không lên, và `Restart=always` biến thành crash-loop trả **502** cho mọi khách. Đã xảy ra thật ngày
2026-08-21. Cách lấy DDL đúng (đừng gõ tay — `validate` so cả kiểu cột): `SHOW CREATE TABLE` trên
database dev, vì bảng ở đó do chính Hibernate sinh ra. Chi tiết ở
[.claude/rules/environment-setup.md](../.claude/rules/environment-setup.md).

**Thứ tự đúng, hạ thời gian chết xuống còn đúng lần khởi động lại:**

```bash
# 1. Đóng gói từ commit SẠCH, không phải từ thư mục làm việc còn dở
git worktree add --detach /tmp/build <commit_moi> && cd /tmp/build
./mvnw clean package                       # 5/5 test phải xanh
unzip -p target/booking-healthy-*.jar META-INF/MANIFEST.MF | grep Main-Class

# 2. Tải lên chỗ tạm TRƯỚC (chưa dừng gì cả), rồi đối chiếu checksum
scp target/booking-healthy-*.jar ubuntu@<IP>:/tmp/app-new.jar
sha256sum target/booking-healthy-*.jar     # phải khớp sha256sum /tmp/app-new.jar

# 3. Sao lưu DB + tệp, rồi chạy migration khi app CŨ vẫn đang phục vụ
sudo /usr/local/bin/nnlhospital-backup
sudo mysql bookinghealthy < /tmp/00N_....sql

# 4. Mới tới lúc đổi jar
sudo cp -a /opt/nnlhospital/app.jar /opt/nnlhospital/app.jar.bak-$(date +%Y%m%d-%H%M%S)
sudo systemctl stop nnlhospital
sudo cp /tmp/app-new.jar /opt/nnlhospital/app.jar
sudo chown nnlhospital:nnlhospital /opt/nnlhospital/app.jar && sudo chmod 750 /opt/nnlhospital/app.jar
sudo systemctl start nnlhospital

# 5. Kiểm chứng
curl -s localhost:8090/actuator/health                     # {"status":"UP"}
sudo journalctl -u nnlhospital -n 50 --no-pager | grep -E 'Started|SchemaGuard|ERROR'
```

> **Đóng gói bằng `git worktree` chứ không build thẳng trong thư mục làm việc.** Hai lý do, cả hai đều
> có thật: thư mục làm việc thường còn thay đổi chưa commit (khoá bí mật dev, tài liệu) sẽ bị nướng vào
> jar; và nếu `spring-boot:run` đang chạy thì `mvn package` ghi đè `target/classes` ngay dưới chân nó —
> xem [build-and-run.md](../.claude/rules/build-and-run.md).

**Quay lui:** `sudo systemctl stop nnlhospital && sudo cp /opt/nnlhospital/app.jar.bak-<stamp>
/opt/nnlhospital/app.jar && sudo systemctl start nnlhospital`. Lưu ý bản quay lui **không** gỡ bảng
mới — không cần gỡ, vì `validate` bỏ qua bảng thừa không được entity nào ánh xạ.

---

## 8. Việc còn nợ (chưa làm trong đợt này)

- **CSRF vẫn đang tắt toàn cục** (`SecurityConfig`). `SameSite=Lax` đã chặn phần lớn đường khai thác thực tế, nhưng đây phải là việc **đầu tiên** sau khi deploy xong.

  Phạm vi đã đo để khỏi phải đoán: **51 chỗ `method="post"` nằm trong 31 template** và **12 lời gọi `fetch` POST** trong JS. Cách làm: bật CSRF, để Thymeleaf tự chèn hidden input cho các form `th:action`, còn `fetch` thì đọc token từ một thẻ `<meta>` đặt trong fragment header dùng chung. **Ngoại lệ bắt buộc giữ:** `/api/payment/webhook` — Casso/SePay gọi server-to-server nên không có phiên, và nó đã tự xác thực bằng bí mật trong header.
- Còn ~96 chỗ `System.out.println` / `printStackTrace` chưa chuyển sang SLF4J (đợt này chỉ đổi các lớp thanh toán và job).
- Soft-lock khung giờ của trợ lý AI vẫn nằm trong bộ nhớ từng tiến trình — chạy nhiều instance thì tính năng này suy giảm (gợi ý kém chính xác hơn) chứ không sai, vì chốt chặn thật là unique index ở DB.
