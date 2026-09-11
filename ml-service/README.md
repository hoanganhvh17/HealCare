# ml-service — sidecar dự đoán nguy cơ bệnh

Bốn mô hình NHANES (`DIABETES`, `HYPERTENSION`, `HEART_DISEASE`, `STROKE`) là pickle
scikit-learn nên **chỉ nạp được bằng Python**. Service này nạp chúng một lần lúc khởi động rồi
phục vụ qua HTTP cho Spring Boot gọi.

Nguồn mô hình: dự án nghiên cứu `chuan_doan_benh_diabect_hypertension_heartdisease_stroke`.

## Vì sao là sidecar chứ không chạy thẳng trong JVM

Xuất ONNX/PMML rồi chạy trong Java nghe gọn hơn, nhưng ba trong bốn mô hình là
`CalibratedClassifierCV(method="sigmoid", cv=5)` bọc quanh Random Forest 500 cây / XGBoost —
loại cấu trúc mà bộ xuất hay hỏng, và mọi sai lệch số học phải kiểm chứng lại trên 7.785 dòng
test. Sidecar giữ nguyên đường tính toán của sklearn nên **xác suất khớp tới ~1e-16** với kết
quả đã nghiệm thu. Xem `tests/test_parity.py`.

## Mô hình KHÔNG nằm trong git

Bốn tệp `best_model.joblib` cộng lại khoảng **100 MB** và `*.joblib` đã bị `.gitignore` ở cả
hai dự án. Chúng phải được **chép bằng tay** — cùng khuôn `src/main/resources/fonts/` và
`db/manual/*.sql`: artifact đặt bằng tay, và có thứ canh nó (ở đây là `GET /health`).

Service đọc hai biến môi trường:

| Biến | Ý nghĩa |
|---|---|
| `ML_MODEL_DIR` | thư mục chứa `<TARGET>/best_model.joblib` |
| `ML_INFO_DIR`  | thư mục chứa `<TARGET>/best_model_info.json` (mặc định = `ML_MODEL_DIR`) |
| `ML_SECRET`    | bí mật dùng chung, khớp `risk-predict.secret` bên Spring |
| `ML_MODEL_VERSION` | nhãn ghi vào mỗi kết quả, mặc định `nhanes-2026-08-18` |

Tách làm hai biến vì trong dự án ML mô hình nằm ở `models/nhanes/` còn metadata ở
`results/nhanes/`. Khi triển khai thì gộp cả hai tệp vào cùng một thư mục con và chỉ cần đặt
`ML_MODEL_DIR`.

## Chạy lúc phát triển

```powershell
$ML = "D:\Dowload\chuan_doan_benh_diabect_hypertension_heartdisease_stroke-main\chuan_doan_benh_diabect_hypertension_heartdisease_stroke-main"
$env:ML_MODEL_DIR = "$ML\models\nhanes"
$env:ML_INFO_DIR  = "$ML\results\nhanes"
$env:ML_SECRET    = "dev-local-secret"
& "$ML\.venv\Scripts\python.exe" -m uvicorn app:app --host 127.0.0.1 --port 8001
```

`--host 127.0.0.1` là bắt buộc, không phải mặc định cho tiện: service này nạp mô hình y tế và
**không có tầng phân quyền nào của Spring che chắn**.

Kiểm tra:

```powershell
curl http://127.0.0.1:8001/health
```

Phải thấy đủ bốn target trong `loaded` và `secretConfigured: true`.

## Chạy phép kiểm parity

Đây là phép kiểm quyết định — nó chứng minh cả chuỗi cùng lúc: phép dẫn xuất feature, mã hoá
`SEX`, ngưỡng đã khoá của từng bệnh, và lớp dương lấy từ `classes_`.

```powershell
$env:ML_PROJECT = $ML
$env:ML_SECRET  = "dev-local-secret"
& "$ML\.venv\Scripts\python.exe" tests\test_parity.py --rows 300
```

Kết quả phải là `DAT` cho cả bốn target với `lech_max` cỡ `1e-16`.

## Triển khai

Unit systemd riêng: `deploy/nnlhospital-ml.service`. Mô hình chép sang
`/var/lib/nnlhospital/ml-models/<TARGET>/` (cả `best_model.joblib` lẫn `best_model_info.json`).

Ba điều đã đo được và cần biết trước:

1. **RAM**: nạp đủ bốn mô hình tốn khoảng **500 MB RSS** (đo trên Windows dev). Máy A1.Flex
   12 GB còn dư nhiều, nhưng shape 1 GB thì không đủ chỗ.
2. **Phiên bản thư viện phải khớp chính xác** `requirements-lock.txt`. Pickle của scikit-learn
   nhạy cảm với phiên bản. Đừng dùng `requirements.txt` của dự án ML — nó chỉ ghi
   `scikit-learn>=1.4`.
3. **`xgboost` là bắt buộc** dù chỉ mô hình tăng huyết áp dùng tới: thiếu nó thì `joblib.load`
   ném `ModuleNotFoundError` ngay lúc unpickle.

Bản Python không cần trùng khít với máy đã train (3.14): pickle đi được qua các bản minor
miễn là phiên bản `scikit-learn` / `numpy` khớp. Sau khi dựng xong trên máy đích, **chạy lại
`tests/test_parity.py`** — đó mới là bằng chứng, không phải số hiệu phiên bản Python.

## Hợp đồng dữ liệu

`POST /predict` nhận **chỉ số thô** bác sĩ gõ; ba phép dẫn xuất mà mô hình cần do service này
tự tính, để chúng nằm cạnh danh sách feature và không trôi khỏi cách dataset được dựng:

- `BMI` = `weightKg / (heightCm/100)²` — chỉ tính khi không gửi sẵn `bmi`
- `NON_HDL` = `totalCholesterol − hdl`, **null nếu thiếu một trong hai**
- `LOG_HSCRP` = `log1p(max(hsCrp, 0))`

Đơn vị: cm, kg, mmHg, HbA1c `%`, cholesterol và HDL **mg/dL**, hs-CRP mg/L.

### Hai điều bắt buộc

- **`age` ≥ 20.** Cohort huấn luyện đã lọc từ 20 tuổi. Ngoài ra `AGE` là feature duy nhất
  **không có cột missing indicator**, nên bỏ trống nó sẽ bị điền 52 mà mô hình không hề biết
  đó là giá trị bịa.
- **`sex` là số 1 (nam) hoặc 2 (nữ).** `OneHotEncoder` được fit với
  `handle_unknown="ignore"`, nên một giá trị lạ thành vector toàn số 0 **mà không báo lỗi** —
  xác suất trả về vẫn trông hợp lý và vẫn sai. Đo thật: gửi chuỗi `"1"` thay vì số `1` làm xác
  suất tiểu đường đổi từ `0.267545` sang `0.282208`. Vì vậy mọi trường số đều bị từ chối nếu
  không đúng kiểu số, kể cả `true` (trong Python `True == 1`).

Các chỉ số còn lại bỏ trống được: pipeline có `SimpleImputer(median, add_indicator=True)` nên
tự điền trung vị và còn báo cho mô hình biết chỗ nào bị thiếu. **Gửi `null`, tuyệt đối không
gửi `0`** — với các chỉ số này 0 là một giá trị thật và cực đoan.
