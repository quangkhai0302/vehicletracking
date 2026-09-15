# Test plan

| AC | Kiểm tra | Kết quả |
|---|---|---|
| 1 | `HereTrafficProviderTest`, `TrafficQueryServiceTest` | URL/style và cache tile được kiểm tra |
| 2 | Type check + production build frontend | Leaflet tạo base layer và traffic layer độc lập |
| 3 | Review source | Chỉ frontend gọi API nội bộ |
