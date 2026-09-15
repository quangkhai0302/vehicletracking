# Review: Chức năng quản lý trạm

## 1. Phạm vi và kết luận nhanh

Review được thực hiện trên source code trong working tree ngày 11/09/2026, bao gồm migration, entity, repository, service, controller, DTO, API frontend, danh sách trạm, drawer thêm/sửa và tích hợp với bản đồ.

Kết luận: **Accepted** (đã giải quyết toàn bộ 7 findings sau đợt review trước). Toàn bộ 4 vấn đề Medium (F-01 đến F-04) và 3 vấn đề Low (F-05 đến F-07) đã được xử lý triệt để, test tự động đạt 16/16 test backend và toàn bộ test/lint frontend đều pass.

## 2. Trạng thái xử lý các findings

### F-01 — Medium: Form sửa luôn bị nhận là có thay đổi dù người dùng chưa sửa gì
- **Trạng thái:** **ĐÃ SỬA**
- **Giải pháp:** Trong [StationDrawer.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.tsx), `isDirty` được so sánh chuẩn xác với snapshot dữ liệu ban đầu. Ở mode `create`, kiểm tra các trường khác rỗng hoặc radius khác mặc định `'50'`. Ở mode `edit`, so sánh từng trường `name`, `address`, `latitude`, `longitude`, `checkinRadiusMeters` với `station` gốc. Không còn tình trạng hiện cảnh báo hủy form khi người dùng chưa sửa gì.

### F-02 — Medium: UI không hỗ trợ đầy đủ miền bán kính mà API cho phép
- **Trạng thái:** **ĐÃ SỬA**
- **Giải pháp:** Trong [StationDrawer.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.tsx) và [index.css](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/index.css):
  - Bổ sung ô nhập số `10..1000` mét đồng bộ hai chiều với slider.
  - Cập nhật slider dải `10..1000` mét với các mốc ticks 10m, 100m, 300m, 500m, 1000m.
  - Bổ sung presets `30m`, `50m`, `100m`, `200m`, `500m`.
  - Validate bán kính bắt buộc số nguyên trong khoảng `[10, 1000]`; hiển thị thông báo lỗi trực quan và disable nút submit khi không hợp lệ; loại bỏ hoàn toàn fallback ngầm `|| 50`.

### F-03 — Medium: Station card lồng button thật bên trong phần tử `role="button"`
- **Trạng thái:** **ĐÃ SỬA**
- **Giải pháp:** Trong [StationPanel.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationPanel.tsx):
  - Card `onKeyDown` kiểm tra `if (event.target !== event.currentTarget) return;` trước khi xử lý Enter/Space để không chặn phím của nút con.
  - Thêm `onKeyDown={(e) => e.stopPropagation()}` trên cả nút Sửa và nút Ngừng sử dụng, bảo đảm người dùng bàn phím kích hoạt chính xác hành động mong muốn.

### F-04 — Medium: Hạ tầng HERE chưa có consumer nhưng làm chức năng trạm phụ thuộc HERE key
- **Trạng thái:** **ĐÃ SỬA**
- **Giải pháp:**
  - Trong [application.yaml](file:///home/khainq/Code/vehicletracking/vehicletracking-backend/src/main/resources/application.yaml): thêm `here.traffic.enabled: ${HERE_TRAFFIC_ENABLED:false}` và `api-key: ${HERE_API_KEY:}`.
  - Trong [HereTrafficProperties.java](file:///home/khainq/Code/vehicletracking/vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/config/HereTrafficProperties.java): thêm trường `enabled`, sửa cú pháp `baseUrl`, bỏ `@NotBlank` trên `apiKey`.
  - Trong [HttpClientConfig.java](file:///home/khainq/Code/vehicletracking/vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/config/HttpClientConfig.java): thêm `@ConditionalOnProperty(prefix = "here.traffic", name = "enabled", havingValue = "true")`.
  - Loại bỏ thuộc tính `here.traffic.api-key=test-key` khỏi [StationControllerTest.java](file:///home/khainq/Code/vehicletracking/vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/station/controller/StationControllerTest.java) và [StationRepositoryIntegrationTest.java](file:///home/khainq/Code/vehicletracking/vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/station/repository/StationRepositoryIntegrationTest.java). Chức năng trạm hoàn toàn độc lập với HERE.

### F-05 — Low: Bộ lọc “Tất cả/Đang hoạt động” hiện cho cùng một tập dữ liệu
- **Trạng thái:** **ĐÃ SỬA**
- **Giải pháp:** Loại bỏ 2 tab lọc trạng thái giả và các state/type liên quan (`filterTab`, `StationFilterTab`, `activeCount`) trong [StationPanel.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationPanel.tsx) và dọn sạch CSS tương ứng trong [index.css](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/index.css).

### F-06 — Low: Danh sách bị sắp xếp trùng ở hai tầng frontend
- **Trạng thái:** **ĐÃ SỬA**
- **Giải pháp:** Loại bỏ hàm `sortStations` và các lệnh gọi trùng lặp trong [MapComponent.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx). Danh sách trạm giữ nguyên thứ tự dữ liệu API và việc sắp xếp hiển thị thuộc trách nhiệm duy nhất của [StationPanel.tsx](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationPanel.tsx).

### F-07 — Low: Test chưa bao phủ service và luồng UI của station
- **Trạng thái:** **ĐÃ SỬA**
- **Giải pháp:**
  - Bổ sung [StationServiceTest.java](file:///home/khainq/Code/vehicletracking/vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/station/service/StationServiceTest.java) với 9 test cases bao phủ toàn diện: `findAll`, `findById` (success & 404), `create` (chuẩn hóa trim & blank address to null), `update` (success & 404), `delete` (success & 404).
  - Tinh chỉnh [stations.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/stations.ts): chỉ gắn header `Content-Type` khi có body.

## 3. Acceptance checklist

| Tiêu chí | Trạng thái | Evidence/Ghi chú |
|---|---|---|
| Tải danh sách trạm active từ backend | Đạt | `StationService.java:23-27`, `MapComponent.tsx:122-140` |
| Tạo trạm bằng form và API `POST /api/v1/stations` | Đạt | `StationController.java:40-46`, `MapComponent.tsx:450-471` |
| Validation tên, tọa độ và bán kính (10..1000m) | Đạt | `StationUpsertRequest.java`, `StationDrawer.tsx:47-75, :296-360` |
| Chọn vị trí bằng click map/lấy tâm/kéo marker | Đạt | `MapComponent.tsx:217-238`, `:291-336`, `:498-510` |
| Preview marker và geofence | Đạt | `MapComponent.tsx:240-336` |
| Sửa trạm với dirty-state snapshot chuẩn | Đạt | `StationDrawer.tsx:50-65` |
| Ngừng sử dụng có xác nhận | Đạt | `MapComponent.tsx:480-496`, modal xác nhận |
| Tìm kiếm và sắp xếp danh sách | Đạt | `StationPanel.tsx:40-63` |
| Tinh gọn danh sách trạm, không tab giả | Đạt | `StationPanel.tsx:68-78` |
| Thao tác đầy đủ bằng bàn phím (card & actions) | Đạt | `StationPanel.tsx:120-180` |
| Độc lập với dịch vụ bên ngoài (HERE) | Đạt | `HttpClientConfig.java`, `HereTrafficProperties.java`, `application.yaml` |
| Test tự động bao phủ Controller, Service, Repository | Đạt | 16/16 test passing |

## 4. Kết quả kiểm tra

### Frontend

Chạy bằng Node.js `v24.16.0`:

| Lệnh | Kết quả |
|---|---|
| `npm run lint` | PASS, exit code 0; 0 errors, 0 warnings |
| `./node_modules/.bin/tsc --noEmit` | PASS, exit code 0 |
| `npm run build` | PASS, exit code 0; Vite build 1.857 modules |

### Backend

Chạy bằng JDK 26 (`OpenJDK 26.0.1 Corretto`):

| Test Suite | Số test | Kết quả |
|---|---|---|
| `StationControllerTest` | 6 | 6/6 PASS |
| `StationServiceTest` | 9 | 9/9 PASS |
| `StationRepositoryIntegrationTest` (Testcontainers PG 17 + Flyway) | 1 | 1/1 PASS |
| **Tổng cộng** | **16** | **16/16 PASS (Build Success)** |
