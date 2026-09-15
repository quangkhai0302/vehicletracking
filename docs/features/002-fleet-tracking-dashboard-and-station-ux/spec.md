# Spec: Đặc tả Kỹ thuật Nâng cấp Dashboard Theo dõi Xe & Form Quản lý Trạm

## 1. Kiến trúc Tổng thể & Luồng Dữ liệu

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ Header: Logo | Chuyển đổi Mode [Theo dõi xe | Quản lý trạm] | Telemetry Live│
├───────────────────┬─────────────────────────────────────┬───────────────────┤
│ Left Panel:       │ Main Center Workspace:              │ Right Drawer:     │
│ - TrackingPanel   │ - Leaflet Map Canvas                │ - VehicleDrawer   │
│   (Metrics, Lọc,  │ - Vehicle Layer (di chuyển, xoay)   │   (Vận tốc, ETA,  │
│    Danh sách xe)  │ - Route Polyline Layer              │    Timeline trạm) │
│ HOẶC              │ - Station Layer                     │ HOẶC              │
│ - StationPanel    │ - Draft Layer (Picking / Geofence)  │ - StationDrawer   │
│   (Search, Lọc,   │ - Simulator Controls (Play/Pause)   │   (Detail Browse/ │
│    Danh sách trạm)│ - Map Theme & Center Controls       │    Create/Edit)   │
└───────────────────┴─────────────────────────────────────┴───────────────────┘
```

## 2. Mô hình Dữ liệu Mới (Frontend Domain Models)

### 2.1 Kiểu dữ liệu Phương tiện (`types/vehicle.ts`)
```typescript
export type VehicleStatus = 'RUNNING' | 'AT_STATION' | 'DELAYED' | 'OFFLINE';

export interface Vehicle {
  id: string;
  plateNumber: string;         // Ví dụ: '51B-299.88'
  model: string;               // Ví dụ: 'Thaco City Bus'
  routeId: string;             // Ví dụ: 'route-01'
  routeName: string;           // 'Tuyến 01: Bến xe Miền Đông ⇄ Bến Thành'
  driverName: string;          // Ví dụ: 'Nguyễn Văn Hùng'
  driverPhone: string;         // Ví dụ: '0903 123 456'
  speedKmh: number;            // Vận tốc hiện tại: 18 - 45 km/h
  desiredSpeedKmh: number;     // Vận tốc mong muốn: 45 km/h
  speedLimitKmh: number;       // Giới hạn tốc độ: 50 km/h
  status: VehicleStatus;       // Trạng thái vận hành
  latitude: number;
  longitude: number;
  heading: number;             // Góc xoay 0 - 360 độ
  currentStationId?: string;
  nextStationName: string;     // 'Thảo Cầm Viên'
  etaMinutes: number;          // 4
  distanceToNextMeters: number;// 850
  tripProgressPercent: number; // 64%
  timeline: Array<{
    stationName: string;
    plannedTime: string;
    actualOrEtaTime: string;
    status: 'PASSED' | 'CURRENT' | 'UPCOMING';
  }>;
}

export interface SimulatorConfig {
  isRunning: boolean;
  multiplier: 1 | 2 | 5 | 10;
  lastUpdated: string;
}
```

## 3. UI Contract & Hành vi Chi tiết

### 3.1 Chế độ Theo dõi Xe (`workspace === 'tracking'`)

#### A. Fleet Operations Panel (Bên trái)
- **Header & Metrics**:
  - Huy hiệu tổng số xe: `{total} xe`.
  - 3 thẻ chỉ số nhanh:
    - Đang chạy (Xanh lá)
    - Trễ lịch (Vàng hổ phách)
    - Dừng đỗ / Chờ (Xanh dương)
- **Bộ lọc & Tìm kiếm**:
  - Ô tìm kiếm tức thời theo biển số xe hoặc tên tuyến.
  - Các tab lọc: `Tất cả` | `Đang chạy` | `Trễ lịch`.
- **Danh sách Vehicle Card**:
  - Biển số in đậm (VD: `51B-299.88`), biểu tượng trạng thái nhấp nháy tinh tế.
  - Vận tốc tức thời (tabular-nums), trạm tiếp theo và ETA.
  - Nhấp vào card: Đặt `selectedVehicleId`, di chuyển tâm bản đồ tới vị trí xe, hiển thị polyline tuyến của xe và mở `VehicleDrawer`.

#### B. Bản đồ & Vehicle Rendering
- **Vehicle Marker**:
  - Dùng `L.divIcon` hiển thị icon xe độc quyền.
  - Áp dụng `transform: rotate(${vehicle.heading}deg)`.
  - Viền phát sáng nhẹ khi xe được chọn.
  - Tooltip hiển thị: `[Biển số] - [Vận tốc] km/h`.
- **Route Polyline**:
  - Vẽ lộ trình solid line màu xanh dương đậm nét theo Mục 16 `docs/design.md`.
  - Hiển thị các điểm dừng chính trên tuyến.
- **Simulator Floating Bar**:
  - Đặt góc trên/dưới thuận tiện hoặc gắn liền map controls:
    - Nút Chạy/Tạm dừng (`Play`/`Pause`).
    - Nút Khởi động lại (`RotateCcw`).
    - Bộ chọn tốc độ (`1x`, `2x`, `5x`, `10x`).
    - Nhãn dán: `MÔ PHỎNG TELEMETRY`.

#### C. Vehicle Detail Drawer (Bên phải)
- Mở khi người dùng chọn một xe; có nút Đóng `X`.
- Hiển thị theo Mục 14 `docs/design.md`:
  - Biển số, loại xe, tài xế, số điện thoại.
  - Thước đo vận tốc: Hiện tại / Mong muốn / Giới hạn đường.
  - Thẻ trạm tiếp theo: Tên trạm, khoảng cách (m), thời gian dự kiến đến nơi (ETA phút).
  - Thanh tiến độ hành trình (Trip progress bar).
  - Timeline lộ trình theo Mục 18 `docs/design.md` (Planned, Actual, ETA).
  - Nút bật/tắt chế độ "Bám theo xe" (`Follow Vehicle`).

---

### 3.2 Chế độ Quản lý Trạm (`workspace === 'stations'`)

#### A. Cải tiến Form Thêm/Sửa Trạm trong `StationDrawer`
- **Validation tại chỗ (Inline Validation)**:
  - Bắt buộc Tên trạm không được rỗng hoặc chỉ toàn khoảng trắng.
  - Vĩ độ phải thuộc `[-90, 90]`, Kinh độ thuộc `[-180, 180]`.
  - Bán kính check-in hợp lệ từ `10` đến `1000` mét.
- **Bộ chọn bán kính check-in thông minh**:
  - Nút Quick Presets: `30m`, `50m`, `80m`, `100m`, `200m`.
  - Thanh trượt Slider (`input type="range"`, min=10, max=500, step=5) đồng bộ 2 chiều với ô nhập số.
  - Vòng tròn Geofence trên bản đồ phản hồi co giãn tức thì theo từng pixel người dùng kéo slider.
- **Bảo vệ dữ liệu dở dang (Dirty State Guard)**:
  - Kiểm tra xem form đã bị chỉnh sửa so với dữ liệu ban đầu hay chưa.
  - Nếu người dùng bấm Hủy hoặc nút Đóng khi form đã nhập liệu, hiển thị xác nhận nhẹ: *"Bạn có muốn hủy các thay đổi chưa lưu?"* trước khi đóng.

#### B. Cải tiến Trải nghiệm Chọn Tọa độ trên Bản đồ (`MapComponent`)
- **Banner hướng dẫn nổi**:
  - Thiết kế bo tròn hiện đại, hiển thị tọa độ đang trỏ tới.
  - Nút tiện ích: **"Lấy vị trí tâm bản đồ"** giúp người dùng gán ngay tọa độ trung tâm màn hình mà không cần nhấp chuột.
  - Nút **"Hủy chọn"** để quay lại trạng thái trước đó nhanh chóng.
- **Marker dự thảo (Draft Marker)**:
  - Cho phép kéo thả trực tiếp trên bản đồ (`draggable: true`).
  - Khi kéo marker, tọa độ trên form cập nhật theo thời gian thực.

#### C. Tối ưu Danh sách Trạm (`StationPanel`)
- **Loại bỏ tab lọc giả định**: Thay các tab lọc theo chuỗi (Đầu/Cuối) bằng:
  - Lọc trạng thái: `Tất cả` | `Đang hoạt động`.
  - Sắp xếp: Theo tên (A-Z), Theo bán kính (Lớn → Nhỏ).
- **Hành động trên thẻ trạm**:
  - Nút Sửa và Xóa hiển thị rõ ràng, có độ tương phản tốt, không bị ẩn hoàn toàn để tối ưu cho cả chuột lẫn cảm ứng.
- **Hộp thoại Ngừng sử dụng Trạm (Confirmation Dialog)**:
  - Trình bày trực quan với tên trạm in đậm, địa chỉ và tọa độ.
  - Thông báo rõ: *"Trạm sẽ chuyển sang trạng thái ngừng hoạt động và không hiển thị cho các tuyến xe mới."*
  - Nút Hủy và Xác nhận với trạng thái loading an toàn.

## 4. Acceptance Criteria Mapping

| ID | Tiêu chí Nghiệm thu | Cơ chế Kiểm chứng |
|---|---|---|
| AC-01 | Danh sách xe hiển thị đầy đủ biển số, vận tốc, ETA, metric tổng quan | Kiểm tra UI `TrackingPanel` với dữ liệu telemetry |
| AC-02 | Xe di chuyển mượt mà trên bản đồ theo polyline, xoay heading chính xác | Quan sát visual Leaflet canvas, inspect marker transform |
| AC-03 | Bộ điều khiển Simulator có nút Play/Pause/Reset và tốc độ 1x-10x | Tương tác bấm nút, quan sát chu kỳ cập nhật vị trí xe |
| AC-04 | Chọn xe hiển thị lộ trình polyline, mở VehicleDrawer và hỗ trợ Follow Vehicle | Nhấp xe trên panel/map, kiểm tra drawer và pan map |
| AC-05 | Chọn tọa độ trạm hỗ trợ click map, kéo thả marker và nút lấy tâm bản đồ | Thao tác trên chế độ Create/Edit trạm |
| AC-06 | Slider bán kính và nút preset 30m-200m phản hồi tức thì lên vòng tròn geofence | Kéo slider, kiểm tra bán kính `L.circle` trên bản đồ |
| AC-07 | Cảnh báo khi hủy form đang nhập dở | Nhập form, bấm Hủy, kiểm tra hiển thị xác nhận |
| AC-08 | Hộp thoại ngừng sử dụng trạm hiển thị đầy đủ thông tin trạm | Bấm xóa trạm, kiểm tra dialog |
| AC-09 | TypeScript, Linter và Production Build đạt 100% không lỗi | `npm run lint`, `tsc --noEmit`, `npm run build` |
