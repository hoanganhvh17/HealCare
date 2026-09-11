-- =====================================================================
--  005 — Lưu kết quả dự đoán nguy cơ bệnh của bác sĩ
-- =====================================================================
-- CHẠY BẰNG TAY, MỘT LẦN, TRƯỚC khi khởi động bản jar có tính năng này:
--     mysql -u <user> -p bookinghealthy < db/manual/005_risk_assessments.sql
--
-- VÌ SAO PHẢI CHẠY TAY:
-- Sau lần boot đầu, production chạy `DDL_AUTO=validate` (xem deploy/README.md
-- bước 4). `validate` KHÔNG tạo bảng — nó chỉ đối chiếu. Nên entity mới
-- `RiskAssessment` sẽ làm Hibernate ném
-- `SchemaManagementException: missing table [risk_assessments]`, context bị
-- huỷ, và `Restart=always` của systemd biến nó thành crash-loop trả 502 cho
-- mọi khách. Đúng thứ đã xảy ra thật ngày 2026-08-21 với commit e6e4ba1.
--
-- DDL dưới đây lấy nguyên văn từ `SHOW CREATE TABLE` trên database dev — tức
-- là do CHÍNH Hibernate sinh ra — nên chắc chắn qua được `validate`. Gõ tay
-- một bản đoán thì sẽ trượt đúng như cũ, vì `validate` so cả tên lẫn kiểu cột.
-- Bỏ `AUTO_INCREMENT=` và `COLLATE=` để bảng thừa kế mặc định của database
-- (utf8mb4 / utf8mb4_unicode_ci), khớp với mọi bảng khác trên production.
--
-- Nhớ đăng ký bảng này trong `config/SchemaGuard` để lần deploy sau nó được
-- kiểm ngay lúc boot thay vì đợi bác sĩ bấm nút mới lộ ra.
-- =====================================================================

-- Mọi cột đều NULL được, cố ý:
--   * `ddl-auto=update` thêm một cột NOT NULL không có DEFAULT là làm hỏng MỌI
--     INSERT về sau nếu field bị gỡ đi (xem .claude/rules/environment-setup.md).
--   * Bốn cột xác suất còn phải NULL được vì một target có thể không nạp được
--     model — khi đó phải để trống chứ tuyệt đối không ghi 0, vì 0 ở đây đọc
--     thành "nguy cơ bằng không".
--   * `patient_id` NULL được vì màn hình này dùng cho cả người chưa có tài
--     khoản trong hệ thống.
CREATE TABLE IF NOT EXISTS `risk_assessments` (
  `id`                        bigint       NOT NULL AUTO_INCREMENT,

  -- Bác sĩ đã chạy. Là chủ sở hữu bản ghi: mọi phép kiểm quyền xem dựa vào cột này.
  `doctor_id`                 bigint       DEFAULT NULL,
  `patient_id`                bigint       DEFAULT NULL,
  `patient_label`             varchar(255) DEFAULT NULL,

  -- --- Chỉ số thô bác sĩ nhập (đơn vị đã quy đổi về chuẩn của model) ---
  `age`                       int          DEFAULT NULL,
  -- Mã giới tính NHANES: 1 = nam, 2 = nữ.
  `sex_code`                  int          DEFAULT NULL,
  `height_cm`                 double       DEFAULT NULL,
  `weight_kg`                 double       DEFAULT NULL,
  `bmi`                       double       DEFAULT NULL,
  `waist_cm`                  double       DEFAULT NULL,
  `systolic_bp`               double       DEFAULT NULL,
  `diastolic_bp`              double       DEFAULT NULL,
  `pulse`                     double       DEFAULT NULL,
  `hba1c`                     double       DEFAULT NULL,
  -- mg/dL, đã quy đổi nếu bác sĩ nhập theo mmol/L.
  `total_cholesterol`         double       DEFAULT NULL,
  `hdl`                       double       DEFAULT NULL,
  -- mg/L, giá trị thô trước log1p.
  `hs_crp`                    double       DEFAULT NULL,

  -- --- Kết quả ---
  `diabetes_probability`      double       DEFAULT NULL,
  `diabetes_positive`         bit(1)       DEFAULT NULL,
  `hypertension_probability`  double       DEFAULT NULL,
  `hypertension_positive`     bit(1)       DEFAULT NULL,
  `heart_disease_probability` double       DEFAULT NULL,
  `heart_disease_positive`    bit(1)       DEFAULT NULL,
  `stroke_probability`        double       DEFAULT NULL,
  `stroke_positive`           bit(1)       DEFAULT NULL,

  -- Xác suất cũ chỉ có nghĩa khi biết model nào sinh ra nó.
  `model_version`             varchar(100) DEFAULT NULL,
  `missing_count`             int          DEFAULT NULL,
  `created_at`                datetime(6)  DEFAULT NULL,

  PRIMARY KEY (`id`),

  -- Composite chứ không chỉ (doctor_id): phục vụ luôn
  -- findTop20ByDoctorIdOrderByCreatedAtDesc, đồng thời vẫn đỡ được khoá ngoại.
  KEY `idx_risk_doctor_created` (`doctor_id`, `created_at`),
  KEY `idx_risk_patient` (`patient_id`),

  CONSTRAINT `fk_risk_doctor`  FOREIGN KEY (`doctor_id`)  REFERENCES `doctors` (`id`),
  CONSTRAINT `fk_risk_patient` FOREIGN KEY (`patient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
