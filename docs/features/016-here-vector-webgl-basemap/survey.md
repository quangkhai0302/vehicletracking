# Survey

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Bản đồ là Leaflet và nhiều layer nghiệp vụ dùng `L.LayerGroup` | `MapComponent.tsx` | Không thay toàn bộ map runtime |
| Key HERE ở backend | `HereTrafficProperties` | MapLibre không được dùng URL HERE trực tiếp |
| Raster proxy đã tồn tại | `TrafficController.mapTile` | Mở rộng cùng ranh giới proxy/cache |
