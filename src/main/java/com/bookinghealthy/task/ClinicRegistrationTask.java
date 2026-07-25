package com.bookinghealthy.task;

import com.bookinghealthy.service.StaffScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nhắc và tự động đăng ký lịch khám tuần sau.
 *
 * Mỗi tuần bác sĩ phải đăng ký lịch khám cho tuần kế tiếp (mỗi ngày tối thiểu 1 ca).
 * - Chủ nhật 08:00: nhắc các bác sĩ chưa đăng ký.
 * - Chủ nhật 22:00: bác sĩ nào vẫn chưa đăng ký thì hệ thống tự xếp lịch cả tuần.
 *
 * Cron 6 trường của Spring: giây phút giờ ngày-tháng tháng thứ-trong-tuần.
 */
@Component
public class ClinicRegistrationTask {

    @Autowired
    private StaffScheduleService staffScheduleService;

    /** Chủ nhật 08:00 — nhắc bác sĩ chưa đăng ký lịch khám tuần sau. */
    @Scheduled(cron = "0 0 8 * * SUN")
    public void remindDoctorsToRegister() {
        try {
            int reminded = staffScheduleService.sendNextWeekRegistrationReminders(null);
            System.out.println("[CRON] Đã nhắc " + reminded + " bác sĩ đăng ký lịch khám tuần sau.");
        } catch (Exception e) {
            System.err.println("[CRON] Lỗi nhắc đăng ký lịch khám: " + e.getMessage());
        }
    }

    /** Chủ nhật 22:00 — bác sĩ vẫn chưa đăng ký thì tự xếp lịch cả tuần. */
    @Scheduled(cron = "0 0 22 * * SUN")
    public void autoRegisterMissingDoctors() {
        try {
            int registered = staffScheduleService.autoRegisterUnregisteredDoctors(null);
            System.out.println("[CRON] Đã tự động xếp lịch khám cả tuần cho " + registered + " bác sĩ.");
        } catch (Exception e) {
            System.err.println("[CRON] Lỗi tự động xếp lịch khám: " + e.getMessage());
        }
    }
}
