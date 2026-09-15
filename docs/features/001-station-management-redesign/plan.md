# Plan: Kế hoạch Triển khai Station Management Redesign

## Bước 1: Cập nhật Typography & CSS Design Tokens
- Sửa `vehicletracking-frontend/src/index.css`:
  - Import và cấu hình font Inter theo Mục 3 của `docs/design.md`.
  - Cấu hình các biến CSS `--color-*` theo đúng semantic color system.
  - Tối ưu layout cho station list panel, contextual drawer, map markers, geofence circle và toast notification.

## Bước 2: Cập nhật Station Drawer Component
- Sửa `vehicletracking-frontend/src/components/StationDrawer.tsx`:
  - Thêm mode badge `BROWSE`, `CREATE`, `EDIT`.
  - Tái cấu trúc phần hiển thị chi tiết theo định dạng thuộc tính dọc trực quan theo Mục 12 `docs/design.md` (bỏ dạng 4 mini metric cards).
  - Bổ sung `aria-label` cho các nút bấm (đóng, chọn vị trí).

## Bước 3: Cập nhật Station Panel Component
- Sửa `vehicletracking-frontend/src/components/StationPanel.tsx`:
  - Đồng bộ mode hiển thị ở footer ("Đang tạo trạm mới", "Đang chỉnh sửa trạm").
  - Đảm bảo các thuộc tính trợ năng (`aria-label`, `aria-live`).

## Bước 4: Cập nhật Map Component
- Sửa `vehicletracking-frontend/src/components/MapComponent.tsx`:
  - Thêm thao tác pan/center bản đồ khi nhấp trực tiếp vào marker trạm trên bản đồ.
  - Đảm bảo xử lý hủy (Cancel) ở chế độ EDIT khôi phục dữ liệu gốc chính xác và xóa triệt để draft layer.

## Bước 5: Chạy Kiểm thử và Build
- Chạy `npm run lint` (oxlint).
- Chạy `./node_modules/.bin/tsc --noEmit`.
- Chạy `npm run build` (Vite production build trên Node 24).
- Ghi nhận kết quả vào `evidence.md`, `walkthrough.md` và `review.md`.
