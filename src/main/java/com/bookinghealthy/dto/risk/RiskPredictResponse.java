package com.bookinghealthy.dto.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Thân response của sidecar {@code POST /predict}.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} để sidecar thêm khoá mới mà không
 * làm chết phía Java — hai bên deploy độc lập nhau.
 *
 * <p>Dùng Lombok {@code @Getter} chứ <b>không dùng {@code record}</b>: Thymeleaf đọc thuộc
 * tính qua SpEL {@code ReflectivePropertyAccessor}, thứ tìm {@code getX()} chứ không tìm
 * accessor kiểu record {@code x()}. Ở đây viết getter tay cho đồng nhất với package
 * {@code dto/risk}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskPredictResponse {

    private String modelVersion;
    private Map<String, Double> featuresUsed;
    private List<String> missingFeatures;
    private List<String> unavailableTargets;
    private List<RiskTargetResult> results;

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Map<String, Double> getFeaturesUsed() {
        return featuresUsed;
    }

    public void setFeaturesUsed(Map<String, Double> featuresUsed) {
        this.featuresUsed = featuresUsed;
    }

    public List<String> getMissingFeatures() {
        return missingFeatures;
    }

    public void setMissingFeatures(List<String> missingFeatures) {
        this.missingFeatures = missingFeatures;
    }

    public List<String> getUnavailableTargets() {
        return unavailableTargets;
    }

    public void setUnavailableTargets(List<String> unavailableTargets) {
        this.unavailableTargets = unavailableTargets;
    }

    public List<RiskTargetResult> getResults() {
        return results;
    }

    public void setResults(List<RiskTargetResult> results) {
        this.results = results;
    }

    /** Tìm kết quả của một target, {@code null} nếu sidecar không nạp được model đó. */
    public RiskTargetResult find(String target) {
        if (results == null) {
            return null;
        }
        return results.stream()
                .filter(r -> target.equals(r.getTarget()))
                .findFirst()
                .orElse(null);
    }
}
