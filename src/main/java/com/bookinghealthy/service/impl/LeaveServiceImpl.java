package com.bookinghealthy.service.impl;

import com.bookinghealthy.config.LeavePolicy;
import com.bookinghealthy.dto.LeaveBalanceDTO;
import com.bookinghealthy.dto.LeaveRequestDTO;
import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.*;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.LeaveService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Nghỉ phép của bác sĩ và lễ tân.
 *
 * Điểm quan trọng nhất: khi đơn của một BÁC SĨ có hiệu lực, service này sinh các bản ghi
 * {@link DoctorBlockTime} phủ khoảng nghỉ. Toàn bộ luồng đặt lịch (BookingApi,
 * TimeSlotService) và trợ lý AI (AiController) đã tôn trọng DoctorBlockTime từ trước,
 * nên bệnh nhân tự động không đặt được vào ngày bác sĩ nghỉ mà không phải sửa gì ở đó.
 */
@Service
public class LeaveServiceImpl implements LeaveService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DoctorBlockTimeRepository doctorBlockTimeRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EmailService emailService;

    // ===================== HỒ SƠ & SỐ DƯ =====================

    @Override
    @Transactional
    public StaffProfile getOrCreateProfile(User user) {
        return staffProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    StaffProfile profile = new StaffProfile();
                    profile.setUser(user);
                    profile.setWorkCondition(resolveWorkCondition(user));
                    profile.setHireDate(estimateHireDate(user));
                    profile.setSocialInsuranceYears(
                            LeavePolicy.yearsOfService(profile.getHireDate(), LocalDate.now()));
                    return staffProfileRepository.save(profile);
                });
    }

    @Override
    public LeaveBalanceDTO getBalance(User user, int year) {
        StaffProfile profile = getOrCreateProfile(user);

        int yearsOfService = LeavePolicy.yearsOfService(profile.getHireDate(), LocalDate.now());
        int seniorityBonus = LeavePolicy.seniorityBonus(yearsOfService);
        int carriedOver = (profile.getCarriedOverDays() != null) ? profile.getCarriedOverDays() : 0;

        int annualQuota = LeavePolicy.annualQuota(profile.getWorkCondition(), yearsOfService) + carriedOver;
        int insuranceYears = (profile.getSocialInsuranceYears() != null)
                ? profile.getSocialInsuranceYears() : yearsOfService;
        int sickQuota = LeavePolicy.sickQuota(profile.getWorkCondition(), insuranceYears);

        BigDecimal annualUsed = usedDays(user.getId(), LeaveType.PHEP_NAM, year);
        BigDecimal sickUsed = usedDays(user.getId(), LeaveType.OM_DAU, year);

        LeaveBalanceDTO balance = new LeaveBalanceDTO();
        balance.setYear(year);
        balance.setWorkCondition(profile.getWorkCondition());
        balance.setYearsOfService(yearsOfService);
        balance.setSeniorityBonus(seniorityBonus);
        balance.setCarriedOverDays(carriedOver);
        balance.setAnnualQuota(annualQuota);
        balance.setAnnualUsed(annualUsed);
        balance.setAnnualRemaining(BigDecimal.valueOf(annualQuota).subtract(annualUsed));
        balance.setSickQuota(sickQuota);
        balance.setSickUsed(sickUsed);
        balance.setSickRemaining(BigDecimal.valueOf(sickQuota).subtract(sickUsed));
        balance.setExplanation(buildExplanation(profile.getWorkCondition(), seniorityBonus, sickQuota));

        return balance;
    }

    private String buildExplanation(WorkCondition condition, int seniorityBonus, int sickQuota) {
        StringBuilder text = new StringBuilder();
        text.append("Bộ luật Lao động 2019, Điều 113: ")
                .append(condition.getBaseAnnualLeaveDays())
                .append(" ngày phép/năm cho ")
                .append(condition.getLabel().toLowerCase())
                .append(".");

        if (seniorityBonus > 0) {
            text.append(" Điều 114: cộng thêm ").append(seniorityBonus)
                    .append(" ngày thâm niên (1 ngày mỗi 5 năm làm việc).");
        }

        text.append(" Luật BHXH 2024, Điều 43: tối đa ").append(sickQuota).append(" ngày nghỉ ốm/năm.");
        return text.toString();
    }

    private BigDecimal usedDays(Long userId, LeaveType type, int year) {
        BigDecimal used = leaveRequestRepository.sumApprovedDays(userId, type, year);
        return (used != null) ? used : BigDecimal.ZERO;
    }

    /** Khoa nặng nhọc thì hưởng mức phép cao hơn — xem LeavePolicy.HEAVY_DEPARTMENTS. */
    private WorkCondition resolveWorkCondition(User user) {
        return doctorRepository.findByUserId(user.getId())
                .map(Doctor::getDepartment)
                .filter(dept -> dept != null && LeavePolicy.HEAVY_DEPARTMENTS.contains(dept.getName()))
                .map(dept -> WorkCondition.HEAVY)
                .orElse(WorkCondition.NORMAL);
    }

    /**
     * Dữ liệu seed không có ngày vào làm, nên suy từ số năm kinh nghiệm của bác sĩ.
     * Lễ tân và tài khoản khác coi như mới vào làm hôm nay.
     */
    private LocalDate estimateHireDate(User user) {
        return doctorRepository.findByUserId(user.getId())
                .map(Doctor::getExperienceYears)
                .filter(years -> years != null && years > 0)
                .map(years -> LocalDate.now().minusYears(years))
                .orElse(LocalDate.now());
    }

    // ===================== GỬI ĐƠN =====================

    @Override
    @Transactional
    public String submit(User user, LeaveRequestDTO form) {
        String validationError = validate(user, form);
        if (validationError != null) {
            return validationError;
        }

        LeaveRequest request = new LeaveRequest();
        request.setUser(user);
        request.setLeaveType(form.getLeaveType());
        request.setSubReason(form.getSubReason());
        request.setStartDate(form.getStartDate());
        request.setEndDate(form.getEndDate());
        request.setHalfDaySession(resolveHalfDay(form));
        request.setReason(form.getReason());
        request.setEmergency(form.isEmergency());
        request.setTotalDays(calculateTotalDays(form.getStartDate(), form.getEndDate(), resolveHalfDay(form)));
        request.setStatus(ApprovalStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(request);

        // Báo bận đột xuất phải chặn lịch NGAY, không chờ duyệt: bệnh nhân không được
        // đặt tiếp vào khoảng giờ mà bác sĩ chắc chắn không có mặt.
        if (saved.isEmergency()) {
            applyBlockTimes(saved);
        }

        return null;
    }

    /** Nghỉ nửa ngày chỉ có nghĩa khi đơn gói gọn trong một ngày. */
    private HalfDaySession resolveHalfDay(LeaveRequestDTO form) {
        HalfDaySession session = (form.getHalfDaySession() != null)
                ? form.getHalfDaySession() : HalfDaySession.NONE;
        boolean singleDay = form.getStartDate() != null && form.getStartDate().equals(form.getEndDate());
        return singleDay ? session : HalfDaySession.NONE;
    }

    private String validate(User user, LeaveRequestDTO form) {
        if (form.getLeaveType() == null) {
            return "Vui lòng chọn loại nghỉ.";
        }
        if (form.getLeaveType().isSystemGenerated()) {
            return "Nghỉ bù sau trực do hệ thống tự tạo khi phiên trực được duyệt, "
                    + "anh/chị không cần đăng ký.";
        }
        if (form.getStartDate() == null || form.getEndDate() == null) {
            return "Vui lòng chọn ngày bắt đầu và ngày kết thúc.";
        }
        if (form.getEndDate().isBefore(form.getStartDate())) {
            return "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.";
        }
        if (form.getStartDate().isBefore(LocalDate.now())) {
            return "Không thể đăng ký nghỉ cho ngày trong quá khứ.";
        }

        // Báo bận đột xuất chỉ dành cho tình huống trong vòng 24 giờ tới.
        if (form.isEmergency()
                && form.getStartDate().isAfter(LocalDate.now().plusDays(1))) {
            return "Báo bận đột xuất chỉ dùng cho trong vòng 24 giờ tới. "
                    + "Nghỉ xa hơn, anh/chị hãy đăng ký nghỉ phép bình thường.";
        }

        String overlapError = checkOverlap(user, form);
        if (overlapError != null) {
            return overlapError;
        }

        return checkQuota(user, form);
    }

    private String checkOverlap(User user, LeaveRequestDTO form) {
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                user.getId(), form.getStartDate(), form.getEndDate());

        boolean clash = overlapping.stream()
                .anyMatch(l -> l.getStatus() == ApprovalStatus.APPROVED
                        || l.getStatus() == ApprovalStatus.PENDING);

        if (clash) {
            return "Anh/chị đã có đơn nghỉ trong khoảng thời gian này rồi.";
        }
        return null;
    }

    /** Chặn vượt hạn mức, kèm trích căn cứ pháp lý để người xin hiểu vì sao bị từ chối. */
    private String checkQuota(User user, LeaveRequestDTO form) {
        BigDecimal requested = calculateTotalDays(
                form.getStartDate(), form.getEndDate(), resolveHalfDay(form));
        LeaveBalanceDTO balance = getBalance(user, form.getStartDate().getYear());

        switch (form.getLeaveType()) {
            case PHEP_NAM -> {
                if (requested.compareTo(balance.getAnnualRemaining()) > 0) {
                    return "Anh/chị chỉ còn " + strip(balance.getAnnualRemaining())
                            + " ngày phép năm nhưng đang xin nghỉ " + strip(requested) + " ngày. "
                            + balance.getExplanation();
                }
            }
            case OM_DAU -> {
                if (requested.compareTo(balance.getSickRemaining()) > 0) {
                    return "Số ngày nghỉ ốm còn lại trong năm là " + strip(balance.getSickRemaining())
                            + " ngày (Luật BHXH 2024, Điều 43), không đủ cho "
                            + strip(requested) + " ngày.";
                }
            }
            case VIEC_RIENG_CO_LUONG, VIEC_RIENG_KHONG_LUONG -> {
                if (form.getSubReason() == null) {
                    return "Vui lòng chọn trường hợp nghỉ việc riêng (Bộ luật Lao động 2019, Điều 115).";
                }
                if (form.getSubReason().getLeaveType() != form.getLeaveType()) {
                    return "Trường hợp \"" + form.getSubReason().getLabel() + "\" thuộc loại "
                            + form.getSubReason().getLeaveType().getLabel() + ".";
                }
                int max = LeavePolicy.personalLeaveMaxDays(form.getSubReason());
                if (requested.compareTo(BigDecimal.valueOf(max)) > 0) {
                    return "Trường hợp \"" + form.getSubReason().getLabel() + "\" được nghỉ tối đa "
                            + max + " ngày (Bộ luật Lao động 2019, Điều 115).";
                }
            }
            case THAI_SAN -> {
                if (requested.compareTo(BigDecimal.valueOf(LeavePolicy.MATERNITY_DAYS)) > 0) {
                    return "Chế độ thai sản tối đa " + LeavePolicy.MATERNITY_DAYS + " ngày (6 tháng).";
                }
            }
            default -> {
                // Nghỉ không lương theo thỏa thuận: Điều 115 khoản 3 không giới hạn số ngày,
                // trưởng khoa quyết định khi duyệt.
            }
        }
        return null;
    }

    /**
     * Số ngày quy đổi. Phép năm tính theo NGÀY LÀM VIỆC nên bỏ qua ngày lễ dương lịch;
     * bệnh viện làm cả thứ 7 và chủ nhật nên cuối tuần vẫn tính.
     */
    private BigDecimal calculateTotalDays(LocalDate start, LocalDate end, HalfDaySession session) {
        if (start == null || end == null) {
            return BigDecimal.ZERO;
        }
        if (session.isHalfDay()) {
            return BigDecimal.valueOf(session.getDayFactor());
        }

        long days = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!LeavePolicy.isPublicHoliday(date)) {
                days++;
            }
        }
        return BigDecimal.valueOf(days);
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // ===================== HỦY / DUYỆT / TỪ CHỐI =====================

    @Override
    @Transactional
    public String cancelByOwner(Long leaveId, User user) {
        Optional<LeaveRequest> found = leaveRequestRepository.findById(leaveId);
        if (found.isEmpty()) {
            return "Không tìm thấy đơn nghỉ.";
        }

        LeaveRequest request = found.get();
        if (!request.getUser().getId().equals(user.getId())) {
            return "Anh/chị không có quyền hủy đơn này.";
        }
        if (!request.isCancelableByOwner()) {
            return "Đơn đã được xử lý, anh/chị không tự hủy được nữa. "
                    + "Vui lòng liên hệ trưởng khoa.";
        }

        request.setStatus(ApprovalStatus.CANCELED);
        request.setDecidedAt(LocalDateTime.now());
        leaveRequestRepository.save(request);

        // Đơn báo bận đột xuất đã chặn giờ ngay lúc gửi, hủy đơn thì phải mở lại.
        removeBlockTimes(request);
        return null;
    }

    @Override
    @Transactional
    public String approve(Long leaveId, User approver, String comment) {
        Optional<LeaveRequest> found = leaveRequestRepository.findById(leaveId);
        if (found.isEmpty()) {
            return "Không tìm thấy đơn nghỉ.";
        }

        LeaveRequest request = found.get();
        if (request.getStatus() == ApprovalStatus.APPROVED) {
            return "Đơn này đã được duyệt trước đó.";
        }
        if (request.getStatus() == ApprovalStatus.CANCELED) {
            return "Đơn đã bị nhân viên hủy, không duyệt được nữa.";
        }

        request.setStatus(ApprovalStatus.APPROVED);
        request.setApprover(approver);
        request.setApproverComment(comment);
        request.setDecidedAt(LocalDateTime.now());
        leaveRequestRepository.save(request);

        applyBlockTimes(request);
        notifyDecision(request, "được phê duyệt");
        return null;
    }

    @Override
    @Transactional
    public String reject(Long leaveId, User approver, String comment) {
        Optional<LeaveRequest> found = leaveRequestRepository.findById(leaveId);
        if (found.isEmpty()) {
            return "Không tìm thấy đơn nghỉ.";
        }

        LeaveRequest request = found.get();
        if (request.getStatus() == ApprovalStatus.CANCELED) {
            return "Đơn đã bị nhân viên hủy.";
        }

        request.setStatus(ApprovalStatus.REJECTED);
        request.setApprover(approver);
        request.setApproverComment(comment);
        request.setDecidedAt(LocalDateTime.now());
        leaveRequestRepository.save(request);

        // Trả lại các khung giờ đã chặn (đơn đã duyệt trước đó, hoặc báo bận đột xuất).
        removeBlockTimes(request);
        notifyDecision(request, "bị từ chối");
        return null;
    }

    private void notifyDecision(LeaveRequest request, String verb) {
        String range = request.getStartDate().format(DATE_FORMAT)
                + (request.getStartDate().equals(request.getEndDate())
                ? "" : " - " + request.getEndDate().format(DATE_FORMAT));

        StringBuilder body = new StringBuilder();
        body.append("<p>Đơn <strong>").append(request.getLeaveType().getLabel())
                .append("</strong> (").append(range).append(") của anh/chị đã ")
                .append(verb).append(".</p>");

        if (request.getApproverComment() != null && !request.getApproverComment().isBlank()) {
            body.append("<p><em>Lời phê của trưởng khoa: “")
                    .append(request.getApproverComment()).append("”</em></p>");
        }

        emailService.sendStaffNotification(
                request.getUser().getEmail(),
                "MediTrust - Kết quả đơn nghỉ phép",
                "Đơn nghỉ phép " + verb,
                body.toString());
    }

    // ===================== CHẶN LỊCH KHÁM =====================

    /**
     * Sinh DoctorBlockTime phủ khoảng nghỉ. Chỉ áp dụng cho BÁC SĨ — lễ tân không có
     * khung giờ khám nào để chặn.
     */
    private void applyBlockTimes(LeaveRequest request) {
        Optional<Doctor> doctor = doctorRepository.findByUserId(request.getUser().getId());
        if (doctor.isEmpty()) {
            return;
        }

        // Chạy lại cho chắc: tránh nhân đôi khi đơn báo bận đột xuất được duyệt sau đó.
        removeBlockTimes(request);

        HalfDaySession session = request.getHalfDaySession();
        String reason = "Nghỉ phép: " + request.getLeaveType().getLabel();

        for (LocalDate date = request.getStartDate();
             !date.isAfter(request.getEndDate());
             date = date.plusDays(1)) {

            DoctorBlockTime block = new DoctorBlockTime();
            block.setDoctor(doctor.get());
            block.setBlockDate(date);
            block.setStartTime(session.getStartTime());
            block.setEndTime(session.getEndTime());
            block.setReason(reason);
            block.setLeaveRequestId(request.getId());
            doctorBlockTimeRepository.save(block);
        }
    }

    private void removeBlockTimes(LeaveRequest request) {
        List<DoctorBlockTime> blocks = doctorBlockTimeRepository.findByLeaveRequestId(request.getId());
        if (!blocks.isEmpty()) {
            doctorBlockTimeRepository.deleteAll(blocks);
        }
    }

    // ===================== TRUY VẤN =====================

    @Override
    public List<LeaveRequest> findByUser(Long userId) {
        return leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<LeaveRequest> findOverlapping(Long userId, LocalDate from, LocalDate to) {
        return leaveRequestRepository.findOverlapping(userId, from, to);
    }

    @Override
    public List<LeaveRequest> findByDepartment(Long departmentId, ApprovalStatus status) {
        if (departmentId == null) {
            return new ArrayList<>();
        }
        return leaveRequestRepository.findByDepartmentAndStatus(departmentId, status);
    }

    @Override
    public long countPendingInDepartment(Long departmentId) {
        if (departmentId == null) {
            return 0;
        }
        return leaveRequestRepository.countByDepartmentAndStatus(departmentId, ApprovalStatus.PENDING);
    }

    @Override
    public List<LeaveRequest> findBlockingOnDate(Long userId, LocalDate date) {
        return leaveRequestRepository.findBlockingOnDate(userId, date);
    }

    @Override
    public int countAffectedBookings(User user, LocalDate from, LocalDate to) {
        Optional<Doctor> doctor = doctorRepository.findByUserId(user.getId());
        if (doctor.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            total += bookingRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                    doctor.get().getId(), date, BookingStatus.CANCELED).size();
        }
        return total;
    }
}
