package com.bookinghealthy.dto.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Kết quả của MỘT bệnh trong bốn bệnh sidecar dự đoán.
 *
 * <p>{@code positive} do sidecar tính bằng {@code probability >= threshold} với ngưỡng riêng
 * đã khoá của từng target (0.41 / 0.34 / 0.63 / 0.07) — <b>không</b> phải 0.5. Phía Java
 * không được tự dựng lại phép so đó: ngưỡng là thuộc tính của model, và nhân bản nó ra hai
 * nơi là mời chúng lệch nhau.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskTargetResult {

    private String target;
    private Double probability;
    private Double threshold;
    private Boolean positive;
    private String model;
    private Boolean calibrated;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Double getProbability() {
        return probability;
    }

    public void setProbability(Double probability) {
        this.probability = probability;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Boolean getPositive() {
        return positive;
    }

    public void setPositive(Boolean positive) {
        this.positive = positive;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Boolean getCalibrated() {
        return calibrated;
    }

    public void setCalibrated(Boolean calibrated) {
        this.calibrated = calibrated;
    }

    /** Phần trăm để in ra màn hình, ví dụ 26.8. */
    public Double getProbabilityPercent() {
        return probability == null ? null : probability * 100.0;
    }
}
