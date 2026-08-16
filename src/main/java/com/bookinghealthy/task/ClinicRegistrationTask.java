package com.bookinghealthy.task;

import com.bookinghealthy.service.StaffScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

@Component
public class ClinicRegistrationTask {

    @Autowired
    private StaffScheduleService staffScheduleService;

    @Scheduled(cron = "0 0 8 * * SUN")
    @SchedulerLock(name = "clinicRegistrationRemind", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void remindDoctorsToRegister() {
        try {
            int reminded = staffScheduleService.sendNextWeekRegistrationReminders(null);
            System.out.println("[CRON] Đã nhắc " + reminded + " bác sĩ đăng ký lịch khám tuần sau.");
        } catch (Exception e) {
            System.err.println("[CRON] Lỗi nhắc đăng ký lịch khám: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 22 * * SUN")
    @SchedulerLock(name = "clinicRegistrationFinalize", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void autoRegisterMissingDoctors() {
        try {
            int registered = staffScheduleService.autoRegisterUnregisteredDoctors(null);
            System.out.println("[CRON] Đã tự động xếp lịch khám cả tuần cho " + registered + " bác sĩ.");
        } catch (Exception e) {
            System.err.println("[CRON] Lỗi tự động xếp lịch khám: " + e.getMessage());
        }
    }
}
