package com.bookinghealthy.service;

import com.bookinghealthy.dto.RescheduleRequestDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingService {

    int MIN_HOURS_BEFORE_CHANGE = 24;

    int MAX_RESCHEDULE_TIMES = 2;

    /**
     * Hình thức "đặt trước, trả tiền tại quầy khi đến khám".
     *
     * Khai ở đây để chuỗi này chỉ tồn tại MỘT lần trong Java — BookingController,
     * BookingServiceImpl và BookingCleanupTask đều dùng chung hằng số này.
     *
     * Cố ý KHÔNG tái dùng "CASH": "CASH" nghĩa là lễ tân đã gặp mặt bệnh nhân tại quầy
     * và lịch được xác nhận ngay, còn giá trị này nghĩa là bệnh nhân tự đặt từ xa, chưa
     * ai gặp mặt, và có thể không tới. Sau khi lễ tân bấm Xác nhận thì cả hai đều thành
     * CONFIRMED, nên `status` KHÔNG cứu được sự phân biệt đó.
     */
    String PAY_AT_COUNTER = "PAY_AT_COUNTER";

    /** Số lịch trả-tại-quầy chưa khám mà một tài khoản được giữ cùng lúc. */
    int MAX_PAY_AT_COUNTER_BOOKINGS = 2;

    Booking save(Booking booking);
    Booking reserve(Booking booking);

    /**
     * Giữ chỗ VÀ trừ tiền ví trong MỘT transaction, rồi chốt CONFIRMED/PAID.
     *
     * <p>Trả về lịch đã lưu nếu trừ được tiền, {@code null} nếu ví không đủ số dư (lịch khi
     * đó đã được ghi CANCELED/FAILED để nhả chỗ).
     *
     * <p>Tồn tại vì controller trước đây điều phối BA transaction rời nhau: reserve() commit,
     * payWithWallet() commit (tiền đã trừ), rồi save() mới chạy. Bước cuối hỏng là bệnh nhân
     * MẤT TIỀN mà lịch vẫn PENDING/UNPAID, và khối catch của controller chỉ in một dòng
     * "Lỗi: ..." — không có đường nào tự hoàn lại.
     */
    Booking reserveAndPayWithWallet(Booking booking, java.math.BigDecimal amount, String description);

    boolean isSlotWithinWorkingHours(Long doctorId, java.time.LocalDate date, String timeSlot);

    List<String> slotsOutsideWorkingHours(Long doctorId, java.time.LocalDate date, List<String> slots);

    boolean hasRegisteredSchedule(Long doctorId, java.time.LocalDate date);

    List<Booking> findAll();
    Optional<Booking> findById(Long id);

    Optional<Booking> findByVnpTxnRef(String vnpTxnRef);

    boolean isBankTxnProcessed(String bankTxnRef);
    void deleteById(Long id);
    List<Booking> findByUser(User user);

    Booking reassign(Long bookingId, Long newDoctorId);

    boolean cancelWithRefund(Long bookingId, String reason);

    String whyCannotReschedule(Booking booking);

    String whyCannotCancel(Booking booking);

    String whyStaffCannotChange(Booking booking);

    /**
     * Nguồn sự thật duy nhất cho "tài khoản này còn đặt lịch trả-tại-quầy được không".
     * Trả null nếu còn được; ngược lại là câu tiếng Việt giải thích — controller dùng để
     * chặn thật, template dùng để vô hiệu hoá radio và in đúng câu đó.
     *
     * KHÔNG mâu thuẫn với ghi chú "there is deliberately no whyCannotReserve()": ghi chú
     * đó từ chối việc gác TRANH CHẤP KHUNG GIỜ, vốn là một race chứ không phải một state.
     * Hạn mức của chính tài khoản đang đăng nhập thì ngược lại — chỉ hành động của họ mới
     * làm nó đổi, và biết được bằng một câu COUNT TRƯỚC KHI vẽ radio.
     */
    String whyCannotBookWithoutPayment(User user);

    /** Số lịch trả-tại-quầy chưa khám mà tài khoản đang giữ — chỉ để hiển thị. */
    long countActivePayAtCounterBookings(User user);

    Long hoursUntilAppointment(Booking booking);

    LocalDateTime appointmentStart(Booking booking);

    Booking rescheduleByUser(Long bookingId, Long userId, RescheduleRequestDTO request);
}
