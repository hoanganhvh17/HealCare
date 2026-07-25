package com.bookinghealthy.model;

import java.time.LocalTime;

/**
 * Loại ca trong lịch làm việc của nhân viên.
 *
 * Phân biệt hai nghiệp vụ HOÀN TOÀN KHÁC NHAU:
 *
 * 1. CA KHÁM (isClinic() == true) — nằm trong GIỜ HÀNH CHÍNH, sinh khung giờ cho bệnh
 *    nhân đặt lịch. Chỉ có ca sáng và ca chiều.
 *
 * 2. PHIÊN TRỰC (isDuty() == true) — theo Thông tư 32/2023/TT-BYT, trực là hoạt động
 *    NGOÀI GIỜ HÀNH CHÍNH, ngày lễ, ngày nghỉ để bảo đảm khám chữa bệnh liên tục
 *    24/24 giờ. Trực KHÔNG sinh khung giờ đặt khám và luôn kéo qua nửa đêm.
 */
public enum ShiftType {

    CA_SANG("Ca sáng", LocalTime.of(7, 30), LocalTime.of(11, 30), false),
    CA_CHIEU("Ca chiều", LocalTime.of(13, 30), LocalTime.of(17, 30), false),

    // Trực phủ TRỌN phần ngoài giờ hành chính của một ngày thường: bắt đầu đúng lúc
    // hết giờ hành chính (17:30) và kết thúc khi bắt đầu giờ hành chính hôm sau (07:30).
    // Giờ hành chính ở đây là 07:30-17:30 nên phần ngoài giờ là 14 tiếng — đây là lý do
    // không dùng mốc 16:00 của "trực 16/24": mốc đó sẽ lấn vào giờ khám.
    TRUC_NGOAI_GIO("Trực ngoài giờ (14 giờ)", LocalTime.of(17, 30), LocalTime.of(7, 30), true),

    // Trực đêm 12/24 giờ.
    TRUC_12H_DEM("Trực đêm 12/24 giờ", LocalTime.of(19, 30), LocalTime.of(7, 30), true),

    // Trực 24/24 giờ — phủ trọn ngày nên chỉ đăng ký được cho ngày nghỉ / ngày lễ.
    TRUC_24H("Trực 24/24 giờ", LocalTime.of(7, 30), LocalTime.of(7, 30), true),

    // Hội chẩn / họp chuyên môn: có mặt tại viện nhưng không nhận bệnh nhân đặt lịch.
    HOI_CHAN("Hội chẩn / họp chuyên môn", LocalTime.of(16, 0), LocalTime.of(20, 0), false);

    private final String label;
    private final LocalTime defaultStart;
    private final LocalTime defaultEnd;
    private final boolean duty;

    ShiftType(String label, LocalTime defaultStart, LocalTime defaultEnd, boolean duty) {
        this.label = label;
        this.defaultStart = defaultStart;
        this.defaultEnd = defaultEnd;
        this.duty = duty;
    }

    public String getLabel() {
        return label;
    }

    public LocalTime getDefaultStart() {
        return defaultStart;
    }

    public LocalTime getDefaultEnd() {
        return defaultEnd;
    }

    /** Phiên trực: ngoài giờ hành chính, kéo qua nửa đêm, KHÔNG sinh slot đặt khám. */
    public boolean isDuty() {
        return duty;
    }

    /** Ca khám trong giờ hành chính: đây là loại DUY NHẤT sinh khung giờ cho bệnh nhân đặt. */
    public boolean isClinic() {
        return this == CA_SANG || this == CA_CHIEU;
    }

    /** Ca kết thúc vào ngày hôm sau (mọi phiên trực đều vậy). */
    public boolean isOvernight() {
        return duty;
    }

    /** Số giờ thực tế của ca, dùng để hiển thị và tính nghỉ bù. */
    public int getDurationHours() {
        int start = defaultStart.getHour() * 60 + defaultStart.getMinute();
        int end = defaultEnd.getHour() * 60 + defaultEnd.getMinute();
        int minutes = end - start;
        if (minutes <= 0) {
            minutes += 24 * 60; // ca qua đêm
        }
        return minutes / 60;
    }
}
