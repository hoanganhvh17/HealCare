"""Phep kiem quyet dinh: chay lai ket qua da dong bang qua chinh API HTTP.

data/processed/nhanes_features.csv cua du an ML giu ca gia tri THO (HSCRP,
TOTAL_CHOLESTEROL, HDL, BMI) lan gia tri dan xuat, nen dung lai duoc dung
payload tho cho tung SEQN cua tap test 2021-2023. Ban chung qua POST /predict
roi so voi cot PROBABILITY trong results/nhanes/<TARGET>/test_predictions.csv.

Khop toi ~1e-9 la da chung minh CA CHUOI cung luc:
  - phep dan xuat BMI / NON_HDL / LOG_HSCRP
  - ma hoa SEX (1.0 / 2.0)
  - nguong da khoa cua tung target
  - lop duong lay tu classes_
Lech mot cho nao la phep kiem nay bat duoc ngay.

Chay:
    set ML_PROJECT=D:\\...\\chuan_doan_benh_...
    set ML_SECRET=dev-local-secret
    python tests/test_parity.py --rows 200
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

import pandas as pd

TARGETS = ["DIABETES", "HYPERTENSION", "HEART_DISEASE", "STROKE"]
TOLERANCE = 1e-9
TEST_CYCLE = "2021_2023"


def _cell(value):
    """NaN cua pandas -> None (JSON null). Tuyet doi khong doi thanh 0."""
    if value is None:
        return None
    try:
        if isinstance(value, float) and math.isnan(value):
            return None
    except TypeError:
        return None
    return float(value)


def build_payload(row: pd.Series) -> dict:
    """Dung lai chi so THO dung nhu bac si se go tren form."""
    return {
        "age": _cell(row.get("AGE")),
        "sex": None if _cell(row.get("SEX")) is None else int(row["SEX"]),
        "bmi": _cell(row.get("BMI")),
        "waistCm": _cell(row.get("WAIST")),
        "systolicBp": _cell(row.get("SBP_MEAN")),
        "diastolicBp": _cell(row.get("DBP_MEAN")),
        "pulse": _cell(row.get("PULSE_MEAN")),
        "hba1c": _cell(row.get("HBA1C")),
        "totalCholesterol": _cell(row.get("TOTAL_CHOLESTEROL")),
        "hdl": _cell(row.get("HDL")),
        "hsCrp": _cell(row.get("HSCRP")),
    }


def call_predict(url: str, secret: str, payload: dict) -> dict:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "X-ML-Secret": secret},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=200)
    parser.add_argument("--base-url", default="http://127.0.0.1:8001")
    args = parser.parse_args()

    ml_project = os.getenv("ML_PROJECT")
    if not ml_project:
        print("Thieu bien moi truong ML_PROJECT (thu muc goc du an ML).")
        return 2
    project = Path(ml_project)
    secret = os.getenv("ML_SECRET", "")

    frame = pd.read_csv(project / "data" / "processed" / "nhanes_features.csv")
    test_frame = frame[frame["CYCLE"] == TEST_CYCLE].copy()

    frozen: dict[str, pd.DataFrame] = {}
    for target in TARGETS:
        path = project / "results" / "nhanes" / target / "test_predictions.csv"
        if path.exists():
            frozen[target] = pd.read_csv(path).set_index("SEQN")

    sample = test_frame.head(args.rows)
    print("Doi chieu " + str(len(sample)) + " dong tap test " + TEST_CYCLE)

    worst: dict[str, float] = {t: 0.0 for t in TARGETS}
    compared: dict[str, int] = {t: 0 for t in TARGETS}
    label_mismatch: dict[str, int] = {t: 0 for t in TARGETS}
    skipped = 0

    for _, row in sample.iterrows():
        payload = build_payload(row)
        # Cohort train loc tu 20 tuoi; sidecar tu choi duoi nguong do.
        if payload["age"] is None or payload["age"] < 20 or payload["sex"] not in (1, 2):
            skipped += 1
            continue
        try:
            answer = call_predict(args.base_url + "/predict", secret, payload)
        except urllib.error.HTTPError as exc:
            print("HTTP " + str(exc.code) + " cho SEQN " + str(row["SEQN"])
                  + ": " + exc.read().decode("utf-8", "replace")[:200])
            return 1

        seqn = row["SEQN"]
        for result in answer["results"]:
            target = result["target"]
            table = frozen.get(target)
            if table is None or seqn not in table.index:
                continue
            expected = float(table.loc[seqn, "PROBABILITY"])
            got = float(result["probability"])
            worst[target] = max(worst[target], abs(expected - got))
            compared[target] += 1
            if bool(got >= result["threshold"]) != bool(int(table.loc[seqn, "Y_PRED"])):
                label_mismatch[target] += 1

    print("Bo qua (duoi 20 tuoi hoac thieu gioi tinh): " + str(skipped))
    failed = False
    for target in TARGETS:
        if compared[target] == 0:
            print("  " + target.ljust(14) + " KHONG co dong nao doi chieu duoc")
            continue
        status = "DAT" if worst[target] <= TOLERANCE and label_mismatch[target] == 0 else "HONG"
        if status == "HONG":
            failed = True
        print("  " + target.ljust(14) + " n=" + str(compared[target]).rjust(4)
              + "  lech_max=" + format(worst[target], ".3e")
              + "  nhan_lech=" + str(label_mismatch[target])
              + "  " + status)

    if failed:
        print("\nHONG: xac suat qua API khong khop ket qua da dong bang.")
        return 1
    print("\nDAT: toan bo xac suat khop ket qua da dong bang trong sai so " + str(TOLERANCE))
    return 0


if __name__ == "__main__":
    sys.exit(main())
