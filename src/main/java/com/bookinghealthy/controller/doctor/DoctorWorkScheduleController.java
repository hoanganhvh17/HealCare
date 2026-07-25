package com.bookinghealthy.controller.doctor;

import com.bookinghealthy.controller.staff.StaffWorkScheduleController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Lịch làm việc & nghỉ phép của BÁC SĨ. Toàn bộ logic nằm ở lớp cha
 * {@link StaffWorkScheduleController} — lớp này chỉ khai báo URL và sidebar.
 */
@Controller
@RequestMapping("/doctor")
public class DoctorWorkScheduleController extends StaffWorkScheduleController {

    @Override
    protected String basePath() {
        return "/doctor";
    }

    @Override
    protected String sidebarFragment() {
        return "doctor/include/sidebar";
    }

    /**
     * URL cũ của màn hình "Đăng ký lịch trực" — màn hình đó gộp nhầm ca khám với phiên
     * trực nên đã bị thay bằng trang này. Giữ redirect để link cũ (bookmark, dashboard)
     * không gãy.
     */
    @GetMapping("/schedule-register")
    public String redirectLegacyScheduleRegister() {
        return "redirect:/doctor/work-schedule";
    }
}
