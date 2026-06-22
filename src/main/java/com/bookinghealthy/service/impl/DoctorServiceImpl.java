package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Schedule;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.ScheduleRepository;
import com.bookinghealthy.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
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
    // === IMPLEMENT LOGIC ĐĂNG KÝ LỊCH MỚI ===
    @Override
    public String registerSchedule(Doctor doctor, String dayOfWeekStr, String session) {
        try {
            // 1. Chuyển đổi Thứ (String -> Enum)
            // Lưu ý: Value từ form sẽ là "MONDAY", "TUESDAY"...
            DayOfWeek day = DayOfWeek.valueOf(dayOfWeekStr);

            // 2. Chuyển đổi Ca (Sáng/Chiều/Tối -> Time)
            LocalTime start;
            LocalTime end;

            if ("Sang".equals(session)) {
                start = LocalTime.of(7, 30);
                end = LocalTime.of(11, 30);
            } else if ("Chieu".equals(session)) {
                start = LocalTime.of(13, 30);
                end = LocalTime.of(17, 30);
            } else {
                // Ca Tối (Giả sử)
                start = LocalTime.of(17, 30);
                end = LocalTime.of(20, 30);
            }

            // 3. Kiểm tra trùng
            if (scheduleRepository.existsByDoctorIdAndDayOfWeekAndStartTime(doctor.getId(), day, start)) {
                return "Lịch trực này (" + session + " - " + day + ") bạn đã đăng ký rồi!";
            }

            // 4. Lưu vào DB
            Schedule schedule = new Schedule();
            schedule.setDoctor(doctor);
            schedule.setDayOfWeek(day);
            schedule.setStartTime(start);
            schedule.setEndTime(end);

            scheduleRepository.save(schedule);
            return null; // Thành công (không có lỗi)

        } catch (IllegalArgumentException e) {
            return "Lỗi dữ liệu ngày tháng không hợp lệ.";
        }
    }

    @Override
    public void deleteSchedule(Long scheduleId) {
        scheduleRepository.deleteById(scheduleId);
    }

    @Override
    public List<Schedule> getDoctorSchedules(Long doctorId) {
        return scheduleRepository.findAllByDoctorId(doctorId);
    }
}