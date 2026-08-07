package com.bookinghealthy.controller.doctor;

import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
public class DoctorDashboardController {

    @Autowired private DoctorService doctorService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;
    @Autowired private DoctorBlockTimeService doctorBlockTimeService;
    @Autowired private ReviewService reviewService;

    // Không inject WalletService ở đây: việc hoàn tiền nằm trong BookingService.cancelWithRefund.

    // Helper: Lấy bác sĩ hiện tại
    private Doctor getLoggedInDoctor(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return doctorService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Doctor not found for current user"));
    }

    // 1. DASHBOARD CHÍNH
    @GetMapping("/dashboard")
    public String doctorDashboard(
            @RequestParam(value = "range", defaultValue = "7") String range,
            Model model,
            Authentication authentication) {

        Doctor currentDoctor = getLoggedInDoctor(authentication);
        LocalDate today = LocalDate.now();
        LocalDate startDate;

        if ("30".equals(range)) {
            startDate = today.minusDays(30);
        } else if ("all".equals(range)) {
            startDate = LocalDate.of(2000, 1, 1);
        } else {
            startDate = today.minusDays(7);
        }

        List<Booking> allBookings = bookingRepository.findByDoctor(currentDoctor);
        List<Booking> filteredBookings = allBookings.stream()
                .filter(b -> !b.getAppointmentDate().isBefore(startDate) && !b.getAppointmentDate().isAfter(today))
                .collect(Collectors.toList());

        long countPending = filteredBookings.stream().filter(b -> b.getStatus() == BookingStatus.PENDING).count();
        long countConfirmed = filteredBookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long countCompleted = filteredBookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
        long countCancelled = filteredBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELED).count();

        long countToday = allBookings.stream()
                .filter(b -> b.getAppointmentDate().equals(today) && b.getStatus() == BookingStatus.CONFIRMED)
                .count();

        model.addAttribute("countPending", countPending);
        model.addAttribute("countConfirmed", countConfirmed);
        model.addAttribute("countCompleted", countCompleted);
        model.addAttribute("countCancelled", countCancelled);
        model.addAttribute("countToday", countToday);
        model.addAttribute("currentRange", range);

        List<Review> recentReviews = reviewService.getRecentReviews(currentDoctor.getId());
        model.addAttribute("recentReviews", recentReviews);

        // Điểm trung bình & Biểu đồ sao
        Double avgRating = reviewService.getAverageRating(currentDoctor.getId());
        List<Integer> ratingDist = reviewService.getRatingDistribution(currentDoctor.getId());
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("ratingDist", ratingDist);

        // Lịch trực
        List<Schedule> rawSchedule = doctorService.getDoctorSchedules(currentDoctor.getId());
        Map<String, List<Schedule>> weeklyScheduleMap = new LinkedHashMap<>();
        DayOfWeek[] days = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };

        for (DayOfWeek day : days) {
            List<Schedule> shifts = rawSchedule.stream()
                    .filter(s -> s.getDayOfWeek() == day)
                    .sorted(Comparator.comparing(Schedule::getStartTime))
                    .collect(Collectors.toList());

            if (!shifts.isEmpty()) {
                String dayName = switch (day) {
                    case MONDAY -> "Thứ 2";
                    case TUESDAY -> "Thứ 3";
                    case WEDNESDAY -> "Thứ 4";
                    case THURSDAY -> "Thứ 5";
                    case FRIDAY -> "Thứ 6";
                    case SATURDAY -> "Thứ 7";
                    case SUNDAY -> "Chủ nhật";
                };
                weeklyScheduleMap.put(dayName, shifts);
            }
        }
        model.addAttribute("weeklyScheduleMap", weeklyScheduleMap);
        model.addAttribute("activePage", "dashboard");
        return "doctor/dashboard";
    }

    // 2. XỬ LÝ DUYỆT LỊCH (CONFIRM)
    @GetMapping("/bookings/confirm/{id}")
    public String confirmBooking(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes ra) {
        try {
            Doctor currentDoctor = getLoggedInDoctor(authentication);
            Booking booking = bookingService.findById(id).orElseThrow(() -> new Exception("Không tìm thấy Lịch hẹn"));

            if (!booking.getDoctor().getId().equals(currentDoctor.getId())) {
                ra.addFlashAttribute("errorMessage", "Lỗi: Bạn không có quyền xác nhận lịch hẹn này.");
                return "redirect:/doctor/dashboard";
            }

            // Chặn thật ở server chứ không tin vào việc giao diện đã làm mờ nút: lịch đã
            // trôi qua / đã hủy / đã khám xong thì xác nhận là vô nghĩa và vẫn gửi email đi.
            String blocked = bookingService.whyStaffCannotChange(booking);
            if (blocked != null) {
                ra.addFlashAttribute("errorMessage", blocked);
                return "redirect:/doctor/booking-requests";
            }

            booking.setStatus(BookingStatus.CONFIRMED);
            bookingService.save(booking);
            emailService.sendBookingConfirmation(booking);
            notificationService.pushBookingEvent(booking, "bi-check-circle text-success",
                    "Lịch hẹn đã được xác nhận");
            ra.addFlashAttribute("successMessage", "Đã xác nhận lịch hẹn thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/doctor/booking-requests";
    }

    // 3. XỬ LÝ HỦY LỊCH (CANCEL & REFUND)
    @Transactional
    @GetMapping("/bookings/cancel/{id}")
    public String cancelBooking(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes ra) {
        try {
            Doctor currentDoctor = getLoggedInDoctor(authentication);
            Booking booking = bookingService.findById(id).orElseThrow(() -> new Exception("Không tìm thấy Lịch hẹn"));

            if (!booking.getDoctor().getId().equals(currentDoctor.getId())) {
                ra.addFlashAttribute("errorMessage", "Lỗi: Bạn không có quyền hủy lịch hẹn này.");
                return "redirect:/doctor/dashboard";
            }

            // Lịch đã trôi qua / đã khám xong thì KHÔNG hủy được: hủy một ca COMPLETED sẽ
            // hoàn tiền cho ca bệnh nhân đã khám thật.
            String blocked = bookingService.whyStaffCannotChange(booking);
            if (blocked != null) {
                ra.addFlashAttribute("errorMessage", blocked);
                return "redirect:/doctor/manage-bookings";
            }

            // Hủy lịch chỉ có MỘT chỗ làm: cancelWithRefund lo cả trạng thái, hoàn ví và email.
            boolean refunded = bookingService.cancelWithRefund(booking.getId(),
                    "Bác sĩ " + currentDoctor.getUser().getFullName() + " đã từ chối/hủy lịch hẹn.");

            ra.addFlashAttribute("successMessage", refunded
                    ? "Đã hủy lịch. Hệ thống đã tự động hoàn tiền vào Ví của khách hàng."
                    : "Đã từ chối lịch hẹn thành công.");

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }

        // Quay lại trang trước đó (Thường là trang Manage Bookings hoặc Request)
        return "redirect:/doctor/manage-bookings";
    }

    // 4. GIỜ BẬN ĐỘT XUẤT
    // Việc quản lý ca làm việc / phiên trực / nghỉ phép đã chuyển sang
    // DoctorWorkScheduleController (/doctor/work-schedule), nơi tách bạch ca khám trong
    // giờ hành chính với phiên trực ngoài giờ. Ở đây chỉ giữ hai thao tác chặn giờ lẻ.

    @PostMapping("/schedule/block")
    public String blockSchedule(@RequestParam("blockDate") LocalDate blockDate, @RequestParam("startTime") LocalTime startTime, @RequestParam("endTime") LocalTime endTime, @RequestParam("reason") String reason, Authentication authentication, RedirectAttributes ra) {
        Doctor currentDoctor = getLoggedInDoctor(authentication);
        if (blockDate.isBefore(LocalDate.now())) {
            ra.addFlashAttribute("errorMessage", "Không thể chặn giờ trong quá khứ.");
            return "redirect:/doctor/work-schedule";
        }
        String error = doctorBlockTimeService.blockTime(currentDoctor, blockDate, startTime, endTime, reason);
        if (error != null) ra.addFlashAttribute("errorMessage", error);
        else ra.addFlashAttribute("successMessage", "Đã chặn khung giờ thành công!");
        return "redirect:/doctor/work-schedule";
    }

    @GetMapping("/schedule/unblock/{id}")
    public String unblockSchedule(@PathVariable("id") Long id, RedirectAttributes ra) {
        doctorBlockTimeService.unblockTime(id);
        ra.addFlashAttribute("successMessage", "Đã gỡ chặn khung giờ.");
        return "redirect:/doctor/work-schedule";
    }

    // Không còn route xóa lẻ một ca khám: lịch khám gắn với từng tuần và chỉ sửa được cho
    // TUẦN SAU qua bảng đăng ký ở /doctor/work-schedule. Xóa theo id sẽ đục thủng cả khóa
    // tuần lẫn ràng buộc "mỗi ngày tối thiểu 1 ca".

    // === THÊM API ĐỂ TRẢ VỀ LỜI NHẮC NHỞ NHANH TRÊN DASHBOARD ===
    @GetMapping("/api/quick-review-advice")
    @ResponseBody
    public Map<String, String> getQuickReviewAdvice(Authentication authentication) {
        Doctor currentDoctor = getLoggedInDoctor(authentication);
        Double avgRating = reviewService.getAverageRating(currentDoctor.getId());

        String advice = "Chưa có đủ đánh giá.";
        String colorClass = "text-muted";

        if (avgRating != null) {
            if (avgRating >= 4.5) {
                advice = "Tuyệt vời! Hãy tiếp tục duy trì thái độ tích cực nhé.";
                colorClass = "text-success fw-bold";
            } else if (avgRating >= 3.5) {
                advice = "Tốt! Nhưng có vài điểm nhỏ cần cải thiện để đạt 5 sao.";
                colorClass = "text-warning text-dark fw-bold";
            } else {
                advice = "Cảnh báo! Điểm đánh giá đang thấp, cần khắc phục ngay.";
                colorClass = "text-danger fw-bold";
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("advice", advice);
        response.put("colorClass", colorClass);
        return response;
    }
}