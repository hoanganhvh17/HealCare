package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.model.AuthProvider;
import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Role;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.DepartmentRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.BookingService;
import com.bookinghealthy.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Đặt lịch hộ tại quầy cho khách vãng lai (walk-in).
 */
@Controller
@RequestMapping("/receptionist/walk-in")
public class ReceptionistWalkInController {

    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookingService bookingService;
    @Autowired private EmailService emailService;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("allDoctors", doctorRepository.findAll());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "walk-in");
        return "receptionist/walk-in-form";
    }

    @PostMapping
    @Transactional
    public String createWalkIn(@RequestParam("doctorId") Long doctorId,
                               @RequestParam("appointmentDate")
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appointmentDate,
                               @RequestParam("appointmentTime") String appointmentTime,
                               @RequestParam("appointmentType") String appointmentType,
                               @RequestParam("patientName") String patientName,
                               @RequestParam("patientPhone") String patientPhone,
                               @RequestParam(value = "patientEmail", required = false) String patientEmail,
                               @RequestParam(value = "notes", required = false) String notes,
                               RedirectAttributes ra) {

        try {
            Doctor doctor = doctorRepository.findById(doctorId)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy bác sĩ."));

            User patient = findOrCreatePatient(patientName, patientPhone, patientEmail);

            Booking booking = new Booking();
            booking.setUser(patient);
            booking.setDoctor(doctor);
            booking.setAppointmentDate(appointmentDate);
            booking.setAppointmentTime(appointmentTime);
            booking.setAppointmentType(appointmentType);
            booking.setNotes(notes);
            booking.setBookingPrice(doctor.getPrice());
            booking.setPatientName(patientName);
            booking.setPatientPhone(patientPhone);
            // Khách đến tận quầy nên xác nhận luôn, thu tiền mặt tại chỗ
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setPaymentStatus("UNPAID");
            booking.setPaymentMethod("CASH");

            // Bắt buộc dùng reserve() để không đặt trùng khung giờ
            Booking saved = bookingService.reserve(booking);

            emailService.sendBookingConfirmation(saved);

            ra.addFlashAttribute("successMessage",
                    "Đã tạo lịch khám #" + saved.getId() + " cho " + patientName + ". Hãy in phiếu thu cho bệnh nhân.");
            return "redirect:/receptionist/bookings";

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
            return "redirect:/receptionist/walk-in";
        }
    }

    /**
     * Tìm bệnh nhân theo email; chưa có thì tạo tài khoản mới với mật khẩu ngẫu nhiên.
     * Username sinh từ số điện thoại để bệnh nhân dễ nhận lại tài khoản sau này.
     */
    private User findOrCreatePatient(String patientName, String patientPhone, String patientEmail) {
        String email = (patientEmail != null && !patientEmail.isBlank())
                ? patientEmail.trim()
                : "walkin_" + patientPhone.trim() + "@meditrust.local";

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        String username = "bn_" + patientPhone.trim();
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + UUID.randomUUID().toString().substring(0, 6);
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Chưa có ROLE_USER trong hệ thống."));

        User patient = new User();
        patient.setUsername(username);
        patient.setEmail(email);
        patient.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        patient.setFullName(patientName);
        patient.setPhone(patientPhone);
        patient.setGender("Chưa cập nhật");
        patient.setAuthProvider(AuthProvider.LOCAL);
        patient.setRoles(Collections.singleton(userRole));
        patient.setBalance(BigDecimal.ZERO);

        return userRepository.save(patient);
    }
}
