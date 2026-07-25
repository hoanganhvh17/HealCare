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

    /**
     * Ca khám của bác sĩ trong TUẦN HIỆN TẠI — chỉ để hiển thị.
     *
     * Đăng ký / sửa ca khám không còn ở đây: lịch khám gắn với từng tuần và chỉ đăng ký được
     * cho TUẦN SAU qua {@code StaffScheduleService.saveClinicTemplate}.
     */
    List<Schedule> getDoctorSchedules(Long doctorId);
}