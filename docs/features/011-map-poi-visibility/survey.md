# Survey

- `MapComponent.tsx`: state showTraffic/showStations/showRoutes; effect base tile gọi Google `/vt` với lyrs m/y và traffic. Chưa có POI state. Comment cũ về HERE raster không còn khớp source.
- `MapControls.tsx`: nhóm Chi tiết bản đồ dùng checkbox native, có sẵn Trạm dừng và Tuyến & điểm nháp.
- `TrafficLayer.tsx`: render incident markers HERE riêng; `useVehicleMarkers.ts` render xe riêng. Không cần thay backend/config/migration để truyền tham số hiển thị cho nền đang dùng.
- Git status sạch trước thay đổi lượt này. Không có docs/templates.

