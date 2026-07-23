package com.bookinghealthy.service;

import com.bookinghealthy.dto.BulkResultDTO;
import com.bookinghealthy.model.Booking;

import java.time.LocalDate;
import java.util.List;

/**
 * Nghiệp vụ quầy lễ tân: đổi lịch hàng loạt khi bác sĩ bận đột xuất
 * và điều phối hàng chờ khám khi bệnh nhân đến trễ.
 */
public interface ReceptionService {

    // Các mã buổi dùng cho bộ lọc (khớp với khung giờ chuẩn trong TimeSlotService)
    String SESSION_MORNING = "SANG";
    String SESSION_AFTERNOON = "CHIEU";
    String SESSION_EVENING = "TOI";
    String SESSION_ALL_DAY = "CA_NGAY";

    /**
     * Lọc các lịch còn hiệu lực (chưa hủy) của một bác sĩ theo ngày và buổi.
     * Đây là danh sách bệnh nhân cần liên hệ khi bác sĩ nghỉ đột xuất.
     */
    List<Booking> findBookingsForChange(Long doctorId, LocalDate date, String session);

    /**
     * Hủy hàng loạt: đổi trạng thái CANCELED, hoàn tiền vào ví nếu đã thanh toán,
     * và gửi email xin lỗi kèm lý do cho từng bệnh nhân.
     */
    BulkResultDTO bulkCancel(List<Long> bookingIds, String reason);

    /**
     * Chuyển hàng loạt sang một bác sĩ khác CÙNG CHUYÊN KHOA, giữ nguyên ngày/giờ/giá.
     * Mỗi bệnh nhân nhận thư xin lỗi kèm lý do và thông tin bác sĩ mới.
     * Lịch nào bị trùng slot của bác sĩ mới sẽ được ghi vào danh sách lỗi.
     */
    BulkResultDTO bulkTransfer(List<Long> bookingIds, Long newDoctorId, String reason);

    /**
     * Chặn luôn khung giờ của bác sĩ nghỉ để không có lịch mới rơi vào đó.
     *
     * @return null nếu chặn thành công, ngược lại là lý do không chặn được
     *         (theo đúng quy ước trả về của {@code DoctorBlockTimeService.blockTime}).
     */
    String blockSessionForDoctor(Long doctorId, LocalDate date, String session, String reason);

    /**
     * Hàng chờ khám trong ngày của một bác sĩ, đã sắp xếp:
     * người đúng giờ (theo giờ hẹn) trước, người bị đẩy xuống cuối sau.
     */
    List<Booking> getQueue(Long doctorId, LocalDate date);

    /** Đẩy một bệnh nhân đến trễ xuống cuối hàng chờ. */
    void pushToEndOfQueue(Long bookingId);

    /** Hoàn tác: trả bệnh nhân về đúng vị trí theo giờ hẹn. */
    void resetQueuePosition(Long bookingId);

    /**
     * Sắp xếp một danh sách lịch theo quy tắc hàng chờ.
     * Dùng chung cho cả trang lễ tân và trang khám bệnh của bác sĩ.
     */
    List<Booking> sortByQueue(List<Booking> bookings);
}
