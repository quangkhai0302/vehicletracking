# Walkthrough 008

## 1. Cấu hình backend

Trong environment của backend đặt `HERE_TRAFFIC_ENABLED=true`, `HERE_API_KEY`,
`HERE_TRAFFIC_BASE_URL=https://data.traffic.hereapi.com` và
`HERE_TRAFFIC_TILE_BASE_URL=https://traffic.maps.hereapi.com`, timeout và TTL theo
`.env.example`. Không đặt key trong `vehicletracking-frontend/.env` hoặc biến `VITE_*`.

Khởi động PostgreSQL/Flyway và backend như các feature trước. Nếu key trống khi bật traffic, Spring fail validation có kiểm soát.

## 2. Kiểm tra API nội bộ

Sau khi backend chạy, gọi bbox nhỏ qua backend (không gọi host HERE từ browser):

```text
GET http://localhost:8080/api/v1/traffic/flow?west=106.69&south=10.77&east=106.71&north=10.79
GET http://localhost:8080/api/v1/traffic/incidents?west=106.69&south=10.77&east=106.71&north=10.79
GET http://localhost:8080/api/v1/trips/{tripId}/eta
```

Response có `source`, `status`, `observedAt`, `fetchedAt`, `ageSeconds`, `results`. Khi upstream lỗi sau một lần thành công, response có thể là `HERE_LAST_KNOWN`/`STALE`; khi chưa có dữ liệu, ETA dùng `ROUTE_SNAPSHOT` và nêu warning.

## 3. Kiểm tra UI

- Mở `Lớp bản đồ → Giao thông trực tiếp`; bản đồ tải raster tile HERE 256px qua
  `/api/v1/traffic/tiles/{z}/{x}/{y}.png`, incident dùng marker theo criticality. Leaflet
  tự tải tile theo viewport khi pan/zoom; request flow JSON nặng không còn chạy cho lớp bản đồ.
- Flow JSON vẫn được backend gọi theo corridor để tính ETA/matching, không gửi toàn bộ payload
  đó xuống trình duyệt.
- Chọn một chuyến trong Đội xe hoặc Mô phỏng. ETA theo stop, source badge, timestamp và trạng thái blocked/fallback xuất hiện trong panel.
- Khi HERE unavailable, UI giữ route/check-in/simulator cũ và hiển thị retry; không tạo incident giả và không lộ API key.
