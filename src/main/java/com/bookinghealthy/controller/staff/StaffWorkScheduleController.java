package com.bookinghealthy.controller.staff;

import com.bookinghealthy.dto.LeaveRequestDTO;
import com.bookinghealthy.dto.ShiftRegisterResultDTO;
import com.bookinghealthy.model.*;
import com.bookinghealthy.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Trang "Lịch làm việc & Nghỉ phép" — DÙNG CHUNG cho bác sĩ và lễ tân.
 *
 * Hai vai trò cùng dùng một template {@code staff/work-schedule.html}; chỉ khác sidebar,
 * được chọn qua thuộc tính model {@code sidebarFragment} và Thymeleaf preprocessing.
 * Lớp cha này giữ toàn bộ logic, hai lớp con chỉ khai báo URL và sidebar tương ứng.
 */
public abstract class StaffWorkScheduleController {

    @Autowired protected CurrentUserService currentUserService;
    @Autowired protected StaffScheduleService staffScheduleService;
    @Autowired protected LeaveService leaveService;
    @Autowired protected ShiftCoverService shiftCoverService;

    /** Tiền tố URL của vai trò: "/doctor" hoặc "/receptionist". */
    protected abstract String basePath();

    /** Fragment sidebar tương ứng, ví dụ "doctor/include/sidebar". */
    protected abstract String sidebarFragment();

    // ===================== TRANG CHÍNH =====================

    @GetMapping("/work-schedule")
    public String showWorkSchedule(Model model, Authentication authentication) {
        User user = currentUserService.require(authentication);
        int year = LocalDate.now().getYear();

        model.addAttribute("currentUser", user);
        model.addAttribute("balance", leaveService.getBalance(user, year));
        model.addAttribute("leaveRequests", leaveService.findByUser(user.getId()));
        model.addAttribute("dutyShifts", staffScheduleService.findDutyShifts(user.getId()));
        model.addAttribute("shiftsNeedingCover", staffScheduleService.findShiftsNeedingCover(user.getId()));
        model.addAttribute("coverInvitations", shiftCoverService.findPendingForUser(user));
        model.addAttribute("department", currentUserService.resolveDepartment(user));
        model.addAttribute("headDepartment", currentUserService.resolveHeadDepartment(user));

        // Dữ liệu đổ vào các form trong modal.
        // Modal "Đăng ký ca" chỉ còn phiên trực + hội chẩn (ngày cụ thể); ca khám tách sang
        // bảng đăng ký theo tuần bên dưới.
        model.addAttribute("dutyShiftTypes", java.util.Arrays.stream(ShiftType.values())
                .filter(type -> !type.isClinic()).toList());
        model.addAttribute("dutyRoles", DutyRole.values());
        model.addAttribute("leaveTypes", selectableLeaveTypes());
        model.addAttribute("personalReasons", PersonalLeaveReason.values());
        model.addAttribute("halfDayOptions", HalfDaySession.values());

        // Bảng đăng ký ca khám (chỉ bác sĩ). Tích sẵn theo lịch TUẦN SAU — tuần duy nhất sửa được.
        boolean isDoctor = currentUserService.findDoctor(user).isPresent();
        model.addAttribute("isDoctor", isDoctor);
        model.addAttribute("weekDays", java.time.DayOfWeek.values());
        java.util.Set<String> morningDays = new java.util.HashSet<>();
        java.util.Set<String> afternoonDays = new java.util.HashSet<>();
        for (com.bookinghealthy.model.Schedule schedule : staffScheduleService.getClinicSchedules(user.getId())) {
            // Buổi sáng nếu bắt đầu trước 12h, ngược lại là buổi chiều.
            if (schedule.getStartTime().getHour() < 12) {
                morningDays.add(schedule.getDayOfWeek().name());
            } else {
                afternoonDays.add(schedule.getDayOfWeek().name());
            }
        }
        model.addAttribute("clinicMorningDays", morningDays);
        model.addAttribute("clinicAfternoonDays", afternoonDays);

        // Đăng ký lịch khám là đăng ký cho TUẦN SAU — hiển thị khoảng ngày để bác sĩ biết rõ.
        java.time.LocalDate nextWeek = staffScheduleService.nextWeekStart();
        model.addAttribute("nextWeekStart", nextWeek);
        model.addAttribute("nextWeekEnd", nextWeek.plusDays(6));
        model.addAttribute("registeredNextWeek", isDoctor && staffScheduleService.hasRegisteredForNextWeek(user.getId()));

        // Tuần hiện tại đang chạy nên chỉ để xem; form đăng ký khóa lại sau hạn chốt lịch.
        model.addAttribute("currentWeekStart", com.bookinghealthy.config.LeavePolicy.weekStartOf(LocalDate.now()));
        model.addAttribute("clinicOpen", staffScheduleService.isClinicRegistrationOpen());
        model.addAttribute("clinicDeadline", staffScheduleService.clinicDeadlineLabel());

        // Chặn ngay ở ô chọn ngày thay vì để nhân viên điền hết modal rồi mới báo lỗi:
        // validateShift() từ chối ca quá khứ, LeaveServiceImpl.validate() từ chối đơn nghỉ
        // quá khứ, và "báo bận đột xuất" chỉ dùng cho trong vòng 24 giờ tới.
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("emergencyMaxDate", LocalDate.now().plusDays(1));

        model.addAttribute("basePath", basePath());
        model.addAttribute("sidebarFragment", sidebarFragment());
        model.addAttribute("activePage", "work-schedule");
        return "staff/work-schedule";
    }

    /** Nghỉ bù sau trực do hệ thống tự tạo nên không hiện trong form đăng ký. */
    private List<LeaveType> selectableLeaveTypes() {
        return java.util.Arrays.stream(LeaveType.values())
                .filter(type -> !type.isSystemGenerated())
                .toList();
    }

    @GetMapping("/leave-history")
    public String showLeaveHistory(Model model, Authentication authentication) {
        User user = currentUserService.require(authentication);

        model.addAttribute("currentUser", user);
        model.addAttribute("leaveRequests", leaveService.findByUser(user.getId()));
        model.addAttribute("balance", leaveService.getBalance(user, LocalDate.now().getYear()));
        model.addAttribute("basePath", basePath());
        model.addAttribute("sidebarFragment", sidebarFragment());
        model.addAttribute("activePage", "work-schedule");
        return "staff/leave-history";
    }

    // ===================== ĐĂNG KÝ CA KHÁM THEO TUẦN =====================

    @PostMapping("/clinic/save")
    public String saveClinicTemplate(
            @RequestParam(value = "morningDays", required = false) List<java.time.DayOfWeek> morningDays,
            @RequestParam(value = "afternoonDays", required = false) List<java.time.DayOfWeek> afternoonDays,
            Authentication authentication,
            RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        String error = staffScheduleService.saveClinicTemplate(
                user,
                morningDays != null ? morningDays : List.of(),
                afternoonDays != null ? afternoonDays : List.of());

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage",
                    "Đã cập nhật lịch khám hằng tuần. Bệnh nhân đặt được vào các khung giờ đã chọn.");
        }
        return redirectToSchedule();
    }

    // ===================== ĐĂNG KÝ PHIÊN TRỰC / HỘI CHẨN =====================

    /**
     * Đăng ký phiên trực hoặc hội chẩn. Cho phép LẶP LẠI hằng tuần trong nhiều tuần liền
     * (mỗi tuần một ca cùng thứ) để đỡ phải thao tác nhiều lần.
     */
    @PostMapping("/shift/register")
    public String registerShift(@RequestParam("shiftType") ShiftType shiftType,
                                @RequestParam("shiftDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shiftDate,
                                @RequestParam(value = "dutyRole", required = false) DutyRole dutyRole,
                                @RequestParam(value = "note", required = false) String note,
                                @RequestParam(value = "repeatWeeks", required = false, defaultValue = "1") int repeatWeeks,
                                Authentication authentication,
                                RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        int weeks = Math.max(1, Math.min(repeatWeeks, 12)); // tối đa 12 tuần một lần

        int success = 0;
        String firstError = null;
        String warning = null;

        for (int i = 0; i < weeks; i++) {
            ShiftRegisterResultDTO result = staffScheduleService.registerShift(
                    user, shiftType, shiftDate.plusWeeks(i), dutyRole, note);
            if (result.isSuccess()) {
                success++;
                if (result.getWarning() != null) {
                    warning = result.getWarning();
                }
            } else if (firstError == null) {
                firstError = result.getError();
            }
        }

        if (success == 0) {
            ra.addFlashAttribute("errorMessage", firstError);
        } else {
            String verb = shiftType.isDuty() ? "phiên trực (đang chờ trưởng khoa duyệt)" : "hội chẩn";
            ra.addFlashAttribute("successMessage",
                    "Đã đăng ký " + success + " " + verb + ".");
            if (firstError != null) {
                ra.addFlashAttribute("warningMessage", "Một số ngày bị bỏ qua: " + firstError);
            } else if (warning != null) {
                ra.addFlashAttribute("warningMessage", warning);
            }
        }
        return redirectToSchedule();
    }

    @PostMapping("/shift/cancel/{id}")
    public String cancelShift(@PathVariable("id") Long shiftId,
                              Authentication authentication,
                              RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        String error = staffScheduleService.cancelShift(shiftId, user);

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage", "Đã hủy ca làm việc.");
        }
        return redirectToSchedule();
    }

    // ===================== ĐƠN NGHỈ =====================

    @PostMapping("/leave/submit")
    public String submitLeave(@ModelAttribute LeaveRequestDTO form,
                              Authentication authentication,
                              RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        String error = leaveService.submit(user, form);

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else if (form.isEmergency()) {
            ra.addFlashAttribute("successMessage",
                    "Đã ghi nhận báo bận đột xuất. Khung giờ khám đã được chặn ngay, "
                            + "đơn đang chờ trưởng khoa duyệt.");
        } else {
            ra.addFlashAttribute("successMessage",
                    "Đã gửi đơn nghỉ, đang chờ trưởng khoa phê duyệt.");
        }
        return redirectToSchedule();
    }

    @PostMapping("/leave/cancel/{id}")
    public String cancelLeave(@PathVariable("id") Long leaveId,
                              Authentication authentication,
                              RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        String error = leaveService.cancelByOwner(leaveId, user);

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage", "Đã hủy yêu cầu nghỉ.");
        }
        return redirectToSchedule();
    }

    // ===================== TÌM NGƯỜI THAY CA =====================

    @GetMapping("/shift/cover/{id}")
    public String showCoverCandidates(@PathVariable("id") Long shiftId,
                                      Model model, Authentication authentication) {

        User user = currentUserService.require(authentication);

        // Chỉ cho xem ca của chính mình — tránh dò id ca của người khác.
        StaffShift shift = staffScheduleService.findShift(shiftId)
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .orElse(null);

        model.addAttribute("currentUser", user);
        model.addAttribute("shift", shift);
        model.addAttribute("shiftId", shiftId);
        model.addAttribute("candidates", shiftCoverService.findCandidates(shiftId));
        model.addAttribute("basePath", basePath());
        model.addAttribute("sidebarFragment", sidebarFragment());
        model.addAttribute("activePage", "work-schedule");
        return "staff/shift-cover";
    }

    @PostMapping("/shift/cover/{id}")
    public String requestCover(@PathVariable("id") Long shiftId,
                               @RequestParam(value = "targetUserId", required = false) Long targetUserId,
                               @RequestParam(value = "reason", required = false) String reason,
                               Authentication authentication,
                               RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        String error = shiftCoverService.request(shiftId, user, targetUserId, reason);

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage", targetUserId != null
                    ? "Đã gửi lời mời nhận ca tới đồng nghiệp."
                    : "Đã mở lời mời nhận ca cho cả khoa.");
        }
        return redirectToSchedule();
    }

    @PostMapping("/cover/accept/{id}")
    public String acceptCover(@PathVariable("id") Long requestId,
                              Authentication authentication,
                              RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        String error = shiftCoverService.accept(requestId, user);

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage", "Anh/chị đã nhận ca này.");
        }
        return redirectToSchedule();
    }

    @PostMapping("/cover/decline/{id}")
    public String declineCover(@PathVariable("id") Long requestId,
                               Authentication authentication,
                               RedirectAttributes ra) {

        User user = currentUserService.require(authentication);
        String error = shiftCoverService.decline(requestId, user);

        if (error != null) {
            ra.addFlashAttribute("errorMessage", error);
        } else {
            ra.addFlashAttribute("successMessage", "Đã từ chối lời mời nhận ca.");
        }
        return redirectToSchedule();
    }

    protected String redirectToSchedule() {
        return "redirect:" + basePath() + "/work-schedule";
    }
}
