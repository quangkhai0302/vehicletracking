# Hiển thị vị trí xe ở mọi màn hình — 2026-09-14

Yêu cầu: giữ vị trí xe trên bản đồ khi chuyển giữa Theo dõi, Tuyến & trạm và Mô phỏng. Cập nhật nhỏ theo quy trình rút gọn, trong phạm vi hiển thị telemetry đã có.

## Khảo sát và thực hiện

- `MapComponent#useVehicleMarkers` trước đây chỉ bật `visible` ở workspace tracking/simulation; nay luôn bật cho các workspace. Điều kiện workspace ở thanh `live-follow` cũng được bỏ để giữ thao tác chọn/bỏ chọn và theo xe ở mọi màn hình.
- `useVehicleMarkers` tiếp tục lấy toàn bộ `snapshot.positions`, cập nhật marker theo vehicleId, giữ nhãn GPS/GIẢ LẬP và trạng thái vị trí cũ/mất tín hiệu. Không tạo vị trí cho xe chưa có telemetry.
- `OperationsSnapshotService#snapshot` đã trả vị trí cuối của các xe; không cần đổi API hoặc schema. Camera vẫn chỉ theo xe khi người dùng bật theo xe; kéo bản đồ hủy theo xe như trước (`MapComponent#onDragStart`).

## Kiểm tra

- Node 24.16.0: `npm run lint`, `./node_modules/.bin/tsc --noEmit`, `npm run build` — exit 0, build 1899 modules.
- Kiểm tra source: marker và thanh chọn xe không còn điều kiện workspace; subscription telemetry và cleanup marker được giữ nguyên.
- Chưa chạy kiểm thử trình duyệt cho thay đổi này. Không thay đổi dữ liệu development.

## Cập nhật icon xe

- Theo yêu cầu đổi biểu tượng: `useVehicleMarkers#vehicleGlyph` thay ký tự tam giác bằng SVG ô tô nhìn từ trên xuống, đầu xe hướng bắc khi heading=0. SVG xoay theo heading; marker 44×44, anchor ở tâm 22×22.
- `simulator.css`: nền sáng/viền trắng, xe xanh, xe có vị trí cũ hoặc đã dừng màu xám; xe được chọn có vòng xanh, hỗ trợ viền focus bàn phím. Tooltip và hành vi chọn/theo xe giữ nguyên.
- Lint, TypeScript và production build trên Node 24.16.0 đều exit 0. Chưa kiểm tra hình thức bằng trình duyệt.
