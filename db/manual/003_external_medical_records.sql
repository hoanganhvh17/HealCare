-- =====================================================================
--  003 — Hồ sơ bệnh án ngoại viện + bộ đếm hạn mức đọc ảnh bằng AI
-- =====================================================================
-- CHẠY BẰNG TAY, MỘT LẦN, trước khi khởi động bản có commit e6e4ba1:
--     mysql -u <user> -p bookinghealthy < db/manual/003_external_medical_records.sql
--
-- VÌ SAO PHẢI CHẠY TAY:
-- Sau lần boot đầu, production chạy `DDL_AUTO=validate` (xem deploy/README.md
-- bước 4). `validate` KHÔNG tạo bảng — nó chỉ đối chiếu. Nên hai entity mới của
-- commit này (`ExternalMedicalRecord`, `AiImageUsage`) làm Hibernate ném
-- `SchemaManagementException: missing table [ai_image_usage]` và ứng dụng
-- KHÔNG khởi động được. Đã xảy ra thật ngày 2026-08-21.
--
-- DDL dưới đây được lấy nguyên văn từ `SHOW CREATE TABLE` trên database dev —
-- tức là do CHÍNH Hibernate sinh ra — nên chắc chắn qua được `validate`.
-- Bỏ `AUTO_INCREMENT=` và `COLLATE=` để bảng thừa kế mặc định của database
-- (utf8mb4 / utf8mb4_unicode_ci), khớp với mọi bảng khác trên production.
-- =====================================================================

-- --- Hồ sơ bệnh án bệnh nhân mang từ nơi khác tới ---------------------
-- Khoá vào `users`, KHÔNG khoá vào `medical_records`: hồ sơ cũ tồn tại
-- TRƯỚC mọi lịch hẹn ở viện này (xem .claude/rules/medical-records.md).
CREATE TABLE IF NOT EXISTS `external_medical_records` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `user_id`            bigint       NOT NULL,
  `title`              varchar(200) NOT NULL,
  `stored_file_name`   varchar(255) NOT NULL,
  `original_file_name` varchar(255)     DEFAULT NULL,
  `content_type`       varchar(100)     DEFAULT NULL,
  `file_size`          bigint           DEFAULT NULL,
  `doc_type`           varchar(20)      DEFAULT NULL,
  `patient_note`       text,
  `ai_status`          varchar(20)      DEFAULT NULL,
  `ai_summary`         text,
  `ai_department_id`   bigint           DEFAULT NULL,
  `analyzed_at`        datetime(6)      DEFAULT NULL,
  `created_at`         datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_external_med_rec_user` (`user_id`),
  CONSTRAINT `fk_external_med_rec_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

-- --- Bộ đếm hạn mức đọc ảnh bằng AI ----------------------------------
-- Một dòng cho mỗi (người dùng, ngày). Là BẢNG chứ không phải map trong bộ
-- nhớ: hạn mức chi phí mà reset mỗi lần restart thì chỉ cần restart là lách
-- được, và nó cũng sai khi chạy nhiều instance.
CREATE TABLE IF NOT EXISTS `ai_image_usage` (
  `id`         bigint NOT NULL AUTO_INCREMENT,
  `user_id`    bigint NOT NULL,
  `usage_date` date   NOT NULL,
  `count`      int    NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_image_usage_user_day` (`user_id`,`usage_date`),
  CONSTRAINT `fk_ai_image_usage_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;
