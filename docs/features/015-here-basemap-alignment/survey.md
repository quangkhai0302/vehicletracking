# Survey

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Tuyến lưu flexible polyline từ HERE | `MapComponent.tsx`, effect “Render Planned Route” | Không có phép biến đổi tọa độ ở frontend |
| Nền cũ là Google tile | `MapComponent.tsx`, effect base map trước feature | Dễ khác dữ liệu lane/bridge so với HERE |
| Traffic tile đã đi qua backend | `TrafficController.tile`, `HereTrafficProvider.fetchTile` | Có thể tái sử dụng mô hình bảo mật/caching |
