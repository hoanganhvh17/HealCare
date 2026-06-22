package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_block_time")
@Data // Lombok: getter, setter, toString, equals, hashCode
public class DoctorBlockTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết với bác sĩ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    // Ngày bận cụ thể
    @Column(name = "block_date", nullable = false)
    private LocalDate blockDate;

    // Giờ bắt đầu chặn (Ví dụ: 10:00)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    // Giờ kết thúc chặn (Ví dụ: 10:30)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Lý do chặn (Ví dụ: họp khẩn, bận cá nhân...)
    @Column(name = "reason", length = 255)
    private String reason;
}