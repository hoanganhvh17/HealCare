package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal; // <-- Thêm import
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    // (Xóa liên kết Service)

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private String appointmentTime;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // === THÊM 2 TRƯỜNG MỚI ===
    @Column(nullable = false)
    private String appointmentType; // (Lưu 'BHYT', 'Dịch vụ', 'Từ xa', 'Tại nhà')

    @Column(nullable = false)
    private BigDecimal bookingPrice; // Giá cuối cùng tại thời điểm đặt

    // === THÊM CÁC TRƯỜNG NÀY ===
    @Column(name = "payment_status")
    private String paymentStatus; // "UNPAID", "PAID", "FAILED"

    @Column(name = "payment_method")
    private String paymentMethod; // "VNPAY" (Mặc định)

    // === CÁC TRƯỜNG MỚI: THÔNG TIN NGƯỜI ĐI KHÁM (ĐẶT HỘ) ===
    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_phone")
    private String patientPhone;

    // === HÀNG CHỜ KHÁM (LỄ TÂN XỬ LÝ TRỄ GIỜ) ===
    // null = bệnh nhân đúng giờ, xếp theo appointmentTime.
    // Có giá trị = đã bị lễ tân đẩy xuống cuối, xếp sau tất cả người đúng giờ.
    @Column(name = "queue_order")
    private Integer queueOrder;

    // Thời điểm lễ tân đánh dấu trễ (để hiển thị badge và tra cứu lại)
    @Column(name = "late_marked_at")
    private LocalDateTime lateMarkedAt;

    // === BỆNH NHÂN TỰ ĐỔI LỊCH ===
    // Số lần bệnh nhân đã tự đổi bác sĩ / ngày / giờ. Chỉ tăng khi khung khám thật sự đổi,
    // sửa ghi chú hay tên người khám thì không tính. Dùng để chặn giữ chỗ ảo.
    @Column(name = "reschedule_count")
    private Integer rescheduleCount;

    @Column(name = "last_rescheduled_at")
    private LocalDateTime lastRescheduledAt;

    // === NHẮC LỊCH KHÁM TRƯỚC 1 NGÀY ===
    // Cờ chống nhắc lặp cho AppointmentReminderTask, KHÔNG phải dữ liệu nghiệp vụ — cùng vai
    // trò với MedicalRecord.followUpReminderSent. Thêm cột vào Booking là an toàn: entity này
    // có @AllArgsConstructor nhưng cả dự án chỉ dựng nó bằng new Booking() rồi set từng
    // trường, nên không dính bẫy khởi tạo theo vị trí của User/Doctor/Department/Schedule
    // trong DataInitializer.
    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    // === ĐỐI SOÁT THANH TOÁN ===
    // Mã giao dịch gửi lên VNPay (vnp_TxnRef). Trước đây mã này được sinh ngẫu nhiên rồi VỨT
    // ĐI, nên khi VNPay trả kết quả về không có gì nối nó với lịch hẹn ngoài chuỗi tự do
    // vnp_OrderInfo — mà chuỗi đó được bóc bằng cách cắt tiền tố "Thanh toan lich kham #",
    // hễ VNPay đổi một ký tự là parse hỏng và lỗi bị catch-all nuốt mất.
    //
    // CỐ Ý nullable: lịch trả bằng ví hoặc tại quầy không có mã này, và một cột NOT NULL
    // không DEFAULT thêm vào bảng đã có dữ liệu sẽ làm hỏng MỌI lệnh INSERT
    // (xem environment-setup.md).
    @Column(name = "vnp_txn_ref", length = 64)
    private String vnpTxnRef;

    // Mã giao dịch của ngân hàng/cổng trung gian cho luồng chuyển khoản VietQR.
    // Dùng để chống xử lý trùng khi Casso/SePay gửi lại cùng một webhook.
    @Column(name = "bank_txn_ref", length = 128)
    private String bankTxnRef;
}