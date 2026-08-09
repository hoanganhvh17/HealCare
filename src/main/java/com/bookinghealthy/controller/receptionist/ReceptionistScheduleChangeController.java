package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.dto.BulkResultDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.service.ReceptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Hủy / dời lịch hàng loạt khi bác sĩ bận đột xuất.
 */
@Controller
@RequestMapping("/receptionist/schedule-change")
public class ReceptionistScheduleChangeController {

    @Autowired private ReceptionService receptionService;
    @Autowired private DoctorRepository doctorRepository;

    @GetMapping
    public String showForm(@RequestParam(value = "doctorId", required = false) Long doctorId,
                           @RequestParam(value = "date", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(value = "session", required = false) String session,
                           Model model) {

        List<Doctor> allDoctors = doctorRepository.findAll();
        model.addAttribute("allDoctors", allDoctors);

        LocalDate selectedDate = (date != null) ? date : LocalDate.now();
        String selectedSession = (session != null && !session.isBlank())
                ? session : ReceptionService.SESSION_ALL_DAY;

        List<Booking> bookings = Collections.emptyList();
        Doctor selectedDoctor = null;
        List<Doctor> replacementDoctors = Collections.emptyList();

        if (doctorId != null) {
            selectedDoctor = doctorRepository.findById(doctorId).orElse(null);
            bookings = receptionService.findBookingsForChange(doctorId, selectedDate, selectedSession);

            // Chỉ cho phép chuyển sang bác sĩ CÙNG CHUYÊN KHOA để giữ đúng chuyên môn ca khám.
            // Khoa nào chỉ có một bác sĩ thì danh sách rỗng — giao diện sẽ hướng lễ tân sang phương án hủy + hoàn tiền.
            Long departmentId = (selectedDoctor != null && selectedDoctor.getDepartment() != null)
                    ? selectedDoctor.getDepartment().getId() : null;
            final Long excludeId = doctorId;

            if (departmentId != null) {
                replacementDoctors = allDoctors.stream()
                        .filter(d -> !excludeId.equals(d.getId()))
                        .filter(d -> d.getDepartment() != null && departmentId.equals(d.getDepartment().getId()))
                        .toList();
            }
        }

        model.addAttribute("selectedDoctor", selectedDoctor);
        model.addAttribute("selectedDoctorId", doctorId);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedSession", selectedSession);
        model.addAttribute("bookings", bookings);
        model.addAttribute("replacementDoctors", replacementDoctors);
        model.addAttribute("searched", doctorId != null);
        // Ngày đã qua thì chỉ còn tra cứu: template ẩn hẳn hai form hủy/chuyển. Lưu ý mốc là
        // NGÀY chứ không phải giờ — công cụ này sinh ra cho tình huống "bác sĩ ốm giữa buổi",
        // nên các ca đã qua giờ TRONG HÔM NAY vẫn phải hủy/chuyển được.
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isPastDate", selectedDate.isBefore(LocalDate.now()));
        model.addAttribute("activePage", "schedule-change");

        return "receptionist/schedule-change";
    }

    /**
     * Ngày đã trôi qua thì hủy/chuyển hàng loạt không còn ý nghĩa: hủy sẽ hoàn tiền cho những
     * ca bệnh nhân đã đến khám thật, còn chuyển thì đẩy một ca của tuần trước sang bác sĩ khác.
     *
     * @return null nếu còn thao tác được, ngược lại là lý do bằng tiếng Việt.
     */
    private String whyCannotChangeDate(LocalDate date) {
        if (date == null) {
            return "Thiếu ngày cần đổi lịch.";
        }
        if (date.isBefore(LocalDate.now())) {
            return "Ngày " + date + " đã qua nên không hủy/chuyển lịch hàng loạt được nữa.";
        }
        return null;
    }

    @PostMapping("/cancel-bulk")
    public String cancelBulk(@RequestParam(value = "bookingIds", required = false) List<Long> bookingIds,
                             @RequestParam(value = "reason", required = false) String reason,
                             @RequestParam(value = "blockDoctor", required = false) Boolean blockDoctor,
                             @RequestParam("doctorId") Long doctorId,
                             @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam("session") String session,
                             RedirectAttributes ra) {

        String blocked = whyCannotChangeDate(date);
        if (blocked != null) {
            ra.addFlashAttribute("errorMessage", blocked);
            return redirectToFilter(doctorId, date, session);
        }

        BulkResultDTO result = receptionService.bulkCancel(bookingIds, reason);
        flashResult(ra, result, "Đã hủy " + result.getSuccessCount() + " lịch hẹn và gửi email xin lỗi.");
        applyBlockIfRequested(ra, blockDoctor, doctorId, date, session, reason);

        return redirectToFilter(doctorId, date, session);
    }

    @PostMapping("/transfer-bulk")
    public String transferBulk(@RequestParam(value = "bookingIds", required = false) List<Long> bookingIds,
                               @RequestParam(value = "newDoctorId", required = false) Long newDoctorId,
                               @RequestParam(value = "reason", required = false) String reason,
                               @RequestParam(value = "blockDoctor", required = false) Boolean blockDoctor,
                               @RequestParam("doctorId") Long doctorId,
                               @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam("session") String session,
                               RedirectAttributes ra) {

        String blocked = whyCannotChangeDate(date);
        if (blocked != null) {
            ra.addFlashAttribute("errorMessage", blocked);
            return redirectToFilter(doctorId, date, session);
        }

        BulkResultDTO result = receptionService.bulkTransfer(bookingIds, newDoctorId, reason);
        flashResult(ra, result, "Đã chuyển " + result.getSuccessCount()
                + " lịch hẹn sang bác sĩ mới và gửi email xin lỗi kèm thông báo đổi bác sĩ.");
        applyBlockIfRequested(ra, blockDoctor, doctorId, date, session, reason);

        return redirectToFilter(doctorId, date, session);
    }

    // ===================== HELPERS =====================

    private void flashResult(RedirectAttributes ra, BulkResultDTO result, String successMessage) {
        if (result.getSuccessCount() > 0) {
            ra.addFlashAttribute("successMessage", successMessage);
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("bulkErrors", result.getErrors());
        }
    }

    /**
     * Chặn luôn khung giờ của bác sĩ nghỉ để không có lịch mới rơi vào khoảng vừa dọn trống.
     */
    private void applyBlockIfRequested(RedirectAttributes ra, Boolean blockDoctor, Long doctorId,
                                       LocalDate date, String session, String reason) {
        if (!Boolean.TRUE.equals(blockDoctor)) {
            return;
        }
        try {
            // blockTime() trả null khi thành công, trả chuỗi lý do khi không chặn được
            String failureReason = receptionService.blockSessionForDoctor(doctorId, date, session, reason);

            if (failureReason == null) {
                ra.addFlashAttribute("blockMessage",
                        "Đã chặn khung giờ này của bác sĩ, sẽ không có bệnh nhân mới đặt vào.");
            } else {
                ra.addFlashAttribute("errorMessage", "Không chặn được lịch bác sĩ: " + failureReason);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không chặn được lịch bác sĩ: " + e.getMessage());
        }
    }

    private String redirectToFilter(Long doctorId, LocalDate date, String session) {
        return "redirect:/receptionist/schedule-change?doctorId=" + doctorId
                + "&date=" + date
                + "&session=" + session;
    }
}
