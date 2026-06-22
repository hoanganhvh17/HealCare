package com.bookinghealthy.repository;

import com.bookinghealthy.model.DoctorBlockTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface DoctorBlockTimeRepository extends JpaRepository<DoctorBlockTime, Long> {

    // 1. Tìm tất cả các giờ chặn của một bác sĩ trong một ngày cụ thể (Cần cho TimeSlotService)
    List<DoctorBlockTime> findByDoctorIdAndBlockDate(Long doctorId, LocalDate blockDate);

    // 2. Tìm tất cả các giờ chặn của một bác sĩ (Cần cho trang hiển thị của bác sĩ)
    List<DoctorBlockTime> findByDoctorId(Long doctorId);

    // 3. Kiểm tra xem có bị trùng giờ chặn khi đăng ký không
    // Cần kiểm tra xem có BlockTime nào TỒN TẠI mà Thời gian của nó giao nhau với thời gian mới đăng ký không
    boolean existsByDoctorIdAndBlockDateAndStartTimeBeforeAndEndTimeAfter(
            Long doctorId,
            LocalDate blockDate,
            LocalTime newEndTime,
            LocalTime newStartTime
    );
}