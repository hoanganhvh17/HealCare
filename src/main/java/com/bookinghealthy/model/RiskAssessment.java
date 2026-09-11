package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một lần bác sĩ chạy dự đoán nguy cơ bệnh trên màn hình {@code /doctor/risk-assessment}.
 *
 * <p>Lưu cả ĐẦU VÀO lẫn ĐẦU RA: không có đầu vào thì một xác suất cũ không đọc lại được,
 * và cũng không đối chiếu được khi model đổi phiên bản — đó là lý do có cột
 * {@code modelVersion}.
 *
 * <p><b>Cố ý KHÔNG khai {@code @AllArgsConstructor}.</b> {@code User}/{@code Doctor}/
 * {@code Department}/{@code Schedule} đang bị dựng theo vị trí trong {@code DataInitializer},
 * nên thêm một field vào chúng là làm hỏng seed. Các entity mới ({@code StaffProfile},
 * {@code StaffShift}, {@code Notification}…) đều bỏ annotation đó để không bao giờ mắc lại;
 * lớp này theo đúng khuôn ấy — dựng bằng {@code new RiskAssessment()} rồi setter.
 *
 * <p><b>Mọi cột đều nullable.</b> {@code ddl-auto=update} thêm một cột {@code NOT NULL}
 * không có DEFAULT là làm hỏng MỌI INSERT về sau nếu field bị gỡ đi. Riêng bốn cột xác suất
 * còn phải nullable thật sự: một target có thể không nạp được model.
 *
 * <p><b>Không dùng {@code @Enumerated} ở bất kỳ đâu trong lớp này</b> — Hibernate ánh xạ nó
 * thành cột {@code ENUM(...)} native của MySQL, mà {@code ddl-auto=update} không bao giờ viết
 * lại danh sách giá trị của một cột ENUM đã tồn tại.
 */
@Entity
@Table(name = "risk_assessments")
@Getter
@Setter
@NoArgsConstructor
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bác sĩ đã chạy. Là chủ sở hữu bản ghi — mọi phép kiểm quyền xem đều dựa vào cột này. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    /**
     * Bệnh nhân, nếu chọn được từ danh sách người đã từng khám. Nullable vì màn hình này
     * dùng được cho cả người chưa có tài khoản trong hệ thống.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private User patient;

    /** Tên/ghi chú bác sĩ tự gõ khi bệnh nhân chưa có tài khoản. */
    @Column(length = 255)
    private String patientLabel;

    // === Chỉ số thô bác sĩ nhập (đơn vị đã quy đổi về chuẩn của model) ===
    private Integer age;
    /** Mã giới tính NHANES: 1 = nam, 2 = nữ. Lưu số để khớp đúng thứ model nhận. */
    private Integer sexCode;
    private Double heightCm;
    private Double weightKg;
    private Double bmi;
    private Double waistCm;
    private Double systolicBp;
    private Double diastolicBp;
    private Double pulse;
    private Double hba1c;
    /** mg/dL — đã quy đổi từ mmol/L nếu bác sĩ chọn đơn vị đó. */
    private Double totalCholesterol;
    /** mg/dL. */
    private Double hdl;
    /** mg/L, giá trị thô trước log1p. */
    private Double hsCrp;

    // === Kết quả ===
    private Double diabetesProbability;
    private Boolean diabetesPositive;
    private Double hypertensionProbability;
    private Boolean hypertensionPositive;
    private Double heartDiseaseProbability;
    private Boolean heartDiseasePositive;
    private Double strokeProbability;
    private Boolean strokePositive;

    /** Ví dụ {@code nhanes-2026-08-18}. Xác suất cũ chỉ có nghĩa khi biết model nào sinh ra nó. */
    @Column(length = 100)
    private String modelVersion;

    /** Số chỉ số bị bỏ trống, tức số giá trị model phải tự điền bằng trung vị tập huấn luyện. */
    private Integer missingCount;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
