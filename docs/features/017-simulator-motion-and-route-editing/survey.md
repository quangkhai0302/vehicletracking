# Survey

| Evidence | Hiện trạng |
|---|---|
| TrafficEtaService.currentSectionRate, SimulationService.emit/describe | Có trần tỷ lệ 1,5 ở hai tầng, làm tốc độ nền 10 chỉ đạt 15. |
| useVehicleMarkers.scheduleAnimations | Animation 900ms kết thúc trước bản tin 1s, camera nhảy mỗi snapshot. |
| RerouteEvaluationService.buildRevision | Đã tạo ACTIVE revision nhưng SimulationService.motion chỉ đọc route gốc. |
| RouteService.update | Chặn sửa route có chuyến; RouteCreateRequest chỉ nhận trạm. |
| CheckInService.entry | Tái tạo RouteMotion gốc để kiểm tra đoạn đi qua trạm. |

Working tree đã có nhiều sửa đổi backend/frontend; giữ toàn bộ thay đổi có sẵn. Migration mới sau V10.

