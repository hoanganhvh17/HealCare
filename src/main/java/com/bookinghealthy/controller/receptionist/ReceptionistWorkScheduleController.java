package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.controller.staff.StaffWorkScheduleController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Lịch làm việc & nghỉ phép của LỄ TÂN — cùng giao diện và cùng logic với bác sĩ.
 *
 * Khác biệt duy nhất về nghiệp vụ: lễ tân không phải bác sĩ nên đơn nghỉ của họ
 * không sinh DoctorBlockTime (không có khung giờ khám nào để chặn) — điều này do
 * {@code LeaveServiceImpl} tự xử lý, không cần code riêng ở đây.
 */
@Controller
@RequestMapping("/receptionist")
public class ReceptionistWorkScheduleController extends StaffWorkScheduleController {

    @Override
    protected String basePath() {
        return "/receptionist";
    }

    @Override
    protected String sidebarFragment() {
        return "receptionist/include/sidebar";
    }
}
