-- =====================================================================
--  004 — Thu tiền mặt tại quầy + trạng thái "bệnh nhân không đến khám"
-- =====================================================================
-- CHẠY BẰNG TAY, MỘT LẦN, TRƯỚC khi khởi động bản jar có tính năng này:
--     mysql -u <user> -p bookinghealthy < db/manual/004_cash_collection_and_no_show.sql
--
-- VÌ SAO PHẢI CHẠY TAY:
-- Sau lần boot đầu, production chạy `DDL_AUTO=validate` (deploy/README.md bước 4).
-- `validate` KHÔNG tạo gì — nó chỉ đối chiếu. Ba cột mới bên dưới sẽ làm Hibernate ném
-- SchemaManagementException và ứng dụng KHÔNG khởi động được.
--
-- CÂU ALTER ENUM Ở MỤC 1 CÒN CẦN CẢ TRÊN MÁY DEV, và đó là phần dễ bỏ sót nhất:
-- `ddl-auto=update` THÊM được cột mới nhưng KHÔNG BAO GIỜ viết lại danh sách giá trị của
-- một cột ENUM đã tồn tại. Đã kiểm chứng trực tiếp khi làm tính năng này — sau khi khởi
-- động lại với hằng số NO_SHOW đã có trong Java, cột vẫn nguyên
-- enum('PENDING','CONFIRMED','CANCELED','COMPLETED') và lệnh ghi trả về:
--     ERROR 1265 (01000): Data truncated for column 'status'
-- Ở tầng ứng dụng nó hiện ra thành HTTP 500 không hề nhắc tới ENUM.
--
-- TỆP NÀY CHẠY LẠI ĐƯỢC NHIỀU LẦN (idempotent). Đó không phải sự cầu kỳ: trên máy dev
-- `ddl-auto=update` đã tự tạo sẵn ba cột, nên một câu ADD COLUMN trần sẽ báo lỗi
-- "Duplicate column name" và người chạy sẽ tưởng migration hỏng. MySQL 8 không có
-- `ADD COLUMN IF NOT EXISTS`, nên phải hỏi INFORMATION_SCHEMA rồi PREPARE.
--
-- Kiểu cột lấy nguyên văn từ `SHOW CREATE TABLE bookings` trên database dev — tức do
-- CHÍNH Hibernate sinh ra — nên chắc chắn qua được `validate`. Riêng tên khoá ngoại đặt
-- lại cho đọc được (bản Hibernate tự sinh là `FKgt0d9hsxur82wb1ghh8xcf8hl`); `validate`
-- không đối chiếu tên ràng buộc nên đổi tên là an toàn.
-- =====================================================================

-- --- 1. Mở rộng danh sách giá trị của cột trạng thái ------------------
-- NO_SHOW nằm CUỐI, khớp đúng thứ tự khai báo trong
-- com.bookinghealthy.model.BookingStatus. Giữ nguyên thứ tự bốn giá trị cũ là điều kiện
-- để dữ liệu hiện có không bị diễn giải sai. Chạy lại câu này là no-op.
ALTER TABLE `bookings`
  MODIFY COLUMN `status`
  ENUM('PENDING','CONFIRMED','CANCELED','COMPLETED','NO_SHOW') NOT NULL;

-- --- 2. Ba cột ghi nhận thu ngân --------------------------------------
-- TẤT CẢ đều nullable: thêm một cột NOT NULL không DEFAULT vào bảng đã có dữ liệu sẽ làm
-- hỏng MỌI lệnh INSERT về sau (xem environment-setup.md).

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bookings` ADD COLUMN `paid_at` datetime(6) DEFAULT NULL',
    'DO 0')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookings' AND COLUMN_NAME = 'paid_at');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bookings` ADD COLUMN `collected_by_id` bigint DEFAULT NULL',
    'DO 0')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookings' AND COLUMN_NAME = 'collected_by_id');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bookings` ADD COLUMN `no_show_marked_at` datetime(6) DEFAULT NULL',
    'DO 0')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookings' AND COLUMN_NAME = 'no_show_marked_at');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- --- 3. Khoá ngoại cho người thu tiền ---------------------------------
-- Hibernate đã tự tạo một khoá ngoại tên ngẫu nhiên trên máy dev; chỉ thêm bản đặt tên
-- tử tế khi CHƯA có ràng buộc nào trên cột này, tránh dựng hai khoá trùng nhau.
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bookings` ADD CONSTRAINT `fk_bookings_collected_by` FOREIGN KEY (`collected_by_id`) REFERENCES `users` (`id`)',
    'DO 0')
  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookings'
    AND COLUMN_NAME = 'collected_by_id' AND REFERENCED_TABLE_NAME IS NOT NULL);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
