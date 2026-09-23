# Review 024 — Cảnh báo xe lệch tuyến

Ngày review: **2026-09-21**.

## Phạm vi đã rà soát

- Detector backend, state persistence và lock theo trip/attempt.
- Migration V16, cấu hình runtime và notification contract.
- Hook telemetry sau commit, snapshot/SSE notification hiện hữu.
- Trang `/alerts`, AlertStream trên operations và điều hướng `tripId` về bản đồ.
- Test state/detector, type check và lint.

## Kết quả

- Không phát hiện lỗi contract rõ ràng trong source đã rà soát.
- Reroute được đánh giá trước off-route trong callback sau commit để detector đọc geometry/revision mới nhất nếu reroute vừa được tạo.
- Detector có debounce theo số mẫu + thời gian, effective threshold theo accuracy, dedupe theo episode và re-arm khi xe quay lại corridor.
- Notification cũ vẫn dùng constructor tương thích; field mới nullable nên không phá dữ liệu hiện có.

## Giới hạn còn lại

- Reviewer subagent độc lập không hoàn tất do giới hạn usage của môi trường; review này là self-review có evidence từ source.
- Non-Docker Maven suite (254 tests) và frontend production build đã đạt trên JDK 26/Node 24; 6 integration test còn cần Docker daemon.
- Chưa chạy integration test PostgreSQL/Flyway vì Docker daemon không khả dụng trong môi trường này.

## Kết luận

Implementation đáp ứng phạm vi MVP; non-Docker tests và frontend build đã đạt. Trước khi phát hành production cần chạy Flyway/integration test trên môi trường có Docker.
