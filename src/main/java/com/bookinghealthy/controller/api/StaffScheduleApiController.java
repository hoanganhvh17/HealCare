package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.LeaveBalanceDTO;
import com.bookinghealthy.dto.ScheduleEventDTO;
import com.bookinghealthy.model.*;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.LeaveService;
import com.bookinghealthy.service.ShiftCoverService;
import com.bookinghealthy.service.StaffScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API cho lưới lịch làm việc. Dùng chung cho bác sĩ và lễ tân — dữ liệu luôn được lọc
 * theo người đang đăng nhập nên không cần phân quyền theo vai trò ở đây.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffScheduleApiController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private CurrentUserService currentUserService;
    @Autowired private StaffScheduleService staffScheduleService;
    @Autowired private LeaveService leaveService;
    @Autowired private ShiftCoverService shiftCoverService;

    /** Sự kiện trong khoảng ngày, đã gom từ ca khám / phiên trực / đơn nghỉ / giờ bận. */
    @GetMapping("/schedule")
    public ResponseEntity<List<ScheduleEventDTO>> getSchedule(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {

        User user = currentUserService.require(authentication);
        return ResponseEntity.ok(staffScheduleService.getEvents(user, from, to));
    }

    @GetMapping("/leave-balance")
    public ResponseEntity<LeaveBalanceDTO> getLeaveBalance(Authentication authentication) {
        User user = currentUserService.require(authentication);
        return ResponseEntity.ok(leaveService.getBalance(user, LocalDate.now().getYear()));
    }

    /**
     * Nội dung chuông thông báo: kết quả đơn nghỉ / ca trực gần đây, lời mời nhận ca,
     * ca của mình đang cần người thay, và với trưởng khoa là số đơn đang chờ duyệt.
     */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(Authentication authentication) {
        User user = currentUserService.require(authentication);
        List<Map<String, String>> items = new ArrayList<>();

        addClinicRegistrationReminder(items, user);
        addDecidedLeaveNotifications(items, user);
        addCoverInvitations(items, user);
        addNeedsCoverNotifications(items, user);
        addHeadDoctorNotifications(items, user);

        Map<String, Object> response = new HashMap<>();
        response.put("count", items.size());
        response.put("items", items);
        return ResponseEntity.ok(response);
    }

    /**
     * Nhắc bác sĩ đăng ký lịch khám tuần sau nếu chưa đăng ký. Chỉ hiện từ Thứ 5 trở đi
     * (getValue() >= 4) để nhắc đúng cuối tuần trước, khớp với mốc nhắc email vào Chủ nhật.
     */
    private void addClinicRegistrationReminder(List<Map<String, String>> items, User user) {
        if (currentUserService.findDoctor(user).isEmpty()) {
            return;
        }
        if (LocalDate.now().getDayOfWeek().getValue() < 4) {
            return;
        }
        if (staffScheduleService.hasRegisteredForNextWeek(user.getId())) {
            return;
        }
        // Quá hạn chốt lịch thì có nhắc cũng không đăng ký được nữa — hệ thống đã tự xếp.
        if (!staffScheduleService.isClinicRegistrationOpen()) {
            return;
        }
        LocalDate week = staffScheduleService.nextWeekStart();
        items.add(item("bi-calendar2-week text-warning",
                "Chưa đăng ký lịch khám tuần sau",
                week.format(DATE_FORMAT) + " - " + week.plusDays(6).format(DATE_FORMAT)));
    }

    /** Đơn đã có quyết định trong 7 ngày gần đây — thứ nhân viên cần biết ngay. */
    private void addDecidedLeaveNotifications(List<Map<String, String>> items, User user) {
        LocalDate cutoff = LocalDate.now().minusDays(7);

        for (LeaveRequest leave : leaveService.findByUser(user.getId())) {
            boolean decidedRecently = leave.getDecidedAt() != null
                    && !leave.getDecidedAt().toLocalDate().isBefore(cutoff);
            boolean decided = leave.getStatus() == ApprovalStatus.APPROVED
                    || leave.getStatus() == ApprovalStatus.REJECTED;

            if (decided && decidedRecently) {
                items.add(item(
                        leave.getStatus() == ApprovalStatus.APPROVED
                                ? "bi-check-circle text-success" : "bi-x-circle text-danger",
                        "Đơn " + leave.getLeaveType().getLabel() + " " + leave.getStatus().getLabel().toLowerCase(),
                        leave.getStartDate().format(DATE_FORMAT)));
            }
        }
    }

    private void addCoverInvitations(List<Map<String, String>> items, User user) {
        for (ShiftCoverRequest request : shiftCoverService.findPendingForUser(user)) {
            items.add(item("bi-arrow-left-right text-primary",
                    request.getRequester().getFullName() + " cần người thay ca",
                    request.getShift().getShiftType().getLabel() + " - "
                            + request.getShift().getShiftDate().format(DATE_FORMAT)));
        }
    }

    private void addNeedsCoverNotifications(List<Map<String, String>> items, User user) {
        for (StaffShift shift : staffScheduleService.findShiftsNeedingCover(user.getId())) {
            items.add(item("bi-exclamation-triangle text-danger",
                    "Ca của anh/chị chưa có người thay",
                    shift.getShiftType().getLabel() + " - " + shift.getShiftDate().format(DATE_FORMAT)));
        }
    }

    private void addHeadDoctorNotifications(List<Map<String, String>> items, User user) {
        Department headDepartment = currentUserService.resolveHeadDepartment(user);
        if (headDepartment == null) {
            return;
        }

        long pendingLeaves = leaveService.countPendingInDepartment(headDepartment.getId());
        if (pendingLeaves > 0) {
            items.add(item("bi-inbox text-warning",
                    pendingLeaves + " đơn nghỉ đang chờ anh/chị duyệt",
                    "Khoa " + headDepartment.getName()));
        }

        int pendingShifts = staffScheduleService
                .findDepartmentShiftsByStatus(headDepartment.getId(), ApprovalStatus.PENDING).size();
        if (pendingShifts > 0) {
            items.add(item("bi-clipboard-check text-warning",
                    pendingShifts + " ca trực đang chờ phê duyệt",
                    "Khoa " + headDepartment.getName()));
        }
    }

    private Map<String, String> item(String icon, String title, String subtitle) {
        Map<String, String> entry = new HashMap<>();
        entry.put("icon", icon);
        entry.put("title", title);
        entry.put("subtitle", subtitle);
        return entry;
    }
}
