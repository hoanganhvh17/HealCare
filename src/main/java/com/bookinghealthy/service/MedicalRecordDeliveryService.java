package com.bookinghealthy.service;

/**
 * Trao hồ sơ bệnh án + đơn thuốc điện tử cho bệnh nhân sau khi bác sĩ khám xong: gửi email
 * (kèm PDF đơn thuốc) VÀ đẩy một thông báo vào chuông.
 *
 * Gửi cả hai đường theo đúng quy ước của dự án: email là {@code @Async} và nuốt lỗi vào
 * {@code System.err}, nên tự nó không bao giờ là bằng chứng bệnh nhân đã biết.
 */
public interface MedicalRecordDeliveryService {

    /**
     * Gọi SAU KHI bệnh án đã lưu xong và transaction đã commit — thư báo "đã có hồ sơ bệnh án"
     * cho một bệnh án bị rollback là điều không sửa lại được.
     *
     * Không bao giờ ném ngoại lệ: mọi lỗi gửi đều chỉ nằm ở log, vì ca khám đã hoàn tất và
     * không có gì để bác sĩ làm lại.
     */
    void deliver(Long bookingId);
}
