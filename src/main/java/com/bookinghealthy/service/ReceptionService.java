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

    // Các mã buổi dùng cho bộ lọc (khớp với khung giờ chuẩn trong TimeSlotService).
    // Không có buổi tối: ngoài giờ hành chính là phiên trực, không nhận đặt khám.
    String SESSION_MORNING = "SANG";
    String SESSION_AFTERNOON = "CHIEU";
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

    /**
     * Lịch này có còn được điều phối hàng chờ (đẩy xuống cuối / hoàn tác) nữa không.
     * Nguồn sự thật DUY NHẤT cho cả giao diện (ẩn nút, in lý do) và server (chặn thật),
     * cùng khuôn với {@code BookingService.whyStaffCannotChange}.
     *
     * @return null nếu còn điều phối được, ngược lại là lý do bằng tiếng Việt.
     */
    String whyCannotReorderQueue(Booking booking);

    /* =========================== THU NGÂN TẠI QUẦY =========================== */

    /**
     * Lịch này có còn ghi nhận "đã thu tiền" được không.
     * {@code null} = thu được, ngược lại là lý do bằng tiếng Việt.
     *
     * <p>CỐ Ý KHÔNG dùng lại {@code BookingService.whyStaffCannotChange}: hàm đó từ chối mọi
     * lịch đã qua giờ hẹn, mà thu tiền tại quầy xảy ra đúng SAU giờ hẹn — bệnh nhân tới ca
     * 08:00 thì lễ tân thu lúc 08:05. Dùng lại là tính năng chết ngay khi vừa ship.
     */
    String whyCannotCollectPayment(Booking booking);

    /**
     * Ghi nhận đã thu tiền mặt tại quầy: {@code paymentStatus = PAID}, lưu thời điểm và người
     * thu. Lịch còn PENDING thì chuyển luôn sang CONFIRMED — trả tiền tại quầy chính là hành
     * động xác nhận.
     *
     * @param collector nhân viên đang đăng nhập, để biết ai cầm tiền
     */
    void collectCashPayment(Long bookingId, com.bookinghealthy.model.User collector);

    /**
     * Lịch này có đánh dấu "bệnh nhân không đến" được không.
     * {@code null} = đánh dấu được, ngược lại là lý do bằng tiếng Việt.
     */
    String whyCannotMarkNoShow(Booking booking);

    /**
     * Đánh dấu bệnh nhân không đến khám.
     *
     * <p>CỐ Ý KHÔNG tự động hoàn tiền dù lịch đã thanh toán: chỗ khám đã bị chiếm và bác sĩ
     * đã chờ. Tự hoàn sẽ biến "vắng khám" thành một đường HỦY MIỄN PHÍ đi vòng qua luật
     * {@code MIN_HOURS_BEFORE_CHANGE} trong {@code whyCannotCancel} — cứ không đến là được
     * trả lại tiền. Muốn hoàn thì lễ tân bấm Hủy, đường {@code cancelWithRefund} vẫn còn đó.
     */
    void markNoShow(Long bookingId);

    /* ========================================================================= */

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
