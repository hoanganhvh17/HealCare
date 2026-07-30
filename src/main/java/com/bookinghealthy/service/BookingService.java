package com.bookinghealthy.service;

import com.bookinghealthy.dto.RescheduleRequestDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.User;

import java.util.List;
import java.util.Optional;

public interface BookingService {

    /** Bệnh nhân chỉ được tự đổi/hủy khi còn cách giờ khám nhiều hơn số giờ này. */
    int MIN_HOURS_BEFORE_CHANGE = 24;

    /** Số lần tối đa một lịch hẹn được bệnh nhân tự dời, tránh giữ chỗ ảo. */
    int MAX_RESCHEDULE_TIMES = 2;

    Booking save(Booking booking);
    Booking reserve(Booking booking);
    // (Chúng ta sẽ thêm các hàm find... sau)

    /**
     * Khung giờ có nằm trong ca làm việc (bảng Schedule) của bác sĩ trong ngày đó không.
     * Bác sĩ CHƯA đăng ký lịch nào -> trả true (không giới hạn) để giữ hành vi cũ cho các
     * bác sĩ seed chưa có lịch. Có lịch rồi thì chỉ giờ trong ca đã đăng ký mới đặt được.
     */
    boolean isSlotWithinWorkingHours(Long doctorId, java.time.LocalDate date, String timeSlot);

    /**
     * Trong danh sách khung giờ, những khung NẰM NGOÀI lịch làm việc của bác sĩ ngày đó.
     * Dùng cho API booked-slots để gạch bỏ các buổi bác sĩ không nhận khám.
     */
    List<String> slotsOutsideWorkingHours(Long doctorId, java.time.LocalDate date, List<String> slots);

    // === THÊM 3 HÀM MỚI NÀY ===
    List<Booking> findAll();
    Optional<Booking> findById(Long id);
    void deleteById(Long id);
    // === THÊM HÀM MỚI NÀY ===
    List<Booking> findByUser(User user);

    /**
     * Chuyển một lịch hẹn sang bác sĩ khác, GIỮ NGUYÊN ngày + khung giờ + giá đã chốt.
     * Bác sĩ mới bắt buộc phải CÙNG CHUYÊN KHOA với bác sĩ hiện tại.
     * Dùng chung cơ chế khóa theo slot với reserve() để không tạo ra trùng lịch.
     *
     * @throws IllegalStateException nếu khác chuyên khoa, hoặc bác sĩ mới đã kín slot / đã chặn giờ đó.
     */
    Booking reassign(Long bookingId, Long newDoctorId);

    /**
     * Hủy một lịch hẹn: đổi trạng thái CANCELED, hoàn tiền vào ví nếu đã thanh toán (PAID)
     * và gửi email thông báo kèm lý do.
     *
     * @return true nếu có hoàn tiền vào ví, false nếu lịch chưa thanh toán.
     */
    boolean cancelWithRefund(Long bookingId, String reason);

    /**
     * Kiểm tra một lịch hẹn có còn được bệnh nhân tự sửa hay không.
     * Dùng chung cho trang hồ sơ (ẩn/hiện nút) và cho controller (chặn thật sự),
     * để giao diện và server không bao giờ nói khác nhau.
     *
     * @return null nếu được phép sửa, ngược lại là lý do bằng tiếng Việt để hiển thị cho bệnh nhân.
     */
    String whyCannotReschedule(Booking booking);

    /**
     * Kiểm tra một lịch hẹn có còn được bệnh nhân tự hủy hay không.
     * Khác {@link #whyCannotReschedule} ở chỗ KHÔNG giới hạn số lần —
     * hủy chỉ phụ thuộc trạng thái và mốc {@value #MIN_HOURS_BEFORE_CHANGE} tiếng.
     *
     * @return null nếu được phép hủy, ngược lại là lý do bằng tiếng Việt.
     */
    String whyCannotCancel(Booking booking);

    /**
     * Kiểm tra một lịch hẹn có còn được NHÂN VIÊN (bác sĩ / lễ tân) xác nhận, hủy hay khám nữa
     * không. Dùng chung cho giao diện (làm mờ nút) và cho controller (chặn thật sự).
     * <p>
     * Khác {@link #whyCannotCancel} ở chỗ KHÔNG áp mốc {@value #MIN_HOURS_BEFORE_CHANGE} tiếng:
     * bác sĩ vẫn phải hủy được lịch đột xuất ngay trong ngày. Ở đây chỉ chặn những gì đã
     * thành hồ sơ: lịch đã hủy, đã khám xong, hoặc giờ khám đã trôi qua.
     *
     * @return null nếu còn được thao tác, ngược lại là lý do bằng tiếng Việt.
     */
    String whyStaffCannotChange(Booking booking);

    /**
     * Số giờ còn lại tính đến giờ khám. Âm nghĩa là đã quá giờ.
     * Trả về null nếu không đọc được khung giờ của lịch hẹn.
     */
    Long hoursUntilAppointment(Booking booking);

    /**
     * Thời điểm bắt đầu ca khám (ngày khám + giờ bắt đầu của khung giờ).
     * Trả về null nếu {@code appointmentTime} không đúng dạng "HH:mm - HH:mm".
     */
    java.time.LocalDateTime appointmentStart(Booking booking);

    /**
     * Bệnh nhân tự sửa lịch hẹn của mình: đổi bác sĩ (bắt buộc cùng chuyên khoa),
     * đổi ngày/giờ, sửa thông tin người khám và ghi chú.
     * <p>
     * Giữ nguyên {@code bookingPrice} đã chốt lúc đặt và trạng thái thanh toán —
     * bệnh nhân không bị thu thêm dù bác sĩ mới có giá niêm yết khác.
     * Dùng chung cơ chế khóa slot với {@code reserve()} nên hai người cùng nhắm
     * một khung giờ sẽ không thể cùng chiếm.
     *
     * @param userId id của người đang đăng nhập, để chặn sửa lịch của người khác.
     * @throws IllegalStateException khi hết hạn sửa, quá số lần cho phép, khác chuyên khoa,
     *                               slot đã kín / bị bác sĩ chặn, hoặc chọn giờ trong quá khứ.
     */
    Booking rescheduleByUser(Long bookingId, Long userId, RescheduleRequestDTO request);
}
