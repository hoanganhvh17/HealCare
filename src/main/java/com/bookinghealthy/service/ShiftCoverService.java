package com.bookinghealthy.service;

import com.bookinghealthy.model.ShiftCoverRequest;
import com.bookinghealthy.model.User;

import java.util.List;

/**
 * Tìm người thay ca. Áp dụng cho cả ca khám lẫn phiên trực.
 */
public interface ShiftCoverService {

    /**
     * Đồng nghiệp cùng khoa đủ điều kiện nhận ca: không nghỉ ngày đó, không trùng ca,
     * và đã qua thời gian nghỉ bù bắt buộc sau phiên trực gần nhất.
     */
    List<User> findCandidates(Long shiftId);

    /**
     * Gửi yêu cầu tìm người thay.
     *
     * @param targetUserId người được nhờ đích danh; null = mở lời mời cho cả khoa
     * @return null nếu gửi được, ngược lại là lý do từ chối
     */
    String request(Long shiftId, User requester, Long targetUserId, String reason);

    /** Nhận ca: đổi chủ ca và gỡ cờ cần thay. */
    String accept(Long requestId, User accepter);

    String decline(Long requestId, User user);

    /** Chủ ca rút lại yêu cầu. */
    String cancel(Long requestId, User requester);

    /** Lời mời nhận ca đang chờ một người trả lời (đích danh hoặc mở cho khoa). */
    List<ShiftCoverRequest> findPendingForUser(User user);

    List<ShiftCoverRequest> findByRequester(Long requesterId);
}
