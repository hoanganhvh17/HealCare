package com.bookinghealthy.controller.head;

import com.bookinghealthy.config.LeavePolicy;
import com.bookinghealthy.dto.ClinicRosterRowDTO;
import com.bookinghealthy.dto.ShiftRegisterResultDTO;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.DutyRole;
import com.bookinghealthy.model.ShiftType;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.StaffScheduleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XẾP CA CHO KHOA — màn hình chủ động của trưởng khoa, đối lập với
 * {@link HeadApprovalController} vốn chỉ ngồi chờ duyệt những gì bác sĩ gửi lên.
 *
 * Trước đây trưởng khoa chỉ có một nút "Chốt &amp; tự xếp lịch" gọi
 * {@code autoRegisterUnregisteredDoctors}, tức là hệ thống nhét ca sáng vào mọi ngày trống
 * của ai chưa đăng ký — trưởng khoa không chọn được ai làm buổi nào.
 *
 * Hai việc ở đây:
 * <ul>
 *   <li>Ca khám tuần sau: bảng Bác sĩ × Thứ × (Sáng/Chiều), lưu một lần, kiểm theo ĐỘ PHỦ
 *       CỦA KHOA (mỗi buổi phải có ≥1 bác sĩ) thay vì bắt từng bác sĩ làm đủ 7 ngày.</li>
 *   <li>Phân công phiên trực cho những ngày khoa chưa có ai trực.</li>
 * </ul>
 */
@Controller
@RequestMapping("/head")
public class HeadRosterController {

    @Autowired private CurrentUserService currentUserService;
    @Autowired private StaffScheduleService staffScheduleService;

    // ===================== CA KHÁM CỦA KHOA =====================

    @GetMapping("/clinic-roster")
    public String clinicRoster(@RequestParam(value = "weekStart", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                               Model model, Authentication authentication) {

        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);

        LocalDate nextWeek = staffScheduleService.nextWeekStart();
        // Luôn quy về thứ Hai để lưới không bị lệch khi có người sửa tay tham số trên URL.
        LocalDate week = (weekStart != null) ? LeavePolicy.weekStartOf(weekStart) : nextWeek;

        List<ClinicRosterRowDTO> rows = (department != null)
                ? staffScheduleService.buildClinicRoster(department.getId(), week)
                : new ArrayList<>();

        model.addAttribute("rows", rows);
        model.addAttribute("weekStart", week);
        model.addAttribute("weekEnd", week.plusDays(6));
        model.addAttribute("prevWeek", week.minusWeeks(1));
        model.addAttribute("nextWeek", week.plusWeeks(1));
        model.addAttribute("nextWeekStart", nextWeek);
        // Chỉ tuần sau sửa được: tuần hiện tại bệnh nhân đã đặt lịch vào, tuần đã qua là hồ sơ.
        model.addAttribute("editable", week.equals(nextWeek));
        model.addAttribute("days", DayOfWeek.values());
        model.addAttribute("dayLabels", dayLabels());
        model.addAttribute("morningCoverage", coverage(rows, true));
        model.addAttribute("afternoonCoverage", coverage(rows, false));

        return prepare(model, user, department, "clinic-roster", "head/clinic-roster");
    }

    /**
     * Nhận bảng tích từ form. Checkbox đặt tên {@code m_<doctorId>_<thứ 1..7>} và
     * {@code a_<doctorId>_<thứ>} nên phải đọc thẳng từ parameter map — số bác sĩ của khoa là
     * động, không khai được thành field của một DTO cố định.
     */
    @PostMapping("/clinic-roster/save")
    public String saveClinicRoster(@RequestParam("weekStart")
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                                   HttpServletRequest request,
                                   Authentication authentication, RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);
        if (department == null) {
            ra.addFlashAttribute("errorMessage", "Anh/chị chưa được gán làm trưởng khoa.");
            return "redirect:/head/dashboard";
        }

        Map<Long, List<DayOfWeek>> morning = new HashMap<>();
        Map<Long, List<DayOfWeek>> afternoon = new HashMap<>();
        for (String name : request.getParameterMap().keySet()) {
            parseCheckbox(name, morning, afternoon);
        }

        String error = staffScheduleService.assignClinicWeek(
                user, department.getId(), weekStart, morning, afternoon);

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage",
                    "Đã lưu lịch khám tuần sau cho khoa. Bác sĩ có lịch thay đổi đã được thông báo.");
        }
        return "redirect:/head/clinic-roster?weekStart=" + weekStart;
    }

    /** {@code m_12_3} = bác sĩ 12, ca sáng, Thứ 4. Tên sai định dạng thì bỏ qua. */
    private void parseCheckbox(String name, Map<Long, List<DayOfWeek>> morning,
                               Map<Long, List<DayOfWeek>> afternoon) {
        if (name == null || (!name.startsWith("m_") && !name.startsWith("a_"))) {
            return;
        }
        String[] parts = name.split("_");
        if (parts.length != 3) {
            return;
        }
        try {
            Long doctorId = Long.valueOf(parts[1]);
            DayOfWeek day = DayOfWeek.of(Integer.parseInt(parts[2]));
            Map<Long, List<DayOfWeek>> target = name.startsWith("m_") ? morning : afternoon;
            target.computeIfAbsent(doctorId, key -> new ArrayList<>()).add(day);
        } catch (Exception ignored) {
            // Tham số rác từ URL — bỏ qua, không làm vỡ cả lần lưu.
        }
    }

    // ===================== PHÂN CÔNG PHIÊN TRỰC =====================

    @PostMapping("/duty-roster/assign")
    public String assignDuty(@RequestParam("doctorId") Long doctorId,
                             @RequestParam("shiftType") ShiftType shiftType,
                             @RequestParam("shiftDate")
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shiftDate,
                             @RequestParam(value = "dutyRole", required = false) DutyRole dutyRole,
                             @RequestParam(value = "note", required = false) String note,
                             @RequestParam(value = "weekStart", required = false) String weekStart,
                             Authentication authentication, RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);
        if (department == null) {
            ra.addFlashAttribute("errorMessage", "Anh/chị chưa được gán làm trưởng khoa.");
            return "redirect:/head/dashboard";
        }

        // Chỉ phân công được cho bác sĩ trong khoa mình.
        boolean inDepartment = staffScheduleService.findDepartmentDoctors(department.getId()).stream()
                .anyMatch(doctor -> doctor.getId().equals(doctorId));
        if (!inDepartment) {
            ra.addFlashAttribute("errorMessage", "Bác sĩ này không thuộc khoa của anh/chị.");
            return redirectToRoster(weekStart);
        }

        ShiftRegisterResultDTO result = staffScheduleService.assignDutyShift(
                user, doctorId, shiftType, shiftDate, dutyRole, note);

        if (!result.isSuccess()) {
            ra.addFlashAttribute("errorMessage", result.getError());
        } else {
            ra.addFlashAttribute("successMessage",
                    "Đã phân công phiên trực và thông báo cho bác sĩ.");
            if (result.getWarning() != null) {
                ra.addFlashAttribute("warningMessage", result.getWarning());
            }
        }
        return redirectToRoster(weekStart);
    }

    // ===================== HELPERS =====================

    /**
     * Số bác sĩ nhận khám mỗi thứ — hàng chân bảng, để thấy ngay ngày nào đang trống.
     *
     * Trả về LIST theo thứ tự Thứ 2 → Chủ nhật, không phải Map khóa {@code DayOfWeek}:
     * Thymeleaf/SpEL tra {@code map[day]} với khóa enum ra rỗng lặng lẽ (bảng vẫn render
     * nhưng mọi con số biến mất), còn {@code list[iter.index]} thì luôn đúng.
     */
    private List<Integer> coverage(List<ClinicRosterRowDTO> rows, boolean isMorning) {
        List<Integer> counts = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            int count = 0;
            for (ClinicRosterRowDTO row : rows) {
                if ((isMorning ? row.getMorning() : row.getAfternoon()).contains(day)) {
                    count++;
                }
            }
            counts.add(count);
        }
        return counts;
    }

    /** Nhãn thứ theo cùng thứ tự với {@code DayOfWeek.values()} — xem lý do ở {@link #coverage}. */
    private List<String> dayLabels() {
        return List.of("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật");
    }

    private String redirectToRoster(String weekStart) {
        return (weekStart != null && !weekStart.isBlank())
                ? "redirect:/head/duty-roster?weekStart=" + weekStart
                : "redirect:/head/duty-roster";
    }

    private String prepare(Model model, User user, Department department,
                           String activePage, String view) {
        model.addAttribute("currentUser", user);
        model.addAttribute("headDepartment", department);
        model.addAttribute("activePage", activePage);
        return view;
    }
}
