package com.bookinghealthy.controller.user;

import com.bookinghealthy.dto.RescheduleRequestDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Bệnh nhân tự sửa lịch hẹn của mình: đổi bác sĩ cùng chuyên khoa, đổi ngày/giờ,
 * sửa thông tin người khám và ghi chú.
 * <p>
 * Toàn bộ điều kiện (còn hạn 24 tiếng, số lần đổi, cùng chuyên khoa, slot còn trống)
 * do {@link BookingService} quyết định — controller chỉ hiển thị và chuyển tiếp thông báo.
 */
@Controller
@RequestMapping("/user/booking")
public class UserBookingEditController {

    @Autowired private BookingService bookingService;
    @Autowired private UserService userService;
    @Autowired private DoctorRepository doctorRepository;

    /** Principal có thể là UserDetails (đăng nhập thường) hoặc OAuth2User (Google/Facebook). */
    private User getCurrentUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String usernameOrEmail;
        if (principal instanceof OAuth2User) {
            usernameOrEmail = ((OAuth2User) principal).getAttribute("email");
        } else if (principal instanceof UserDetails) {
            usernameOrEmail = ((UserDetails) principal).getUsername();
        } else {
            usernameOrEmail = principal.toString();
        }
        return userService.findByUsername(usernameOrEmail)
                .or(() -> userService.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new RuntimeException("User not found: " + usernameOrEmail));
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Authentication authentication,
                               Model model, RedirectAttributes ra) {
        User currentUser = getCurrentUser(authentication);

        Booking booking = bookingService.findById(id).orElse(null);
        if (booking == null || booking.getUser() == null
                || !booking.getUser().getId().equals(currentUser.getId())) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy lịch hẹn của bạn.");
            return "redirect:/user/profile#booking-history";
        }

        String blockedReason = bookingService.whyCannotReschedule(booking);
        if (blockedReason != null) {
            ra.addFlashAttribute("errorMessage", blockedReason);
            return "redirect:/user/profile#booking-history";
        }

        // Chỉ liệt kê bác sĩ cùng khoa; khoa chỉ có một bác sĩ thì danh sách chỉ còn chính họ
        List<Doctor> sameDepartmentDoctors = (booking.getDoctor() != null && booking.getDoctor().getDepartment() != null)
                ? doctorRepository.findByDepartmentId(booking.getDoctor().getDepartment().getId())
                : Collections.emptyList();

        int used = booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount();

        model.addAttribute("booking", booking);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("sameDepartmentDoctors", sameDepartmentDoctors);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("hoursLeft", bookingService.hoursUntilAppointment(booking));
        model.addAttribute("usedTimes", used);
        model.addAttribute("remainingTimes", BookingService.MAX_RESCHEDULE_TIMES - used);
        model.addAttribute("maxTimes", BookingService.MAX_RESCHEDULE_TIMES);
        model.addAttribute("minHours", BookingService.MIN_HOURS_BEFORE_CHANGE);

        return "user/booking-edit";
    }

    @PostMapping("/edit/{id}")
    @Transactional
    public String processEdit(@PathVariable("id") Long id,
                              @ModelAttribute RescheduleRequestDTO request,
                              Authentication authentication,
                              RedirectAttributes ra) {
        User currentUser = getCurrentUser(authentication);

        try {
            Booking updated = bookingService.rescheduleByUser(id, currentUser.getId(), request);

            ra.addFlashAttribute("successMessage",
                    "Đã cập nhật lịch hẹn #" + updated.getId() + ": BS. "
                            + updated.getDoctor().getUser().getFullName() + " — "
                            + updated.getAppointmentTime() + " ngày " + updated.getAppointmentDate()
                            + ". Email xác nhận đã được gửi cho bạn.");
            return "redirect:/user/profile#booking-history";

        } catch (IllegalStateException e) {
            // Lỗi nghiệp vụ đã có câu chữ dành cho người bệnh, hiển thị nguyên văn
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/user/booking/edit/" + id;
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Không đổi được lịch hẹn: " + e.getMessage());
            return "redirect:/user/booking/edit/" + id;
        }
    }
}
