package com.bookinghealthy.util;

import com.bookinghealthy.model.VitalSign;

import java.util.ArrayList;
import java.util.List;

/**
 * Gộp bộ chỉ số sinh tồn thành MỘT dòng tiếng Việt để in ra giấy hoặc đưa vào thư.
 *
 * Tách ra khỏi {@code PdfExportServiceImpl} khi thư "đã có hồ sơ bệnh án" cần đúng dòng đó:
 * đây là dữ liệu lâm sàng, đơn thuốc bệnh nhân cầm trên tay và thư trong hộp mail của họ
 * không được phép ghi huyết áp theo hai kiểu khác nhau.
 */
public final class VitalSignFormatter {

    private VitalSignFormatter() {
    }

    /**
     * @return "Mạch 80 l/p · Huyết áp 120/80 mmHg · Nhiệt độ 37 °C", hoặc {@code null} khi
     *         bác sĩ không đo chỉ số nào — chỗ gọi dựa vào null để ẩn hẳn mục này.
     */
    public static String describe(VitalSign vs) {
        if (vs == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (vs.getHeartRate() != null) parts.add("Mạch " + vs.getHeartRate() + " l/p");
        if (vs.getSystolicBp() != null && vs.getDiastolicBp() != null) {
            parts.add("Huyết áp " + trimDecimal(vs.getSystolicBp()) + "/" + trimDecimal(vs.getDiastolicBp()) + " mmHg");
        }
        if (vs.getTemperature() != null) parts.add("Nhiệt độ " + trimDecimal(vs.getTemperature()) + " °C");
        if (vs.getSpo2() != null) parts.add("SpO2 " + trimDecimal(vs.getSpo2()) + " %");
        if (vs.getHeight() != null) parts.add("Cao " + trimDecimal(vs.getHeight()) + " cm");
        if (vs.getWeight() != null) parts.add("Nặng " + trimDecimal(vs.getWeight()) + " kg");

        return parts.isEmpty() ? null : String.join("  ·  ", parts);
    }

    /** 37.0 → "37" — chỉ số đo bằng số tròn không nên hiện đuôi ".0" trên giấy tờ y tế. */
    private static String trimDecimal(Double value) {
        if (value == null) {
            return "-";
        }
        if (value == Math.floor(value)) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(value);
    }
}
