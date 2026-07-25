package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Review;
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.ScheduleRepository;
import com.bookinghealthy.service.DepartmentService;
import com.bookinghealthy.service.DoctorService;
import com.bookinghealthy.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/")
public class DoctorController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @GetMapping("/doctors")
    public String listDoctors(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            Model model) {

        List<Doctor> doctors = doctorService.searchDoctors(keyword, departmentId);
        List<Department> departments = departmentService.findAll();

        model.addAttribute("doctors", doctors);
        model.addAttribute("departments", departments);
        model.addAttribute("currentKeyword", keyword);
        model.addAttribute("currentDepartmentId", departmentId);

        return "user/doctors";
    }

    @GetMapping("/doctors/{id}")
    public String showDoctorDetails(@PathVariable("id") Long id, Model model) {

        Optional<Doctor> doctorOpt = doctorService.findById(id);

        if (doctorOpt.isEmpty()) {
            return "redirect:/doctors";
        }

        Doctor doctor = doctorOpt.get();
        model.addAttribute("doctor", doctor);

        // 1. DỮ LIỆU ĐÁNH GIÁ
        List<Review> reviews = reviewService.getReviewsByDoctor(id);
        Double averageRating = reviewService.getAverageRating(id);
        Long reviewCount = reviewService.countReviews(id);

        model.addAttribute("reviews", reviews);
        model.addAttribute("averageRating", averageRating != null ? averageRating : 5.0);
        model.addAttribute("reviewCount", reviewCount != null ? reviewCount : 0);

        // 2. DỮ LIỆU THỐNG KÊ (Số ca đã khám thành công)
        try {
            List<Booking> completedBookings = bookingRepository.findByDoctorIdAndStatus(id, BookingStatus.COMPLETED);
            long treatedCount = completedBookings.size();

            // Nếu có dữ liệu thì hiển thị số thật, nếu không (0) thì hiển thị số liệu marketing
            model.addAttribute("treatedCount", treatedCount > 0 ? treatedCount : "5k+");
        } catch (Exception e) {
            model.addAttribute("treatedCount", "5k+"); // Fallback an toàn
        }

        // Lịch làm việc hiển thị trên trang giới thiệu là lịch của TUẦN NÀY.
        List<Schedule> schedules = scheduleRepository.findEffective(id, LocalDate.now());
        Map<String, List<String>> workingScheduleMap = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Schedule s : schedules) {
            String day = s.getDayOfWeek().name(); // VD: MONDAY, TUESDAY...
            String time = s.getStartTime().format(formatter); // VD: "08:00"
            workingScheduleMap.computeIfAbsent(day, k -> new ArrayList<>()).add(time);
        }
        model.addAttribute("workingScheduleMap", workingScheduleMap);

        return "user/doctor-details";
    }
}