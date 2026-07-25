package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.ScheduleRepository;
import com.bookinghealthy.service.TimeSlotService; // <-- Import Service mới
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ScheduleInfoController {

    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private TimeSlotService timeSlotService; // <-- Tiêm Service

    // 1. TRANG GIỜ LÀM VIỆC
    @GetMapping("/working-hours")
    public String showWorkingHours(Model model) {
        model.addAttribute("activePage", "schedule");
        return "user/working-hours";
    }

    // 2. TRANG QUY TRÌNH KHÁM
    @GetMapping("/medical-process")
    public String showMedicalProcess(Model model) {
        model.addAttribute("activePage", "schedule");
        return "user/medical-process";
    }

    // 3. TRANG LỊCH TRỰC BÁC SĨ (ĐÃ NÂNG CẤP LOGIC)
    @GetMapping("/doctor-schedule")
    public String showDoctorSchedule(
            @RequestParam(value = "searchDate", required = false) LocalDate searchDate,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            Model model) {

        if (searchDate == null) {
            searchDate = LocalDate.now();
        }

        // 1. Lấy tất cả lịch CÓ HIỆU LỰC trong ngày. Lịch khám gắn với từng tuần nên phải
        // hỏi theo ngày cụ thể, không phải theo thứ trong tuần chung chung.
        List<Schedule> schedules = scheduleRepository.findEffectiveOn(searchDate);

        // 2. Lọc
        if (departmentId != null) {
            schedules = schedules.stream()
                    .filter(s -> s.getDoctor().getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }
        if (doctorId != null) {
            schedules = schedules.stream()
                    .filter(s -> s.getDoctor().getId().equals(doctorId))
                    .collect(Collectors.toList());
        }

        // 3. Gom nhóm: Map<Doctor, List<Schedule>>
        Map<Doctor, List<Schedule>> rawMap = schedules.stream()
                .collect(Collectors.groupingBy(Schedule::getDoctor));

        // 4. CHUYỂN ĐỔI SANG SLOT (Logic Mới)
        // Map<Doctor, List<TimeSlotDTO>>: Mỗi bác sĩ sẽ có 1 danh sách các ô giờ 30p
        Map<Doctor, List<TimeSlotService.TimeSlotDTO>> finalMap = new LinkedHashMap<>();

        for (Map.Entry<Doctor, List<Schedule>> entry : rawMap.entrySet()) {
            Doctor doctor = entry.getKey();
            List<Schedule> doctorWorkShifts = entry.getValue();

            // Gọi Service để tính toán slot cho bác sĩ này vào ngày này
            List<TimeSlotService.TimeSlotDTO> slots = timeSlotService.getAvailableSlots(doctor.getId(), searchDate, doctorWorkShifts);

            if (!slots.isEmpty()) {
                finalMap.put(doctor, slots);
            }
        }

        model.addAttribute("doctorSlotsMap", finalMap); // Gửi Map mới
        model.addAttribute("searchDate", searchDate);
        model.addAttribute("currentDepartmentId", departmentId);
        model.addAttribute("currentDoctorId", doctorId);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("doctors", doctorRepository.findAll());
        model.addAttribute("activePage", "schedule");

        return "user/doctor-schedule";
    }
}