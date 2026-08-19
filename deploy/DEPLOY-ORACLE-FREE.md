# Deploy NNL Hospital lên Oracle Cloud Always Free

Bản cụ thể-theo-nhà-cung-cấp của [README.md](README.md). Những bước không phụ thuộc nhà cung cấp (xoay bí mật, chạy `db/manual/*.sql`, checklist kiểm chứng) **không chép lại ở đây** — tệp này trỏ ngược về README.

**Đích đến:** app chạy 24/7 trên HTTPS với domain thật, MySQL + nginx + certbot cùng nằm trên một máy ảo miễn phí vĩnh viễn.

---

## Vì sao chọn Oracle chứ không phải Render / Railway / Fly

Ứng dụng này có ba yêu cầu mà mọi PaaS free đều không đáp ứng được:

| Yêu cầu | Vì sao bắt buộc |
|---|---|
| **Luôn chạy** | 8 job `@Scheduled`. `BookingCleanupTask` huỷ lịch chưa thanh toán mỗi 3 phút; `AppointmentReminderTask` chạy 07:30. Render free **ngủ sau 15 phút** không ai truy cập → cron không chạy, và lịch chưa thanh toán giữ chỗ vô thời hạn. |
| **Ổ đĩa bền** | 133 ảnh bác sĩ + ảnh tin tức tự tải về + CV ứng viên. Render free **không gắn được persistent disk** → mất sạch sau mỗi lần deploy. |
| **MySQL 8 thật** | `db/manual/001` dùng **cột sinh (generated column)** và unique index trên nó. Free tier của phần lớn nhà cung cấp chỉ có PostgreSQL. |

Oracle Always Free cho một máy ảo đầy đủ, nên cả ba đều là chuyện đương nhiên.

---

## Bước 1 — Xoay 5 bí mật (làm TRƯỚC, trên máy dev)

Xem [README.md § 0](README.md). Bắt buộc vì hệ thống sẽ có người dùng thật: cả 5 bí mật đọc lại được bằng `git log -p src/main/resources/application.properties`, nên chuyển sang biến môi trường **không** thu hồi được chúng.

> Không cần `git filter-repo` để xoá khỏi lịch sử. Xoay khoá là đã đủ — khoá cũ trở thành vô hại. Viết lại lịch sử git là rủi ro riêng, tách ra làm sau nếu muốn.

---

## Bước 2 — Tạo tài khoản và máy ảo

1. Đăng ký tại [cloud.oracle.com](https://cloud.oracle.com) — cần thẻ tín dụng/ghi nợ để **xác minh danh tính**, không bị trừ tiền.
2. **Home Region chọn `Singapore (ap-singapore-1)` và cân nhắc kỹ: KHÔNG đổi được về sau.** Singapore gần Việt Nam nhất (độ trễ ~30ms) và ít báo hết chỗ hơn các region Mỹ.
3. Tạo instance:
   - Image: **Ubuntu 24.04 (aarch64)**
   - Shape: **`VM.Standard.A1.Flex`** — **2 OCPU / 12 GB RAM**
   - Boot volume: 50 GB là đủ (hạn mức free là 200 GB tổng)
   - Lưu private key SSH lại ngay, Oracle không cho tải lần hai

> **`Out of host capacity`** là lỗi rất hay gặp với shape ARM — không phải bạn làm sai. Thử lại vào giờ thấp điểm, đổi Availability Domain (AD-1 → AD-2 → AD-3), hoặc tạm dùng shape `VM.Standard.E2.1.Micro` rồi đổi sau. **Micro chỉ có 1 GB RAM — không đủ cho cả MySQL lẫn Spring Boot**, chỉ dùng làm bước đệm.

> **Instance Always Free có thể bị thu hồi nếu để không (idle) quá lâu.** Cách miễn nhiễm: nâng tài khoản lên **Pay As You Go** nhưng vẫn chỉ dùng đúng tài nguyên Always Free — hoá đơn vẫn 0đ, và chính sách thu hồi không còn áp dụng.

---

## Bước 3 — Mở cổng (HAI lớp — bẫy kinh điển của Oracle)

Oracle chặn ở **hai nơi độc lập**. Mở một nơi thôi thì trình duyệt quay vô tận còn log nginx **trống trơn**, nên rất khó đoán ra.

**Lớp 1 — Security List của VCN** (trên web console):
`Networking → Virtual Cloud Networks → <VCN> → Subnets → <subnet> → Security Lists → Default → Add Ingress Rules`

| Source CIDR | Protocol | Dest. Port |
|---|---|---|
| `0.0.0.0/0` | TCP | 80 |
| `0.0.0.0/0` | TCP | 443 |

**Lớp 2 — iptables bên trong Ubuntu.** Image của Oracle cài sẵn một luật `REJECT` chặn hết trừ SSH:

```bash
# Chèn lên ĐẦU chuỗi để chắc chắn đứng trước luật REJECT mặc định
sudo iptables -I INPUT -p tcp --dport 80  -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT

# KHÔNG có dòng này thì luật mất sạch sau lần reboot đầu tiên
sudo netfilter-persistent save
```

**Kiểm chứng:** `sudo iptables -L INPUT -n --line-numbers` — hai luật ACCEPT phải nằm **trên** dòng REJECT.

> **Oracle chặn vĩnh viễn cổng 25 chiều ra** và không mở theo yêu cầu. Không sao: ứng dụng gửi mail qua **cổng 587** (STARTTLS), cổng này thông bình thường.

---

## Bước 4 — Cài nền

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-21-jre-headless mysql-server nginx certbot python3-certbot-nginx

# 2GB swap — rẻ và cứu được những lúc MedicalNewsTask gọi AI cùng lúc với backup
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

> Dùng `openjdk-21-jre-headless` vì jar được build sẵn ở máy dev. Nếu định build ngay trên máy ảo thì cài `openjdk-21-jdk-headless` — nhưng 2 core ARM biên dịch chậm hơn nhiều.

Tạo database và user:

```bash
sudo mysql_secure_installation      # đặt mật khẩu root, xoá anonymous user, xoá test db
sudo mysql
```
```sql
CREATE DATABASE bookinghealthy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'bookinghealthy'@'localhost' IDENTIFIED BY '<mật khẩu vừa xoay ở bước 1>';
GRANT ALL PRIVILEGES ON bookinghealthy.* TO 'bookinghealthy'@'localhost';
FLUSH PRIVILEGES;
```

> `GRANT ALL` trên đúng một schema, không phải toàn server: `ddl-auto` cần quyền `ALTER`, còn `db/manual/001` cần `INDEX` và `ALTER`.

Tạo user hệ thống chạy app (không login được):

```bash
sudo useradd --system --home /var/lib/nnlhospital --shell /usr/sbin/nologin nnlhospital
sudo mkdir -p /opt/nnlhospital /etc/nnlhospital
```

---

## Bước 5 — Tên miền + DNS

1. Mua **`.io.vn`** hoặc **`.id.vn`** (~30–50k VND/năm) ở nhà đăng ký trong nước. Đây là hạng tên miền cá nhân của VNNIC nên **cần CCCD để xác minh** — thường xong trong ngày. Không muốn thủ tục thì dùng `.com` (~250k/năm) hoặc subdomain DuckDNS miễn phí.
2. Tạo tài khoản [Cloudflare](https://dash.cloudflare.com) (Free), thêm domain, đổi nameserver ở nhà đăng ký sang cặp NS mà Cloudflare cấp.
3. Thêm bản ghi **A** trỏ về **Public IP** của máy ảo.
4. **Để mây màu xám (DNS only) cho tới khi certbot xong.** Bật proxy (mây cam) ngay sẽ khiến Let's Encrypt xác thực HTTP-01 với chính Cloudflare chứ không phải máy chủ của bạn.

**Kiểm chứng:** `dig +short nnlhospital.io.vn` phải trả đúng IP máy ảo trước khi sang bước 8.

---

## Bước 6 — Build và đưa lên

Trên **máy dev** (Windows — dùng Git Bash hoặc PowerShell, `scp` có sẵn trong Windows 10+):

```bash
./mvnw clean package
unzip -p target/booking-healthy-*.jar META-INF/MANIFEST.MF | grep Main-Class
# BẮT BUỘC in ra: Main-Class: org.springframework.boot.loader.launch.JarLauncher
```

Thiếu dòng đó nghĩa là jar **mỏng** — `java -jar` sẽ chết với *"no main manifest attribute"*. Xem [README.md § 1](README.md).

```bash
scp -i <key> target/booking-healthy-0.0.1-SNAPSHOT.jar ubuntu@<IP>:/tmp/app.jar
scp -i <key> -r deploy db ubuntu@<IP>:/tmp/
```

Trên **máy ảo**:
```bash
sudo install -o nnlhospital -g nnlhospital -m 750 /tmp/app.jar /opt/nnlhospital/app.jar
```

---

## Bước 7 — Thư mục + 133 ảnh bác sĩ

Theo [README.md § 2](README.md):

```bash
sudo mkdir -p /var/lib/nnlhospital/uploads /var/lib/nnlhospital/private
sudo chown -R nnlhospital:nnlhospital /var/lib/nnlhospital
sudo chmod 750 /var/lib/nnlhospital/private     # CV ứng viên là dữ liệu cá nhân
```

Chép ảnh (từ máy dev — Windows không có `rsync` sẵn nên dùng `scp -r`):

```bash
scp -i <key> -r uploads ubuntu@<IP>:/tmp/uploads
```
```bash
sudo cp -a /tmp/uploads/. /var/lib/nnlhospital/uploads/
sudo chown -R nnlhospital:nnlhospital /var/lib/nnlhospital/uploads
ls /var/lib/nnlhospital/uploads | wc -l      # phải ≥ 133
```

**Bỏ qua bước này là toàn bộ bác sĩ hiện ảnh vỡ.** 133 ảnh đó được git theo dõi nhưng **không nằm trong jar**.

---

## Bước 8 — Cấu hình + boot lần đầu

```bash
sudo cp /tmp/deploy/env.example /etc/nnlhospital/.env
sudo chmod 600 /etc/nnlhospital/.env
sudo nano /etc/nnlhospital/.env
```

Điền hết. Lần boot này để **`DDL_AUTO=update`** và **`SCHEMA_STRICT=false`** (mặc định của `env.example` đã đúng — DB còn rỗng, để `true` là `SchemaGuard` chặn khởi động).

Bốn giá trị **bắt buộc điền**, không được để trống:

| Biến | Vì sao |
|---|---|
| `SEED_ADMIN_PASSWORD` | Để trống là app dùng `admin123` trên một site y tế công khai |
| `SEED_DOCTOR_PASSWORD` | Mật khẩu chung của ~132 bác sĩ, mặc định là `123456` |
| `PAYMENT_WEBHOOK_SECRET` | `openssl rand -hex 32`. Để rỗng = webhook từ chối mọi request (an toàn hơn mở toang) |
| `APP_BASE_URL` | VNPay dựng URL trả kết quả từ đây; sai là thanh toán không quay về được |

Cũng đặt `SEED_DEMO_ACCOUNTS=false` để không tạo `patient_tom` / `testsang31`.

**Kiểm chứng không sót biến nào:**
```bash
grep -o '\${[A-Z_]*' /tmp/deploy/../application.properties 2>/dev/null || \
grep -o '\${[A-Z_]*' src/main/resources/application.properties | tr -d '${' | sort -u
```
So danh sách đó với `.env`.

Cài dịch vụ và khởi động:

```bash
sudo cp /tmp/deploy/nnlhospital.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now nnlhospital
sudo journalctl -u nnlhospital -f
```

Chờ tới khi log in `Started BookingHealthyApplication`. Lần đầu lâu hơn vì `DataInitializer` seed 22 khoa + ~132 bác sĩ + lịch làm việc.

**Kiểm chứng:** `curl -I http://127.0.0.1:8090/actuator/health` → `200`.

---

## Bước 9 — Chạy DDL tay rồi siết lại

```bash
sudo systemctl stop nnlhospital

mysql -u bookinghealthy -p bookinghealthy < /tmp/db/manual/001_prod_hardening.sql
mysql -u bookinghealthy -p bookinghealthy < /tmp/db/manual/002_spring_session.sql
```

> Truy vấn 1a trong `001` tìm lịch đặt trùng. Trên DB production vừa seed thì **không trả dòng nào** — khác với DB dev đang mang sẵn cặp lịch #17/#18 trùng thật. Nếu có dòng trả về thì phải xử lý trước, `ALTER TABLE` bên dưới sẽ thất bại.

Sửa `/etc/nnlhospital/.env`:
```
DDL_AUTO=validate
SCHEMA_STRICT=true
```
```bash
sudo systemctl start nnlhospital
sudo journalctl -u nnlhospital | grep SchemaGuard
# phải thấy: [SchemaGuard] Lược đồ đầy đủ...
```

---

## Bước 10 — nginx + HTTPS

**Thứ tự ở đây quan trọng.** `nginx.conf.example` trỏ tới tệp chứng chỉ Let's Encrypt **chưa tồn tại**, nên cài nó trước rồi mới chạy certbot sẽ làm nginx không khởi động được.

**10a. Cấu hình HTTP tối thiểu để certbot có chỗ xác thực:**
```bash
sudo tee /etc/nginx/sites-available/nnlhospital >/dev/null <<'EOF'
server {
    listen 80;
    server_name nnlhospital.io.vn;
    location / { proxy_pass http://127.0.0.1:8090; }
}
EOF
sudo ln -s /etc/nginx/sites-available/nnlhospital /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

**10b. Xin chứng chỉ:**
```bash
sudo certbot --nginx -d nnlhospital.io.vn
```

**10c. Giờ mới cài cấu hình đầy đủ** (đã có chứng chỉ nên các đường dẫn `ssl_certificate` hợp lệ):
```bash
sudo cp /tmp/deploy/nginx.conf.example /etc/nginx/sites-available/nnlhospital
sudo nano /etc/nginx/sites-available/nnlhospital     # sửa server_name + đường dẫn chứng chỉ
sudo nginx -t && sudo systemctl reload nginx
```

Bốn thứ trong tệp mẫu đó **không phải tuỳ chọn** — `client_max_body_size 12m`, bộ header `X-Forwarded-*`, `location /uploads/`, và chuyển hướng 80→443. Đọc chú thích trong tệp trước khi cắt bớt.

**10d. Bật lại proxy Cloudflare** (mây cam) nếu muốn, và **đặt SSL/TLS mode thành `Full (strict)`**. Để `Flexible` sẽ gây vòng lặp chuyển hướng vô tận vì Cloudflare gọi về máy chủ bằng HTTP còn nginx lại đẩy ngược lên HTTPS.

**10e. Tự động gia hạn:** `sudo systemctl status certbot.timer` — đã bật sẵn khi cài gói. Thử: `sudo certbot renew --dry-run`.

---

## Bước 11 — Đăng ký lại URL ở bên thứ ba

Nguyên checklist [README.md § 6](README.md): Google redirect URI, Facebook redirect URI, webhook Casso/SePay (kèm header bí mật đúng bằng `PAYMENT_WEBHOOK_SECRET`), VNPay return URL.

---

## Bước 12 — Sao lưu + giám sát

```bash
sudo cp /tmp/deploy/backup.sh /usr/local/bin/nnlhospital-backup
sudo chmod 700 /usr/local/bin/nnlhospital-backup
sudo /usr/local/bin/nnlhospital-backup          # chạy thử ngay
sudo crontab -e
# 15 3 * * * /usr/local/bin/nnlhospital-backup >> /var/log/nnlhospital-backup.log 2>&1
```

**Phục hồi thử một lần vào database tạm ngay bây giờ.** Bản sao lưu chưa từng phục hồi thử thì chưa phải bản sao lưu — và bệnh án với đơn thuốc là dữ liệu không dựng lại được.

Giám sát: [UptimeRobot](https://uptimerobot.com) free, thêm monitor HTTP tới `https://<domain>/actuator/health`, chu kỳ 5 phút. Đây đúng là lý do route đó được đặt `permitAll`.

---

## Bước 13 — Kiểm chứng

Chạy nguyên checklist [README.md § 7](README.md). Ba mục dễ bỏ sót nhất:

- **Đăng nhập Google và Facebook bằng trình duyệt thật.** Mục duy nhất trong README còn bỏ ngỏ — `curl` không bắn được đường này.
- **Nút micro trong khung chat phải hiện.** Đó là bằng chứng secure context đã đúng; thiếu HTTPS thì toàn bộ tính năng giọng nói biến mất **không một dòng lỗi nào**.
- **Sửa tay `vnp_SecureHash` trong URL VNPay trả về → phải bị từ chối.**

---

## Sau khi lên sóng

Xem [README.md § 8](README.md). Việc **đầu tiên** là bật lại CSRF trong `SecurityConfig` — hiện đang tắt toàn cục, tức mọi POST đổi trạng thái (ví tiền, sửa lịch hẹn, toàn bộ CRUD của admin) đều CSRF-able. `SameSite=Lax` chặn phần lớn đường khai thác thực tế nhưng không phải bản vá.

## Cập nhật phiên bản mới về sau

```bash
./mvnw clean package                                    # máy dev
scp -i <key> target/booking-healthy-*.jar ubuntu@<IP>:/tmp/app.jar
```
```bash
sudo /usr/local/bin/nnlhospital-backup                  # sao lưu trước đã
sudo systemctl stop nnlhospital
sudo install -o nnlhospital -g nnlhospital -m 750 /tmp/app.jar /opt/nnlhospital/app.jar
sudo systemctl start nnlhospital
sudo journalctl -u nnlhospital -f
```

**Nếu bản mới có sửa entity:** `DDL_AUTO` đang là `validate` nên app sẽ **không khởi động** và báo đúng cột nào lệch. Đó là hành vi mong muốn. Xử lý: viết DDL vào `db/manual/003_*.sql`, chạy tay, rồi khởi động lại — chứ **đừng** quay về `update` trên production, vì nó cho phép Hibernate âm thầm ALTER bảng thật.

**Nếu bản mới đổi hình dạng `CustomUserDetails` / `CustomOAuth2User`:** phải xoá phiên cũ, nếu không mọi người đăng nhập sẵn sẽ gặp lỗi giải tuần tự hoá:
```sql
DELETE FROM SPRING_SESSION_ATTRIBUTES; DELETE FROM SPRING_SESSION;
```
