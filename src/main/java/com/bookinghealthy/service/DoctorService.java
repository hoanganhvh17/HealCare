package com.bookinghealthy.service;

import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Schedule;

import java.util.List;
import java.util.Optional;

public interface DoctorService {
    List<Doctor> findAll();
    Optional<Doctor> findById(Long id);
//    List<Doctor> searchBySpecialty(String specialty);
// === THAY BẰNG DÒNG NÀY ===
    List<Doctor> findByDepartmentId(Long departmentId);
    List<Doctor> searchByName(String name);

    // (Chúng ta sẽ thêm save, update, delete khi làm Module Admin)
    // === THÊM 2 HÀM MỚI ===
    Doctor save(Doctor doctor);
    void deleteById(Long id);

    // === THÊM HÀM MỚI NÀY ===
    Optional<Doctor> findByUsername(String username);

    // Thêm hàm này
    List<Doctor> searchDoctors(String keyword, Long departmentId);

    // === THÊM HÀM MỚI CHO MODULE LỊCH TRỰC ===
    // Hàm này sẽ trả về String thông báo lỗi hoặc null nếu thành công
    String registerSchedule(Doctor doctor, String dayOfWeekStr, String session);

    // Hàm xóa lịch
    void deleteSchedule(Long scheduleId);

    // Lấy danh sách lịch đã đăng ký của Bác sĩ
    List<Schedule> getDoctorSchedules(Long doctorId);
}