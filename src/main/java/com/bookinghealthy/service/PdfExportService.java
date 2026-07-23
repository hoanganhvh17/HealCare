package com.bookinghealthy.service;

/**
 * Xuất phiếu PDF cho quầy lễ tân in ra giấy đưa bệnh nhân.
 * Font Unicode được nhúng nên tiếng Việt có dấu hiển thị đúng.
 */
public interface PdfExportService {

    /** Phiếu thu tiền khám của một lịch hẹn. */
    byte[] buildReceipt(Long bookingId);

    /** Đơn thuốc lấy từ bệnh án của lịch hẹn đã khám xong. */
    byte[] buildPrescription(Long bookingId);
}
