# 012 — Chạy lại mô phỏng trên cùng chuyến

Trạng thái: Verified (người dùng xác nhận giữ mã chuyến và tách lịch sử mỗi lần chạy; kiểm thử ngày 2026-09-15).

AC1: Reset không tạo route/trip mới. AC2: Giữ lịch sử telemetry/check-in từng lần. AC3: Lần mới PAUSED, tiến độ 0, chuyến SCHEDULED, check-in từ trạm đầu. AC4: ETA/realtime không dùng dữ liệu lần cũ. AC5: Không reset chuyến GPS hoặc xe có chuyến khác đang chạy. Không thay thuật toán định tuyến/check-in.
