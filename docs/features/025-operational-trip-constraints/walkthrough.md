# Walkthrough 025 — Ràng buộc vận hành chuyến

## Backend

1. Khi tạo, sửa giờ hoặc sinh chuyến từ lịch, `TripService` kiểm tra các chuyến `SCHEDULED/IN_PROGRESS` của cùng xe có khoảng thời gian giao nhau. Một tài xế có thể được gán nhiều chuyến dù lịch dự kiến trùng nhau.
2. Khi bắt đầu chuyến hoặc bắt đầu giả lập, backend yêu cầu xe và tài xế còn active, đồng thời chỉ chặn nếu tài xế đang có chuyến khác `IN_PROGRESS`; giờ dự kiến không giới hạn thời điểm khởi hành.
3. Khi hoàn thành, hệ thống kiểm tra stop cuối của attempt hiện tại đã có visit; nếu thiếu trả `409` và không đổi trạng thái.
4. Khi hủy, API nhận lý do bắt buộc, lưu vào snapshot chuyến; reason của chuyến đã hủy không bị ghi đè khi gọi lại.

## Frontend

1. Trong chi tiết chuyến, nút **Khởi hành** chỉ bật khi đã gán tài xế active; thông báo prerequisite được hiển thị ngay dưới phần trạng thái.
2. Với chuyến đang chạy, nút **Hoàn thành** chỉ bật sau khi check-in trạm cuối; lỗi backend vẫn hiển thị nếu dữ liệu thay đổi đồng thời.
3. **Hủy chuyến** mở dialog có textarea lý do tối thiểu 3 ký tự, gửi `POST /api/v1/trips/{id}/cancel` với JSON `{ reason }` và giữ dialog mở khi request lỗi.
4. Chi tiết chuyến đã hủy hiển thị reason nếu dữ liệu có sẵn.

## Cấu hình và chạy kiểm tra

- Từ 2026-09-24 không còn `TRIP_EARLY_START_WINDOW_SECONDS` hoặc `TRIP_LATE_START_WINDOW_SECONDS`; thao tác khởi hành chuyến và bắt đầu mô phỏng không bị giới hạn bởi giờ dự kiến.
- Chạy `./mvnw test` trong `vehicletracking-backend` bằng JDK 26 trở lên.
- Chạy `npm run lint`, `./node_modules/.bin/tsc --noEmit` và `npm run build` trong `vehicletracking-frontend` bằng Node 22.12+.

## Giới hạn MVP

- Chưa có override hoàn thành dành cho quản trị viên.
- Chưa lưu actor/audit trail cho lý do hủy vì repository chưa có authentication/RBAC.
- UI không tự dự đoán toàn bộ xung đột tài nguyên; backend là nguồn quyết định cuối cùng và trả lỗi nghiệp vụ.
