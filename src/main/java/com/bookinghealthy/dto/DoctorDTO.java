package com.bookinghealthy.dto;

import com.bookinghealthy.model.Doctor;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DoctorDTO {
    private Long id;
    private String fullName;
    private String departmentName;
    private String degree;
    private BigDecimal price;
    private Long departmentId;

    // === 3 TRƯỜNG THÊM MỚI CHO UI AI CHAT ===
    private String avatar;
    private Integer experienceYears;
    private Double rating;
    // THÊM DÒNG NÀY ĐỂ ĐỰNG LỊCH TRỰC (Chống N+1 API)
    private java.util.List<String> availableSlots;

    public DoctorDTO(Doctor doctor) {
        this(doctor, 5.0);
    }

    public DoctorDTO(Doctor doctor, Double averageRating) {
        this.id = doctor.getId();
        this.fullName = doctor.getUser().getFullName();

        if (doctor.getDepartment() != null) {
            this.departmentName = doctor.getDepartment().getName();
            this.departmentId = doctor.getDepartment().getId();
        } else {
            this.departmentName = "N/A";
            this.departmentId = null;
        }

        this.degree = doctor.getDegree();
        this.price = doctor.getPrice();

        // === MAPPING DỮ LIỆU MỚI ===
        this.experienceYears = doctor.getExperienceYears() != null ? doctor.getExperienceYears() : 0;
        String userAvatar = doctor.getUser().getAvatar();
        this.avatar = (userAvatar != null && !userAvatar.isEmpty()) ? "/uploads/" + userAvatar : "/assets/img/default-doctor.png";

        // Cập nhật điểm rating thực tế
        this.rating = (averageRating != null) ? averageRating : 5.0;
    }
}