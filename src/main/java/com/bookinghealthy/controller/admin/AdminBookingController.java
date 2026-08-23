package com.bookinghealthy.controller.admin;

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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminBookingController {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    /**
     * Nhãn hiển thị của từng bộ lọc. Cố ý là ALLOW-LIST tên cố định chứ không nhận
     * {@code ?status=}/{@code ?paymentStatus=} tự do: mỗi tên ứng với đúng một câu hỏi mà dashboard
     * đặt ra, nên không có tổ hợp vô nghĩa nào lọt vào và người dùng luôn đọc được mình đang lọc gì.
     */
    private static final Map<String, String> FILTER_LABELS = Map.of(
            "overdue",  "Đã qua ngày hẹn nhưng chưa đóng",
            "expired",  "Bỏ dở khi thanh toán",
            "unpaid",   "Chưa thanh toán",
            "refunded", "Đã hoàn tiền",
            "paid",     "Đã thanh toán",
            "today",    "Lịch khám hôm nay");

    // 1. HIỂN THỊ DANH SÁCH LỊCH HẸN
    @GetMapping("/manage-booking")
    public String showBookingList(@RequestParam(name = "filter", required = false) String filter,
                                  Model model) {
        // Lấy tất cả booking, sắp xếp mới nhất
        List<Booking> listBookings = bookingRepository.findAllByOrderByCreatedAtDesc();

        // Các khối cảnh báo trên /admin/dashboard trỏ tới đây kèm ?filter=... Không có bước lọc này
        // thì mỗi mũi tên "Xử lý →" chỉ mở nguyên danh sách chưa lọc, tức là một lời hứa suông.
        // Lọc trong bộ nhớ chứ không thêm 6 truy vấn: danh sách đã nạp sẵn ở dòng trên, và màn hình
        // này vốn đã render toàn bộ bảng.
        String activeFilter = (filter != null && FILTER_LABELS.containsKey(filter)) ? filter : null;
        if (activeFilter != null) {
            LocalDate today = LocalDate.now();
            listBookings = listBookings.stream().filter(b -> switch (activeFilter) {
                case "overdue"  -> b.getStatus() == BookingStatus.CONFIRMED
                                   && b.getAppointmentDate() != null
                                   && b.getAppointmentDate().isBefore(today);
                case "expired"  -> "EXPIRED".equals(b.getPaymentStatus());
                case "unpaid"   -> "UNPAID".equals(b.getPaymentStatus())
                                   && b.getStatus() != BookingStatus.CANCELED;
                case "refunded" -> "REFUNDED".equals(b.getPaymentStatus());
                case "paid"     -> "PAID".equals(b.getPaymentStatus());
                case "today"    -> today.equals(b.getAppointmentDate());
                default         -> true;
            }).toList();
        }
        model.addAttribute("activeFilter", activeFilter);
        model.addAttribute("activeFilterLabel", activeFilter == null ? null : FILTER_LABELS.get(activeFilter));

        // Lý do KHÔNG còn thao tác được, tính sẵn bằng đúng hàm mà controller cũng gọi:
        // template làm mờ nút và in lý do, nên giao diện không bao giờ mời admin bấm một
        // nút chắc chắn báo lỗi. Cùng khuôn với booking-manager.html của bác sĩ.
        Map<Long, String> actionBlockReasons = new HashMap<>();
        for (Booking booking : listBookings) {
            actionBlockReasons.put(booking.getId(), bookingService.whyStaffCannotChange(booking));
        }

        model.addAttribute("listBookings", listBookings);
        model.addAttribute("actionBlockReasons", actionBlockReasons);
        return "admin/booking-list"; // Đảm bảo bạn có file html này (giống doctor/booking-manager)
    }

    // 2. XÁC NHẬN LỊCH HẸN
    @PostMapping("/manage-booking/confirm/{id}")
    public String confirmBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id).orElseThrow(() -> new Exception("Booking not found"));

            // Lịch đã hủy / đã khám xong / đã trôi qua thì không xác nhận được nữa: trước đây
            // admin "xác nhận" được một lịch của năm ngoái và email xác nhận vẫn gửi đi.
            String blocked = bookingService.whyStaffCannotChange(booking);
            if (blocked != null) {
                ra.addFlashAttribute("errorMessage", blocked);
                return "redirect:/admin/manage-booking";
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
        return "redirect:/admin/manage-booking";
    }

    // 3. HỦY LỊCH & HOÀN TIỀN (dùng chung logic với quầy lễ tân)
    @PostMapping("/manage-booking/cancel/{id}")
    public String cancelBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + id));

            // Guard nằm ở ĐÂY chứ không trong cancelWithRefund: công cụ hủy hàng loạt của lễ
            // tân cần hủy được cả những ca đã qua giờ trong ngày ("bác sĩ ốm giữa buổi").
            // Còn admin hủy một ca COMPLETED thì ví bệnh nhân bị hoàn tiền cho ca đã khám thật.
            String blocked = bookingService.whyStaffCannotChange(booking);
            if (blocked != null) {
                ra.addFlashAttribute("errorMessage", blocked);
                return "redirect:/admin/manage-booking";
            }

            boolean refunded = bookingService.cancelWithRefund(id, "Admin hệ thống đã hủy lịch hẹn.");

            if (refunded) {
                ra.addFlashAttribute("successMessage", "Đã hủy lịch #" + id + ". Hệ thống đã tự động hoàn tiền vào Ví khách hàng.");
            } else {
                ra.addFlashAttribute("successMessage", "Đã hủy lịch hẹn #" + id);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/manage-booking";
    }

    // 4. XÓA VĨNH VIỄN (Chỉ dùng cho admin)
    @PostMapping("/manage-booking/delete/{id}")
    public String deleteBooking(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Booking booking = bookingService.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch hẹn #" + id));

            // Luật "chỉ xóa lịch đã hủy hoặc đang chờ" trước đây chỉ nằm trong template, nên
            // một URL gõ tay xóa vĩnh viễn được cả ca đã khám xong — mất luôn dấu vết của một
            // lần khám có hồ sơ bệnh án, và không khôi phục được.
            if (booking.getStatus() != BookingStatus.CANCELED
                    && booking.getStatus() != BookingStatus.PENDING) {
                ra.addFlashAttribute("errorMessage",
                        "Chỉ xóa được lịch đã hủy hoặc đang chờ duyệt. Lịch đã xác nhận/đã khám là hồ sơ,"
                                + " hãy hủy lịch thay vì xóa.");
                return "redirect:/admin/manage-booking";
            }

            bookingService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn lịch hẹn.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: Không thể xóa (Có thể do ràng buộc dữ liệu).");
        }
        return "redirect:/admin/manage-booking";
    }
}