-- =====================================================================
--  DDL CHẠY TAY — MỘT LẦN, THEO ĐÚNG THỨ TỰ DƯỚI ĐÂY
-- =====================================================================
-- Dự án KHÔNG có Flyway/Liquibase; lược đồ do Hibernate `ddl-auto=update` dựng.
-- Tệp này chứa những thứ Hibernate KHÔNG diễn đạt được bằng annotation.
--
-- CỐ Ý không đặt ở db/migration (để sau này có dùng Flyway thì nó không nhầm đây là
-- migration của mình) và cố ý không đặt tên schema.sql/data.sql (Spring sẽ tự chạy).
--
-- CÁCH CHẠY:
--     mysql -u <user> -p bookinghealthy < db/manual/001_prod_hardening.sql
--
-- App có `SchemaGuard` kiểm lúc khởi động: thiếu thứ gì trong đây thì log ERROR thật to,
-- và nếu đặt SCHEMA_STRICT=true (khuyến nghị cho production) thì chặn luôn việc khởi động.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. CHỐNG ĐẶT TRÙNG KHUNG GIỜ Ở TẦNG CƠ SỞ DỮ LIỆU
-- ---------------------------------------------------------------------
-- Khoá `slotLocks` trong BookingServiceImpl là ReentrantLock, tức là chỉ có tác dụng
-- TRONG MỘT TIẾN TRÌNH. Chạy hai instance là hai bệnh nhân đặt trúng cùng một khung giờ.
--
-- KHÔNG dùng UNIQUE(doctor_id, appointment_date, appointment_time) trần được, dù đó là
-- cách hiển nhiên và đúng khuôn với MedicalRecord/Review/AiChatSession. Lý do: bản ghi
-- ĐÃ HUỶ KHÔNG bị xoá — mọi đường huỷ đều chỉ lật `status`, và BookingCleanupTask còn tự
-- huỷ mọi lịch chưa thanh toán sau 3 phút. Nên một khung giờ hoàn toàn có thể có nhiều
-- dòng hợp lệ, và ràng buộc trần sẽ chặn cả những lần đặt lại chính đáng.
--
-- Cách dùng ở đây: cột sinh (generated column) mang giá trị NULL khi lịch đã huỷ.
-- MySQL cho phép NHIỀU giá trị NULL trong một unique index, nên các dòng đã huỷ không
-- bao giờ va nhau, còn các dòng còn hiệu lực thì bị ép duy nhất.

-- 1a. BẮT BUỘC chạy trước trên DB đã có dữ liệu (DB production rỗng thì bỏ qua).
--     Còn dòng trùng thì lệnh ALTER bên dưới sẽ THẤT BẠI.
--     Cách xử lý: giữ dòng có created_at sớm nhất, huỷ các dòng còn lại.
SELECT doctor_id, appointment_date, appointment_time, COUNT(*) AS so_dong
FROM bookings
WHERE status <> 'CANCELED'
GROUP BY doctor_id, appointment_date, appointment_time
HAVING so_dong > 1;

-- 1b. Cột sinh + unique index.
--     KHÔNG map cột này vào entity Booking. Hibernate chỉ THÊM chứ không sửa/xoá cột lạ,
--     nên `ddl-auto=update` để yên; và INSERT của Hibernate liệt kê cột tường minh nên
--     không bao giờ đụng vào cột generated (MySQL từ chối mọi lệnh ghi thẳng vào nó).
--     Đặt tên `slot_uk` chứ không phải một cái tên trông giống trường Java, để không ai
--     vô tình thêm field `slotUk` vào entity — làm thế là Hibernate map nó và MỌI INSERT hỏng.
ALTER TABLE bookings
  ADD COLUMN slot_uk VARCHAR(64)
  GENERATED ALWAYS AS (
    CASE WHEN status = 'CANCELED' THEN NULL
         ELSE CONCAT(doctor_id, '|', appointment_date, '|', appointment_time)
    END
  ) STORED;

CREATE UNIQUE INDEX uk_bookings_slot ON bookings (slot_uk);

-- LƯU Ý cho tương lai: nếu đổi tên hằng CANCELED trong enum BookingStatus thì biểu thức
-- trên IM LẶNG đổi nghĩa (lịch đã huỷ sẽ bắt đầu va nhau). Đổi enum là phải sửa cả đây.


-- ---------------------------------------------------------------------
-- 2. CHỐNG LƯU TRÙNG BÀI TIN TỰ THU THẬP
-- ---------------------------------------------------------------------
-- MedicalNewsTask chống trùng bằng existsBySourceUrl — kiểm-rồi-mới-ghi, nên hai instance
-- chạy cùng lúc đều thấy "chưa có" và cùng lưu. Mỗi bài trùng còn tốn thêm một lượt gọi AI.
-- Post.sourceUrl là varchar(500) và nullable: bài admin tự viết không có nguồn, và MySQL
-- cho phép nhiều NULL trong unique index nên chúng không va nhau.
CREATE UNIQUE INDEX uk_posts_source_url ON posts (source_url);


-- ---------------------------------------------------------------------
-- 3. KHOÁ PHÂN TÁN CHO CÁC JOB ĐỊNH KỲ (ShedLock)
-- ---------------------------------------------------------------------
-- Không có bảng này thì mọi cron chạy trên MỌI instance: bệnh nhân nhận email nhắc lịch
-- nhân đôi, MedicalNewsTask lấy trùng bài, ClinicRegistrationTask xếp lịch chồng nhau.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);


-- ---------------------------------------------------------------------
-- 4. KIỂM TRA LẠI SAU KHI CHẠY XONG
-- ---------------------------------------------------------------------
-- Cả ba dòng phải trả về kết quả:
SELECT INDEX_NAME FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookings' AND INDEX_NAME = 'uk_bookings_slot';
SELECT INDEX_NAME FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'posts' AND INDEX_NAME = 'uk_posts_source_url';
SELECT TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shedlock';
