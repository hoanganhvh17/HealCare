package com.bookinghealthy.service.impl;

import com.bookinghealthy.config.LeavePolicy;
import com.bookinghealthy.dto.ClinicRosterRowDTO;
import com.bookinghealthy.dto.ScheduleEventDTO;
import com.bookinghealthy.dto.ShiftRegisterResultDTO;
import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.*;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.NotificationService;
import com.bookinghealthy.service.StaffScheduleService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class StaffScheduleServiceImpl implements StaffScheduleService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired private StaffShiftRepository staffShiftRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DoctorBlockTimeRepository doctorBlockTimeRepository;
    @Autowired private ShiftCoverRequestRepository shiftCoverRequestRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;
    @Autowired private EmailService emailService;
    @Autowired private NotificationService notificationService;

    // ===================== ĐĂNG KÝ CA =====================

    @Override
    @Transactional
    public ShiftRegisterResultDTO registerShift(User user, ShiftType shiftType, LocalDate date,
                                                DutyRole dutyRole, String note) {
        if (shiftType == null || date == null) {
            return ShiftRegisterResultDTO.reject("Vui lòng chọn loại ca và ngày làm việc.");
        }

        // Ca khám KHÔNG đăng ký lẻ từng ngày ở đây nữa: nó là lịch lặp lại hằng tuần và
        // được quản lý bằng bảng tuần (saveClinicTemplate) để đăng ký cả tuần trong một lần.
        // Endpoint này chỉ còn nhận phiên trực và hội chẩn (ngày cụ thể).
        if (shiftType.isClinic()) {
            return ShiftRegisterResultDTO.reject(
                    "Ca khám được đăng ký theo tuần ở mục \"Ca khám (cả tuần)\", không đăng ký lẻ từng ngày.");
        }

        String error = validateShift(user, shiftType, date);
        if (error != null) {
            return ShiftRegisterResultDTO.reject(error);
        }

        Optional<Doctor> doctor = doctorRepository.findByUserId(user.getId());

        StaffShift shift = new StaffShift();
        shift.setUser(user);
        shift.setDepartment(doctor.map(Doctor::getDepartment).orElse(null));
        shift.setShiftDate(date);
        shift.setShiftType(shiftType);
        shift.setStartTime(shiftType.getDefaultStart());
        shift.setEndTime(shiftType.getDefaultEnd());
        shift.setOvernight(shiftType.isOvernight());
        shift.setDutyRole(shiftType.isDuty() ? resolveDutyRole(dutyRole) : null);
        shift.setNote(note);
        shift.setCreatedAt(LocalDateTime.now());

        // Phiên trực bắt buộc phải được lãnh đạo phê duyệt (Thông tư 32/2023/TT-BYT);
        // hội chẩn thì ghi nhận luôn.
        shift.setStatus(shiftType.isDuty() ? ApprovalStatus.PENDING : ApprovalStatus.APPROVED);
        if (!shiftType.isDuty()) {
            shift.setDecidedAt(LocalDateTime.now());
        }

        staffShiftRepository.save(shift);

        return buildResult(shiftType, date);
    }

    @Override
    @Transactional
    public String saveClinicTemplate(User user, List<DayOfWeek> morningDays,
                                     List<DayOfWeek> afternoonDays) {
        Optional<Doctor> doctor = doctorRepository.findByUserId(user.getId());
        if (doctor.isEmpty()) {
            return "Chỉ bác sĩ mới đăng ký ca khám — lễ tân không mở khung giờ khám cho bệnh nhân.";
        }

        // Chỉ đăng ký được cho TUẦN SAU, và phải trước giờ chốt lịch của bệnh viện.
        // Sau mốc đó lịch tuần sau đã công bố cho bệnh nhân đặt, không sửa một mình được nữa.
        if (!LeavePolicy.isClinicRegistrationOpen(LocalDateTime.now())) {
            return "Đã quá hạn chốt lịch khám tuần sau ("
                    + vietnameseDay(LeavePolicy.CLINIC_DEADLINE_DAY) + " "
                    + LeavePolicy.CLINIC_DEADLINE_TIME.format(TIME_FORMAT)
                    + "). Lịch tuần sau đã được công bố, anh/chị liên hệ trưởng khoa nếu cần điều chỉnh.";
        }

        // Yêu cầu nghiệp vụ: mỗi ngày trong tuần phải có tối thiểu 1 ca (phủ kín cả tuần).
        String missing = missingDaysMessage(morningDays, afternoonDays);
        if (missing != null) {
            return missing;
        }

        replaceClinicSchedules(doctor.get(), nextWeekStart(), morningDays, afternoonDays);
        markRegisteredForNextWeek(user);
        return null;
    }

    /**
     * Thay thế lịch khám của bác sĩ trong ĐÚNG MỘT TUẦN bằng lựa chọn mới — đăng ký/gỡ cả
     * tuần trong một lần.
     *
     * Chỉ đụng tới các bản ghi có {@code weekStart} bằng tuần đang đăng ký, nên tuần hiện tại
     * và mọi tuần đã qua giữ nguyên. Trước đây hàm này xóa TOÀN BỘ lịch của bác sĩ (lịch chỉ
     * có thứ trong tuần, không có tuần), nên một lần lưu là đổi luôn cả lịch đang chạy dở lẫn
     * lịch đã khám xong.
     */
    private void replaceClinicSchedules(Doctor doc, LocalDate weekStart,
                                        List<DayOfWeek> morningDays, List<DayOfWeek> afternoonDays) {
        scheduleRepository.deleteByDoctorIdAndWeekStart(doc.getId(), weekStart);
        scheduleRepository.flush(); // xóa xong mới ghi, tránh lẫn bản ghi cũ của cùng tuần

        List<Schedule> replacement = new ArrayList<>();
        if (morningDays != null) {
            for (DayOfWeek day : morningDays) {
                replacement.add(buildClinicSchedule(doc, weekStart, day, ShiftType.CA_SANG));
            }
        }
        if (afternoonDays != null) {
            for (DayOfWeek day : afternoonDays) {
                replacement.add(buildClinicSchedule(doc, weekStart, day, ShiftType.CA_CHIEU));
            }
        }
        scheduleRepository.saveAll(replacement);
    }

    /** Null nếu cả 7 ngày đều có ≥1 ca; ngược lại liệt kê các ngày còn trống. */
    private String missingDaysMessage(List<DayOfWeek> morningDays, List<DayOfWeek> afternoonDays) {
        Set<DayOfWeek> covered = new HashSet<>();
        if (morningDays != null) covered.addAll(morningDays);
        if (afternoonDays != null) covered.addAll(afternoonDays);

        List<String> missing = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (!covered.contains(day)) {
                missing.add(vietnameseDay(day));
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        return "Mỗi ngày phải có tối thiểu 1 ca khám. Còn thiếu: " + String.join(", ", missing) + ".";
    }

    // ===================== ĐĂNG KÝ LỊCH KHÁM CHO TUẦN SAU =====================

    @Override
    public LocalDate nextWeekStart() {
        return LeavePolicy.weekStartOf(LocalDate.now()).plusWeeks(1);
    }

    @Override
    public boolean isClinicRegistrationOpen() {
        return LeavePolicy.isClinicRegistrationOpen(LocalDateTime.now());
    }

    @Override
    public String clinicDeadlineLabel() {
        return vietnameseDay(LeavePolicy.CLINIC_DEADLINE_DAY) + " "
                + LeavePolicy.CLINIC_DEADLINE_TIME.format(TIME_FORMAT);
    }

    @Override
    public boolean hasRegisteredForNextWeek(Long userId) {
        return isRegisteredForWeek(userId, nextWeekStart());
    }

    private boolean isRegisteredForWeek(Long userId, LocalDate weekMonday) {
        return staffProfileRepository.findByUserId(userId)
                .map(profile -> weekMonday.equals(profile.getClinicRegisteredForWeek()))
                .orElse(false);
    }

    /** Ghi nhận bác sĩ đã đăng ký lịch khám cho tuần sau. */
    private void markRegisteredForNextWeek(User user) {
        StaffProfile profile = staffProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    StaffProfile fresh = new StaffProfile();
                    fresh.setUser(user);
                    return fresh;
                });
        profile.setClinicRegisteredForWeek(nextWeekStart());
        staffProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public int sendNextWeekRegistrationReminders(Long departmentId) {
        LocalDate week = nextWeekStart();
        int reminded = 0;

        for (Doctor doctor : doctorsInScope(departmentId)) {
            User staff = doctor.getUser();
            if (staff == null || isRegisteredForWeek(staff.getId(), week)) {
                continue;
            }
            emailService.sendStaffNotification(staff.getEmail(),
                    "MediTrust - Nhắc đăng ký lịch khám tuần sau",
                    "Chưa đăng ký lịch khám tuần sau",
                    "<p>Anh/chị chưa đăng ký lịch khám cho tuần <strong>" + weekLabel(week) + "</strong>.</p>"
                            + "<p>Vui lòng vào mục <em>Lịch làm việc &amp; Nghỉ phép → Ca khám</em> để đăng ký "
                            + "(mỗi ngày tối thiểu 1 ca) trước tối Chủ nhật, nếu không hệ thống sẽ tự xếp lịch "
                            + "cả tuần cho anh/chị.</p>");
            reminded++;
        }
        return reminded;
    }

    @Override
    @Transactional
    public int autoRegisterUnregisteredDoctors(Long departmentId) {
        LocalDate week = nextWeekStart();
        int registered = 0;

        for (Doctor doctor : doctorsInScope(departmentId)) {
            User staff = doctor.getUser();
            if (staff == null || isRegisteredForWeek(staff.getId(), week)) {
                continue;
            }
            autoFillFullWeek(doctor, week);
            markRegisteredForNextWeek(staff);
            emailService.sendStaffNotification(staff.getEmail(),
                    "MediTrust - Đã tự động xếp lịch khám tuần sau",
                    "Tự động xếp lịch khám cả tuần",
                    "<p>Do chưa đăng ký, hệ thống đã tự xếp cho anh/chị lịch khám cả tuần <strong>"
                            + weekLabel(week) + "</strong> (mỗi ngày tối thiểu một ca sáng).</p>"
                            + "<p>Anh/chị có thể điều chỉnh trong mục Ca khám nếu cần.</p>");
            notificationService.push(staff, "bi-calendar2-check text-primary",
                    "Hệ thống đã tự xếp lịch khám tuần sau",
                    "Tuần " + weekLabel(week) + " • mỗi ngày tối thiểu một ca sáng",
                    "/doctor/work-schedule");
            registered++;
        }
        return registered;
    }

    /**
     * Bảo đảm mỗi ngày trong tuần {@code weekStart} có ≥1 ca: lấy lịch đang có hiệu lực làm
     * khung (lần đăng ký gần nhất của bác sĩ), ngày nào trống thì thêm ca sáng.
     */
    private void autoFillFullWeek(Doctor doctor, LocalDate weekStart) {
        Set<DayOfWeek> morning = new HashSet<>();
        Set<DayOfWeek> afternoon = new HashSet<>();
        for (Schedule schedule : scheduleRepository.findEffective(doctor.getId(), weekStart)) {
            if (schedule.getStartTime().getHour() < 12) {
                morning.add(schedule.getDayOfWeek());
            } else {
                afternoon.add(schedule.getDayOfWeek());
            }
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            if (!morning.contains(day) && !afternoon.contains(day)) {
                morning.add(day);
            }
        }
        replaceClinicSchedules(doctor, weekStart, new ArrayList<>(morning), new ArrayList<>(afternoon));
    }

    // ===================== TRƯỞNG KHOA XẾP CA CHO KHOA =====================

    @Override
    public List<Doctor> findDepartmentDoctors(Long departmentId) {
        return (departmentId != null) ? doctorRepository.findByDepartmentId(departmentId) : new ArrayList<>();
    }

    @Override
    public List<ClinicRosterRowDTO> buildClinicRoster(Long departmentId, LocalDate weekStart) {
        List<ClinicRosterRowDTO> rows = new ArrayList<>();

        for (Doctor doctor : findDepartmentDoctors(departmentId)) {
            ClinicRosterRowDTO row = new ClinicRosterRowDTO();
            row.setDoctorId(doctor.getId());
            row.setDoctorName(doctor.getUser() != null ? doctor.getUser().getFullName() : "—");
            row.setUserId(doctor.getUser() != null ? doctor.getUser().getId() : null);
            row.setDegree(doctor.getDegree());

            // Tích sẵn theo lịch ĐANG CÓ HIỆU LỰC: tuần chưa xếp thì findEffective trả về mẫu
            // của lần đăng ký gần nhất, coi như gợi ý "giống tuần trước".
            for (Schedule schedule : scheduleRepository.findEffective(doctor.getId(), weekStart)) {
                if (schedule.getStartTime().getHour() < 12) {
                    row.getMorning().add(schedule.getDayOfWeek());
                } else {
                    row.getAfternoon().add(schedule.getDayOfWeek());
                }
            }

            // Ngày bác sĩ đang nghỉ phép thì không xếp ca được — ô sẽ bị vô hiệu trên bảng.
            if (row.getUserId() != null) {
                for (DayOfWeek day : DayOfWeek.values()) {
                    LocalDate date = dateOf(weekStart, day);
                    if (!leaveRequestRepository.findBlockingOnDate(row.getUserId(), date).isEmpty()) {
                        row.getOnLeave().add(day);
                    }
                }
                row.setRegistered(isRegisteredForWeek(row.getUserId(), weekStart));
            }

            rows.add(row);
        }
        return rows;
    }

    @Override
    @Transactional
    public String assignClinicWeek(User head, Long departmentId, LocalDate weekStart,
                                   java.util.Map<Long, List<DayOfWeek>> morningByDoctor,
                                   java.util.Map<Long, List<DayOfWeek>> afternoonByDoctor) {

        if (departmentId == null) {
            return "Anh/chị chưa được gán làm trưởng khoa của khoa nào.";
        }
        // Tuần hiện tại bệnh nhân đã đặt lịch vào, tuần đã qua là hồ sơ — chỉ tuần sau sửa được.
        if (weekStart == null || !weekStart.equals(nextWeekStart())) {
            return "Chỉ xếp được ca khám cho TUẦN SAU (" + weekLabel(nextWeekStart())
                    + "). Lịch tuần hiện tại đã công bố cho bệnh nhân đặt.";
        }

        List<Doctor> doctors = findDepartmentDoctors(departmentId);
        if (doctors.isEmpty()) {
            return "Khoa chưa có bác sĩ nào để xếp ca.";
        }

        java.util.Map<Long, Doctor> allowed = new java.util.HashMap<>();
        for (Doctor doctor : doctors) {
            allowed.put(doctor.getId(), doctor);
        }

        java.util.Map<Long, Set<DayOfWeek>> morning = new java.util.HashMap<>();
        java.util.Map<Long, Set<DayOfWeek>> afternoon = new java.util.HashMap<>();

        String scopeError = collectAssignment(allowed, morningByDoctor, morning, weekStart, "sáng");
        if (scopeError != null) {
            return scopeError;
        }
        scopeError = collectAssignment(allowed, afternoonByDoctor, afternoon, weekStart, "chiều");
        if (scopeError != null) {
            return scopeError;
        }

        String coverage = missingCoverageMessage(doctors, morning, afternoon);
        if (coverage != null) {
            return coverage;
        }

        String emptyWeek = emptyWeekMessage(doctors, morning, afternoon, weekStart);
        if (emptyWeek != null) {
            return emptyWeek;
        }

        for (Doctor doctor : doctors) {
            List<DayOfWeek> newMorning = new ArrayList<>(morning.getOrDefault(doctor.getId(), Set.of()));
            List<DayOfWeek> newAfternoon = new ArrayList<>(afternoon.getOrDefault(doctor.getId(), Set.of()));

            boolean changed = changesEffectiveSchedule(doctor, weekStart, newMorning, newAfternoon);

            replaceClinicSchedules(doctor, weekStart, newMorning, newAfternoon);
            if (doctor.getUser() != null) {
                markRegisteredForNextWeek(doctor.getUser());
                if (changed) {
                    notifyClinicAssignment(head, doctor.getUser(), weekStart, newMorning, newAfternoon);
                }
            }
        }
        return null;
    }

    /**
     * Đưa dữ liệu từ form về dạng Set, đồng thời chặn hai thứ: bác sĩ không thuộc khoa
     * (id gửi lên từ form đã bị sửa), và xếp ca vào ngày bác sĩ đang có đơn nghỉ.
     */
    private String collectAssignment(java.util.Map<Long, Doctor> allowed,
                                     java.util.Map<Long, List<DayOfWeek>> source,
                                     java.util.Map<Long, Set<DayOfWeek>> target,
                                     LocalDate weekStart, String sessionLabel) {

        if (source == null) {
            return null;
        }
        for (java.util.Map.Entry<Long, List<DayOfWeek>> entry : source.entrySet()) {
            Doctor doctor = allowed.get(entry.getKey());
            if (doctor == null) {
                return "Có bác sĩ không thuộc khoa của anh/chị trong danh sách xếp ca.";
            }
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }

            for (DayOfWeek day : entry.getValue()) {
                if (doctor.getUser() != null) {
                    LocalDate date = dateOf(weekStart, day);
                    List<LeaveRequest> leaves = leaveRequestRepository
                            .findBlockingOnDate(doctor.getUser().getId(), date);
                    if (!leaves.isEmpty()) {
                        return "Không xếp được ca " + sessionLabel + " " + vietnameseDay(day)
                                + " cho " + doctor.getUser().getFullName() + ": ngày "
                                + date.format(DATE_FORMAT) + " bác sĩ đang có đơn "
                                + leaves.get(0).getLeaveType().getLabel() + ".";
                    }
                }
            }
            target.computeIfAbsent(entry.getKey(), key -> new HashSet<>()).addAll(entry.getValue());
        }
        return null;
    }

    /**
     * Luật của trưởng khoa là ĐỘ PHỦ CỦA KHOA, không phải "mỗi bác sĩ đủ 7 ngày" như lúc bác
     * sĩ tự đăng ký ({@code missingDaysMessage}): từng người được nghỉ, miễn là mỗi buổi của
     * mỗi ngày khoa vẫn còn ít nhất một bác sĩ nhận khám.
     */
    private String missingCoverageMessage(List<Doctor> doctors,
                                          java.util.Map<Long, Set<DayOfWeek>> morning,
                                          java.util.Map<Long, Set<DayOfWeek>> afternoon) {
        List<String> gaps = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            if (!anyDoctorOn(doctors, morning, day)) {
                gaps.add(vietnameseDay(day) + " buổi sáng");
            }
            if (!anyDoctorOn(doctors, afternoon, day)) {
                gaps.add(vietnameseDay(day) + " buổi chiều");
            }
        }

        if (gaps.isEmpty()) {
            return null;
        }
        return "Mỗi buổi trong tuần khoa phải có tối thiểu 1 bác sĩ nhận khám. "
                + "Chưa có ai: " + String.join(", ", gaps) + ".";
    }

    /**
     * Bác sĩ không có ca nào cả tuần là trường hợp KHÔNG biểu diễn được, nên phải chặn ở đây.
     * Lý do: {@code replaceClinicSchedules} xóa hết bản ghi của tuần đó, mà
     * {@code ScheduleRepository.findEffective} coi "tuần không có bản ghi" là "chưa đăng ký"
     * và rơi về mẫu của lần đăng ký gần nhất — nên lịch lưu ra một đằng, bệnh nhân đặt được
     * một nẻo, và bảng xếp ca lần sau lại tích sẵn theo mẫu cũ.
     *
     * Ngoại lệ: bác sĩ đã có đơn nghỉ chặn TRỌN tuần thì để trống là đúng, và
     * {@code DoctorBlockTime} của đơn nghỉ đã chặn hết khung giờ nên mẫu cũ không gây hại.
     */
    private String emptyWeekMessage(List<Doctor> doctors,
                                    java.util.Map<Long, Set<DayOfWeek>> morning,
                                    java.util.Map<Long, Set<DayOfWeek>> afternoon,
                                    LocalDate weekStart) {
        for (Doctor doctor : doctors) {
            boolean hasAny = !morning.getOrDefault(doctor.getId(), Set.of()).isEmpty()
                    || !afternoon.getOrDefault(doctor.getId(), Set.of()).isEmpty();
            if (hasAny || doctor.getUser() == null || onLeaveAllWeek(doctor.getUser().getId(), weekStart)) {
                continue;
            }
            return doctor.getUser().getFullName() + " chưa được xếp ca nào trong tuần. "
                    + "Mỗi bác sĩ cần tối thiểu 1 ca, vì tuần để trống hoàn toàn sẽ bị hiểu là "
                    + "\"chưa đăng ký\" và hệ thống dùng lại lịch tuần gần nhất của bác sĩ. "
                    + "Nếu bác sĩ nghỉ cả tuần, hãy duyệt đơn nghỉ phép cho họ thay vì để trống.";
        }
        return null;
    }

    private boolean onLeaveAllWeek(Long userId, LocalDate weekStart) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (leaveRequestRepository.findBlockingOnDate(userId, dateOf(weekStart, day)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean anyDoctorOn(List<Doctor> doctors,
                                java.util.Map<Long, Set<DayOfWeek>> assignment, DayOfWeek day) {
        for (Doctor doctor : doctors) {
            Set<DayOfWeek> days = assignment.get(doctor.getId());
            if (days != null && days.contains(day)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lịch mới có khác lịch bác sĩ vốn sẽ làm trong tuần đó không. So với lịch ĐANG CÓ HIỆU
     * LỰC chứ không với bản ghi của đúng tuần: trưởng khoa mở bảng rồi lưu mà không sửa gì
     * thì tuần đó có thêm bản ghi mới, nhưng giờ làm của bác sĩ không đổi — báo cho họ lúc
     * đó chỉ là thông báo rác.
     */
    private boolean changesEffectiveSchedule(Doctor doctor, LocalDate weekStart,
                                             List<DayOfWeek> newMorning, List<DayOfWeek> newAfternoon) {
        Set<DayOfWeek> oldMorning = new HashSet<>();
        Set<DayOfWeek> oldAfternoon = new HashSet<>();
        for (Schedule schedule : scheduleRepository.findEffective(doctor.getId(), weekStart)) {
            if (schedule.getStartTime().getHour() < 12) {
                oldMorning.add(schedule.getDayOfWeek());
            } else {
                oldAfternoon.add(schedule.getDayOfWeek());
            }
        }
        return !oldMorning.equals(new HashSet<>(newMorning))
                || !oldAfternoon.equals(new HashSet<>(newAfternoon));
    }

    private void notifyClinicAssignment(User head, User staff, LocalDate weekStart,
                                        List<DayOfWeek> morning, List<DayOfWeek> afternoon) {
        String summary = "Ca sáng: " + dayListLabel(morning) + " • Ca chiều: " + dayListLabel(afternoon);
        String headName = (head != null) ? head.getFullName() : "Trưởng khoa";

        emailService.sendStaffNotification(staff.getEmail(),
                "MediTrust - Lịch khám tuần sau đã được trưởng khoa xếp",
                "Lịch khám tuần " + weekLabel(weekStart),
                "<p>" + headName + " đã xếp lịch khám tuần <strong>" + weekLabel(weekStart)
                        + "</strong> cho anh/chị.</p><p>" + summary + "</p>"
                        + "<p>Anh/chị xem chi tiết trong mục <em>Lịch làm việc &amp; Nghỉ phép</em>.</p>");

        notificationService.push(staff, "bi-calendar2-week text-primary",
                "Trưởng khoa đã xếp lịch khám tuần " + weekLabel(weekStart),
                summary,
                "/doctor/work-schedule");
    }

    private String dayListLabel(List<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return "không có";
        }
        List<String> labels = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (days.contains(day)) {
                labels.add(vietnameseDay(day));
            }
        }
        return String.join(", ", labels);
    }

    private LocalDate dateOf(LocalDate weekStart, DayOfWeek day) {
        return weekStart.plusDays(day.getValue() - 1L);
    }

    @Override
    @Transactional
    public ShiftRegisterResultDTO assignDutyShift(User head, Long doctorId, ShiftType shiftType,
                                                 LocalDate date, DutyRole dutyRole, String note) {
        if (doctorId == null || shiftType == null || date == null) {
            return ShiftRegisterResultDTO.reject("Vui lòng chọn bác sĩ, loại phiên trực và ngày.");
        }
        if (shiftType.isClinic()) {
            return ShiftRegisterResultDTO.reject(
                    "Ca khám được xếp ở bảng \"Xếp ca khoa\", mục này chỉ dành cho phiên trực.");
        }

        Optional<Doctor> found = doctorRepository.findById(doctorId);
        if (found.isEmpty() || found.get().getUser() == null) {
            return ShiftRegisterResultDTO.reject("Không tìm thấy bác sĩ được phân công.");
        }
        Doctor doctor = found.get();
        User staff = doctor.getUser();

        // Dùng NGUYÊN bộ luật của việc tự đăng ký: trưởng khoa phân công cũng không được phá
        // quy định nghỉ bù, không lấn giờ hành chính, không trùng ca của bác sĩ.
        String error = validateShift(staff, shiftType, date);
        if (error != null) {
            return ShiftRegisterResultDTO.reject(error);
        }

        StaffShift shift = new StaffShift();
        shift.setUser(staff);
        shift.setDepartment(doctor.getDepartment());
        shift.setShiftDate(date);
        shift.setShiftType(shiftType);
        shift.setStartTime(shiftType.getDefaultStart());
        shift.setEndTime(shiftType.getDefaultEnd());
        shift.setOvernight(shiftType.isOvernight());
        shift.setDutyRole(shiftType.isDuty() ? resolveDutyRole(dutyRole) : null);
        shift.setNote(note);
        shift.setCreatedAt(LocalDateTime.now());

        // Người phân công chính là người có quyền duyệt nên ca vào thẳng trạng thái đã duyệt.
        shift.setStatus(ApprovalStatus.APPROVED);
        shift.setApprover(head);
        shift.setDecidedAt(LocalDateTime.now());
        staffShiftRepository.save(shift);

        generateCompensatoryLeave(shift);
        notifyDutyAssignment(head, shift);

        return buildResult(shiftType, date);
    }

    private void notifyDutyAssignment(User head, StaffShift shift) {
        String headName = (head != null) ? head.getFullName() : "Trưởng khoa";
        String when = shift.getShiftDate().format(DATE_FORMAT) + " "
                + shift.getStartTime().format(TIME_FORMAT) + " - "
                + shift.getEndTime().format(TIME_FORMAT);

        emailService.sendStaffNotification(shift.getUser().getEmail(),
                "MediTrust - Anh/chị được phân công phiên trực",
                "Phân công phiên trực",
                "<p>" + headName + " đã phân công anh/chị <strong>"
                        + shift.getShiftType().getLabel() + "</strong> ngày " + when + ".</p>"
                        + (shift.getNote() != null && !shift.getNote().isBlank()
                        ? "<p><em>Ghi chú: " + shift.getNote() + "</em></p>" : "")
                        + "<p>Nếu không nhận được ca, anh/chị hãy dùng chức năng "
                        + "<em>Tìm người thay thế</em> trong mục Lịch làm việc.</p>");

        notificationService.push(shift.getUser(), "bi-clipboard-check text-primary",
                "Anh/chị được phân công " + shift.getShiftType().getLabel(),
                when + (shift.getDutyRole() != null ? " • " + shift.getDutyRole().getLabel() : ""),
                "/doctor/work-schedule");
    }

    private List<Doctor> doctorsInScope(Long departmentId) {
        return (departmentId != null)
                ? doctorRepository.findByDepartmentId(departmentId)
                : doctorRepository.findAll();
    }

    private String weekLabel(LocalDate monday) {
        return monday.format(DATE_FORMAT) + " - " + monday.plusDays(6).format(DATE_FORMAT);
    }

    private String vietnameseDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }

    private Schedule buildClinicSchedule(Doctor doctor, LocalDate weekStart,
                                         DayOfWeek day, ShiftType session) {
        Schedule schedule = new Schedule();
        schedule.setDoctor(doctor);
        schedule.setWeekStart(weekStart);
        schedule.setDayOfWeek(day);
        schedule.setStartTime(session.getDefaultStart());
        schedule.setEndTime(session.getDefaultEnd());
        return schedule;
    }

    /**
     * Lịch để tích sẵn bảng đăng ký — luôn là lịch của TUẦN SAU (tuần duy nhất còn sửa được).
     * Chưa đăng ký tuần sau thì {@code findEffective} trả về lần đăng ký gần nhất, coi như
     * gợi ý "giống tuần trước" để bác sĩ chỉnh cho nhanh.
     */
    @Override
    public List<Schedule> getClinicSchedules(Long userId) {
        LocalDate week = nextWeekStart();
        return doctorRepository.findByUserId(userId)
                .map(doctor -> scheduleRepository.findEffective(doctor.getId(), week))
                .orElseGet(ArrayList::new);
    }

    private DutyRole resolveDutyRole(DutyRole dutyRole) {
        // Bác sĩ trực thì mặc định là trực lâm sàng — vị trí phổ biến nhất theo TT 32/2023.
        return (dutyRole != null) ? dutyRole : DutyRole.TRUC_LAM_SANG;
    }

    private ShiftRegisterResultDTO buildResult(ShiftType shiftType, LocalDate date) {
        if (!shiftType.isDuty()) {
            return ShiftRegisterResultDTO.ok();
        }

        long daysAhead = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (daysAhead < LeavePolicy.DUTY_NOTICE_DAYS) {
            return ShiftRegisterResultDTO.okWithWarning(
                    "Đã gửi đăng ký, nhưng lịch trực nên được công bố trước ít nhất "
                            + LeavePolicy.DUTY_NOTICE_DAYS + " ngày (Thông tư 32/2023/TT-BYT). "
                            + "Ca này chỉ còn " + daysAhead + " ngày nữa, trưởng khoa có thể không kịp sắp xếp.");
        }
        return ShiftRegisterResultDTO.ok();
    }

    /**
     * Toàn bộ luật nghiệp vụ khi đăng ký ca. Trả về null nếu hợp lệ.
     */
    private String validateShift(User user, ShiftType shiftType, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime shiftStart = LocalDateTime.of(date, shiftType.getDefaultStart());

        // 1. Không đăng ký cho quá khứ.
        if (shiftStart.isBefore(now)) {
            return "Không thể đăng ký ca trong quá khứ.";
        }

        // 2. Phiên trực phải đăng ký trước tối thiểu 24 giờ. Sát hơn là tình huống đột xuất.
        if (shiftType.isDuty()
                && Duration.between(now, shiftStart).toHours() < LeavePolicy.DUTY_MIN_HOURS_AHEAD) {
            return "Phiên trực phải đăng ký trước ít nhất " + LeavePolicy.DUTY_MIN_HOURS_AHEAD
                    + " giờ. Nếu là tình huống gấp, anh/chị hãy dùng chức năng \"Báo bận đột xuất\".";
        }

        // 3. Trực 24/24 phủ trọn cả giờ hành chính nên chỉ tổ chức vào ngày nghỉ hoặc ngày lễ.
        if (shiftType == ShiftType.TRUC_24H && !LeavePolicy.allowsFullDayDuty(date)) {
            return "Trực 24/24 giờ chỉ áp dụng cho ngày nghỉ hằng tuần hoặc ngày lễ, Tết. "
                    + "Ngày thường anh/chị hãy chọn Trực 16/24 giờ hoặc Trực đêm 12/24 giờ "
                    + "để không chồng lên giờ hành chính.";
        }

        // 4. Phiên trực không được lấn vào giờ hành chính của ngày thường.
        String officeClash = checkOfficeHoursClash(shiftType, date);
        if (officeClash != null) {
            return officeClash;
        }

        // 5. Không trùng giờ với ca khác của chính mình (tính cả ca trực qua đêm hôm trước).
        String overlap = checkShiftOverlap(user, shiftType, date);
        if (overlap != null) {
            return overlap;
        }

        // 6. Không đăng ký ca vào ngày đang nghỉ.
        List<LeaveRequest> leaves = leaveRequestRepository.findBlockingOnDate(user.getId(), date);
        if (!leaves.isEmpty()) {
            return "Ngày " + date.format(DATE_FORMAT) + " anh/chị đang có đơn "
                    + leaves.get(0).getLeaveType().getLabel() + ", không đăng ký ca được.";
        }

        // 7. Phải nghỉ đủ sau phiên trực gần nhất.
        return checkMandatoryRest(user, shiftType, date);
    }

    /**
     * Giờ hành chính là 07:30 - 17:30 các ngày trong tuần. Phiên trực phải nằm ngoài
     * khoảng này, trừ trực 24/24 vốn chỉ tổ chức vào ngày nghỉ / ngày lễ.
     */
    private String checkOfficeHoursClash(ShiftType shiftType, LocalDate date) {
        if (!shiftType.isDuty() || shiftType == ShiftType.TRUC_24H) {
            return null;
        }
        if (LeavePolicy.isWeekend(date) || LeavePolicy.isPublicHoliday(date)) {
            return null; // ngày nghỉ thì không có giờ hành chính để lấn
        }
        if (shiftType.getDefaultStart().isBefore(LeavePolicy.OFFICE_END)) {
            return "Ca trực bắt đầu lúc " + shiftType.getDefaultStart().format(TIME_FORMAT)
                    + " sẽ lấn vào giờ hành chính (kết thúc " + LeavePolicy.OFFICE_END.format(TIME_FORMAT)
                    + "). Trực là hoạt động ngoài giờ hành chính (Thông tư 32/2023/TT-BYT).";
        }
        return null;
    }

    private String checkShiftOverlap(User user, ShiftType shiftType, LocalDate date) {
        LocalDateTime newStart = LocalDateTime.of(date, shiftType.getDefaultStart());
        LocalDateTime newEnd = LocalDateTime.of(
                shiftType.isOvernight() ? date.plusDays(1) : date, shiftType.getDefaultEnd());

        // Quét cả ca của hôm trước vì ca trực qua đêm kéo sang ngày đang xét.
        List<StaffShift> nearby = staffShiftRepository
                .findByUserIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                        user.getId(), date.minusDays(1), date.plusDays(1));

        for (StaffShift existing : nearby) {
            if (existing.getStatus() == ApprovalStatus.REJECTED
                    || existing.getStatus() == ApprovalStatus.CANCELED) {
                continue;
            }
            if (newStart.isBefore(existing.getEndsAt()) && newEnd.isAfter(existing.getStartsAt())) {
                return "Ca này trùng giờ với " + existing.getShiftType().getLabel()
                        + " ngày " + existing.getShiftDate().format(DATE_FORMAT) + " của anh/chị.";
            }
        }
        return null;
    }

    /**
     * Nghỉ bù sau trực — Quyết định 73/2011/QĐ-TTg, Điều 2 khoản 4.
     * Trực 24/24 được nghỉ bù trọn ngày (2 ngày nếu là lễ, Tết); trực 12/24 và 16/24
     * được nghỉ ít nhất 12 giờ. Trong khoảng đó không được xếp ca mới.
     */
    private String checkMandatoryRest(User user, ShiftType shiftType, LocalDate date) {
        LocalDateTime newStart = LocalDateTime.of(date, shiftType.getDefaultStart());

        List<StaffShift> recent = staffShiftRepository.findApprovedBetween(
                user.getId(), date.minusDays(3), date);

        for (StaffShift previous : recent) {
            if (!previous.isDuty()) {
                continue;
            }

            int compensatoryDays = LeavePolicy.compensatoryDaysAfterDuty(
                    previous.getShiftType(), LeavePolicy.isPublicHoliday(previous.getShiftDate()));

            LocalDateTime restUntil = (compensatoryDays > 0)
                    ? previous.getEndsAt().toLocalDate().plusDays(compensatoryDays).atStartOfDay()
                    : previous.getEndsAt().plusHours(LeavePolicy.REST_HOURS_AFTER_PARTIAL_DUTY);

            if (newStart.isBefore(restUntil)) {
                return "Sau phiên " + previous.getShiftType().getLabel() + " ngày "
                        + previous.getShiftDate().format(DATE_FORMAT)
                        + ", anh/chị phải nghỉ đến " + restUntil.format(
                        DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
                        + " (Quyết định 73/2011/QĐ-TTg).";
            }
        }
        return null;
    }

    // ===================== HỦY CA =====================

    @Override
    @Transactional
    public String cancelShift(Long shiftId, User user) {
        Optional<StaffShift> found = staffShiftRepository.findById(shiftId);
        if (found.isEmpty()) {
            return "Không tìm thấy ca làm việc.";
        }

        StaffShift shift = found.get();
        if (!shift.getUser().getId().equals(user.getId())) {
            return "Anh/chị không có quyền hủy ca này.";
        }
        if (shift.getStartsAt().isBefore(LocalDateTime.now())) {
            return "Ca đã bắt đầu hoặc đã qua, không hủy được.";
        }

        // Ca khám đã có bệnh nhân đặt thì không được bỏ trống: phải tìm người thay
        // để bệnh nhân vẫn được khám đúng hẹn.
        String bookingBlock = checkBookedPatients(shift);
        if (bookingBlock != null) {
            return bookingBlock;
        }

        shift.setStatus(ApprovalStatus.CANCELED);
        shift.setDecidedAt(LocalDateTime.now());
        shift.setNeedsCover(false);
        staffShiftRepository.save(shift);
        shiftCoverRequestRepository.deleteByShiftId(shiftId);
        return null;
    }

    /**
     * Ca khám đã có bệnh nhân đặt thì không được bỏ trống — bệnh nhân đã trả tiền và
     * đang chờ. Phải đi qua luồng "Tìm người thay thế" hoặc nhờ lễ tân dời lịch.
     */
    private String checkBookedPatients(StaffShift shift) {
        if (!shift.isClinic()) {
            return null;
        }

        Optional<Doctor> doctor = doctorRepository.findByUserId(shift.getUser().getId());
        if (doctor.isEmpty()) {
            return null;
        }

        long booked = bookingRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                        doctor.get().getId(), shift.getShiftDate(), BookingStatus.CANCELED).stream()
                .filter(booking -> fallsInShift(booking.getAppointmentTime(), shift))
                .count();

        if (booked > 0) {
            return "Ca này đã có " + booked + " bệnh nhân đặt lịch. "
                    + "Anh/chị hãy dùng \"Tìm người thay thế\" hoặc nhờ lễ tân dời lịch trước khi hủy ca.";
        }
        return null;
    }

    /** Khung giờ hẹn "HH:mm - HH:mm" có nằm trong ca không (so theo giờ bắt đầu). */
    private boolean fallsInShift(String appointmentTime, StaffShift shift) {
        if (appointmentTime == null || !appointmentTime.contains(" - ")) {
            return false;
        }
        try {
            java.time.LocalTime slotStart = java.time.LocalTime.parse(
                    appointmentTime.split(" - ")[0].trim(), TIME_FORMAT);
            return !slotStart.isBefore(shift.getStartTime()) && slotStart.isBefore(shift.getEndTime());
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== DUYỆT / TỪ CHỐI CA TRỰC =====================

    /**
     * Chỉ chứa điều kiện chung cho cả duyệt và từ chối. Riêng "đã duyệt rồi" thuộc về
     * {@code approveShift}: từ chối một ca đã duyệt là cách trưởng khoa thu hồi quyết định.
     */
    @Override
    public String whyCannotDecideShift(StaffShift shift) {
        if (shift == null) {
            return "Không tìm thấy ca trực.";
        }
        if (shift.getStatus() == ApprovalStatus.CANCELED) {
            return "Ca trực đã bị hủy, không xử lý được nữa.";
        }
        // Ca đã trực xong thì quyết định không còn ý nghĩa: ngày nghỉ bù sinh ra cũng rơi
        // vào quá khứ, còn từ chối thì cũng không xóa được việc người ta đã trực.
        if (shift.getEndsAt() != null && shift.getEndsAt().isBefore(LocalDateTime.now())) {
            return "Ca " + shift.getShiftType().getLabel() + " ngày "
                    + shift.getShiftDate().format(DATE_FORMAT)
                    + " đã kết thúc nên không còn hiệu lực để xử lý.";
        }
        return null;
    }

    @Override
    @Transactional
    public String approveShift(Long shiftId, User approver, String comment) {
        Optional<StaffShift> found = staffShiftRepository.findById(shiftId);
        if (found.isEmpty()) {
            return "Không tìm thấy ca trực.";
        }

        StaffShift shift = found.get();
        String blocked = whyCannotDecideShift(shift);
        if (blocked != null) {
            return blocked;
        }
        if (shift.getStatus() == ApprovalStatus.APPROVED) {
            return "Ca trực này đã được duyệt trước đó.";
        }

        shift.setStatus(ApprovalStatus.APPROVED);
        shift.setApprover(approver);
        shift.setApproverComment(comment);
        shift.setDecidedAt(LocalDateTime.now());
        staffShiftRepository.save(shift);

        generateCompensatoryLeave(shift);
        notifyShiftDecision(shift, "được phê duyệt");
        return null;
    }

    @Override
    @Transactional
    public String rejectShift(Long shiftId, User approver, String comment) {
        Optional<StaffShift> found = staffShiftRepository.findById(shiftId);
        if (found.isEmpty()) {
            return "Không tìm thấy ca trực.";
        }

        StaffShift shift = found.get();
        String blocked = whyCannotDecideShift(shift);
        if (blocked != null) {
            return blocked;
        }
        if (shift.getStatus() == ApprovalStatus.REJECTED) {
            return "Ca trực này đã bị từ chối trước đó.";
        }

        shift.setStatus(ApprovalStatus.REJECTED);
        shift.setApprover(approver);
        shift.setApproverComment(comment);
        shift.setDecidedAt(LocalDateTime.now());
        staffShiftRepository.save(shift);

        notifyShiftDecision(shift, "bị từ chối");
        return null;
    }

    /**
     * Sinh đơn nghỉ bù sau phiên trực 24/24 giờ — Quyết định 73/2011/QĐ-TTg, Điều 2 khoản 4.
     * Đơn này do hệ thống tạo và tự duyệt luôn, người trực không phải xin.
     */
    private void generateCompensatoryLeave(StaffShift shift) {
        int days = LeavePolicy.compensatoryDaysAfterDuty(
                shift.getShiftType(), LeavePolicy.isPublicHoliday(shift.getShiftDate()));
        if (days <= 0) {
            return;
        }

        // Ca trực kết thúc sáng hôm sau nên ngày nghỉ bù bắt đầu từ chính ngày đó.
        LocalDate restStart = shift.getEndsAt().toLocalDate();
        LocalDate restEnd = restStart.plusDays(days - 1L);

        boolean alreadyExists = leaveRequestRepository
                .findOverlapping(shift.getUser().getId(), restStart, restEnd).stream()
                .anyMatch(l -> l.getLeaveType() == LeaveType.NGHI_BU_TRUC
                        && l.getStatus() == ApprovalStatus.APPROVED);
        if (alreadyExists) {
            return;
        }

        LeaveRequest rest = new LeaveRequest();
        rest.setUser(shift.getUser());
        rest.setLeaveType(LeaveType.NGHI_BU_TRUC);
        rest.setStartDate(restStart);
        rest.setEndDate(restEnd);
        rest.setHalfDaySession(HalfDaySession.NONE);
        rest.setTotalDays(BigDecimal.valueOf(days));
        rest.setReason("Nghỉ bù sau " + shift.getShiftType().getLabel() + " ngày "
                + shift.getShiftDate().format(DATE_FORMAT)
                + " (Quyết định 73/2011/QĐ-TTg, Điều 2 khoản 4)");
        rest.setStatus(ApprovalStatus.APPROVED);
        rest.setApprover(shift.getApprover());
        rest.setDecidedAt(LocalDateTime.now());
        leaveRequestRepository.save(rest);

        // Bác sĩ nghỉ bù thì cũng phải chặn khung giờ khám của ngày đó.
        applyRestBlockTimes(rest);
    }

    private void applyRestBlockTimes(LeaveRequest rest) {
        Optional<Doctor> doctor = doctorRepository.findByUserId(rest.getUser().getId());
        if (doctor.isEmpty()) {
            return;
        }

        for (LocalDate date = rest.getStartDate();
             !date.isAfter(rest.getEndDate());
             date = date.plusDays(1)) {

            DoctorBlockTime block = new DoctorBlockTime();
            block.setDoctor(doctor.get());
            block.setBlockDate(date);
            block.setStartTime(LeavePolicy.OFFICE_START);
            block.setEndTime(LeavePolicy.OFFICE_END);
            block.setReason("Nghỉ bù sau trực");
            block.setLeaveRequestId(rest.getId());
            doctorBlockTimeRepository.save(block);
        }
    }

    /** Báo kết quả qua CẢ email VÀ chuông thông báo — xem lý do ở {@code NotificationService}. */
    private void notifyShiftDecision(StaffShift shift, String verb) {
        StringBuilder body = new StringBuilder();
        body.append("<p>Ca <strong>").append(shift.getShiftType().getLabel())
                .append("</strong> ngày ").append(shift.getShiftDate().format(DATE_FORMAT))
                .append(" của anh/chị đã ").append(verb).append(".</p>");

        if (shift.getApproverComment() != null && !shift.getApproverComment().isBlank()) {
            body.append("<p><em>Ghi chú của trưởng khoa: “")
                    .append(shift.getApproverComment()).append("”</em></p>");
        }

        emailService.sendStaffNotification(
                shift.getUser().getEmail(),
                "MediTrust - Kết quả đăng ký ca trực",
                "Ca trực " + verb,
                body.toString());

        StringBuilder note = new StringBuilder(shift.getShiftType().getLabel())
                .append(" • ").append(shift.getShiftDate().format(DATE_FORMAT));
        if (shift.getApproverComment() != null && !shift.getApproverComment().isBlank()) {
            note.append(" • Ghi chú: “").append(shift.getApproverComment()).append("”");
        }

        notificationService.push(
                shift.getUser(),
                shift.getStatus() == ApprovalStatus.APPROVED
                        ? "bi-check-circle text-success" : "bi-x-circle text-danger",
                "Ca trực " + verb,
                note.toString(),
                "/doctor/work-schedule");
    }

    // ===================== SỰ KIỆN TRÊN LỊCH =====================

    @Override
    public List<ScheduleEventDTO> getEvents(User user, LocalDate from, LocalDate to) {
        List<ScheduleEventDTO> events = new ArrayList<>();

        addStaffShiftEvents(events, user, from, to);
        addClinicScheduleEvents(events, user, from, to);
        addLeaveEvents(events, user, from, to);
        addBlockTimeEvents(events, user, from, to);

        return events;
    }

    /** Phiên trực và hội chẩn. Lùi 1 ngày để bắt được ca qua đêm bắt đầu từ hôm trước. */
    private void addStaffShiftEvents(List<ScheduleEventDTO> events, User user,
                                     LocalDate from, LocalDate to) {
        List<StaffShift> shifts = staffShiftRepository
                .findByUserIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                        user.getId(), from.minusDays(1), to);

        for (StaffShift shift : shifts) {
            if (shift.getStatus() == ApprovalStatus.CANCELED
                    || shift.getStatus() == ApprovalStatus.REJECTED) {
                continue;
            }

            // Ca khám được vẽ từ bảng Schedule (addClinicScheduleEvents). Bỏ qua ở đây để
            // một ca khám không hiện thành HAI khối chồng lên nhau.
            if (shift.isClinic()) {
                continue;
            }

            ScheduleEventDTO event = new ScheduleEventDTO();
            event.setId(shift.getId());
            event.setKind(resolveKind(shift.getShiftType()));
            event.setTitle(buildShiftTitle(shift));
            event.setSubtitle(shift.getDepartment() != null ? shift.getDepartment().getName() : null);
            event.setDate(shift.getShiftDate().format(ISO_DATE));
            event.setEndDate(shift.getEndsAt().toLocalDate().format(ISO_DATE));
            event.setStartTime(shift.getStartTime().format(TIME_FORMAT));
            event.setEndTime(shift.getEndTime().format(TIME_FORMAT));
            event.setOvernight(shift.isOvernight());
            event.setStatus(shift.getStatus().name());
            event.setStatusLabel(shift.getStatus().getLabel());
            event.setNeedsCover(shift.isNeedsCover());
            event.setReadOnly(false);
            event.setNote(shift.getNote());
            events.add(event);
        }
    }

    private String resolveKind(ShiftType type) {
        if (type.isDuty()) {
            return "DUTY";
        }
        return type == ShiftType.HOI_CHAN ? "MEETING" : "CLINIC";
    }

    private String buildShiftTitle(StaffShift shift) {
        if (shift.isDuty() && shift.getDutyRole() != null) {
            return shift.getDutyRole().getLabel();
        }
        return shift.getShiftType().getLabel();
    }

    /**
     * Ca khám định kỳ từ bảng Schedule (theo thứ trong tuần) trải ra thành từng ngày cụ thể
     * của khoảng đang xem. Chỉ bác sĩ mới có nguồn này.
     */
    private void addClinicScheduleEvents(List<ScheduleEventDTO> events, User user,
                                         LocalDate from, LocalDate to) {
        Optional<Doctor> doctor = doctorRepository.findByUserId(user.getId());
        if (doctor.isEmpty()) {
            return;
        }

        String departmentName = (doctor.get().getDepartment() != null)
                ? doctor.get().getDepartment().getName() : null;
        LocalDate editableWeek = nextWeekStart();

        // Mỗi tuần trong khoảng đang xem có lịch riêng, nên tra theo tuần và nhớ lại để
        // không phải truy vấn bảy lần cho bảy ngày của cùng một tuần.
        java.util.Map<LocalDate, List<Schedule>> schedulesByWeek = new java.util.HashMap<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate week = LeavePolicy.weekStartOf(date);
            List<Schedule> schedules = schedulesByWeek.computeIfAbsent(week,
                    w -> scheduleRepository.findEffective(doctor.get().getId(), w));

            for (Schedule schedule : schedules) {
                if (schedule.getDayOfWeek() != date.getDayOfWeek()) {
                    continue;
                }

                boolean editable = week.equals(editableWeek);
                ScheduleEventDTO event = new ScheduleEventDTO();
                event.setId(schedule.getId());
                event.setKind("CLINIC");
                event.setTitle(labelForClinicHours(schedule));
                event.setSubtitle(departmentName);
                event.setDate(date.format(ISO_DATE));
                event.setEndDate(date.format(ISO_DATE));
                event.setStartTime(schedule.getStartTime().format(TIME_FORMAT));
                event.setEndTime(schedule.getEndTime().format(TIME_FORMAT));
                event.setStatus(ApprovalStatus.APPROVED.name());
                event.setStatusLabel(clinicWeekLabel(week, editableWeek));
                // readOnly = tuần đã chốt: giao diện làm mờ khối và không cho sửa.
                event.setReadOnly(!editable);
                event.setNote(clinicWeekNote(week, editableWeek));
                events.add(event);
            }
        }
    }

    /** Ca khám thuộc tuần nào so với tuần đang mở đăng ký. */
    private String clinicWeekLabel(LocalDate week, LocalDate editableWeek) {
        if (week.equals(editableWeek)) {
            return "Ca khám tuần sau (đang mở đăng ký)";
        }
        return week.isBefore(editableWeek) ? "Ca khám đã chốt" : "Ca khám dự kiến";
    }

    private String clinicWeekNote(LocalDate week, LocalDate editableWeek) {
        if (week.equals(editableWeek)) {
            return "Tuần sau (" + weekLabel(week) + ") — anh/chị còn sửa được ở mục \"Ca khám\".";
        }
        if (week.isBefore(editableWeek)) {
            return "Tuần " + weekLabel(week) + " đã chốt, không sửa được. "
                    + "Nếu bận đột xuất, anh/chị dùng \"Báo bận đột xuất\" hoặc nhờ lễ tân dời lịch.";
        }
        return "Dự kiến theo lần đăng ký gần nhất. Đến lượt tuần " + weekLabel(week)
                + " anh/chị sẽ đăng ký lại.";
    }

    private String labelForClinicHours(Schedule schedule) {
        return schedule.getStartTime().isBefore(LeavePolicy.OFFICE_START.plusHours(5))
                ? "Ca sáng" : "Ca chiều";
    }

    private void addLeaveEvents(List<ScheduleEventDTO> events, User user,
                                LocalDate from, LocalDate to) {
        for (LeaveRequest leave : leaveRequestRepository.findOverlapping(user.getId(), from, to)) {
            if (leave.getStatus() == ApprovalStatus.CANCELED
                    || leave.getStatus() == ApprovalStatus.REJECTED) {
                continue;
            }

            ScheduleEventDTO event = new ScheduleEventDTO();
            event.setId(leave.getId());
            event.setKind("LEAVE");
            event.setTitle(leave.getLeaveType().getLabel()
                    + (leave.getHalfDaySession().isHalfDay() ? " (nửa ngày)" : ""));
            event.setSubtitle(leave.isEmergency() ? "Báo bận đột xuất" : null);
            event.setDate(leave.getStartDate().format(ISO_DATE));
            event.setEndDate(leave.getEndDate().format(ISO_DATE));
            event.setStartTime(leave.getHalfDaySession().getStartTime().format(TIME_FORMAT));
            event.setEndTime(leave.getHalfDaySession().getEndTime().format(TIME_FORMAT));
            event.setStatus(leave.getStatus().name());
            event.setStatusLabel(leave.getStatus().getLabel());
            event.setNote(leave.getReason());
            events.add(event);
        }
    }

    /** Giờ bận đột xuất bác sĩ tự chặn tay (không gắn với đơn nghỉ nào). */
    private void addBlockTimeEvents(List<ScheduleEventDTO> events, User user,
                                    LocalDate from, LocalDate to) {
        Optional<Doctor> doctor = doctorRepository.findByUserId(user.getId());
        if (doctor.isEmpty()) {
            return;
        }

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<DoctorBlockTime> blocks = doctorBlockTimeRepository
                    .findByDoctorIdAndBlockDate(doctor.get().getId(), date);

            for (DoctorBlockTime block : blocks) {
                if (block.getLeaveRequestId() != null) {
                    continue; // đã hiện dưới dạng đơn nghỉ, không vẽ trùng
                }

                ScheduleEventDTO event = new ScheduleEventDTO();
                event.setId(block.getId());
                event.setKind("BLOCK");
                event.setTitle("Bận đột xuất");
                event.setSubtitle(block.getReason());
                event.setDate(date.format(ISO_DATE));
                event.setEndDate(date.format(ISO_DATE));
                event.setStartTime(block.getStartTime().format(TIME_FORMAT));
                event.setEndTime(block.getEndTime().format(TIME_FORMAT));
                event.setNote(block.getReason());
                events.add(event);
            }
        }
    }

    // ===================== TRUY VẤN =====================

    @Override
    public List<StaffShift> findDutyShifts(Long userId) {
        return staffShiftRepository
                .findByUserIdAndStatusNotOrderByShiftDateDescStartTimeDesc(userId, ApprovalStatus.CANCELED)
                .stream()
                .filter(StaffShift::isDuty)
                .toList();
    }

    @Override
    public Optional<StaffShift> findShift(Long shiftId) {
        return staffShiftRepository.findById(shiftId);
    }

    @Override
    public List<StaffShift> findShiftsNeedingCover(Long userId) {
        return staffShiftRepository.findByUserIdAndNeedsCoverTrueOrderByShiftDateAsc(userId);
    }

    @Override
    public List<StaffShift> findDepartmentShifts(Long departmentId, LocalDate from, LocalDate to) {
        if (departmentId == null) {
            return new ArrayList<>();
        }
        return staffShiftRepository
                .findByDepartmentIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(departmentId, from, to);
    }

    @Override
    public List<StaffShift> findDepartmentShiftsByStatus(Long departmentId, ApprovalStatus status) {
        if (departmentId == null) {
            return new ArrayList<>();
        }
        return staffShiftRepository.findByDepartmentIdAndStatusOrderByShiftDateAsc(departmentId, status);
    }

    @Override
    public List<LocalDate> findUncoveredDutyDates(Long departmentId, LocalDate from, LocalDate to) {
        List<LocalDate> uncovered = new ArrayList<>();
        if (departmentId == null) {
            return uncovered;
        }

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (staffShiftRepository.countApprovedDutyOnDate(departmentId, date) == 0) {
                uncovered.add(date);
            }
        }
        return uncovered;
    }
}
