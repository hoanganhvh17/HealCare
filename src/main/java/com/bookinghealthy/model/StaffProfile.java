package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Hồ sơ nhân sự phục vụ tính chế độ nghỉ. Áp dụng cho CẢ bác sĩ VÀ lễ tân nên khóa
 * theo {@link User} chứ không theo {@link Doctor}.
 *
 * Cố tình tách thành bảng riêng thay vì thêm cột vào User/Doctor: hai lớp đó dùng
 * {@code @AllArgsConstructor} và được gọi THEO VỊ TRÍ ở hàng chục chỗ trong
 * DataInitializer, thêm field vào chúng sẽ làm hỏng toàn bộ khối seed.
 *
 * KHÔNG dùng @AllArgsConstructor ở đây để tránh lặp lại đúng cái bẫy đó.
 */
@Entity
@Table(name = "staff_profiles")
@Getter
@Setter
@NoArgsConstructor
public class StaffProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Ngày vào làm — dùng tính thâm niên (+1 ngày phép mỗi 5 năm, BLLĐ 2019 Điều 114). */
    @Column(name = "hire_date")
    private LocalDate hireDate;

    /** Quyết định số ngày phép năm 12 / 14 / 16 (BLLĐ 2019 Điều 113 khoản 1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "work_condition", nullable = false, length = 20)
    private WorkCondition workCondition = WorkCondition.NORMAL;

    /** Số năm đã đóng BHXH — quyết định số ngày nghỉ ốm (Luật BHXH 2024 Điều 43). */
    @Column(name = "social_insurance_years")
    private Integer socialInsuranceYears = 0;

    /** Số ngày phép năm cũ được chuyển sang (BLLĐ cho phép gộp tối đa 3 năm một lần). */
    @Column(name = "carried_over_days")
    private Integer carriedOverDays = 0;

    /**
     * Khoa mà người này làm TRƯỞNG KHOA. Null = nhân viên thường.
     * Trưởng khoa là người duyệt đơn nghỉ và lịch trực của khoa mình.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_of_department_id")
    private Department headOfDepartment;

    /**
     * Thứ Hai của TUẦN mà bác sĩ đã đăng ký (hoặc được tự động đăng ký) lịch khám gần nhất.
     * Mỗi tuần bác sĩ phải đăng ký lịch khám cho tuần sau; trường này để biết đã đăng ký
     * cho tuần nào, phục vụ nhắc nhở vào Chủ nhật và tự động đăng ký nếu quên.
     * Null = chưa từng đăng ký.
     */
    @Column(name = "clinic_registered_for_week")
    private LocalDate clinicRegisteredForWeek;

    public boolean isHeadOfDepartment() {
        return headOfDepartment != null;
    }
}
