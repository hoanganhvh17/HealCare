package com.bookinghealthy.service;

import com.bookinghealthy.dto.LeaveBalanceDTO;
import com.bookinghealthy.dto.LeaveRequestDTO;
import com.bookinghealthy.model.ApprovalStatus;
import com.bookinghealthy.model.LeaveRequest;
import com.bookinghealthy.model.StaffProfile;
import com.bookinghealthy.model.User;

import java.time.LocalDate;
import java.util.List;

/**
 * Nghiệp vụ nghỉ phép của nhân viên (bác sĩ và lễ tân).
 *
 * Mọi hạn mức đều lấy từ {@code config/LeavePolicy} — không hardcode con số ở đây.
 */
public interface LeaveService {

    /** Hồ sơ nhân sự, tạo mới với giá trị mặc định nếu chưa có. */
    StaffProfile getOrCreateProfile(User user);

    /** Số ngày phép / ngày ốm còn lại trong năm. */
    LeaveBalanceDTO getBalance(User user, int year);

    /**
     * Gửi đơn nghỉ.
     *
     * @return null nếu thành công, ngược lại là lý do từ chối bằng tiếng Việt
     *         (theo đúng quy ước trả về của các service khác trong dự án)
     */
    String submit(User user, LeaveRequestDTO form);

    /** Nhân viên tự hủy đơn của mình, chỉ khi đơn còn ở trạng thái chờ duyệt. */
    String cancelByOwner(Long leaveId, User user);

    /** Trưởng khoa duyệt đơn: chặn lịch khám của bác sĩ trong khoảng nghỉ. */
    String approve(Long leaveId, User approver, String comment);

    /** Trưởng khoa từ chối đơn: gỡ các khung giờ đã chặn (nếu là đơn báo bận đột xuất). */
    String reject(Long leaveId, User approver, String comment);

    /**
     * Lý do đơn không còn ra quyết định được (đã duyệt / đã bị hủy / kỳ nghỉ đã trôi qua);
     * null nghĩa là trưởng khoa vẫn duyệt hoặc từ chối được.
     * <p>
     * Dùng chung cho giao diện (ẩn nút, gắn nhãn "Đã hết hiệu lực") và cho {@link #approve}
     * / {@link #reject} (chặn thật) nên hai bên không bao giờ nói khác nhau. Duyệt một kỳ
     * nghỉ đã kết thúc là sinh giờ chặn cho ngày quá khứ và trừ sai số dư phép.
     */
    String whyCannotDecide(LeaveRequest request);

    List<LeaveRequest> findByUser(Long userId);

    /** Các đơn của một người giao với khoảng ngày — dùng vẽ lịch. */
    List<LeaveRequest> findOverlapping(Long userId, LocalDate from, LocalDate to);

    /** Hàng đợi duyệt của trưởng khoa. */
    List<LeaveRequest> findByDepartment(Long departmentId, ApprovalStatus status);

    /**
     * Số đơn chờ duyệt mà trưởng khoa CÒN xử lý được — đơn có kỳ nghỉ đã trôi qua bị loại,
     * vì không duyệt cũng không từ chối được nữa (xem {@link #whyCannotDecide}).
     */
    long countPendingInDepartment(Long departmentId);

    /** Đơn đang chặn lịch trong ngày (đã duyệt, hoặc báo bận đột xuất chờ duyệt). */
    List<LeaveRequest> findBlockingOnDate(Long userId, LocalDate date);

    /**
     * Số lịch hẹn đã đặt rơi vào khoảng nghỉ — hiện cảnh báo cho trưởng khoa trước khi
     * duyệt, kèm đường dẫn sang công cụ hủy/dời hàng loạt của lễ tân.
     */
    int countAffectedBookings(User user, LocalDate from, LocalDate to);
}
