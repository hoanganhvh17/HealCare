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

    // === CĂN CỨ XẾP HẠNG (chỉ /api/chat/doctors/department/{id} điền, nơi khác để null) ===
    //
    // availableSlots chỉ là 4 khung XEM TRƯỚC dùng để trang trí thẻ bác sĩ, tuyệt đối không được
    // dùng làm căn cứ quyết định. Mấy trường dưới đây thì NGƯỢC LẠI: chúng là sự thật server vừa
    // kiểm chứng, nên giao diện được phép trích dẫn để giải thích "vì sao em chọn bác sĩ này".
    //
    // An toàn khi thêm field: lớp này dùng @Data + 2 constructor viết tay, KHÔNG có
    // @AllArgsConstructor — bẫy dựng-theo-vị-trí chỉ áp cho entity Doctor trong DataInitializer.

    /** Số ca khám của bác sĩ trong khoảng ±90 phút quanh mốc khách xin. Ít hơn = khách đỡ chờ. */
    private Integer nearbyLoad;

    /** Tổng số ca khám (chưa hủy) của bác sĩ trong ngày được xếp hạng. */
    private Integer dayLoad;

    /** Khung trống ĐẦU TIÊN nằm trong phạm vi khách xin, vd "09:30 - 10:00". null = không có. */
    private String matchedSlot;

    /** Nhãn máy của khung trên ("T5 24/07 (09:30 - 10:00)") — để lớp giọng nói đọc thành lời. */
    private String matchedSlotLabel;

    /** Ngày mà TOÀN BỘ bác sĩ trong khoa được đem ra so với nhau. */
    private String matchedDate;

    /** Bác sĩ này có trống đúng thứ khách vừa xin không. false = vẫn trong danh sách, chỉ xếp sau. */
    private Boolean matchesRequest;

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