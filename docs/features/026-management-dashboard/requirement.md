# Requirement 026 — Dashboard quản lý vận hành

Trạng thái: **Implemented** — kiểm tra non-Docker đạt ngày 2026-09-21; PostgreSQL integration cần Docker.

## Bối cảnh

Dashboard hiện có các số liệu cơ bản từ `/vehicles`, `/drivers` và `/trips`, nhưng chưa có KPI chuyến trễ, xe lệch tuyến và cảnh báo cần xử lý. Người điều phối phải mở nhiều màn hình để biết tình hình vận hành.

## Phạm vi MVP

- Hiển thị số phương tiện và tài xế đang hoạt động.
- Hiển thị chuyến đang chạy, chờ khởi hành, đã hoàn thành và đã hủy.
- Hiển thị số chuyến đang chạy quá `plannedEndAt`.
- Hiển thị số xe đang có trạng thái lệch tuyến active.
- Hiển thị tổng cảnh báo chưa đọc và danh sách tối đa 5 cảnh báo cần xử lý.
- Tự động làm mới dashboard mỗi 15 giây, có loading/error/empty state và liên kết tới nghiệp vụ chi tiết.

## Ngoài phạm vi

- Báo cáo theo khoảng thời gian, tỷ lệ đúng giờ, tổng quãng đường và quá tốc độ.
- Suy diễn chuyến hoàn thành trễ khi chưa có policy actual arrival chuẩn hóa.
- Phân quyền dashboard theo vai trò.

## Acceptance criteria

- AC-01: `GET /api/v1/dashboard/summary` trả các KPI xe, tài xế, chuyến và cảnh báo trong một response nhất quán với `serverTime`.
- AC-02: `overdueTrips` đếm các chuyến `IN_PROGRESS` có thời điểm kết thúc kế hoạch trước `serverTime`.
- AC-03: `offRouteVehicleCount` chỉ đếm state lệch tuyến active gắn với chuyến `IN_PROGRESS`; chuyến đã kết thúc không còn tính.
- AC-04: `unreadAlertCount` đếm toàn bộ cảnh báo chưa đọc; `pendingAlerts` trả tối đa 5 cảnh báo mới nhất để hiển thị hành động.
- AC-05: Dashboard có KPI cards, danh sách chuyến gần đây, danh sách cảnh báo, link tới `/alerts`, `/operations?tripId=...` và các module quản lý.
- AC-06: Dashboard refresh định kỳ, giữ dữ liệu cũ khi refresh lỗi, hiển thị lỗi và cho phép thử lại.
- AC-07: Backend service/controller test, frontend lint/typecheck và tài liệu evidence được cập nhật.
