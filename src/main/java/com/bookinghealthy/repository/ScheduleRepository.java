package com.bookinghealthy.repository;

import com.bookinghealthy.model.Schedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // Tìm tất cả các khung giờ làm việc của 1 bác sĩ
    List<Schedule> findByDoctorId(Long doctorId);

    // Tìm khung giờ của 1 bác sĩ VÀO 1 NGÀY CỤ THỂ TRONG TUẦN
    List<Schedule> findByDoctorIdAndDayOfWeek(Long doctorId, DayOfWeek dayOfWeek);

    // === THÊM HÀM MỚI: ĐỂ TRANG ĐĂNG KÝ HIỂN THỊ LỊCH CỦA BÁC SĨ HIỆN TẠI ===
    @EntityGraph(attributePaths = {"doctor"})
    List<Schedule> findAllByDoctorId(Long doctorId);

    // === THÊM HÀM MỚI: ĐỂ CHECK TRÙNG CA TRỰC ===
    boolean existsByDoctorIdAndDayOfWeekAndStartTime(Long doctorId, DayOfWeek dayOfWeek, LocalTime startTime);

    // Hàm tìm kiếm để xóa (nếu cần)
    void deleteByDoctorIdAndDayOfWeekAndStartTime(Long doctorId, DayOfWeek dayOfWeek, LocalTime startTime);
    // Tìm lịch theo Thứ (vd: MONDAY, TUESDAY...)
    // Dùng @EntityGraph để tải luôn thông tin Bác sĩ và Khoa (tránh lỗi Lazy)
    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    List<Schedule> findByDayOfWeek(DayOfWeek dayOfWeek);
}