# 004 — Bố cục không gian điều hành

Ngày: 2026-09-13. Phạm vi được người dùng yêu cầu trực tiếp: khảo sát tiến độ, lập kế hoạch chức năng tiếp theo và cải thiện layout hiện có.

> **Revision Map-First đã duyệt/triển khai:** acceptance criteria hiện hành nằm ở [map-first-implementation.md](map-first-implementation.md). Các mục về sidebar, nền sáng và hai cột bên dưới lưu lại đặc tả của phiên bản dashboard trước đó, đã được thay thế theo yêu cầu người dùng.

## Cơ sở và giới hạn

- `docs/workflow.md` và hồ sơ 001–003 không có trong checkout này; đã tìm bằng `rg --files --hidden`. Không tái dựng chúng như tài liệu gốc. Quy ước áp dụng: `AGENTS.md`, mục 3–10. ID 004 tiếp nối 003 được ghi trong `PROJECT_HANDOFF.md`, mục 12.
- Layout trước thay đổi: `src/App.tsx` / `App`, `src/index.css` / `.header-glass`, `.station-panel`, `.station-drawer` và `src/components/route/route.css` / `.route-panel`, `.route-drawer`: header và các panel định vị tuyệt đối trên bản đồ, khoảng cách top và chiều rộng khác nhau. Các đường dẫn `src/` ở đây thuộc `vehicletracking-frontend/`.
- Nghiệp vụ giữ theo các service hiện tại: `src/services/stations.ts`, `src/services/routes.ts`. Tracking chưa có nguồn dữ liệu: `src/components/MapComponent.tsx` / state `vehicles`.

## Acceptance criteria

1. Ba workspace có điều hướng nhất quán, tiêu đề và mô tả tiếng Việt; trạng thái tracking nói đúng rằng chưa kết nối dữ liệu.
2. Desktop: danh sách hoặc chi tiết/biểu mẫu nằm trong cột thao tác riêng, không đè lên vùng bản đồ. Đóng chi tiết trở lại danh sách, giữ tìm kiếm.
3. Mobile: chuyển Danh sách/Bản đồ; chọn tọa độ mở bản đồ, chọn xong quay về biểu mẫu và giữ dữ liệu đang nhập. Control bản đồ và attribution vẫn truy cập được.
4. Giữ CRUD trạm, tìm kiếm, sắp xếp, xác nhận bỏ thay đổi/ngừng sử dụng; giữ tạo tuyến, thứ tự điểm dừng, thời gian dừng, detail và vẽ polyline.
5. Panel có cuộn riêng, chữ dễ đọc, focus bằng bàn phím rõ, thao tác sửa/xóa truy cập được trên thiết bị cảm ứng. Kiểm tra viewport 1440, 1024, 768, 390 và 320 px.
6. Leaflet cập nhật kích thước khi vùng hiển thị thay đổi; observer/listener phải cleanup. Không thêm telemetry, traffic, simulator hoặc số liệu giả vào runtime.

## Kiểm tra

Chạy lint, TypeScript, build frontend; browser smoke với API fixture được ghi rõ và request bị chặn không chạm dữ liệu thật. Khảo sát backend bằng bộ test hiện có, báo riêng lỗi môi trường Docker. Kết quả thực tế ghi trong `verification.md` khi hoàn tất.
