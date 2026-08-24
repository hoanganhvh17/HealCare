package com.bookinghealthy.config;

import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Bơm các con số huy hiệu + tên khoa cho sidebar bác sĩ, trên mọi URL {@code /doctor/**}.
 *
 * <p><b>Chỉ huy hiệu, KHÔNG đặt {@code activePage}.</b> Đây là chỗ khác hẳn
 * {@link AdminNavInterceptor}: khu admin gom {@code activePage} vào interceptor vì trước đó nó bị
 * hardcode ở template và 9 controller phải sửa. Khu bác sĩ thì 4 controller đã tự đặt và đang đặt
 * đúng; ghi đè lên chúng ở đây là đổi một hành vi đang tốt để lấy sự "gọn" không ai cần, lại thêm một
 * chỗ nữa có thể lệch. Interceptor chỉ làm đúng phần mà không controller nào làm nổi tại chỗ: một con
 * số dùng chung cho cả 8 trang đang nhúng sidebar.
 *
 * <p><b>Vì sao là {@code postHandle} chứ không phải {@code @ControllerAdvice}</b> — y hệt lập luận ở
 * {@link AdminNavInterceptor}: {@code @ModelAttribute} chạy <b>trước</b> handler, nên một truy vấn
 * hỏng ở đó là HTTP 500 cho <b>mọi</b> URL bác sĩ, mà dự án không có global exception handler nào đỡ.
 * Chốt chặn {@code mv == null} cũng bỏ qua luôn mọi {@code redirect:} và mọi endpoint trả
 * {@code ResponseEntity}.
 *
 * <p><b>Tiền tố {@code nav} trên mọi thuộc tính là bắt buộc.</b> {@code AbstractView} trộn FlashMap
 * vào model <i>trước</i> model của handler, nên một thuộc tính trùng tên sẽ <b>nuốt</b> thông báo — mà
 * {@code RedirectAttributes} là kênh báo lỗi duy nhất của ứng dụng này.
 */
@Component
public class DoctorNavInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;
    private final BookingRepository bookingRepository;

    public DoctorNavInterceptor(CurrentUserService currentUserService,
                                BookingRepository bookingRepository) {
        this.currentUserService = currentUserService;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView mv) {
        // Không có view thì không có sidebar để tô: mọi redirect, mọi lượt tải tệp.
        if (mv == null || mv.getViewName() == null || mv.getViewName().startsWith("redirect:")) {
            return;
        }

        // Huy hiệu KHÔNG được phép làm sập trang: mất một con số nhỏ rẻ hơn nhiều so với trang trắng.
        // Nuốt lỗi vào log theo đúng khuôn xử lý lỗi sẵn có của dự án.
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // Qua CurrentUserService chứ không đọc thẳng auth.getName(): principal có thể là
            // OAuth2User, nơi getName() trả về thuộc tính của nhà cung cấp chứ không phải username.
            Optional<User> user = currentUserService.find(auth);
            if (user.isEmpty()) {
                return;
            }
            Optional<Doctor> doctor = currentUserService.findDoctor(user.get());
            if (doctor.isEmpty()) {
                return; // Trưởng khoa không kiêm bác sĩ, hoặc admin ghé qua: không có gì để đếm.
            }

            Long doctorId = doctor.get().getId();
            LocalDate today = LocalDate.now();

            mv.addObject("navPendingRequests",
                    bookingRepository.countByDoctorIdAndStatus(doctorId, BookingStatus.PENDING));

            // Chỉ CONFIRMED, khớp đúng countToday của DoctorDashboardController — hai màn hình đếm
            // cùng một thứ thì phải ra cùng một số.
            mv.addObject("navTodayExams", bookingRepository.countByDoctorIdAndStatusAndDateRange(
                    doctorId, BookingStatus.CONFIRMED, today, today));

            Department department = doctor.get().getDepartment();
            if (department != null) {
                mv.addObject("navDepartmentName", department.getName());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
