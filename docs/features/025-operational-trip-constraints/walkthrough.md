# Walkthrough 025 — Ràng buộc vận hành chuyến

## Backend

1. Khi tạo, sửa giờ, gán tài xế hoặc sinh chuyến từ lịch, `TripService` khóa tài nguyên và kiểm tra các chuyến `SCHEDULED/IN_PROGRESS` có khoảng thời gian giao nhau.
2. Khi bắt đầu, backend yêu cầu xe và tài xế còn active, tài xế không chạy chuyến khác và thời điểm hiện tại nằm trong cửa sổ cấu hình.
3. Khi hoàn thành, hệ thống kiểm tra stop cuối của attempt hiện tại đã có visit; nếu thiếu trả `409` và không đổi trạng thái.
4. Khi hủy, API nhận lý do bắt buộc, lưu vào snapshot chuyến; reason của chuyến đã hủy không bị ghi đè khi gọi lại.

## Frontend

1. Trong chi tiết chuyến, nút **Khởi hành** chỉ bật khi đã gán tài xế active; thông báo prerequisite được hiển thị ngay dưới phần trạng thái.
2. Với chuyến đang chạy, nút **Hoàn thành** chỉ bật sau khi check-in trạm cuối; lỗi backend vẫn hiển thị nếu dữ liệu thay đổi đồng thời.
3. **Hủy chuyến** mở dialog có textarea lý do tối thiểu 3 ký tự, gửi `POST /api/v1/trips/{id}/cancel` với JSON `{ reason }` và giữ dialog mở khi request lỗi.
4. Chi tiết chuyến đã hủy hiển thị reason nếu dữ liệu có sẵn.

## Cấu hình và chạy kiểm tra

- Điều chỉnh `TRIP_EARLY_START_WINDOW_SECONDS` và `TRIP_LATE_START_WINDOW_SECONDS` trong môi trường backend khi cần.
- Chạy `./mvnw test` trong `vehicletracking-backend` bằng JDK 26 trở lên.
- Chạy `npm run lint`, `./node_modules/.bin/tsc --noEmit` và `npm run build` trong `vehicletracking-frontend` bằng Node 22.12+.

## Giới hạn MVP

- Chưa có override hoàn thành dành cho quản trị viên.
- Chưa lưu actor/audit trail cho lý do hủy vì repository chưa có authentication/RBAC.
- UI không tự dự đoán toàn bộ conflict/time-window; backend là nguồn quyết định cuối cùng và trả lỗi nghiệp vụ.
