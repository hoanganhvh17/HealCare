package com.bookinghealthy.controller.head;

import com.bookinghealthy.model.ApprovalStatus;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.LeaveRequest;
import com.bookinghealthy.model.StaffShift;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.LeaveService;
import com.bookinghealthy.service.StaffScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
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
 * Màn hình của TRƯỞNG KHOA: duyệt đơn nghỉ và lịch trực của khoa mình.
 *
 * Trưởng khoa được xác định bằng {@code StaffProfile.headOfDepartment} chứ không chỉ bằng
 * ROLE_HEAD_DOCTOR — role mở cửa vào /head/**, còn hồ sơ quyết định họ duyệt cho khoa nào.
 */
@Controller
@RequestMapping("/head")
public class HeadApprovalController {

    @Autowired private CurrentUserService currentUserService;
    @Autowired private LeaveService leaveService;
    @Autowired private StaffScheduleService staffScheduleService;

    // ===================== TỔNG QUAN =====================

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model, Authentication authentication) {
        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);

        if (department == null) {
            model.addAttribute("errorMessage",
                    "Tài khoản của anh/chị chưa được gán làm trưởng khoa của khoa nào.");
            return prepare(model, user, null, "dashboard", "head/dashboard");
        }

        LocalDate today = LocalDate.now();
        LocalDate weekAhead = today.plusDays(7);

        List<LeaveRequest> pendingLeaves = leaveService.findByDepartment(department.getId(), ApprovalStatus.PENDING);
        List<StaffShift> pendingShifts = staffScheduleService
                .findDepartmentShiftsByStatus(department.getId(), ApprovalStatus.PENDING);
        List<LocalDate> uncovered = staffScheduleService
                .findUncoveredDutyDates(department.getId(), today, weekAhead);

        model.addAttribute("pendingLeaveCount", pendingLeaves.size());
        model.addAttribute("pendingShiftCount", pendingShifts.size());
        model.addAttribute("emergencyCount", pendingLeaves.stream().filter(LeaveRequest::isEmergency).count());
        model.addAttribute("uncoveredDates", uncovered);
        model.addAttribute("recentLeaves", pendingLeaves.stream().limit(5).toList());

        return prepare(model, user, department, "dashboard", "head/dashboard");
    }

    // ===================== DUYỆT ĐƠN NGHỈ =====================

    @GetMapping("/leave-requests")
    public String leaveRequests(@RequestParam(value = "status", required = false) ApprovalStatus status,
                                Model model, Authentication authentication) {

        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);
        ApprovalStatus filter = (status != null) ? status : ApprovalStatus.PENDING;

        List<LeaveRequest> requests = (department != null)
                ? leaveService.findByDepartment(department.getId(), filter)
                : new ArrayList<>();

        // Mỗi đơn kèm số dư phép của người xin và số lịch hẹn sẽ bị ảnh hưởng, để trưởng
        // khoa quyết định có cơ sở thay vì duyệt mù.
        Map<Long, Integer> affectedBookings = new HashMap<>();
        Map<Long, String> remainingLeave = new HashMap<>();
        for (LeaveRequest request : requests) {
            affectedBookings.put(request.getId(), leaveService.countAffectedBookings(
                    request.getUser(), request.getStartDate(), request.getEndDate()));
            remainingLeave.put(request.getId(), leaveService
                    .getBalance(request.getUser(), request.getStartDate().getYear())
                    .getAnnualRemaining().stripTrailingZeros().toPlainString());
        }

        model.addAttribute("requests", requests);
        model.addAttribute("selectedStatus", filter);
        model.addAttribute("statuses", ApprovalStatus.values());
        model.addAttribute("affectedBookings", affectedBookings);
        model.addAttribute("remainingLeave", remainingLeave);

        return prepare(model, user, department, "leave-requests", "head/leave-requests");
    }

    @PostMapping("/leave-requests/{id}/approve")
    public String approveLeave(@PathVariable Long id,
                               @RequestParam(value = "comment", required = false) String comment,
                               Authentication authentication, RedirectAttributes ra) {

        User approver = currentUserService.require(authentication);
        flash(ra, leaveService.approve(id, approver, comment),
                "Đã phê duyệt đơn nghỉ. Khung giờ khám của bác sĩ trong ngày nghỉ đã được chặn.");
        return "redirect:/head/leave-requests";
    }

    @PostMapping("/leave-requests/{id}/reject")
    public String rejectLeave(@PathVariable Long id,
                              @RequestParam(value = "comment", required = false) String comment,
                              Authentication authentication, RedirectAttributes ra) {

        User approver = currentUserService.require(authentication);
        flash(ra, leaveService.reject(id, approver, comment), "Đã từ chối đơn nghỉ.");
        return "redirect:/head/leave-requests";
    }

    // ===================== CHỐT LỊCH KHÁM TUẦN SAU CHO KHOA =====================

    /** Nhắc các bác sĩ trong khoa chưa đăng ký lịch khám tuần sau (gửi email). */
    @PostMapping("/clinic/remind")
    public String remindClinicRegistration(Authentication authentication, RedirectAttributes ra) {
        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);
        if (department == null) {
            ra.addFlashAttribute("errorMessage", "Anh/chị chưa được gán làm trưởng khoa.");
            return "redirect:/head/dashboard";
        }
        int reminded = staffScheduleService.sendNextWeekRegistrationReminders(department.getId());
        ra.addFlashAttribute("successMessage",
                "Đã gửi nhắc đăng ký lịch khám tuần sau tới " + reminded + " bác sĩ chưa đăng ký.");
        return "redirect:/head/dashboard";
    }

    /** Chốt lịch: bác sĩ nào chưa đăng ký thì tự xếp cả tuần (mỗi ngày tối thiểu 1 ca). */
    @PostMapping("/clinic/finalize")
    public String finalizeClinicRegistration(Authentication authentication, RedirectAttributes ra) {
        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);
        if (department == null) {
            ra.addFlashAttribute("errorMessage", "Anh/chị chưa được gán làm trưởng khoa.");
            return "redirect:/head/dashboard";
        }
        int registered = staffScheduleService.autoRegisterUnregisteredDoctors(department.getId());
        ra.addFlashAttribute("successMessage",
                "Đã tự xếp lịch khám cả tuần cho " + registered + " bác sĩ chưa đăng ký.");
        return "redirect:/head/dashboard";
    }

    // ===================== DUYỆT LỊCH TRỰC =====================

    @GetMapping("/duty-roster")
    public String dutyRoster(@RequestParam(value = "weekStart", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                             Model model, Authentication authentication) {

        User user = currentUserService.require(authentication);
        Department department = currentUserService.resolveHeadDepartment(user);

        LocalDate start = (weekStart != null) ? weekStart : startOfWeek(LocalDate.now());
        LocalDate end = start.plusDays(6);

        List<StaffShift> shifts = (department != null)
                ? staffScheduleService.findDepartmentShifts(department.getId(), start, end)
                : new ArrayList<>();

        model.addAttribute("shifts", shifts);
        model.addAttribute("dutyShifts", shifts.stream().filter(StaffShift::isDuty).toList());
        model.addAttribute("weekStart", start);
        model.addAttribute("weekEnd", end);
        model.addAttribute("prevWeek", start.minusWeeks(1));
        model.addAttribute("nextWeek", start.plusWeeks(1));
        model.addAttribute("uncoveredDates", department != null
                ? staffScheduleService.findUncoveredDutyDates(department.getId(), start, end)
                : new ArrayList<>());

        return prepare(model, user, department, "duty-roster", "head/duty-roster");
    }

    @PostMapping("/duty-roster/{id}/approve")
    public String approveShift(@PathVariable Long id,
                               @RequestParam(value = "comment", required = false) String comment,
                               @RequestParam(value = "weekStart", required = false) String weekStart,
                               Authentication authentication, RedirectAttributes ra) {

        User approver = currentUserService.require(authentication);
        flash(ra, staffScheduleService.approveShift(id, approver, comment),
                "Đã phê duyệt phiên trực. Hệ thống đã tự tạo ngày nghỉ bù nếu là trực 24/24 giờ.");
        return redirectToRoster(weekStart);
    }

    @PostMapping("/duty-roster/{id}/reject")
    public String rejectShift(@PathVariable Long id,
                              @RequestParam(value = "comment", required = false) String comment,
                              @RequestParam(value = "weekStart", required = false) String weekStart,
                              Authentication authentication, RedirectAttributes ra) {

        User approver = currentUserService.require(authentication);
        flash(ra, staffScheduleService.rejectShift(id, approver, comment), "Đã từ chối phiên trực.");
        return redirectToRoster(weekStart);
    }

    // ===================== HELPERS =====================

    private String redirectToRoster(String weekStart) {
        return (weekStart != null && !weekStart.isBlank())
                ? "redirect:/head/duty-roster?weekStart=" + weekStart
                : "redirect:/head/duty-roster";
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - (long) DayOfWeek.MONDAY.getValue());
    }

    private void flash(RedirectAttributes ra, String error, String successMessage) {
        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage", successMessage);
        }
    }

    private String prepare(Model model, User user, Department department,
                           String activePage, String view) {
        model.addAttribute("currentUser", user);
        model.addAttribute("headDepartment", department);
        model.addAttribute("activePage", activePage);
        return view;
    }
}
