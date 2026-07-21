package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.DoctorDTO;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.service.DoctorService;
import com.bookinghealthy.service.ReviewService; // <-- THÊM IMPORT
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class DoctorApiController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/doctors")
    public List<DoctorDTO> getDoctorsByDepartment(@RequestParam(required = false) Long departmentId) {
        List<Doctor> doctors;

        if (departmentId != null) {
            doctors = doctorService.findByDepartmentId(departmentId);
        } else {
            doctors = doctorService.findAll();
        }
        return doctors.stream()
                .map(doctor -> {
                    Double avgRating = reviewService.getAverageRating(doctor.getId());
                    return new DoctorDTO(doctor, avgRating);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/doctors/search")
    public List<DoctorDTO> searchDoctors(
            @RequestParam String keyword,
            @RequestParam(required = false) Long departmentId) {
        return doctorService.searchDoctors(keyword, departmentId).stream()
                .map(doctor -> {
                    Double avgRating = reviewService.getAverageRating(doctor.getId());
                    return new DoctorDTO(doctor, avgRating);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/doctor/{id}")
    public ResponseEntity<DoctorDTO> getDoctorById(@PathVariable Long id) {
        Optional<Doctor> doctorOpt = doctorService.findById(id);

        if (doctorOpt.isPresent()) {
            Double avgRating = reviewService.getAverageRating(id);
            DoctorDTO doctorDTO = new DoctorDTO(doctorOpt.get(), avgRating);
            return ResponseEntity.ok(doctorDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}