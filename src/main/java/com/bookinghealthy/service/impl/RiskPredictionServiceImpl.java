package com.bookinghealthy.service.impl;

import com.bookinghealthy.config.RiskPredictProperties;
import com.bookinghealthy.dto.risk.RiskPredictRequest;
import com.bookinghealthy.dto.risk.RiskPredictResponse;
import com.bookinghealthy.service.RiskPredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RiskPredictionServiceImpl implements RiskPredictionService {

    private static final String HEADER_SECRET = "X-ML-Secret";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RiskPredictProperties properties;

    /**
     * {@inheritDoc}
     *
     * <p><b>Không {@code @Transactional}</b> — xem javadoc của interface. Người gọi phải lưu
     * bản ghi SAU khi hàm này trả về, dựa vào transaction riêng của repository.
     */
    @Override
    public RiskPredictResponse predict(RiskPredictRequest request) {
        if (whyCannotPredict() != null) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_SECRET, properties.getSecret());

            return restTemplate.postForObject(
                    properties.getUrl() + "/predict",
                    new HttpEntity<>(request, headers),
                    RiskPredictResponse.class);
        } catch (Exception e) {
            // Cùng khuôn AiService.postOnce: nuốt lỗi vào log kèm tiền tố tra cứu được,
            // trả null, và để controller quyết định nói gì với bác sĩ.
            System.err.println("[RiskPredict] Gọi sidecar thất bại: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    @Override
    public String whyCannotPredict() {
        if (!properties.isEnabled()) {
            return "Chức năng dự đoán nguy cơ đang được tắt trong cấu hình hệ thống.";
        }
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            return "Hệ thống chưa cấu hình khoá bí mật cho dịch vụ dự đoán. Vui lòng báo quản trị viên.";
        }
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            return "Hệ thống chưa cấu hình địa chỉ dịch vụ dự đoán. Vui lòng báo quản trị viên.";
        }
        return null;
    }

    @Override
    public Double toMgPerDl(Double value, String unit) {
        if (value == null) {
            return null;
        }
        if (UNIT_MMOL_L.equalsIgnoreCase(unit)) {
            return value * MMOL_L_TO_MG_DL;
        }
        return value;
    }
}
