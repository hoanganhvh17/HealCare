package com.bookinghealthy.service.impl;

import com.bookinghealthy.config.LeavePolicy;
import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.*;
import com.bookinghealthy.service.EmailService;
import com.bookinghealthy.service.ShiftCoverService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ShiftCoverServiceImpl implements ShiftCoverService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private ShiftCoverRequestRepository coverRequestRepository;
    @Autowired private StaffShiftRepository staffShiftRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private EmailService emailService;

    // ===================== TÌM NGƯỜI ĐỦ ĐIỀU KIỆN =====================

    @Override
    public List<User> findCandidates(Long shiftId) {
        Optional<StaffShift> found = staffShiftRepository.findById(shiftId);
        if (found.isEmpty() || found.get().getDepartment() == null) {
            return new ArrayList<>();
        }

        StaffShift shift = found.get();
        Long ownerId = shift.getUser().getId();

        return doctorRepository.findByDepartmentId(shift.getDepartment().getId()).stream()
                .map(Doctor::getUser)
                .filter(colleague -> !colleague.getId().equals(ownerId))
                .filter(colleague -> isAvailableFor(colleague, shift))
                .toList();
    }

    /**
     * Một người nhận được ca khi: không nghỉ trong ngày đó, không có ca nào trùng giờ,
     * và đã qua thời gian nghỉ bù bắt buộc sau phiên trực gần nhất
     * (Quyết định 73/2011/QĐ-TTg, Điều 2 khoản 4).
     */
    private boolean isAvailableFor(User candidate, StaffShift shift) {
        if (!leaveRequestRepository.findBlockingOnDate(candidate.getId(), shift.getShiftDate()).isEmpty()) {
            return false;
        }

        List<StaffShift> nearby = staffShiftRepository
                .findByUserIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                        candidate.getId(), shift.getShiftDate().minusDays(2), shift.getShiftDate().plusDays(1));

        for (StaffShift existing : nearby) {
            if (existing.getStatus() == ApprovalStatus.REJECTED
                    || existing.getStatus() == ApprovalStatus.CANCELED) {
                continue;
            }

            // Trùng giờ
            if (shift.getStartsAt().isBefore(existing.getEndsAt())
                    && shift.getEndsAt().isAfter(existing.getStartsAt())) {
                return false;
            }

            // Chưa nghỉ đủ sau phiên trực trước đó
            if (existing.isDuty() && existing.getEndsAt().isBefore(shift.getStartsAt())) {
                int compensatoryDays = LeavePolicy.compensatoryDaysAfterDuty(
                        existing.getShiftType(), LeavePolicy.isPublicHoliday(existing.getShiftDate()));

                LocalDateTime restUntil = (compensatoryDays > 0)
                        ? existing.getEndsAt().toLocalDate().plusDays(compensatoryDays).atStartOfDay()
                        : existing.getEndsAt().plusHours(LeavePolicy.REST_HOURS_AFTER_PARTIAL_DUTY);

                if (shift.getStartsAt().isBefore(restUntil)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ===================== GỬI / TRẢ LỜI YÊU CẦU =====================

    @Override
    @Transactional
    public String request(Long shiftId, User requester, Long targetUserId, String reason) {
        Optional<StaffShift> found = staffShiftRepository.findById(shiftId);
        if (found.isEmpty()) {
            return "Không tìm thấy ca làm việc.";
        }

        StaffShift shift = found.get();
        if (!shift.getUser().getId().equals(requester.getId())) {
            return "Anh/chị chỉ xin đổi được ca của chính mình.";
        }
        if (shift.getStartsAt().isBefore(LocalDateTime.now())) {
            return "Ca đã bắt đầu hoặc đã qua, không xin đổi được nữa.";
        }

        User target = null;
        if (targetUserId != null) {
            target = findCandidates(shiftId).stream()
                    .filter(candidate -> candidate.getId().equals(targetUserId))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                return "Đồng nghiệp này không nhận được ca (đang nghỉ, trùng ca, hoặc chưa nghỉ đủ sau trực).";
            }
        }

        ShiftCoverRequest request = new ShiftCoverRequest();
        request.setShift(shift);
        request.setRequester(requester);
        request.setTargetUser(target);
        request.setReason(reason);
        request.setStatus(ApprovalStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        coverRequestRepository.save(request);

        shift.setNeedsCover(true);
        staffShiftRepository.save(shift);

        notifyInvitation(request, shift);
        return null;
    }

    private void notifyInvitation(ShiftCoverRequest request, StaffShift shift) {
        String body = "<p><strong>" + request.getRequester().getFullName()
                + "</strong> đang cần người thay ca <strong>" + shift.getShiftType().getLabel()
                + "</strong> ngày " + shift.getShiftDate().format(DATE_FORMAT) + " ("
                + shift.getStartTime() + " - " + shift.getEndTime() + ").</p>"
                + (request.getReason() != null && !request.getReason().isBlank()
                ? "<p>Lý do: " + request.getReason() + "</p>" : "")
                + "<p>Anh/chị vào mục <em>Lịch làm việc &amp; Nghỉ phép</em> để nhận ca.</p>";

        if (request.getTargetUser() != null) {
            emailService.sendStaffNotification(request.getTargetUser().getEmail(),
                    "NNL Hospital - Lời mời nhận ca trực", "Có đồng nghiệp cần người thay ca", body);
            return;
        }

        // Mở cho cả khoa: báo cho mọi người đủ điều kiện.
        for (User candidate : findCandidates(shift.getId())) {
            emailService.sendStaffNotification(candidate.getEmail(),
                    "NNL Hospital - Lời mời nhận ca trực", "Có đồng nghiệp cần người thay ca", body);
        }
    }

    @Override
    @Transactional
    public String accept(Long requestId, User accepter) {
        Optional<ShiftCoverRequest> found = coverRequestRepository.findById(requestId);
        if (found.isEmpty()) {
            return "Không tìm thấy yêu cầu đổi ca.";
        }

        ShiftCoverRequest request = found.get();
        if (request.getStatus() != ApprovalStatus.PENDING) {
            return "Yêu cầu này đã được xử lý rồi.";
        }
        if (request.getTargetUser() != null
                && !request.getTargetUser().getId().equals(accepter.getId())) {
            return "Yêu cầu này gửi đích danh cho người khác.";
        }

        StaffShift shift = request.getShift();
        if (!isAvailableFor(accepter, shift)) {
            return "Anh/chị không nhận được ca này: đang nghỉ, trùng ca khác, "
                    + "hoặc chưa nghỉ đủ sau phiên trực gần nhất.";
        }

        User previousOwner = shift.getUser();
        shift.setUser(accepter);
        shift.setNeedsCover(false);
        staffShiftRepository.save(shift);

        request.setStatus(ApprovalStatus.APPROVED);
        request.setRespondedAt(LocalDateTime.now());
        coverRequestRepository.save(request);

        // Các lời mời khác cho cùng ca này không còn ý nghĩa.
        closeOtherPendingRequests(shift.getId(), requestId);

        String body = "<p><strong>" + accepter.getFullName() + "</strong> đã nhận ca "
                + shift.getShiftType().getLabel() + " ngày "
                + shift.getShiftDate().format(DATE_FORMAT) + " giúp anh/chị.</p>";
        emailService.sendStaffNotification(previousOwner.getEmail(),
                "NNL Hospital - Đã có người nhận ca", "Ca của anh/chị đã có người thay", body);

        return null;
    }

    private void closeOtherPendingRequests(Long shiftId, Long keepRequestId) {
        for (ShiftCoverRequest other : coverRequestRepository.findByShiftIdAndStatus(shiftId, ApprovalStatus.PENDING)) {
            if (other.getId().equals(keepRequestId)) {
                continue;
            }
            other.setStatus(ApprovalStatus.CANCELED);
            other.setRespondedAt(LocalDateTime.now());
            coverRequestRepository.save(other);
        }
    }

    @Override
    @Transactional
    public String decline(Long requestId, User user) {
        Optional<ShiftCoverRequest> found = coverRequestRepository.findById(requestId);
        if (found.isEmpty()) {
            return "Không tìm thấy yêu cầu đổi ca.";
        }

        ShiftCoverRequest request = found.get();
        if (request.getStatus() != ApprovalStatus.PENDING) {
            return "Yêu cầu này đã được xử lý rồi.";
        }

        request.setStatus(ApprovalStatus.REJECTED);
        request.setRespondedAt(LocalDateTime.now());
        coverRequestRepository.save(request);

        // Lời mời mở cho cả khoa thì một người từ chối không có nghĩa là hết người nhận,
        // nên chỉ gỡ cờ khi đây là lời mời đích danh cuối cùng.
        if (coverRequestRepository.findByShiftIdAndStatus(
                request.getShift().getId(), ApprovalStatus.PENDING).isEmpty()) {
            StaffShift shift = request.getShift();
            shift.setNeedsCover(false);
            staffShiftRepository.save(shift);
        }
        return null;
    }

    @Override
    @Transactional
    public String cancel(Long requestId, User requester) {
        Optional<ShiftCoverRequest> found = coverRequestRepository.findById(requestId);
        if (found.isEmpty()) {
            return "Không tìm thấy yêu cầu đổi ca.";
        }

        ShiftCoverRequest request = found.get();
        if (!request.getRequester().getId().equals(requester.getId())) {
            return "Anh/chị chỉ rút lại được yêu cầu của chính mình.";
        }

        request.setStatus(ApprovalStatus.CANCELED);
        request.setRespondedAt(LocalDateTime.now());
        coverRequestRepository.save(request);

        StaffShift shift = request.getShift();
        shift.setNeedsCover(false);
        staffShiftRepository.save(shift);
        return null;
    }

    // ===================== TRUY VẤN =====================

    @Override
    public List<ShiftCoverRequest> findPendingForUser(User user) {
        Long departmentId = doctorRepository.findByUserId(user.getId())
                .map(Doctor::getDepartment)
                .map(Department::getId)
                .orElse(null);

        return coverRequestRepository.findPendingForUser(user.getId(), departmentId);
    }

    @Override
    public List<ShiftCoverRequest> findByRequester(Long requesterId) {
        return coverRequestRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId);
    }
}
