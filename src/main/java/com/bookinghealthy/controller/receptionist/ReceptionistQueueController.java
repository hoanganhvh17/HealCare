package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.service.ReceptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Điều phối hàng chờ khám: đẩy bệnh nhân đến trễ xuống cuối danh sách
 * để không ảnh hưởng người đặt đúng giờ.
 */
@Controller
@RequestMapping("/receptionist/queue")
public class ReceptionistQueueController {

    @Autowired private ReceptionService receptionService;
    @Autowired private DoctorRepository doctorRepository;

    @GetMapping
    public String showQueue(@RequestParam(value = "doctorId", required = false) Long doctorId,
                            @RequestParam(value = "date", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Model model) {

        LocalDate selectedDate = (date != null) ? date : LocalDate.now();

        List<Booking> queue = Collections.emptyList();
        Doctor selectedDoctor = null;

        if (doctorId != null) {
            selectedDoctor = doctorRepository.findById(doctorId).orElse(null);
            queue = receptionService.getQueue(doctorId, selectedDate);
        }

        // Ngày đã qua thì hàng chờ chỉ còn để TRA CỨU: template ẩn hẳn nút "Đẩy xuống cuối" /
        // "Hoàn tác" và in lý do, thay vì để lễ tân bấm rồi mới nhận thông báo lỗi.
        // Dùng đúng hàm mà service gọi khi chặn thật, nên hai bên không thể nói khác nhau.
        Map<Long, String> queueBlockReasons = new HashMap<>();
        for (Booking booking : queue) {
            queueBlockReasons.put(booking.getId(), receptionService.whyCannotReorderQueue(booking));
        }

        model.addAttribute("allDoctors", doctorRepository.findAll());
        model.addAttribute("selectedDoctor", selectedDoctor);
        model.addAttribute("selectedDoctorId", doctorId);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("queue", queue);
        model.addAttribute("queueBlockReasons", queueBlockReasons);
        model.addAttribute("isPastDate", selectedDate.isBefore(LocalDate.now()));
        model.addAttribute("searched", doctorId != null);
        model.addAttribute("activePage", "queue");

        return "receptionist/queue";
    }

    @PostMapping("/push-last/{id}")
    public String pushLast(@PathVariable Long id,
                           @RequestParam("doctorId") Long doctorId,
                           @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           RedirectAttributes ra) {
        try {
            receptionService.pushToEndOfQueue(id);
            ra.addFlashAttribute("successMessage",
                    "Đã đẩy bệnh nhân của lịch #" + id + " xuống cuối hàng chờ.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/receptionist/queue?doctorId=" + doctorId + "&date=" + date;
    }

    @PostMapping("/reset/{id}")
    public String reset(@PathVariable Long id,
                        @RequestParam("doctorId") Long doctorId,
                        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        RedirectAttributes ra) {
        try {
            receptionService.resetQueuePosition(id);
            ra.addFlashAttribute("successMessage",
                    "Đã trả lịch #" + id + " về đúng vị trí theo giờ hẹn.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/receptionist/queue?doctorId=" + doctorId + "&date=" + date;
    }
}
