# Evidence: Bằng chứng Kiểm thử Tính năng Theo dõi Đội xe & Nâng cấp Form Trạm (Feature 002)

## 1. Trạng thái Kiểm tra
- Branch: `master`
- Môi trường: Node.js `v24.16.0` (thông qua `.nvmrc` / nvm), TypeScript `7.0.2`, Leaflet `1.9.4`, Vite `8.2.2`.
- Dữ liệu vận hành: **100% dữ liệu thật từ Backend API `/api/v1/stations`**. Đã loại bỏ hoàn toàn mock data / telemetry simulator theo yêu cầu người dùng.

## 2. Danh sách File đã Tạo / Cập nhật
- `[NEW]` [types/vehicle.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types/vehicle.ts): Định nghĩa kiểu dữ liệu `Vehicle`, `VehicleStatus`, `SimulatorConfig`, `VehicleTimelineStop`.
- `[NEW]` [components/VehicleDrawer.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/VehicleDrawer.tsx): Drawer chi tiết phương tiện khi nhận telemetry xe thật (vận tốc, trạm kế tiếp, ETA, tiến độ hành trình, timeline và Follow Vehicle).
- `[MODIFY]` [components/TrackingPanel.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/TrackingPanel.tsx): Bảng điều khiển đội xe trực tiếp. Khi chưa có xe thật, hiển thị Empty State chuyên nghiệp thông báo "Chưa có dữ liệu telemetry", sẵn sàng nhận tín hiệu GPS; khi có xe thật, hiển thị danh sách thẻ xe, bộ lọc trạng thái và tìm kiếm.
- `[MODIFY]` [components/StationDrawer.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.tsx): Nâng cấp form trạm với thanh trượt slider bán kính (10m - 500m), hàng nút preset bán kính (30m, 50m, 80m, 100m, 200m) và cơ chế cảnh báo bảo vệ dữ liệu dở dang (dirty state protection).
- `[MODIFY]` [components/StationPanel.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationPanel.tsx): Sử dụng danh sách trạm thật từ backend, hỗ trợ lọc trạng thái thực tế (`Tất cả` / `Đang hoạt động`), sắp xếp theo tên hoặc bán kính, tìm kiếm đa trường và hiển thị rõ ràng nút Sửa/Xóa.
- `[MODIFY]` [components/MapComponent.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx): Đã loại bỏ hoàn toàn mock data / simulator. Bản đồ hiển thị chính xác các trạm thật từ database PostgreSQL backend; banner chọn vị trí có nút "Lấy tâm bản đồ".
- `[MODIFY]` [App.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/App.tsx): Cập nhật header status hiển thị trạng thái "Chờ nguồn telemetry" ở chế độ theo dõi xe và "Chế độ quản lý trạm" ở chế độ quản lý trạm.
- `[MODIFY]` [index.css](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/index.css): Cung cấp toàn bộ styling theo chuẩn Dark operations shell tại `docs/design.md`.

## 3. Mapping Acceptance Criteria

| Tiêu chí Nghiệm thu (AC) | Minh chứng Mã nguồn | Kết quả |
|---|---|---|
| **AC-01**: Bảng điều hành xe realtime & empty state khi chưa có telemetry | [TrackingPanel.tsx:64-105](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/TrackingPanel.tsx#L64-L105) | Đạt |
| **AC-02**: Bản đồ hiển thị dữ liệu trạm thật từ Backend API | [MapComponent.tsx:112-132, 235-275](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx#L112-L132) | Đạt |
| **AC-03**: Không nạp mock data hoặc giả mạo luồng xe ảo | [MapComponent.tsx:98-105](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx#L98-L105) | Đạt |
| **AC-04**: VehicleDrawer sẵn sàng hiển thị thông số khi có xe kết nối | [VehicleDrawer.tsx:27-210](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/VehicleDrawer.tsx#L27-L210) | Đạt |
| **AC-05**: Chọn tọa độ trạm có nút "Lấy tâm bản đồ" | [MapComponent.tsx:370-385, 435-460](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx#L370-L385) | Đạt |
| **AC-06**: Slider bán kính + preset 30m-200m phản hồi tức thì lên geofence | [StationDrawer.tsx:265-305](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.tsx#L265-L305), [MapComponent.tsx:280-310](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx#L280-L310) | Đạt |
| **AC-07**: Bảo vệ form dở dang (Dirty state confirmation) | [StationDrawer.tsx:39-65, 102-123](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.tsx#L39-L65) | Đạt |
| **AC-08**: Hộp thoại ngừng sử dụng trạm chi tiết, an toàn | [MapComponent.tsx:470-510](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx#L470-L510) | Đạt |
| **AC-09**: TypeScript, Lint và Production Build đạt 100% | Lệnh kiểm tra chạy bên dưới | Đạt |

## 4. Kết quả Lệnh Kiểm tra Tự động

### 4.1 Linter (oxlint)
```bash
npm run lint
```
**Kết quả**:
```text
> vehicletracking-frontend@0.0.0 lint
> oxlint

Found 0 warnings and 0 errors.
Finished in 40ms on 20 files with 104 rules using 12 threads.
```
Exit code: `0`.

### 4.2 TypeScript Type Checking
```bash
./node_modules/.bin/tsc --noEmit
```
**Kết quả**:
Không có bất kỳ lỗi biên dịch nào. Exit code: `0`.

### 4.3 Production Bundle Build
```bash
export NVM_DIR="$HOME/.nvm"; [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"; nvm use 24; npm run build
```
**Kết quả**:
```text
Now using node v24.16.0 (npm v11.13.0)

> vehicletracking-frontend@0.0.0 build
> vite build

vite v8.2.2 building client environment for production...
✓ 1857 modules transformed.
rendering chunks (1)...computing gzip size...
dist/index.html                   0.47 kB │ gzip:   0.28 kB
dist/assets/index-B_uUh4jG.css   50.60 kB │ gzip:  13.42 kB
dist/assets/index-BuU9kaoB.js   389.01 kB │ gzip: 117.68 kB

✓ built in 240ms
```
Exit code: `0`.
