package com.bookinghealthy.model;

public enum BookingStatus {
    PENDING,   // Chờ xác nhận
    CONFIRMED, // Bác sĩ đã xác nhận
    CANCELED,  // Bị hủy (bởi user hoặc doctor)
    COMPLETED, // Đã hoàn thành

    /**
     * Bệnh nhân KHÔNG đến khám dù lịch đã được xác nhận. Lễ tân đánh dấu sau khi giờ hẹn
     * đã trôi qua.
     *
     * <p>Cố ý là một trạng thái riêng chứ không gộp vào CANCELED: người bệnh không hề hủy,
     * chỗ khám đã bị chiếm và bác sĩ đã chờ. Gộp vào CANCELED là xóa mất chính thông tin
     * mà phòng khám cần để nhận ra bệnh nhân hay quên lịch.
     *
     * <p>ĐẶT Ở CUỐI ENUM là bắt buộc. Hibernate ánh xạ trường này thành cột MySQL
     * ENUM('PENDING','CONFIRMED','CANCELED','COMPLETED') native, và `ddl-auto=update` KHÔNG
     * bao giờ viết lại danh sách giá trị đó — nên hằng số mới phải đi kèm một câu
     * ALTER TABLE ... MODIFY COLUMN trong db/manual/. Thêm vào cuối thì thứ tự ordinal của
     * bốn giá trị cũ không đổi; chèn vào giữa hoặc đổi tên thì dữ liệu cũ hỏng.
     */
    NO_SHOW
}