package com.bookinghealthy.dto.risk;

/**
 * Thân request gửi sang sidecar {@code POST /predict}.
 *
 * <p>Chỉ mang <b>chỉ số thô</b> bác sĩ gõ. Ba phép dẫn xuất mà model cần
 * ({@code BMI}, {@code NON_HDL}, {@code LOG_HSCRP}) do sidecar tự tính, vì chúng phải khớp
 * đúng cách dataset được dựng lúc huấn luyện — để phép dẫn xuất nằm cạnh danh sách feature là
 * cách duy nhất giữ cho nó không trôi đi. Cùng lập luận khiến dự án đặt phép ánh xạ
 * buổi → khung giờ ở máy chủ chứ không ở trình duyệt.
 *
 * <p><b>Thiếu chỉ số thì để {@code null}, TUYỆT ĐỐI không điền 0.</b> Với các chỉ số này 0 là
 * một giá trị thật và cực đoan. Pipeline có sẵn imputer nên {@code null} được điền bằng trung
 * vị tập huấn luyện, và model còn được báo là chỗ đó vốn bị thiếu.
 *
 * <p>Đơn vị: cân nặng kg, chiều cao/vòng eo cm, huyết áp mmHg, HbA1c %,
 * cholesterol và HDL <b>mg/dL</b>, hs-CRP mg/L.
 */
public class RiskPredictRequest {

    /** Bắt buộc, và phải ≥ 20 — cohort huấn luyện đã lọc từ 20 tuổi trở lên. */
    private Integer age;

    /** Bắt buộc. Mã NHANES: 1 = nam, 2 = nữ. Sidecar từ chối mọi giá trị khác. */
    private Integer sex;

    private Double heightCm;
    private Double weightKg;
    private Double bmi;
    private Double waistCm;
    private Double systolicBp;
    private Double diastolicBp;
    private Double pulse;
    private Double hba1c;
    private Double totalCholesterol;
    private Double hdl;
    private Double hsCrp;

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Double heightCm) {
        this.heightCm = heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getBmi() {
        return bmi;
    }

    public void setBmi(Double bmi) {
        this.bmi = bmi;
    }

    public Double getWaistCm() {
        return waistCm;
    }

    public void setWaistCm(Double waistCm) {
        this.waistCm = waistCm;
    }

    public Double getSystolicBp() {
        return systolicBp;
    }

    public void setSystolicBp(Double systolicBp) {
        this.systolicBp = systolicBp;
    }

    public Double getDiastolicBp() {
        return diastolicBp;
    }

    public void setDiastolicBp(Double diastolicBp) {
        this.diastolicBp = diastolicBp;
    }

    public Double getPulse() {
        return pulse;
    }

    public void setPulse(Double pulse) {
        this.pulse = pulse;
    }

    public Double getHba1c() {
        return hba1c;
    }

    public void setHba1c(Double hba1c) {
        this.hba1c = hba1c;
    }

    public Double getTotalCholesterol() {
        return totalCholesterol;
    }

    public void setTotalCholesterol(Double totalCholesterol) {
        this.totalCholesterol = totalCholesterol;
    }

    public Double getHdl() {
        return hdl;
    }

    public void setHdl(Double hdl) {
        this.hdl = hdl;
    }

    public Double getHsCrp() {
        return hsCrp;
    }

    public void setHsCrp(Double hsCrp) {
        this.hsCrp = hsCrp;
    }
}
