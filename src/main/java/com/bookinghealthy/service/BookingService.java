package com.bookinghealthy.service;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.User;

import java.util.List;
import java.util.Optional;

public interface BookingService {
    Booking save(Booking booking);
    Booking reserve(Booking booking);
    // (Chúng ta sẽ thêm các hàm find... sau)

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
}
