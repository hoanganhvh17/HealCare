package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.LeaveBalanceDTO;
import com.bookinghealthy.dto.ScheduleEventDTO;
import com.bookinghealthy.model.*;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.LeaveService;
import com.bookinghealthy.service.NotificationService;
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
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired private CurrentUserService currentUserService;
    @Autowired private StaffScheduleService staffScheduleService;
    @Autowired private LeaveService leaveService;
    @Autowired private ShiftCoverService shiftCoverService;
    @Autowired private NotificationService notificationService;

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
     * Nội dung chuông thông báo, gom từ HAI nguồn:
     * <ol>
     *   <li>Thông báo đã lưu trong bảng {@code notifications} — quyết định của trưởng khoa,
     *       lịch được xếp hộ… Đây là phần có trạng thái đã/chưa đọc và bấm vào được.</li>
     *   <li>Các lời nhắc TÍNH ĐỘNG từ dữ liệu hiện tại: chưa đăng ký lịch tuần sau, lời mời
     *       nhận ca, ca đang cần người thay, và với trưởng khoa là số việc đang chờ duyệt.
     *       Những mục này không lưu được vì chúng phải tự biến mất khi việc đã xong.</li>
     * </ol>
     */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(Authentication authentication) {
        User user = currentUserService.require(authentication);
        List<Map<String, Object>> items = new ArrayList<>();

        addStoredNotifications(items, user);
        addClinicRegistrationReminder(items, user);
        addCoverInvitations(items, user);
        addNeedsCoverNotifications(items, user);
        addHeadDoctorNotifications(items, user);

        Map<String, Object> response = new HashMap<>();
        response.put("count", items.size());
        response.put("unreadCount", notificationService.countUnread(user.getId()));
        response.put("items", items);
        return ResponseEntity.ok(response);
    }

    /** Mở chuông là coi như đã đọc — chuông không có nút đánh dấu riêng từng mục. */
    @PostMapping("/notifications/read")
    public ResponseEntity<Map<String, Object>> markNotificationsRead(Authentication authentication) {
        User user = currentUserService.require(authentication);
        notificationService.markAllRead(user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", 0);
        return ResponseEntity.ok(response);
    }

    private void addStoredNotifications(List<Map<String, Object>> items, User user) {
        for (Notification saved : notificationService.recent(user.getId())) {
            Map<String, Object> entry = item(saved.getIcon(), saved.getTitle(), saved.getMessage());
            entry.put("link", saved.getLink());
            entry.put("read", saved.isReadFlag());
            entry.put("time", saved.getCreatedAt() != null
                    ? saved.getCreatedAt().format(DATE_TIME_FORMAT) : null);
            items.add(entry);
        }
    }

    /**
     * Nhắc bác sĩ đăng ký lịch khám tuần sau nếu chưa đăng ký. Chỉ hiện từ Thứ 5 trở đi
     * (getValue() >= 4) để nhắc đúng cuối tuần trước, khớp với mốc nhắc email vào Chủ nhật.
     */
    private void addClinicRegistrationReminder(List<Map<String, Object>> items, User user) {
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

    // Quyết định về đơn nghỉ / ca trực KHÔNG còn tính động ở đây nữa: LeaveServiceImpl và
    // StaffScheduleServiceImpl đã ghi hẳn một bản ghi Notification lúc ra quyết định, nên
    // tính lại lần nữa chỉ làm mỗi quyết định hiện hai lần trong chuông.

    private void addCoverInvitations(List<Map<String, Object>> items, User user) {
        for (ShiftCoverRequest request : shiftCoverService.findPendingForUser(user)) {
            items.add(item("bi-arrow-left-right text-primary",
                    request.getRequester().getFullName() + " cần người thay ca",
                    request.getShift().getShiftType().getLabel() + " - "
                            + request.getShift().getShiftDate().format(DATE_FORMAT)));
        }
    }

    private void addNeedsCoverNotifications(List<Map<String, Object>> items, User user) {
        for (StaffShift shift : staffScheduleService.findShiftsNeedingCover(user.getId())) {
            items.add(item("bi-exclamation-triangle text-danger",
                    "Ca của anh/chị chưa có người thay",
                    shift.getShiftType().getLabel() + " - " + shift.getShiftDate().format(DATE_FORMAT)));
        }
    }

    private void addHeadDoctorNotifications(List<Map<String, Object>> items, User user) {
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

        // Ca đã trực xong thì không duyệt được nữa, đếm vào đây chỉ làm chuông báo mãi.
        long pendingShifts = staffScheduleService
                .findDepartmentShiftsByStatus(headDepartment.getId(), ApprovalStatus.PENDING).stream()
                .filter(shift -> staffScheduleService.whyCannotDecideShift(shift) == null)
                .count();
        if (pendingShifts > 0) {
            items.add(item("bi-clipboard-check text-warning",
                    pendingShifts + " ca trực đang chờ phê duyệt",
                    "Khoa " + headDepartment.getName()));
        }
    }

    /**
     * Một dòng trong chuông. Các mục tính động không có id nên cũng không có trạng thái đã
     * đọc — {@code read = true} để chúng không làm badge số chưa đọc nhảy lên.
     */
    private Map<String, Object> item(String icon, String title, String subtitle) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("icon", icon);
        entry.put("title", title);
        entry.put("subtitle", subtitle);
        entry.put("read", true);
        return entry;
    }
}
