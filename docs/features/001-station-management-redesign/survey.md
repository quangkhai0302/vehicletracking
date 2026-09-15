# Survey: Khảo sát Hiện trạng Source Code

## 1. Cấu trúc file liên quan
- `vehicletracking-frontend/src/App.tsx`: Chứa navigation chuyển đổi workspace (`tracking` vs `stations`).
- `vehicletracking-frontend/src/components/MapComponent.tsx`: Quản lý Leaflet map instance, layers trạm/draft, handlers tương tác bản đồ.
- `vehicletracking-frontend/src/components/StationPanel.tsx`: Component danh sách trạm và thanh tìm kiếm.
- `vehicletracking-frontend/src/components/StationDrawer.tsx`: Component drawer chi tiết và form chỉnh sửa/tạo mới.
- `vehicletracking-frontend/src/types/station.ts`: Định nghĩa kiểu dữ liệu `Station`, `StationInput`, `StationFormMode`, `StationFormState`.
- `vehicletracking-frontend/src/services/stations.ts`: Wrapper API gọi backend Spring Boot (`/api/stations`).
- `vehicletracking-frontend/src/index.css`: Toàn bộ CSS styling, biến giao diện và responsive layout.

## 2. Luồng dữ liệu hiện tại
- `fetchStations()` tải danh sách `Station[]` từ backend `GET /api/stations`.
- `MapComponent` giữ state trung tâm: `stations`, `selectedStationId`, `formMode` (`closed` | `create` | `edit`), `stationForm`, `pickingLocation`.
- Khi chọn trạm: `selectedStationId` được gán -> `StationDrawer` mở ra hiển thị thông tin trạm -> geofence circle được vẽ.
- Khi sửa/tạo trạm: `formMode` đổi sang `create` hoặc `edit` -> `StationDrawer` render form -> Leaflet hiển thị draft marker kéo thả được và preview geofence.
- Lưu trạm: gọi `createStation()` hoặc `updateStation()` -> cập nhật state -> toast thông báo thành công.
- Ngừng sử dụng trạm: mở confirmation dialog -> gọi `deleteStation()` (`DELETE /api/stations/{id}`).

## 3. Bảng Evidence

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| API contract giữ nguyên | `vehicletracking-frontend/src/services/stations.ts:1-45` | Không cần và không được thay đổi backend API |
| Type definition trạm đầy đủ | `vehicletracking-frontend/src/types/station.ts:1-35` | Tái sử dụng `Station`, `StationInput`, `StationFormMode`, `StationFormState` |
| Font hiện tại chưa chuẩn | `vehicletracking-frontend/src/index.css:4-5` | Cần đổi Outfit / Plus Jakarta Sans sang Inter theo `docs/design.md` |
| Detail drawer có metric cards nhỏ | `vehicletracking-frontend/src/components/StationDrawer.tsx:55-60` | Cần chuẩn hóa thành property list theo Mục 12 `docs/design.md` |
| Click marker trên bản đồ chưa center | `vehicletracking-frontend/src/components/MapComponent.tsx:236-241` | Cần bổ sung `setView` khi click marker trên bản đồ |
| Mode BROWSE/CREATE/EDIT chưa có badge rõ ràng | `vehicletracking-frontend/src/components/StationDrawer.tsx:43-46` | Cần hiển thị badge trạng thái mode trực quan |
