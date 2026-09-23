# Survey — Báo cáo và thống kê vận hành

## Backend hiện tại

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Chuyến có xe, tuyến, tài xế tùy chọn, thời gian dự kiến, lifecycle timestamps và stops | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/entity/TripEntity.java` — `TripEntity`; `TripStopEntity` | Có thể lọc theo xe/tài xế và tính runtime/plan end |
| Tuyến lưu tổng quãng đường và thời lượng ước tính | `.../route/entity/RouteEntity.java` — `totalDistanceMeters`, `estimatedTripDurationSeconds` | Báo cáo dùng distance planned, không giả vờ là GPS distance |
| Repository chuyến hiện chưa có date-range query | `.../trip/repository/TripRepository.java` — các finder hiện có | Cần JPQL aggregate scope theo scheduled departure |
| Telemetry lưu trip/attempt/recordedAt/speed/source dạng append-only | `.../telemetry/entity/TelemetrySampleEntity.java`; `TelemetryRepository.java` | Có thể đếm episode quá tốc độ theo thứ tự GPS |
| Notification có type, trip và createdAt; có enum `OFF_ROUTE_DETECTED` | `.../reroute/entity/TripNotificationEntity.java`; `NotificationType.java` | Có thể đếm sự kiện lệch tuyến đã dedupe |
| Chưa có chính sách speed limit | `.../telemetry/service/TelemetryService.java` — validation chỉ giới hạn 0..500 | Cần config `reporting.default-speed-limit-kmh` và nêu rõ trong UI |

## Frontend hiện tại

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| `/reports` đang render roadmap | `vehicletracking-frontend/src/App.tsx` — route `reports` | Thay bằng trang dữ liệu thật |
| Menu có cờ `planned` và topbar dùng cờ này để hiển thị Roadmap | `.../app/routeConfig.ts`; `.../app/ApplicationShell.tsx` | Bỏ cờ cho Báo cáo sau khi feature sẵn sàng |
| Service fleet đã cung cấp danh sách xe/tài xế và pattern AbortSignal | `.../services/fleet.ts` | Tái sử dụng metadata cho bộ lọc |
| Business page hiện có pattern loading/error/empty và responsive CSS | `.../pages/DashboardPage.tsx`; `.../pages/ScheduleManagementPage.tsx` | Trang báo cáo theo cùng UX shell |

## Rủi ro

- Distance planned không phản ánh sai lệch thực tế; UI phải ghi chú đây là tổng chiều dài tuyến đã lập.
- Replay có thể thay đổi timestamp của cùng trip trong khi telemetry giữ attempt; báo cáo hiện theo record trip hiện tại và đếm telemetry theo attempt.
- Query telemetry lịch sử lớn cần index chuyên dụng trong tương lai; MVP giới hạn 366 ngày và bằng trip IDs đã lọc.
