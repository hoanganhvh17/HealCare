package com.bookinghealthy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một khối sự kiện trên lưới lịch làm việc. Gom từ 4 nguồn khác nhau
 * (ca khám, phiên trực, đơn nghỉ, giờ bận đột xuất) về CÙNG một hình dạng
 * để JavaScript chỉ phải vẽ một loại phần tử.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEventDTO {

    /** Id của bản ghi gốc. Có thể trùng nhau giữa các kind khác nhau. */
    private Long id;

    /**
     * Loại sự kiện, quyết định màu sắc và hành động khả dụng:
     * CLINIC (ca khám), DUTY (phiên trực), MEETING (hội chẩn),
     * LEAVE (đơn nghỉ), BLOCK (giờ bận đột xuất).
     */
    private String kind;

    private String title;

    /** Tên khoa, hiện trong ngoặc sau tiêu đề: "Trực chính (ICU)". */
    private String subtitle;

    /** yyyy-MM-dd — ngày bắt đầu. */
    private String date;

    /** yyyy-MM-dd — ngày kết thúc; khác date khi ca trực qua đêm hoặc nghỉ nhiều ngày. */
    private String endDate;

    /** HH:mm */
    private String startTime;

    /** HH:mm */
    private String endTime;

    private boolean overnight;

    /** PENDING / APPROVED / REJECTED / CANCELED — null với giờ bận đột xuất. */
    private String status;

    private String statusLabel;

    /** Ca trực đang cần người thay: hiện chấm đỏ + chip "Cần thay ca". */
    private boolean needsCover;

    /** Ca do người khác nhận hộ hoặc ca của đồng nghiệp — chỉ xem, không sửa. */
    private boolean readOnly;

    /** Ghi chú / lý do nghỉ, hiện trong popover chi tiết. */
    private String note;
}
