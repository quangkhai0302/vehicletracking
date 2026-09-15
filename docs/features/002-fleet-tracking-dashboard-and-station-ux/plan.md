# Plan: Kế hoạch Triển khai Nâng cấp Dashboard Theo dõi Xe & Trải nghiệm Quản lý Trạm

## Bước 1: Mô hình hóa Dữ liệu Xe & Dịch vụ Giả lập Telemetry
- **Mục tiêu**: Định nghĩa kiểu dữ liệu phương tiện (`Vehicle`, `SimulatorConfig`) và xây dựng động cơ mô phỏng luồng telemetry di chuyển theo tuyến đường thực tế TP.HCM (`telemetrySimulator.ts`).
- **File tạo/sửa**:
  - `[NEW]` `vehicletracking-frontend/src/types/vehicle.ts`
  - `[NEW]` `vehicletracking-frontend/src/services/telemetrySimulator.ts`
- **Rủi ro & Phụ thuộc**: Phụ thuộc vào dữ liệu tọa độ `INITIAL_ROUTE` trong `mockData.ts`. Đặt tên rõ ràng là simulator tuân thủ `AGENTS.md`.
- **Điều kiện hoàn thành**: Dịch vụ simulator phát ra vị trí xe, tính toán heading và cập nhật tiến độ mỗi chu kỳ thành công.

## Bước 2: Nâng cấp Bảng Điều hành Theo dõi Xe (`TrackingPanel`) & Bộ điều khiển Simulator
- **Mục tiêu**: Xây dựng lại `TrackingPanel.tsx` thành bảng giám sát đội xe chuyên nghiệp: hiển thị số liệu tổng quan, bộ lọc trạng thái, tìm kiếm xe, danh sách thẻ xe động và thanh điều khiển Simulator.
- **File tạo/sửa**:
  - `[MODIFY]` `vehicletracking-frontend/src/components/TrackingPanel.tsx`
  - `[NEW]` `vehicletracking-frontend/src/components/SimulatorControls.tsx`
- **Rủi ro & Phụ thuộc**: Tối ưu re-render khi xe cập nhật tọa độ liên tục.
- **Điều kiện hoàn thành**: Panel hiển thị danh sách xe động, lọc theo trạng thái và cho phép điều khiển Play/Pause/Speed mô phỏng.

## Bước 3: Xây dựng Drawer Chi tiết Phương tiện (`VehicleDrawer`)
- **Mục tiêu**: Cung cấp giao diện xem chi tiết phương tiện khi được chọn: đồng hồ tốc độ, trạm kế tiếp, ETA, thanh tiến độ chuyến đi, timeline lộ trình và nút "Follow Vehicle".
- **File tạo/sửa**:
  - `[NEW]` `vehicletracking-frontend/src/components/VehicleDrawer.tsx`
- **Rủi ro & Phụ thuộc**: Tuân thủ Mục 14 & 18 của `docs/design.md`.
- **Điều kiện hoàn thành**: Nhấp chọn xe mở drawer đầy đủ thông số; bấm đóng drawer hoạt động mượt mà.

## Bước 4: Tích hợp Lớp Xe & Lộ trình vào Bản đồ (`MapComponent`)
- **Mục tiêu**: Thêm `vehicleLayer` và `routeLayer` vào Leaflet; hiển thị marker xe xoay theo hướng di chuyển (`heading`); vẽ polyline tuyến đường xe đang chạy; triển khai cơ chế "Follow Vehicle".
- **File tạo/sửa**:
  - `[MODIFY]` `vehicletracking-frontend/src/components/MapComponent.tsx`
- **Rủi ro & Phụ thuộc**: Quản lý bộ nhớ và marker instance trong Leaflet, không tạo lại marker mỗi frame để tránh lag.
- **Điều kiện hoàn thành**: Xe di chuyển mượt mà trên bản đồ, xoay đúng góc; chọn xe vẽ lộ trình và bám theo xe chính xác.

## Bước 5: Cải tiến Form & Trải nghiệm Quản lý Trạm (`StationDrawer` & `StationPanel`)
- **Mục tiêu**:
  - Nâng cấp `StationDrawer.tsx`: Thêm thanh trượt slider bán kính, nút preset bán kính (30m, 50m, 80m, 100m, 200m), inline validation và cảnh báo hủy form dở dang (dirty state).
  - Tối ưu `MapComponent.tsx`: Thêm nút "Lấy vị trí tâm bản đồ" trên banner chọn vị trí, cho phép kéo thả marker và cập nhật tức thì vòng tròn geofence theo bán kính.
  - Cải tiến `StationPanel.tsx`: Bỏ lọc theo chuỗi đầu/cuối sai lệch; bổ sung sắp xếp tên/bán kính; cải thiện hiển thị nút Sửa/Xóa trạm.
- **File tạo/sửa**:
  - `[MODIFY]` `vehicletracking-frontend/src/components/StationDrawer.tsx`
  - `[MODIFY]` `vehicletracking-frontend/src/components/StationPanel.tsx`
  - `[MODIFY]` `vehicletracking-frontend/src/components/MapComponent.tsx`
- **Rủi ro & Phụ thuộc**: Bảo toàn hoàn toàn contract gửi lên API backend `/api/v1/stations`.
- **Điều kiện hoàn thành**: Thao tác tạo/sửa trạm trực quan, có slider bán kính, có nút lấy tâm bản đồ và bảo vệ dữ liệu dở dang.

## Bước 6: Hoàn thiện CSS, Typography & Trải nghiệm Tổng thể
- **Mục tiêu**: Cập nhật CSS cho các thành phần mới (vehicle markers, simulator controls, vehicle drawer, radius slider, preset buttons) theo chuẩn dark operations shell tại `docs/design.md`.
- **File tạo/sửa**:
  - `[MODIFY]` `vehicletracking-frontend/src/index.css`
  - `[MODIFY]` `vehicletracking-frontend/src/App.tsx` (cập nhật header status kết nối telemetry)
- **Điều kiện hoàn thành**: Giao diện đồng bộ, sắc nét, responsive trên cả desktop và tablet.

## Bước 7: Kiểm thử Tự động & Xác nhận Nghiệm thu
- **Mục tiêu**: Chạy toàn bộ các bước kiểm tra chất lượng mã nguồn:
  - `npm run lint` (oxlint)
  - `./node_modules/.bin/tsc --noEmit`
  - `npm run build`
- **Điều kiện hoàn thành**: 0 lỗi lint, 0 lỗi TypeScript, build production thành công.
