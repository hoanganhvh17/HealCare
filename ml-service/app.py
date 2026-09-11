"""Sidecar du doan nguy co benh cho NNL Hospital.

Bon model NHANES (DIABETES, HYPERTENSION, HEART_DISEASE, STROKE) la pickle
scikit-learn, chi nap duoc bang Python voi dung phien ban da khoa trong
requirements-lock.txt. Service nay nap chung MOT lan luc khoi dong roi phuc vu
qua HTTP cho Spring Boot goi.

CHI nghe 127.0.0.1. Tuyet doi khong them location nao vao nginx cho cong nay.

Bay quan trong nhat cua ca tep: OneHotEncoder cua SEX duoc fit voi
handle_unknown="ignore", nen mot gia tri SEX la se thanh vector toan so 0 ma
KHONG he bao loi -- xac suat tra ve van trong hop ly nhung sai. Do that:
gui chuoi "1" thay vi so 1 lam xac suat DIABETES doi tu 0.267545 sang 0.282208.
Vi vay moi truong so deu bi tu choi neu khong phai kieu so that su.
"""
from __future__ import annotations

import hmac
import json
import math
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
import sklearn
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse

# Dung thu tu nay -- chep tu src/nhanes_common.py FEATURE_COLUMNS cua du an ML.
FEATURE_COLUMNS = [
    "AGE", "SEX", "BMI", "WAIST", "SBP_MEAN", "DBP_MEAN", "PULSE_MEAN",
    "HBA1C", "TOTAL_CHOLESTEROL", "HDL", "NON_HDL", "LOG_HSCRP",
]
TARGETS = ["DIABETES", "HYPERTENSION", "HEART_DISEASE", "STROKE"]

# Cohort train da loc RIDAGEYR >= 20 nen model khong co co so nao duoi tuoi do.
MIN_AGE = 20
MAX_AGE = 120

MODEL_DIR = Path(os.getenv("ML_MODEL_DIR", "models"))
INFO_DIR = Path(os.getenv("ML_INFO_DIR", str(MODEL_DIR)))
MODEL_VERSION = os.getenv("ML_MODEL_VERSION", "nhanes-2026-08-18")
SECRET = os.getenv("ML_SECRET", "")

LOADED: dict[str, dict[str, Any]] = {}
LOAD_ERRORS: dict[str, str] = {}


def _positive_index(model: Any) -> int:
    """Lop duong lay tu classes_, khong gia dinh la cot 1.

    Chep tu src/audit_nhanes_test_predictions.py:282 cua du an ML.
    """
    classes = getattr(model, "classes_", None)
    if classes is None and hasattr(model, "named_steps"):
        classes = getattr(list(model.named_steps.values())[-1], "classes_", None)
    if classes is None:
        raise RuntimeError("Model khong expose classes_")
    classes_list = list(classes)
    if 1 not in classes_list:
        raise RuntimeError("Khong tim thay lop duong 1 trong classes_: " + str(classes_list))
    return classes_list.index(1)


def load_models() -> None:
    """Nap bon model mot lan. Thieu mot target thi ghi log va di tiep.

    Cung nguyen tac voi SchemaGuard va font PDF cua du an: mot artifact thieu
    phai lam giam chuc nang chu khong duoc giet tien trinh luc khoi dong.
    """
    for target in TARGETS:
        model_path = MODEL_DIR / target / "best_model.joblib"
        info_path = INFO_DIR / target / "best_model_info.json"
        try:
            if not model_path.exists():
                raise FileNotFoundError("Thieu " + str(model_path))
            if not info_path.exists():
                raise FileNotFoundError("Thieu " + str(info_path))
            info = json.loads(info_path.read_text(encoding="utf-8"))
            if info.get("feature_columns") and list(info["feature_columns"]) != FEATURE_COLUMNS:
                raise RuntimeError(
                    "feature_columns cua " + target + " lech voi FEATURE_COLUMNS cua sidecar"
                )
            model = joblib.load(model_path)
            LOADED[target] = {
                "model": model,
                "threshold": float(info["threshold"]),
                "model_name": info.get("best_model", "unknown"),
                "calibrated": bool(info.get("calibrated_sigmoid_cv5", False)),
                "positive_index": _positive_index(model),
            }
            print(
                "[ML] Da nap " + target + ": " + str(LOADED[target]["model_name"])
                + " threshold=" + str(LOADED[target]["threshold"])
                + " calibrated=" + str(LOADED[target]["calibrated"]),
                flush=True,
            )
        except Exception as exc:  # noqa: BLE001 - thieu 1 target khong duoc giet ca service
            LOAD_ERRORS[target] = exc.__class__.__name__ + ": " + str(exc)
            print("[ML] !! KHONG nap duoc " + target + ": " + LOAD_ERRORS[target], flush=True)

    if not LOADED:
        print("[ML] !! CANH BAO: khong nap duoc model nao. /predict se tra 503.", flush=True)


@asynccontextmanager
async def lifespan(_app: "FastAPI"):
    print("[ML] MODEL_DIR=" + str(MODEL_DIR.resolve()), flush=True)
    print("[ML] INFO_DIR =" + str(INFO_DIR.resolve()), flush=True)
    print("[ML] scikit-learn=" + sklearn.__version__ + " pandas=" + pd.__version__, flush=True)
    if not SECRET:
        print("[ML] !! ML_SECRET rong -- /predict se tu choi moi request.", flush=True)
    load_models()
    yield
    LOADED.clear()


app = FastAPI(
    title="NNL Hospital - Du doan nguy co benh",
    version="1.0.0",
    lifespan=lifespan,
)


def _check_secret(provided: str | None) -> None:
    """Bi mat dung chung, so bang compare_digest -- cung khuon webhook VietQR.

    De rong la dong han, an toan hon mo toang.
    """
    if not SECRET:
        raise HTTPException(status_code=401, detail="Sidecar chua cau hinh ML_SECRET.")
    if not provided or not hmac.compare_digest(provided, SECRET):
        raise HTTPException(status_code=401, detail="Bi mat khong hop le.")


def _number(body: dict, key: str, required: bool = False) -> float | None:
    """Lay mot so tu body.

    Tu choi chuoi va bool. bool la subclass cua int trong Python nen True == 1,
    de lot neu chi kiem isinstance(v, int).
    """
    if key not in body or body[key] is None:
        if required:
            raise HTTPException(status_code=400, detail="Thieu truong bat buoc: " + key)
        return None
    value = body[key]
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise HTTPException(
            status_code=400,
            detail="Truong " + key + " phai la so, nhan duoc "
                   + type(value).__name__ + ": " + repr(value),
        )
    if not math.isfinite(float(value)):
        raise HTTPException(status_code=400, detail="Truong " + key + " khong phai so huu han.")
    return float(value)


def build_features(body: dict) -> tuple[dict[str, float | None], list[str]]:
    """Doi chi so tho bac si go thanh 12 feature model can.

    Ba phep dan xuat phai khop DUNG src/build_nhanes_dataset.py:89-97:
      NON_HDL   = TOTAL_CHOLESTEROL - HDL   (null neu thieu mot trong hai)
      LOG_HSCRP = log1p(max(hsCrp, 0))
      BMI       = weightKg / (heightCm/100)^2
    """
    age = _number(body, "age", required=True)
    if age < MIN_AGE or age > MAX_AGE:
        raise HTTPException(
            status_code=400,
            detail="Tuoi phai trong khoang " + str(MIN_AGE) + "-" + str(MAX_AGE)
                   + ". Model duoc huan luyen tren cohort tu " + str(MIN_AGE) + " tuoi tro len.",
        )

    # SEX bat buoc va phai la ma so NHANES 1/2. Xem doc-string dau tep.
    sex_raw = body.get("sex")
    if isinstance(sex_raw, bool) or not isinstance(sex_raw, (int, float)):
        raise HTTPException(
            status_code=400,
            detail="Truong sex phai la so 1 (nam) hoac 2 (nu), nhan duoc "
                   + type(sex_raw).__name__ + ": " + repr(sex_raw),
        )
    if float(sex_raw) not in (1.0, 2.0):
        raise HTTPException(
            status_code=400,
            detail="Truong sex phai la 1 (nam) hoac 2 (nu), nhan duoc " + repr(sex_raw),
        )

    bmi = _number(body, "bmi")
    height_cm = _number(body, "heightCm")
    weight_kg = _number(body, "weightKg")
    if bmi is None and height_cm and weight_kg and height_cm > 0:
        metre = height_cm / 100.0
        bmi = weight_kg / (metre * metre)

    total_cholesterol = _number(body, "totalCholesterol")
    hdl = _number(body, "hdl")
    non_hdl = None
    if total_cholesterol is not None and hdl is not None:
        non_hdl = total_cholesterol - hdl

    hs_crp = _number(body, "hsCrp")
    log_hscrp = None if hs_crp is None else math.log1p(max(hs_crp, 0.0))

    features: dict[str, float | None] = {
        "AGE": age,
        "SEX": float(sex_raw),
        "BMI": bmi,
        "WAIST": _number(body, "waistCm"),
        "SBP_MEAN": _number(body, "systolicBp"),
        "DBP_MEAN": _number(body, "diastolicBp"),
        "PULSE_MEAN": _number(body, "pulse"),
        "HBA1C": _number(body, "hba1c"),
        "TOTAL_CHOLESTEROL": total_cholesterol,
        "HDL": hdl,
        "NON_HDL": non_hdl,
        "LOG_HSCRP": log_hscrp,
    }
    missing = [name for name, value in features.items() if value is None]
    return features, missing


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok" if LOADED else "degraded",
        "modelVersion": MODEL_VERSION,
        "loaded": sorted(LOADED.keys()),
        "unavailable": sorted(LOAD_ERRORS.keys()),
        "errors": LOAD_ERRORS,
        "sklearn": sklearn.__version__,
        "secretConfigured": bool(SECRET),
    }


@app.post("/predict")
def predict(body: dict, x_ml_secret: str | None = Header(default=None)) -> JSONResponse:
    _check_secret(x_ml_secret)
    if not LOADED:
        raise HTTPException(status_code=503, detail="Sidecar chua nap duoc model nao.")

    features, missing = build_features(body)

    # Pipeline doi DataFrame co ten cot -- mang NumPy tran se nem ValueError.
    # astype float64 de cot toan None khong thanh dtype object, va de SEX la 1.0/2.0
    # dung kieu ma OneHotEncoder da duoc fit.
    row = {name: (np.nan if value is None else value) for name, value in features.items()}
    frame = pd.DataFrame([row], columns=FEATURE_COLUMNS).astype("float64")

    results = []
    for target in TARGETS:
        entry = LOADED.get(target)
        if entry is None:
            continue
        probability = float(
            entry["model"].predict_proba(frame)[:, entry["positive_index"]][0]
        )
        results.append({
            "target": target,
            "probability": probability,
            "threshold": entry["threshold"],
            # >= la bao gom bang, dung nhu train_nhanes_models.py:277.
            # Tuyet doi khong dung model.predict() -- ham do dung nguong 0.5.
            "positive": bool(probability >= entry["threshold"]),
            "model": entry["model_name"],
            "calibrated": entry["calibrated"],
        })

    return JSONResponse({
        "modelVersion": MODEL_VERSION,
        "featuresUsed": {k: (None if v is None else v) for k, v in features.items()},
        "missingFeatures": missing,
        "unavailableTargets": sorted(LOAD_ERRORS.keys()),
        "results": results,
    })
