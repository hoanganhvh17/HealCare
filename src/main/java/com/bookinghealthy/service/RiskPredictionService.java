package com.bookinghealthy.service;

import com.bookinghealthy.dto.risk.RiskPredictRequest;
import com.bookinghealthy.dto.risk.RiskPredictResponse;

/**
 * Gọi sidecar dự đoán nguy cơ bệnh (thư mục {@code ml-service/}).
 *
 * <p>Theo khuôn interface + {@code impl} của cả dự án.
 */
public interface RiskPredictionService {

    /** Đơn vị cholesterol/HDL bác sĩ chọn trên form. */
    String UNIT_MG_DL = "mg/dL";
    String UNIT_MMOL_L = "mmol/L";

    /**
     * Hệ số quy đổi cholesterol/HDL: mmol/L × 38.67 = mg/dL.
     *
     * <p><b>Đây là một bẫy an toàn, không phải chuyện thẩm mỹ.</b> Model học trên mg/dL
     * (cholesterol toàn phần trung vị 182), còn phòng khám Việt Nam thường ghi mmol/L. Bác sĩ
     * gõ 5.2 thay vì 200 sẽ nhận một xác suất trông bình thường và hoàn toàn sai — không có
     * lỗi nào được ném ra.
     */
    double MMOL_L_TO_MG_DL = 38.67;

    /** Ngưỡng tuổi thấp nhất model có cơ sở: cohort huấn luyện đã lọc từ 20 tuổi. */
    int MIN_AGE = 20;

    /**
     * Trả {@code null} khi tính năng bị tắt hoặc sidecar không trả lời được — người gọi tự
     * phát câu tiếng Việt cho bác sĩ, đúng khuôn {@code AiService.postOnce}.
     *
     * <p><b>Cài đặt TUYỆT ĐỐI không được {@code @Transactional}.</b> Có lời gọi mạng giữa
     * hàm; một transaction ở đây giam một connection HikariCP (pool 10) suốt thời gian chờ.
     */
    RiskPredictResponse predict(RiskPredictRequest request);

    /**
     * {@code null} = còn chạy được, ngược lại là câu tiếng Việt giải thích vì sao không.
     * Cùng khuôn {@code whyCannot…()} đã dùng khắp dự án: controller chặn thật bằng hàm này,
     * template cũng in đúng câu này ra, nên giao diện và máy chủ không bao giờ nói khác nhau.
     */
    String whyCannotPredict();

    /** Quy đổi cholesterol/HDL về mg/dL theo đơn vị bác sĩ chọn. */
    Double toMgPerDl(Double value, String unit);
}
