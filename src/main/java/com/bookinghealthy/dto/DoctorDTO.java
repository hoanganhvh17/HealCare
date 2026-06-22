package com.bookinghealthy.dto;

import com.bookinghealthy.model.Doctor;
import lombok.Data;
import java.math.BigDecimal; // <-- Thêm import

@Data
public class DoctorDTO {
    private Long id;
    private String fullName;
    private String departmentName;
    private String degree; // <-- Thêm
    private BigDecimal price; // <-- Thêm
    private Long departmentId; // <-- THÊM TRƯỜNG NÀY

    // === 3 TRƯỜNG THÊM MỚI CHO UI AI CHAT ===
    private String avatar;
    private Integer experienceYears;
    private Double rating;
    // THÊM DÒNG NÀY ĐỂ ĐỰNG LỊCH TRỰC (Chống N+1 API)
    private java.util.List<String> availableSlots;
    // ========================================

    public DoctorDTO(Doctor doctor) {
        this.id = doctor.getId();
        this.fullName = doctor.getUser().getFullName();

        if (doctor.getDepartment() != null) {
            this.departmentName = doctor.getDepartment().getName();
            this.departmentId = doctor.getDepartment().getId(); // <-- GÁN GIÁ TRỊ
        } else {
            this.departmentName = "N/A";
            this.departmentId = null;
        }

        this.degree = doctor.getDegree(); // <-- Gán
        this.price = doctor.getPrice();   // <-- Gán

        // === MAPPING DỮ LIỆU MỚI ===
        this.experienceYears = doctor.getExperienceYears() != null ? doctor.getExperienceYears() : 0;
        String userAvatar = doctor.getUser().getAvatar();
        this.avatar = (userAvatar != null && !userAvatar.isEmpty()) ? "/uploads/" + userAvatar : "/assets/img/default-doctor.png";
        this.rating = 5.0; // Tạm fix cứng 5 sao cho UI đẹp, sau này bạn gọi logic Review vào đây sau


    }
}