#!/usr/bin/env bash
# =====================================================================
#  SAO LƯU NNL HOSPITAL — cơ sở dữ liệu + tệp người dùng tải lên
# =====================================================================
# CÀI ĐẶT (chạy bằng root):
#     sudo cp deploy/backup.sh /usr/local/bin/nnlhospital-backup
#     sudo chmod 700 /usr/local/bin/nnlhospital-backup
#     sudo crontab -e
#     # 03:15 hằng ngày, trước giờ cao điểm và sau khi MedicalNewsTask 18:00 đã xong
#     15 3 * * * /usr/local/bin/nnlhospital-backup >> /var/log/nnlhospital-backup.log 2>&1
#
# PHỤC HỒI:
#     systemctl stop nnlhospital
#     gunzip < /var/backups/nnlhospital/db-2026-08-17.sql.gz | mysql -u root -p bookinghealthy
#     tar xzf /var/backups/nnlhospital/files-2026-08-17.tar.gz -C /
#     systemctl start nnlhospital
#
# BẢN SAO LƯU CHƯA TỪNG PHỤC HỒI THỬ THÌ CHƯA PHẢI BẢN SAO LƯU.
# Làm thử một lần vào database tạm ngay sau khi cài, đừng đợi tới lúc cần thật.
#
# Muốn có bản sao ngoài máy (khuyến nghị — Oracle vẫn có thể thu hồi instance
# Always Free nếu để không quá lâu): cài rclone, cấu hình một remote trỏ vào
# Google Drive (15GB miễn phí) rồi bỏ dấu # ở khối RCLONE cuối tệp.
# =====================================================================

set -euo pipefail

ENV_FILE=/etc/nnlhospital/.env
DEST=/var/backups/nnlhospital
KEEP_DAYS=7
STAMP=$(date +%F)

# .env dùng khuôn "chỉ bình luận đứng riêng + giá trị có dấu cách thì đặt nháy kép"
# (bắt buộc vì systemd EnvironmentFile không hiểu bình luận cuối dòng), nên bash
# nạp được trực tiếp.
if [ ! -r "$ENV_FILE" ]; then
    echo "[backup] KHÔNG đọc được $ENV_FILE" >&2
    exit 1
fi
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

DB_NAME=$(printf '%s' "${DB_URL}" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')
if [ -z "$DB_NAME" ] || [ "$DB_NAME" = "$DB_URL" ]; then
    echo "[backup] Không bóc được tên database từ DB_URL=$DB_URL" >&2
    exit 1
fi

mkdir -p "$DEST"
chmod 700 "$DEST"

# Mật khẩu đi qua tệp tạm chứ KHÔNG qua tham số dòng lệnh: mọi user trên máy đều
# đọc được dòng lệnh của tiến trình khác bằng `ps aux`.
CNF=$(mktemp)
chmod 600 "$CNF"
trap 'rm -f "$CNF"' EXIT
cat > "$CNF" <<EOF
[client]
user=${DB_USERNAME}
password=${DB_PASSWORD}
EOF

# --single-transaction: ảnh chụp nhất quán cho InnoDB mà không khoá bảng, nên
#   lễ tân vẫn đặt lịch được trong lúc sao lưu chạy.
# --default-character-set=utf8mb4: THIẾU LÀ HỎNG THẦM. Tên bệnh nhân, chẩn đoán
#   và toàn bộ nội dung tiếng Việt bị mã hoá sai trong tệp dump, và chỉ lộ ra
#   đúng lúc phục hồi — tức lúc không còn bản nào khác để đối chiếu.
echo "[backup] Đang dump database '$DB_NAME'..."
mysqldump --defaults-extra-file="$CNF" \
          --single-transaction \
          --routines --triggers --events \
          --default-character-set=utf8mb4 \
          "$DB_NAME" | gzip -9 > "$DEST/db-$STAMP.sql.gz"

# UPLOAD_DIR: ảnh bác sĩ, ảnh tin tức tự thu thập, ảnh dịch vụ.
# PRIVATE_DIR: CV ứng viên — dữ liệu cá nhân, nên tệp nén phải để chmod 600.
echo "[backup] Đang đóng gói tệp tải lên..."
tar czf "$DEST/files-$STAMP.tar.gz" \
        --absolute-names \
        "${UPLOAD_DIR}" "${PRIVATE_DIR}"

chmod 600 "$DEST"/db-"$STAMP".sql.gz "$DEST"/files-"$STAMP".tar.gz

echo "[backup] Dọn bản cũ hơn $KEEP_DAYS ngày..."
find "$DEST" -maxdepth 1 -type f -name '*.gz' -mtime +"$KEEP_DAYS" -delete

# --- RCLONE (tuỳ chọn — bản sao ngoài máy) ---
# rclone copy "$DEST/db-$STAMP.sql.gz"    gdrive:nnlhospital/
# rclone copy "$DEST/files-$STAMP.tar.gz" gdrive:nnlhospital/

echo "[backup] Xong: $(du -sh "$DEST" | cut -f1) tại $DEST"
