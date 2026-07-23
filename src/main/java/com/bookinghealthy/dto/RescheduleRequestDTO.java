package com.bookinghealthy.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Dữ liệu bệnh nhân gửi lên khi tự sửa lịch hẹn của mình
 * (đổi bác sĩ cùng khoa, đổi ngày/giờ, sửa thông tin người khám và ghi chú).
 */
@Getter
@Setter
@NoArgsConstructor
public class RescheduleRequestDTO {

    private Long doctorId;
    private LocalDate appointmentDate;
    private String appointmentTime;
    private String appointmentType;
    private String patientName;
    private String patientPhone;
    private String notes;
}
