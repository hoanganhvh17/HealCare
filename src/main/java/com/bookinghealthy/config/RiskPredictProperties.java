package com.bookinghealthy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình cho sidecar dự đoán nguy cơ bệnh (thư mục {@code ml-service/}).
 *
 * <p>Cùng khuôn {@link VnPayProperties} / {@link VietQrProperties}: getter/setter viết tay,
 * không Lombok — cả package {@code config/} đang như vậy.
 *
 * <p><b>Đừng gắn {@code @Value} lên setter tĩnh để nạp các giá trị này.</b> Đó là mìn thứ tự
 * khởi tạo: bất cứ ai đọc field tĩnh trước khi bean được dựng sẽ nhận {@code null}, và mã
 * nguồn không hề cho thấy điều đó.
 */
@Component
@ConfigurationProperties(prefix = "risk-predict")
public class RiskPredictProperties {

    /** Tắt cả tính năng mà không cần deploy lại, cùng khuôn {@code medical-doc.ai-enabled}. */
    private boolean enabled = true;

    /**
     * Địa chỉ sidecar. <b>Chỉ được trỏ tới 127.0.0.1.</b> Sidecar nạp model y tế và không có
     * tầng phân quyền nào của Spring che chắn — lộ nó ra nginx là mở cổng cho cả internet.
     */
    private String url = "http://127.0.0.1:8001";

    /**
     * Bí mật dùng chung, gửi ở header {@code X-ML-Secret}. Để rỗng thì sidecar từ chối mọi
     * request — cố ý, an toàn hơn mở toang. Cùng lập luận với {@code payment.webhook.secret}.
     */
    private String secret = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
