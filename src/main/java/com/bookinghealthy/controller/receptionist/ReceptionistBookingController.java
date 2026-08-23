package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Xem và xử lý lẻ từng lịch hẹn ở quầy tiếp đón.
 */
@Controller
@RequestMapping("/receptionist/bookings")
public class ReceptionistBookingController {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    @GetMapping
    public String list(@RequestParam(value = "status", required = false) String status, Model model) {
        List<Booking> listBookings = bookingRepository.findAllByOrderByCreatedAtDesc();

        if (status != null && !status.isBlank()) {
            BookingStatus filter = BookingStatus.valueOf(status);
            listBookings = listBookings.stream()
                    .filter(b -> b.getStatus() == filter)
                    .toList();
        }

        // null = còn thao tác được; ngược lại là lý do để template làm mờ nút và in ra.
        // Cùng hàm mà confirm()/cancel() gọi, nên giao diện không mời bấm một nút chắc chắn lỗi.
        Map<Long, String> actionBlockReasons = new HashMap<>();
        for (Booking booking : listBookings) {
            actionBlockReasons.put(booking.getId(), bookingService.whyStaffCannotChange(booking));
        }

        model.addAttribute("listBookings", listBookings);
        model.addAttribute("actionBlockReasons", actionBlockReasons);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("activePage", "bookings");
        return "receptionist/booking-list";
    }

    @PostMapping("/confirm/{id}")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + id));

            // Trạng thái PENDING thôi thì chưa đủ: một lịch PENDING của tuần trước vẫn lọt qua
            // và email "đã xác nhận" vẫn bay tới bệnh nhân cho ca khám đã trôi qua.
            String blocked = bookingService.whyStaffCannotChange(booking);
            if (blocked != null) {
                throw new IllegalStateException(blocked);
            }
            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new IllegalStateException("Chỉ xác nhận được lịch đang chờ duyệt.");
            }

            booking.setStatus(BookingStatus.CONFIRMED);
            bookingService.save(booking);
            emailService.sendBookingConfirmation(booking);
            notificationService.pushBookingEvent(booking, "bi-check-circle text-success",
                    "Lịch hẹn đã được xác nhận");

            ra.addFlashAttribute("successMessage", "Đã xác nhận lịch hẹn #" + id);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/receptionist/bookings";
    }

    @PostMapping("/cancel/{id}")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + id));

            // Guard đặt ở controller chứ KHÔNG trong cancelWithRefund: công cụ hủy hàng loạt
            // (/receptionist/schedule-change) phải hủy được cả những ca đã qua giờ trong ngày
            // khi bác sĩ ốm giữa buổi. Còn hủy lẻ một ca đã khám xong thì ví bệnh nhân bị
            // hoàn tiền cho một lần khám có thật.
            String blocked = bookingService.whyStaffCannotChange(booking);
            if (blocked != null) {
                throw new IllegalStateException(blocked);
            }

            String finalReason = (reason == null || reason.isBlank())
                    ? "Quầy lễ tân đã hủy lịch hẹn theo yêu cầu."
                    : reason;

            boolean refunded = bookingService.cancelWithRefund(id, finalReason);

            ra.addFlashAttribute("successMessage", refunded
                    ? "Đã hủy lịch #" + id + " và hoàn tiền vào ví bệnh nhân."
                    : "Đã hủy lịch hẹn #" + id);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/receptionist/bookings";
    }
}
