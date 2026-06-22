package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.DoctorDTO;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.service.DoctorService;
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

    @GetMapping("/doctors")
    public List<DoctorDTO> getDoctorsByDepartment(@RequestParam Long departmentId) {

        List<Doctor> doctors = doctorService.findByDepartmentId(departmentId);

        return doctors.stream()
                .map(DoctorDTO::new)
                .collect(Collectors.toList());
    }
    // === THÊM API MỚI NÀY ===
    /**
     * Lấy thông tin chi tiết của 1 bác sĩ (dưới dạng DTO)
     * Dùng cho JavaScript khi trang appointment tải
     */
    @GetMapping("/doctor/{id}")
    public ResponseEntity<DoctorDTO> getDoctorById(@PathVariable Long id) {
        Optional<Doctor> doctorOpt = doctorService.findById(id); // findById đã có @EntityGraph

        if (doctorOpt.isPresent()) {
            DoctorDTO doctorDTO = new DoctorDTO(doctorOpt.get());
            return ResponseEntity.ok(doctorDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}