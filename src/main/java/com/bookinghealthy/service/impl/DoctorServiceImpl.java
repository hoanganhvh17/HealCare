package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.ScheduleRepository;
import com.bookinghealthy.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class DoctorServiceImpl implements DoctorService {

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Override
    public List<Doctor> findAll() {
        return doctorRepository.findAll();
    }

    @Override
    public Optional<Doctor> findById(Long id) {
        return doctorRepository.findById(id);
    }

    // === SỬA HÀM NÀY ===
    @Override
    public List<Doctor> findByDepartmentId(Long departmentId) {
        // Gọi hàm mới trong repository
        return doctorRepository.findByDepartmentId(departmentId);
    }
    // === KẾT THÚC SỬA ĐỔI ===

    @Override
    public List<Doctor> searchByName(String name) {
        return doctorRepository.findByUserFullNameContainingIgnoreCase(name);
    }
    // === THÊM 2 PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public Doctor save(Doctor doctor) {
        return doctorRepository.save(doctor);
    }

    @Override
    public void deleteById(Long id) {
        doctorRepository.deleteById(id);
    }

    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public Optional<Doctor> findByUsername(String username) {
        return doctorRepository.findByUser_Username(username);
    }


    // Implement hàm mới
    @Override
    public List<Doctor> searchDoctors(String keyword, Long departmentId) {
        return doctorRepository.searchDoctors(keyword, departmentId);
    }
    /**
     * Ca khám của bác sĩ trong TUẦN HIỆN TẠI (chỉ để hiển thị).
     *
     * Lịch khám nay gắn với từng tuần, nên không còn khái niệm "toàn bộ lịch của bác sĩ":
     * đọc phải đi qua {@code findEffective} để lấy đúng lịch đang có hiệu lực. Việc thêm/xóa
     * ca khám chỉ còn một đường duy nhất là đăng ký cho tuần sau ở
     * {@code StaffScheduleService.saveClinicTemplate} — nhờ vậy tuần hiện tại và các tuần đã
     * qua không thể bị sửa.
     */
    @Override
    public List<Schedule> getDoctorSchedules(Long doctorId) {
        return scheduleRepository.findEffective(doctorId, LocalDate.now());
    }
}