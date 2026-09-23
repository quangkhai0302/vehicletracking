# Review 026 — Dashboard quản lý vận hành

Ngày review: **2026-09-21**.

## Kết quả

- Aggregate endpoint tách khỏi telemetry snapshot, nên dashboard không cần tải toàn bộ vị trí GPS để lấy KPI.
- Count xe lệch tuyến lọc state active và chuyến `IN_PROGRESS`, tránh đếm notification lịch sử hoặc trip đã kết thúc.
- Count unread tách khỏi danh sách top 50; danh sách hiển thị giới hạn 5 để payload ổn định.
- Frontend có polling, loading/error/empty state và deeplink tới alert center/bản đồ.

## Giới hạn

- Chưa có aggregate theo ngày, tỷ lệ đúng giờ, quãng đường hoặc quá tốc độ; các mục này cần feature báo cáo và event data chuẩn hóa.
- Non-Docker backend suite (254 tests) và frontend production build đã đạt trên JDK 26/Node 24; 6 integration test còn cần Docker daemon.
- Review là self-review; các survey subagent đã cung cấp evidence read-only cho backend/frontend.

## Kết luận

Feature đáp ứng dashboard quản lý MVP theo các KPI người dùng yêu cầu và không thay đổi schema dữ liệu.
