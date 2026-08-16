package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.DoctorDTO;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.DoctorService;
import com.bookinghealthy.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/chat")
public class PatientChatLookupApiController {

    @Autowired private UserRepository userRepository;
    @Autowired private BookingService bookingService;
    @Autowired private DoctorService doctorService;
    @Autowired private ReviewService reviewService;
    @Autowired private com.bookinghealthy.service.TimeSlotService timeSlotService;

    @GetMapping("/my-bookings")
    public ResponseEntity<Map<String, Object>> myBookings() {
        Optional<User> currentUser = resolveCurrentUser(
                SecurityContextHolder.getContext().getAuthentication());
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_LOGGED_IN"));
        }

        List<Booking> all = bookingService.findByUser(currentUser.get());
        LocalDateTime now = LocalDateTime.now();

        List<Booking> upcoming = new ArrayList<>();
        List<Booking> past = new ArrayList<>();
        for (Booking b : all) {
            boolean stillOpen = b.getStatus() == BookingStatus.PENDING
                    || b.getStatus() == BookingStatus.CONFIRMED;
            if (stillOpen && !startOf(b).isBefore(now)) {
                upcoming.add(b);
            } else {
                past.add(b);
            }
        }
        
        
        upcoming.sort(Comparator.comparing(PatientChatLookupApiController::startOf));
        past.sort(Comparator.comparing(PatientChatLookupApiController::startOf).reversed());

        Map<String, Object> result = new HashMap<>();
        result.put("upcoming", upcoming.stream().limit(5).map(this::toBookingCard).toList());
        result.put("past", past.stream().limit(3).map(this::toBookingCard).toList());
        result.put("upcomingCount", upcoming.size());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toBookingCard(Booking b) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", b.getId());
        item.put("doctorName", b.getDoctor() != null && b.getDoctor().getUser() != null
                ? b.getDoctor().getUser().getFullName() : "—");
        item.put("departmentName", b.getDoctor() != null && b.getDoctor().getDepartment() != null
                ? b.getDoctor().getDepartment().getName() : "—");
        item.put("date", b.getAppointmentDate() != null ? b.getAppointmentDate().toString() : null);
        item.put("time", b.getAppointmentTime());
        item.put("status", b.getStatus() != null ? b.getStatus().name() : null);
        item.put("statusLabel", statusLabel(b.getStatus()));
        item.put("paymentStatus", b.getPaymentStatus());
        item.put("cancelBlockReason", bookingService.whyCannotCancel(b));
        item.put("rescheduleBlockReason", bookingService.whyCannotReschedule(b));
        return item;
    }

    private String statusLabel(BookingStatus status) {
        if (status == null) return "—";
        switch (status) {
            case PENDING:   return "Chờ xác nhận";
            case CONFIRMED: return "Đã xác nhận";
            case COMPLETED: return "Đã khám xong";
            case CANCELED:  return "Đã hủy";
            default:        return status.name();
        }
    }

    private static LocalDateTime startOf(Booking b) {
        LocalDate date = b.getAppointmentDate() != null ? b.getAppointmentDate() : LocalDate.MIN;
        try {
            String[] parts = b.getAppointmentTime().split(" - ");
            return date.atTime(LocalTime.parse(parts[0].trim()));
        } catch (Exception e) {
            return date.atStartOfDay();
        }
    }

    @GetMapping("/doctor-profile")
    public ResponseEntity<Map<String, Object>> doctorProfile(@RequestParam Long doctorId) {
        Doctor doc = doctorService.findById(doctorId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(404).body(Map.of("error", "DOCTOR_NOT_FOUND"));
        }
        return ResponseEntity.ok(buildProfile(doc));
    }

    private Map<String, Object> buildProfile(Doctor doc) {
        long reviewCount = orZero(reviewService.countReviews(doc.getId()));
        double avg = reviewCount > 0 ? reviewService.getAverageRating(doc.getId()) : 0.0;
        return buildProfile(doc, new ReviewService.RatingStats(avg, reviewCount));
    }

    private Map<String, Object> buildProfile(Doctor doc, ReviewService.RatingStats stats) {
        DoctorDTO dto = new DoctorDTO(doc);
        long reviewCount = stats.count();

        Map<String, Object> item = new HashMap<>();
        item.put("id", dto.getId());
        item.put("fullName", dto.getFullName());
        item.put("avatar", dto.getAvatar());
        item.put("degree", dto.getDegree());
        item.put("departmentId", dto.getDepartmentId());
        item.put("departmentName", dto.getDepartmentName());
        item.put("price", dto.getPrice());
        item.put("experienceYears", dto.getExperienceYears());
        item.put("gender", doc.getUser() != null ? doc.getUser().getGender() : null);
        item.put("reviewCount", reviewCount);
        item.put("avgRating", reviewCount > 0 ? stats.average() : null);
        String bio = doc.getBio();
        item.put("bio", (bio != null && bio.length() > 220) ? bio.substring(0, 220) + "…" : bio);
        return item;
    }

    @GetMapping("/doctors/filter")
    public ResponseEntity<List<Map<String, Object>>> filterDoctors(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String date) {

        List<Doctor> doctors = (departmentId != null)
                ? doctorService.findByDepartmentId(departmentId)
                : doctorService.findAll();

        if (gender != null && !gender.isBlank()) {
            String wanted = gender.trim().toLowerCase();
            doctors = doctors.stream()
                    .filter(d -> d.getUser() != null && d.getUser().getGender() != null
                            && d.getUser().getGender().trim().toLowerCase().equals(wanted))
                    .toList();
        }
        Map<Long, ReviewService.RatingStats> ratings = reviewService.getRatingStats(
                doctors.stream().map(Doctor::getId).toList());

        List<Doctor> sorted = new ArrayList<>(doctors);
        if ("price".equalsIgnoreCase(sortBy)) {
            sorted.sort(Comparator.comparing(d -> d.getPrice() == null
                    ? java.math.BigDecimal.ZERO : d.getPrice()));
        } else if ("rating".equalsIgnoreCase(sortBy)) {
           sorted.sort(Comparator
                    .comparing((Doctor d) -> !statsOf(ratings, d).hasReal())
                    .thenComparing(Comparator.comparingDouble(
                            (Doctor d) -> statsOf(ratings, d).average()).reversed())
                    .thenComparing(Comparator.comparingLong(
                            (Doctor d) -> statsOf(ratings, d).count()).reversed())
                    .thenComparing(Comparator.comparingInt(
                            (Doctor d) -> d.getExperienceYears() == null ? 0 : d.getExperienceYears()).reversed())
                    .thenComparing(Doctor::getId));
        } else {
            
            sorted.sort(Comparator.comparingInt(
                    (Doctor d) -> d.getExperienceYears() == null ? 0 : d.getExperienceYears()).reversed());
        }
        LocalDate targetDate = LocalDate.now();
        if (date != null && !date.isBlank()) {
            try {
                targetDate = LocalDate.parse(date.trim());
            } catch (Exception ignored) {
                
            }
        }
        if (targetDate.isBefore(LocalDate.now())) targetDate = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Doctor doc : sorted.stream().limit(6).toList()) {
            Map<String, Object> item = buildProfile(doc, statsOf(ratings, doc));
            List<String> grid = timeSlotService.allSlots();
            boolean works = bookingService.slotsOutsideWorkingHours(doc.getId(), targetDate, grid)
                    .size() < grid.size();
            item.put("worksOnDate", works);
            item.put("scheduleKnown", bookingService.hasRegisteredSchedule(doc.getId(), targetDate));
            item.put("date", targetDate.toString());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static ReviewService.RatingStats statsOf(
            Map<Long, ReviewService.RatingStats> ratings, Doctor doc) {
        return ratings.getOrDefault(doc.getId(), new ReviewService.RatingStats(0.0, 0L));
    }

    private Optional<User> resolveCurrentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails details) {
            return userRepository.findByUsername(details.getUsername());
        } else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth) {
            String email = oauth.getAttribute("email");
            return email != null ? userRepository.findByEmail(email) : Optional.empty();
        }
        String name = auth.getName();
        Optional<User> byUsername = userRepository.findByUsername(name);
        return byUsername.isPresent() ? byUsername : userRepository.findByEmail(name);
    }
}
