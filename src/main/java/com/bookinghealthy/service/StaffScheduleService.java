package com.bookinghealthy.service;

import com.bookinghealthy.dto.ScheduleEventDTO;
import com.bookinghealthy.dto.ShiftRegisterResultDTO;
import com.bookinghealthy.model.ApprovalStatus;
import com.bookinghealthy.model.DutyRole;
import com.bookinghealthy.model.ShiftType;
import com.bookinghealthy.model.StaffShift;
import com.bookinghealthy.model.User;

import java.time.LocalDate;
import java.util.List;

/**
 * Lịch làm việc và lịch trực của nhân viên.
 *
 * Ranh giới nghiệp vụ quan trọng nhất của service này:
 * - CA KHÁM nằm trong giờ hành chính và sinh khung giờ cho bệnh nhân đặt (qua bảng Schedule).
 * - PHIÊN TRỰC nằm NGOÀI giờ hành chính (Thông tư 32/2023/TT-BYT) và KHÔNG bao giờ
 *   sinh khung giờ đặt khám.
 */
public interface StaffScheduleService {

    /**
     * Đăng ký một ca (ca khám hoặc phiên trực).
     * Kết quả tách riêng "từ chối" và "nhận nhưng có cảnh báo" vì lịch trực phải công bố
     * trước 1 tuần — đăng ký sát hơn vẫn nhận được nhưng phải báo cho người dùng biết.
     */
    ShiftRegisterResultDTO registerShift(User user, ShiftType shiftType, LocalDate date,
                                         DutyRole dutyRole, String note);

    /**
     * Đăng ký lịch khám cho TUẦN SAU trong MỘT lần: thay các ca khám CỦA RIÊNG TUẦN ĐÓ bằng
     * danh sách Thứ được chọn cho buổi sáng và buổi chiều. Chỉ bác sĩ mới có ca khám.
     *
     * Tuần sau là tuần DUY NHẤT sửa được: lịch tuần hiện tại đang chạy (bệnh nhân đã đặt vào
     * đó) và lịch các tuần đã qua là hồ sơ, cả hai đều không bị hàm này đụng tới —
     * {@code Schedule.weekStart} giữ chúng tách bạch.
     *
     * Yêu cầu: MỖI ngày trong tuần phải có tối thiểu 1 ca (phủ kín cả tuần), và phải đăng ký
     * trước hạn chốt lịch ({@code LeavePolicy.CLINIC_DEADLINE_*}). Sau khi lưu, hệ thống ghi
     * nhận bác sĩ đã đăng ký cho tuần sau (phục vụ nhắc nhở & tự động đăng ký).
     *
     * @return null nếu thành công, ngược lại là lý do từ chối
     */
    String saveClinicTemplate(User user, java.util.List<java.time.DayOfWeek> morningDays,
                              java.util.List<java.time.DayOfWeek> afternoonDays);

    /**
     * Ca khám của TUẦN SAU, để tích sẵn bảng đăng ký. Chưa đăng ký thì trả về lần đăng ký
     * gần nhất, coi như gợi ý "giống tuần trước".
     */
    java.util.List<com.bookinghealthy.model.Schedule> getClinicSchedules(Long userId);

    /** Thứ Hai của TUẦN SAU — tuần duy nhất bác sĩ được đăng ký / sửa lịch khám. */
    LocalDate nextWeekStart();

    /** Chưa tới hạn chốt lịch khám tuần sau (22:00 Chủ nhật) nên vẫn còn sửa được. */
    boolean isClinicRegistrationOpen();

    /** Hạn chốt lịch khám tuần sau, dạng tiếng Việt để hiện lên giao diện: "Chủ nhật 22:00". */
    String clinicDeadlineLabel();

    /** Bác sĩ đã đăng ký (hoặc được tự động xếp) lịch khám cho tuần sau chưa. */
    boolean hasRegisteredForNextWeek(Long userId);

    /**
     * Nhắc các bác sĩ CHƯA đăng ký lịch khám tuần sau (gửi email).
     * @param departmentId null = toàn viện; ngược lại chỉ khoa đó.
     * @return số bác sĩ được nhắc.
     */
    int sendNextWeekRegistrationReminders(Long departmentId);

    /**
     * Tự động xếp lịch khám CẢ TUẦN (mỗi ngày tối thiểu 1 ca) cho các bác sĩ vẫn chưa đăng ký
     * lịch tuần sau, rồi báo cho họ. Chạy tự động vào tối Chủ nhật, hoặc do trưởng khoa chốt.
     * @return số bác sĩ được tự động xếp.
     */
    int autoRegisterUnregisteredDoctors(Long departmentId);

    /** @return null nếu hủy được, ngược lại là lý do không hủy được. */
    String cancelShift(Long shiftId, User user);

    /**
     * Toàn bộ sự kiện hiển thị trên lưới lịch, gom từ 4 nguồn:
     * ca trực/hội chẩn ({@code StaffShift}), ca khám định kỳ ({@code Schedule}),
     * đơn nghỉ ({@code LeaveRequest}) và giờ bận đột xuất ({@code DoctorBlockTime}).
     */
    List<ScheduleEventDTO> getEvents(User user, LocalDate from, LocalDate to);

    /** Phiên trực của một người, mới nhất trước — tab "Trực của tôi". */
    List<StaffShift> findDutyShifts(Long userId);

    java.util.Optional<StaffShift> findShift(Long shiftId);

    List<StaffShift> findShiftsNeedingCover(Long userId);

    /** Lịch trực toàn khoa trong khoảng ngày — màn hình của trưởng khoa. */
    List<StaffShift> findDepartmentShifts(Long departmentId, LocalDate from, LocalDate to);

    List<StaffShift> findDepartmentShiftsByStatus(Long departmentId, ApprovalStatus status);

    /**
     * Duyệt phiên trực. Sau khi duyệt, hệ thống tự sinh đơn nghỉ bù theo
     * Quyết định 73/2011/QĐ-TTg (trực 24/24 vào ngày thường nghỉ bù 1 ngày, ngày lễ 2 ngày).
     */
    String approveShift(Long shiftId, User approver, String comment);

    String rejectShift(Long shiftId, User approver, String comment);

    /**
     * Các ngày trong khoảng mà khoa CHƯA có ai trực — Thông tư 32/2023/TT-BYT yêu cầu
     * phiên trực phải có đủ nhân lực, tối thiểu một bác sĩ.
     */
    List<LocalDate> findUncoveredDutyDates(Long departmentId, LocalDate from, LocalDate to);
}
