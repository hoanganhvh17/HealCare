-- 1. Tạm thời tắt kiểm tra khóa ngoại
SET FOREIGN_KEY_CHECKS = 0;

-- 2. Xóa sạch dữ liệu cũ
TRUNCATE TABLE roles;
TRUNCATE TABLE users;
TRUNCATE TABLE user_roles;
TRUNCATE TABLE departments;
TRUNCATE TABLE doctors;
TRUNCATE TABLE schedules;
TRUNCATE TABLE services;
TRUNCATE TABLE bookings;

-- 3. Bật lại kiểm tra khóa ngoại
SET FOREIGN_KEY_CHECKS = 1;

-- === CHỈ CHÈN SERVICES (ĐÃ VIỆT HÓA) ===
INSERT INTO services(id, name, description, price, category) VALUES
(1, 'Khám tư vấn tổng quát', 'Tư vấn sức khỏe tổng quát, kiểm tra định kỳ và sàng lọc bệnh.', 300000, 'Primary'),
(2, 'Dịch vụ Tiêm chủng', 'Tiêm chủng các loại vắc-xin phòng bệnh cho trẻ em và người lớn.', 500000, 'Primary'),
(3, 'Khám chuyên khoa Tim mạch', 'Khám, chẩn đoán và điều trị các bệnh lý liên quan đến tim mạch.', 600000, 'Specialty'),
(4, 'Khám chuyên khoa Thần kinh', 'Khám, chẩn đoán và điều trị các bệnh lý về thần kinh và đột quỵ.', 600000, 'Specialty'),
(5, 'Xét nghiệm', 'Thực hiện các xét nghiệm máu, nước tiểu... để chẩn đoán bệnh.', 250000, 'Diagnostics'),
(6, 'Chẩn đoán Hình ảnh', 'Chụp X-quang, MRI, CT scanner để phát hiện các bất thường trong cơ thể.', 1200000, 'Diagnostics'),
(7, 'Cấp cứu 24/7', 'Dịch vụ cấp cứu 24/7, xử lý các trường hợp khẩn cấp, tai nạn.', 2000000, 'Emergency');