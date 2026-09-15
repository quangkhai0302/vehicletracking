# Research: Kỹ thuật Mô phỏng Vận chuyển & Tương tác Bản đồ Leaflet

## 1. Mục tiêu Nghiên cứu
Xác định giải pháp kỹ thuật tối ưu cho:
1. Hiển thị và cập nhật liên tục vị trí phương tiện trên Leaflet mà không gây lag hoặc giật màn hình.
2. Xoay biểu tượng xe (`heading`/bearing) theo hướng di chuyển dọc polyline.
3. Cơ chế nội suy tọa độ mượt mà (polyline interpolation) và tính toán khoảng cách/ETA.
4. Quản lý trạng thái form trực quan và vòng đời dọn dẹp (cleanup) các layer Leaflet trong React 19.

## 2. Các Lựa chọn Kỹ thuật & Quyết định

### 2.1 Hiển thị Vehicle Marker & Xoay Heading
- **Phương án A: Thư viện ngoài (ví dụ `leaflet-rotatedmarker`)**: Cần cài thêm npm package cũ, có rủi ro không tương thích với Leaflet 1.9+ và React 19/Vite ESM.
- **Phương án B: `L.divIcon` kết hợp CSS Transform (Khuyến nghị)**:
  - Tạo `L.divIcon` chứa thẻ SVG xe hoặc icon phương tiện.
  - Xoay hướng xe trực tiếp bằng CSS style inline: `transform: rotate(${heading}deg); transform-origin: center center;`.
  - Ưu điểm: Không cần dependency bên ngoài, hoàn toàn tương thích ESM/Vite, hiệu năng GPU cực cao, dễ dàng tùy biến màu sắc và trạng thái (xanh/vàng/đỏ).

### 2.2 Thuật toán Nội suy Vị trí & Góc hướng (Polyline Interpolation & Heading)
- Cho một tuyến đường gồm mảng các điểm `[[lat0, lng0], [lat1, lng1], ...]`.
- Đoạn đường được tính tổng chiều dài $D = \sum d_i$ theo công thức Haversine hoặc xấp xỉ phẳng (với cự ly nhỏ trong đô thị).
- Với tiến độ di chuyển $p \in [0, 1]$, xác định đoạn thẳng $[A, B]$ mà xe đang ở giữa, tính tọa độ nội suy tuyến tính:
  $$lat = lat_A + t \cdot (lat_B - lat_A), \quad lng = lng_A + t \cdot (lng_B - lng_A)$$
- Góc hướng chuyển động (`bearing`):
  $$\theta = \text{atan2}(\sin(\Delta lng) \cdot \cos(lat_B), \cos(lat_A) \cdot \sin(lat_B) - \sin(lat_A) \cdot \cos(lat_B) \cdot \cos(\Delta lng))$$
- Chuyển đổi sang độ ($0^\circ - 360^\circ$) để truyền vào CSS `rotate()`.

### 2.3 Quản lý Layer & Hiệu năng Leaflet trong React 19
- Giữ vững nguyên tắc từ `AGENTS.md` và `docs/design.md`:
  - Không khởi tạo lại instance `L.map` khi state thay đổi.
  - Sử dụng `L.layerGroup` riêng biệt cho `vehicleLayer`, `routeLayer`, `stationLayer`, `draftLayer`.
  - Tái sử dụng marker instance (`Map<string, L.Marker>`) và chỉ gọi `marker.setLatLng()` thay vì xoá và tạo lại marker mỗi frame.
  - Bộ mô phỏng chạy bằng timer nhịp đều (tick 1000ms hoặc 500ms nhân với multiplier), khi component unmount phải `clearInterval` dọn dẹp sạch sẽ.

### 2.4 Trải nghiệm Form & Bán kính Geofence
- Bán kính check-in trạm là thông số quan trọng (theo `StationEntity.checkinRadiusMeters`, 10m - 1000m).
- Cung cấp:
  - Nút bấm nhanh (Presets): `30m`, `50m`, `80m`, `100m`, `200m`.
  - Input type `range` (slider) từ `10m` đến `500m` đồng bộ với ô số.
  - Vòng tròn `L.circle` trên draftLayer cập nhật kích thước tức thì bằng `circle.setRadius()`.

## 3. Kết luận
- Sử dụng công nghệ sẵn có trong repo (`leaflet`, `lucide-react`, `react 19`), không cần cài đặt thêm thư viện bên ngoài.
- Dữ liệu mock phục vụ simulator tuân thủ tuyệt đối quy định đặt tên rõ ràng tại `telemetrySimulator.ts`.
